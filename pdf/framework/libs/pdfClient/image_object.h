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

#ifndef MEDIAPROVIDER_PDF_JNI_PDFCLIENT_IMAGE_OBJECT_H_
#define MEDIAPROVIDER_PDF_JNI_PDFCLIENT_IMAGE_OBJECT_H_

#include <stdint.h>

#include "cpp/fpdf_scopers.h"
#include "fpdfview.h"
#include "page_object.h"

typedef unsigned int uint;

namespace pdfClient {

class ImageObject : public PageObject {
  public:
    ImageObject();

    ScopedFPDFPageObject CreateFPDFInstance(FPDF_DOCUMENT document, FPDF_PAGE page) override;
    bool UpdateFPDFInstance(FPDF_PAGEOBJECT image_object, FPDF_PAGE page) override;
    bool PopulateFromFPDFInstance(FPDF_PAGEOBJECT image_object, FPDF_PAGE page) override;

    void* GetBitmapReadableBuffer() const;

    ~ImageObject();

    int width_ = 0;
    int height_ = 0;
    ScopedFPDFBitmap bitmap_;
};

}  // namespace pdfClient

#endif  // MEDIAPROVIDER_PDF_JNI_PDFCLIENT_IMAGE_OBJECT_H_