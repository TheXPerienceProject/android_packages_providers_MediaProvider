/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "path_object.h"

#include <stddef.h>
#include <stdint.h>

#include "fpdf_edit.h"
#include "logging.h"
#include "rect.h"

#define LOG_TAG "path_object"

namespace pdfClient {

PathObject::PathObject() : PageObject(Type::Path) {}

ScopedFPDFPageObject PathObject::CreateFPDFInstance(FPDF_DOCUMENT document, FPDF_PAGE page) {
    int segment_count = segments_.size();
    if (segment_count == 0) {
        return nullptr;
    }
    // Get Start Points
    float x = segments_[0].x;
    float y = segments_[0].y;

    // Create a scoped PDFium path object.
    ScopedFPDFPageObject scoped_path_object(FPDFPageObj_CreateNewPath(x, y));
    if (!scoped_path_object) {
        return nullptr;
    }

    // Insert all segments into PDFium Path Object
    for (int i = 1; i < segment_count; ++i) {
        // Get EndPoint for current segment.
        x = segments_[i].x;
        y = segments_[i].y;
        switch (segments_[i].command) {
            case Segment::Command::Move: {
                FPDFPath_MoveTo(scoped_path_object.get(), x, y);
                break;
            }
            case Segment::Command::Line: {
                FPDFPath_LineTo(scoped_path_object.get(), x, y);
                break;
            }
            default:
                break;
        }
        if (segments_[i].is_closed) {
            FPDFPath_Close(scoped_path_object.get());
        }
    }

    // Update attributes of PDFium path object.
    if (!UpdateFPDFInstance(scoped_path_object.get(), page)) {
        return nullptr;
    }

    return scoped_path_object;
}

bool PathObject::UpdateFPDFInstance(FPDF_PAGEOBJECT path_object, FPDF_PAGE page) {
    if (!path_object) {
        return false;
    }

    // Check for Type Correctness.
    if (FPDFPageObj_GetType(path_object) != FPDF_PAGEOBJ_PATH) {
        return false;
    }

    // Set the updated Draw Mode
    int fill_mode = this->is_fill_ ? FPDF_FILLMODE_WINDING : FPDF_FILLMODE_NONE;
    if (!FPDFPath_SetDrawMode(path_object, fill_mode, is_stroke_)) {
        return false;
    }

    // Set the updated matrix.
    if (!SetDeviceToPageMatrix(path_object, page)) {
        return false;
    }

    // Set the updated Stroke Width
    FPDFPageObj_SetStrokeWidth(path_object, stroke_width_);
    // Set the updated Stroke Color
    FPDFPageObj_SetStrokeColor(path_object, stroke_color_.r, stroke_color_.g, stroke_color_.b,
                               stroke_color_.a);
    // Set the updated Fill Color
    FPDFPageObj_SetFillColor(path_object, fill_color_.r, fill_color_.g, fill_color_.b,
                             fill_color_.a);

    return true;
}

bool PathObject::PopulateFromFPDFInstance(FPDF_PAGEOBJECT path_object, FPDF_PAGE page) {
    // Count the number of segments in the Path
    int segment_count = FPDFPath_CountSegments(path_object);
    if (segment_count == 0) {
        return false;
    }

    // Get Path segments
    for (int index = 0; index < segment_count; ++index) {
        FPDF_PATHSEGMENT path_segment = FPDFPath_GetPathSegment(path_object, index);

        // Get Type for the current Path Segment
        int type = FPDFPathSegment_GetType(path_segment);
        if (type == FPDF_SEGMENT_UNKNOWN || type == FPDF_SEGMENT_BEZIERTO) {
            // Control point extraction of bezier curve is not supported by Pdfium as of now.
            return false;
        }

        // Get EndPoint for the current Path Segment
        float x, y;
        FPDFPathSegment_GetPoint(path_segment, &x, &y);

        // Get Close for the current Path Segment
        bool is_closed = FPDFPathSegment_GetClose(path_segment);

        // Add Segment to PageObject Data
        if (type == FPDF_SEGMENT_LINETO) {
            segments_.emplace_back(Segment::Command::Line, x, y, is_closed);
        } else {
            segments_.emplace_back(Segment::Command::Move, x, y, is_closed);
        }
    }

    // Get Draw Mode
    int fill_mode;
    FPDF_BOOL stroke;
    if (!FPDFPath_GetDrawMode(path_object, &fill_mode, &stroke)) {
        LOGE("Path GetDrawMode Failed!");
        return false;
    }
    is_fill_ = fill_mode;
    is_stroke_ = stroke;

    // Get Matrix
    if (!GetPageToDeviceMatrix(path_object, page)) {
        return false;
    }

    // Get Fill Color
    FPDFPageObj_GetFillColor(path_object, &fill_color_.r, &fill_color_.g, &fill_color_.b,
                             &fill_color_.a);
    // Get Stroke Color
    FPDFPageObj_GetStrokeColor(path_object, &stroke_color_.r, &stroke_color_.g, &stroke_color_.b,
                               &stroke_color_.a);
    // Get Stroke Width
    FPDFPageObj_GetStrokeWidth(path_object, &stroke_width_);

    return true;
}

bool PathObject::GetPageToDeviceMatrix(FPDF_PAGEOBJECT path_object, FPDF_PAGE page) {
    Matrix page_matrix;
    if (!FPDFPageObj_GetMatrix(path_object, reinterpret_cast<FS_MATRIX*>(&page_matrix))) {
        LOGE("GetPageMatrix failed!");
        return false;
    }

    float page_height = FPDF_GetPageHeightF(page);

    // Page to device matrix.
    device_matrix_.a = page_matrix.a;
    device_matrix_.b = (page_matrix.b != 0) ? -page_matrix.b : 0;
    device_matrix_.c = (page_matrix.c != 0) ? -page_matrix.c : 0;
    device_matrix_.d = page_matrix.d;
    device_matrix_.e = page_matrix.e + (page_height * page_matrix.c);
    device_matrix_.f = page_height - page_matrix.f - (page_height * page_matrix.d);

    return true;
}

bool PathObject::SetDeviceToPageMatrix(FPDF_PAGEOBJECT path_object, FPDF_PAGE page) {
    // Reset Previous Transformation.
    Matrix identity = {1, 0, 0, 1, 0, 0};
    if (!FPDFPageObj_SetMatrix(path_object, reinterpret_cast<FS_MATRIX*>(&identity))) {
        LOGE("SetMatrix failed!");
        return false;
    }

    float page_height = FPDF_GetPageHeightF(page);

    FPDFPageObj_Transform(path_object, 1, 0, 0, 1, 0, -page_height);
    FPDFPageObj_Transform(path_object, device_matrix_.a, -device_matrix_.b, -device_matrix_.c,
                          device_matrix_.d, device_matrix_.e, -device_matrix_.f);
    FPDFPageObj_Transform(path_object, 1, 0, 0, 1, 0, page_height);

    return true;
}

PathObject::~PathObject() = default;

}  // namespace pdfClient