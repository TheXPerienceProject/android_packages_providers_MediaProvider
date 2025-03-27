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

#ifndef MEDIAPROVIDER_PDF_JNI_PDFCLIENT_PAGE_OBJECT_H_
#define MEDIAPROVIDER_PDF_JNI_PDFCLIENT_PAGE_OBJECT_H_

#include <stdint.h>

#include "cpp/fpdf_scopers.h"
#include "fpdfview.h"

typedef unsigned int uint;

namespace pdfClient {

struct Color {
    uint r;
    uint g;
    uint b;
    uint a;

    Color() : Color(0, 0, 0, 255) {}
    Color(uint r, uint g, uint b, uint a) : r(r), g(g), b(b), a(a) {}

    bool operator==(const Color& other) const {
        return r == other.r && g == other.g && b == other.b && a == other.a;
    }
};

struct Matrix {
    float a;
    float b;
    float c;
    float d;
    float e;
    float f;

    Matrix() {}
    Matrix(float a, float b, float c, float d, float e, float f)
        : a(a), b(b), c(c), d(d), e(e), f(f) {}

    bool operator==(const Matrix& other) const {
        return a == other.a && b == other.b && c == other.c && d == other.d && e == other.e &&
               f == other.f;
    }
};

class PageObject {
  public:
    enum class Type {
        Unknown = 0,
        Text = 1,
        Path = 2,
        Image = 3,
    };

    Type GetType() const;
    // Returns a FPDF Instance for a PageObject.
    virtual ScopedFPDFPageObject CreateFPDFInstance(FPDF_DOCUMENT document, FPDF_PAGE page) = 0;
    // Updates the FPDF Instance of PageObject present on Page.
    virtual bool UpdateFPDFInstance(FPDF_PAGEOBJECT page_object, FPDF_PAGE page) = 0;
    // Populates data from FPDFInstance of PageObject present on Page.
    virtual bool PopulateFromFPDFInstance(FPDF_PAGEOBJECT page_object, FPDF_PAGE page) = 0;

    virtual ~PageObject();

    Matrix matrix_;  // Matrix used to scale, rotate, shear and translate the page object.
    Color fill_color_;
    Color stroke_color_;
    float stroke_width_ = 1.0f;

  protected:
    PageObject(Type type = Type::Unknown);

  private:
    Type type_;
};

}  // namespace pdfClient

#endif  // MEDIAPROVIDER_PDF_JNI_PDFCLIENT_PAGE_OBJECT_H_