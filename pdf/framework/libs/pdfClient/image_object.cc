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

#include "image_object.h"

#include <stddef.h>
#include <stdint.h>

#include "fpdf_edit.h"
#include "logging.h"

#define LOG_TAG "image_object"

namespace pdfClient {

BitmapFormat GetBitmapFormat(int bitmap_format) {
    switch (bitmap_format) {
        case FPDFBitmap_BGR: {
            return BitmapFormat::BGR;
        }
        case FPDFBitmap_BGRA: {
            return BitmapFormat::BGRA;
        }
        case FPDFBitmap_BGRx: {
            return BitmapFormat::BGRx;
        }
        default: {
            return BitmapFormat::Unknown;
        }
    }
}

ImageObject::ImageObject() : PageObject(Type::Image) {}

ScopedFPDFPageObject ImageObject::CreateFPDFInstance(FPDF_DOCUMENT document, FPDF_PAGE page) {
    // Create a scoped PDFium image object.
    ScopedFPDFPageObject scoped_image_object(FPDFPageObj_NewImageObj(document));
    if (!scoped_image_object) {
        return nullptr;
    }
    // Update attributes of PDFium image object.
    if (!UpdateFPDFInstance(scoped_image_object.get(), page)) {
        return nullptr;
    }
    return scoped_image_object;
}

bool ImageObject::UpdateFPDFInstance(FPDF_PAGEOBJECT image_object, FPDF_PAGE page) {
    if (!image_object) {
        return false;
    }

    // Check for Type Correctness.
    if (FPDFPageObj_GetType(image_object) != FPDF_PAGEOBJ_IMAGE) {
        return false;
    }

    // Set the updated bitmap.
    if (!FPDFImageObj_SetBitmap(nullptr, 0, image_object, bitmap_.get())) {
        return false;
    }

    // Set the updated matrix.
    if (!SetDeviceToPageMatrix(image_object, page)) {
        return false;
    }

    // Set the updated dimensions.
    width_ = FPDFBitmap_GetWidth(bitmap_.get());
    height_ = FPDFBitmap_GetHeight(bitmap_.get());

    // Set the updated bitmap format.
    bitmap_format_ = GetBitmapFormat(FPDFBitmap_GetFormat(bitmap_.get()));

    return true;
}

bool ImageObject::PopulateFromFPDFInstance(FPDF_PAGEOBJECT image_object, FPDF_PAGE page) {
    // Get bitmap.
    bitmap_ = ScopedFPDFBitmap(FPDFImageObj_GetBitmap(image_object));
    if (bitmap_.get() == nullptr) {
        return false;
    }

    // Get matrix.
    if (!GetPageToDeviceMatrix(image_object, page)) {
        return false;
    }

    // Get dimensions.
    width_ = FPDFBitmap_GetWidth(bitmap_.get());
    height_ = FPDFBitmap_GetHeight(bitmap_.get());

    // Get bitmap format.
    bitmap_format_ = GetBitmapFormat(FPDFBitmap_GetFormat(bitmap_.get()));
    if (bitmap_format_ == BitmapFormat::Unknown) {
        LOGE("Bitmap format unknown");
        return false;
    }
    return true;
}

void* ImageObject::GetBitmapBuffer() const {
    return FPDFBitmap_GetBuffer(bitmap_.get());
}

ImageObject::~ImageObject() = default;

}  // namespace pdfClient