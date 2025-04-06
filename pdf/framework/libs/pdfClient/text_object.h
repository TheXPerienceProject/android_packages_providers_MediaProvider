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

#ifndef MEDIAPROVIDER_PDF_JNI_PDFCLIENT_TEXT_OBJECT_H_
#define MEDIAPROVIDER_PDF_JNI_PDFCLIENT_TEXT_OBJECT_H_

#include <stdint.h>

#include <string>
#include <unordered_map>
#include <vector>

#include "cpp/fpdf_scopers.h"
#include "fpdfview.h"
#include "page_object.h"

namespace pdfClient {

class Font {
  public:
    enum class Family {
        Unknown = -1,
        Courier,
        Helvetica,
        Symbol,
        TimesRoman,
    };

    Font() : family_(Family::Unknown), bold_(false), italic_(false) {};
    Font(const std::string& font_name, Family family = Family::Unknown, bool bold = false,
         bool italic = false);

    std::string GetName();
    Family GetFamily() const { return family_; }
    bool IsBold() const { return bold_; }
    bool IsItalic() const { return italic_; }

  private:
    std::string font_name_;
    Family family_;
    bool bold_;
    bool italic_;
};

class TextObject : public PageObject {
  public:
    TextObject();

    ScopedFPDFPageObject CreateFPDFInstance(FPDF_DOCUMENT document, FPDF_PAGE page) override;
    bool UpdateFPDFInstance(FPDF_PAGEOBJECT text_object, FPDF_PAGE page) override;
    bool PopulateFromFPDFInstance(FPDF_PAGEOBJECT text_object, FPDF_PAGE page) override;

    ~TextObject();

    enum class RenderMode {
        Unknown = -1,
        Fill,
        Stroke,
        FillStroke,
    };

    Font font_;
    float font_size_;
    RenderMode render_mode_;
    std::wstring text_;
};

// Define font names as constants
const std::string Courier = "Courier";
const std::string CourierNew = "CourierNew";
const std::string Helvetica = "Helvetica";
const std::string Symbol = "Symbol";
const std::string Times = "Times";
const std::string TimesRoman = "Times-Roman";
const std::string TimesNewRoman = "TimesNewRoman";

// Define font variants as constants
const std::string Bold = "-Bold";
const std::string Italic = "-Italic";
const std::string Oblique = "-Oblique";
const std::string BoldItalic = "-BoldItalic";
const std::string BoldOblique = "-BoldOblique";

// Font Mapper
extern std::unordered_map<std::string, Font> font_mapper;

// Standard Font Names
extern std::vector<std::string> font_names;

}  // namespace pdfClient

#endif  // MEDIAPROVIDER_PDF_JNI_PDFCLIENT_TEXT_OBJECT_H_