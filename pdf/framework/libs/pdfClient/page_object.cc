/*
 * Copyright (C) 2024 The Android Open Source Project
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

#include "page_object.h"

#include <stddef.h>
#include <stdint.h>

#include "cpp/fpdf_scopers.h"
#include "fpdf_edit.h"
#include "fpdfview.h"
#include "logging.h"
#include "rect.h"

#define LOG_TAG "page_object"

namespace pdfClient {

PageObject::PageObject(Type type) : type_(type) {}

PageObject::Type PageObject::GetType() const {
    return type_;
}

bool PageObject::GetPageToDeviceMatrix(FPDF_PAGEOBJECT page_object, FPDF_PAGE page) {
    Matrix page_matrix;
    if (!FPDFPageObj_GetMatrix(page_object, reinterpret_cast<FS_MATRIX*>(&page_matrix))) {
        LOGE("GetPageMatrix failed!");
        return false;
    }

    // Set identity transformation for GetBounds.
    Matrix identity = {1, 0, 0, 1, 0, 0};
    FPDFPageObj_SetMatrix(page_object, reinterpret_cast<FS_MATRIX*>(&identity));

    // Get Bounds.
    Rectangle_f bounds;
    FPDFPageObj_GetBounds(page_object, &bounds.left, &bounds.bottom, &bounds.right, &bounds.top);

    // Reset the original page matrix.
    FPDFPageObj_SetMatrix(page_object, reinterpret_cast<FS_MATRIX*>(&page_matrix));

    float page_height = FPDF_GetPageHeightF(page);

    // Page to device matrix.
    device_matrix_.a = page_matrix.a;
    device_matrix_.b = (page_matrix.b != 0) ? -page_matrix.b : 0;
    device_matrix_.c = (page_matrix.c != 0) ? -page_matrix.c : 0;
    device_matrix_.d = page_matrix.d;
    device_matrix_.e = page_matrix.e + ((bounds.top + bounds.bottom) * page_matrix.c);
    device_matrix_.f = page_height - page_matrix.f - ((bounds.top + bounds.bottom) * page_matrix.d);

    return true;
}

bool PageObject::SetDeviceToPageMatrix(FPDF_PAGEOBJECT page_object, FPDF_PAGE page) {
    // Reset Previous Transformation.
    Matrix identity = {1, 0, 0, 1, 0, 0};
    if (!FPDFPageObj_SetMatrix(page_object, reinterpret_cast<FS_MATRIX*>(&identity))) {
        LOGE("SetMatrix failed!");
        return false;
    }

    Rectangle_f bounds;
    FPDFPageObj_GetBounds(page_object, &bounds.left, &bounds.bottom, &bounds.right, &bounds.top);

    float page_height = FPDF_GetPageHeightF(page);

    FPDFPageObj_Transform(page_object, 1, 0, 0, 1, 0, -(bounds.top + bounds.bottom));
    FPDFPageObj_Transform(page_object, device_matrix_.a, -device_matrix_.b, -device_matrix_.c,
                          device_matrix_.d, device_matrix_.e, -device_matrix_.f);
    FPDFPageObj_Transform(page_object, 1, 0, 0, 1, 0, page_height);

    return true;
}

PageObject::~PageObject() = default;

}  // namespace pdfClient