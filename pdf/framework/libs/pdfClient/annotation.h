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

#ifndef MEDIAPROVIDER_PDF_JNI_PDFCLIENT_ANNOTATION_H_
#define MEDIAPROVIDER_PDF_JNI_PDFCLIENT_ANNOTATION_H_

#include <map>

#include "fpdf_annot.h"
#include "fpdfview.h"
#include "page_object.h"
#include "rect.h"

using pdfClient::Color;
using pdfClient::PageObject;
using pdfClient::Rectangle_f;

namespace pdfClient {
// Base class for different type of annotations
class Annotation {
  public:
    enum class Type { UNKNOWN = 0, FreeText = 1, Highlight = 2, Stamp = 3 };

    Annotation(Type type) : type_(type) {}
    virtual ~Annotation() = default;

    Type GetType() const { return type_; }

    virtual bool PopulateFromPdfiumInstance(FPDF_ANNOTATION fpdf_annot, FPDF_PAGE page) = 0;
    virtual ScopedFPDFAnnotation CreatePdfiumInstance(FPDF_DOCUMENT document, FPDF_PAGE page) = 0;
    virtual bool UpdatePdfiumInstance(FPDF_ANNOTATION fpdf_annot, FPDF_DOCUMENT document,
                                      FPDF_PAGE page) = 0;

  private:
    Type type_;
};

// This class represents a stamp annotation on a page of the pdf document. It doesn't take the
// ownership of the pdfium annotation. It takes the ownership of PdfPageObject inside it but not of
// underlying pdfium page objects
class StampAnnotation : public Annotation {
  public:
    StampAnnotation(const Rectangle_f& bounds) : Annotation(Type::Stamp) { bounds_ = bounds; }

    Rectangle_f GetBounds() const { return bounds_; }
    void SetBounds(Rectangle_f bounds) { bounds_ = bounds; }

    // Return a const reference to the list
    // Stamp annotation will have the ownership of the page objects inside it
    std::vector<PageObject*> GetObjects() const;

    void AddObject(std::unique_ptr<PageObject> pageObject) {
        // Take ownership of the PageObject
        pageObjects_.push_back(std::move(pageObject));
    }

    void RemoveObject(int index) {
        auto it = pageObjects_.begin();
        std::advance(it, index);
        pageObjects_.erase(it);
    }

    bool PopulateFromPdfiumInstance(FPDF_ANNOTATION fpdf_annot, FPDF_PAGE page) override;
    ScopedFPDFAnnotation CreatePdfiumInstance(FPDF_DOCUMENT document, FPDF_PAGE page) override;
    bool UpdatePdfiumInstance(FPDF_ANNOTATION fpdf_annot, FPDF_DOCUMENT document,
                              FPDF_PAGE page) override;

  private:
    Rectangle_f bounds_;
    std::vector<std::unique_ptr<PageObject>> pageObjects_;
};

class HighlightAnnotation : public Annotation {
  public:
    HighlightAnnotation(const std::vector<Rectangle_f>& bounds) : Annotation(Type::Highlight) {
        bounds_ = bounds;
    }

    std::vector<Rectangle_f> GetBounds() const { return bounds_; }
    void SetBounds(std::vector<Rectangle_f> bounds) { bounds_ = bounds; }

    Color GetColor() const { return color_; }
    void SetColor(Color color) { color_ = color; }

    bool PopulateFromPdfiumInstance(FPDF_ANNOTATION fpdf_annot, FPDF_PAGE page) override;
    ScopedFPDFAnnotation CreatePdfiumInstance(FPDF_DOCUMENT document, FPDF_PAGE page) override;
    bool UpdatePdfiumInstance(FPDF_ANNOTATION fpdf_annot, FPDF_DOCUMENT document,
                              FPDF_PAGE page) override;

  private:
    std::vector<Rectangle_f> bounds_;
    Color color_;
};

class FreeTextAnnotation : public Annotation {
  public:
    static constexpr const char* kContents = "Contents";
    FreeTextAnnotation(const Rectangle_f& bounds) : Annotation(Type::FreeText) { bounds_ = bounds; }

    Rectangle_f GetBounds() const { return bounds_; }
    void SetBounds(Rectangle_f bounds) { bounds_ = bounds; }

    std::wstring GetTextContent() const { return text_content_; }
    void SetTextContent(std::wstring textContent) { text_content_ = textContent; }

    Color GetTextColor() const { return text_color_; }
    void SetTextColor(Color color) { text_color_ = color; }

    Color GetBackgroundColor() const { return background_color_; }
    void SetBackgroundColor(Color color) { background_color_ = color; }

    bool PopulateFromPdfiumInstance(FPDF_ANNOTATION fpdf_annot, FPDF_PAGE page) override;
    ScopedFPDFAnnotation CreatePdfiumInstance(FPDF_DOCUMENT document, FPDF_PAGE page) override;
    bool UpdatePdfiumInstance(FPDF_ANNOTATION fpdf_annot, FPDF_DOCUMENT document,
                              FPDF_PAGE page) override;

  private:
    Rectangle_f bounds_;
    std::wstring text_content_;
    Color text_color_;
    Color background_color_;
    static bool GetTextContentFromPdfium(FPDF_ANNOTATION fpdf_annot, unsigned long text_length,
                                         std::wstring& text);
};

}  // namespace pdfClient

#endif