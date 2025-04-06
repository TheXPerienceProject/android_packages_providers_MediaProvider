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

#include "text_object.h"

#include <stddef.h>
#include <stdint.h>
#include <utils/pdf_strings.h>

#include <string>

#include "cpp/fpdf_scopers.h"
#include "fpdf_edit.h"
#include "fpdfview.h"
#include "logging.h"

#define LOG_TAG "text_object"

using FontFamily = pdfClient::Font::Family;

namespace pdfClient {

std::optional<Font> GetFont(FPDF_PAGEOBJECT text_object) {
    // Get FPDF font.
    FPDF_FONT font = FPDFTextObj_GetFont(text_object);

    // Get buffer length.
    unsigned long text_len = FPDFFont_GetBaseFontName(font, nullptr, 0);

    // Get font name.
    std::unique_ptr<char[]> p_font_name = std::make_unique<char[]>(text_len);
    if (!FPDFFont_GetBaseFontName(font, p_font_name.get(), text_len)) {
        LOGE("GetBaseFontName failed");
        return std::nullopt;
    }

    // Find font index.
    std::string font_name(p_font_name.get());
    if (font_mapper.find(font_name) == font_mapper.end()) {
        LOGE("Font not found in font_mapper %s", font_name.c_str());
        return std::nullopt;
    }

    return font_mapper[font_name];
}

TextObject::RenderMode GetRenderMode(FPDF_TEXT_RENDERMODE render_mode) {
    switch (render_mode) {
        case FPDF_TEXTRENDERMODE_FILL: {
            return TextObject::RenderMode::Fill;
        }
        case FPDF_TEXTRENDERMODE_STROKE: {
            return TextObject::RenderMode::Stroke;
        }
        case FPDF_TEXTRENDERMODE_FILL_STROKE: {
            return TextObject::RenderMode::FillStroke;
        }
        default: {
            return TextObject::RenderMode::Unknown;
        }
    }
}

FPDF_TEXT_RENDERMODE GetRenderMode(TextObject::RenderMode render_mode) {
    switch (render_mode) {
        case TextObject::RenderMode::Fill: {
            return FPDF_TEXTRENDERMODE_FILL;
        }
        case TextObject::RenderMode::Stroke: {
            return FPDF_TEXTRENDERMODE_STROKE;
        }
        case TextObject::RenderMode::FillStroke: {
            return FPDF_TEXTRENDERMODE_FILL_STROKE;
        }
        default: {
            return FPDF_TEXTRENDERMODE_UNKNOWN;
        }
    }
}

std::optional<std::wstring> GetText(FPDF_PAGEOBJECT text_object, FPDF_PAGE page) {
    // Get text page.
    ScopedFPDFTextPage text_page(FPDFText_LoadPage(page));
    if (!text_page) {
        return std::nullopt;
    }

    // Get buffer length.
    unsigned long text_len = FPDFTextObj_GetText(text_object, text_page.get(), nullptr, 0);

    // Get text.
    std::unique_ptr<FPDF_WCHAR[]> p_text_buffer = std::make_unique<FPDF_WCHAR[]>(text_len);
    if (!FPDFTextObj_GetText(text_object, text_page.get(), p_text_buffer.get(), text_len)) {
        LOGE("GetText failed");
        return std::nullopt;
    }

    return pdfClient_utils::ToWideString(p_text_buffer.get(), text_len);
}

Font::Font(const std::string& font_name, Family family, bool bold, bool italic)
    : font_name_(font_name), family_(family), bold_(bold), italic_(italic) {}

std::string Font::GetName() {
    std::string name = font_name_;

    if (bold_ && italic_) {
        name += BoldItalic;
    } else if (bold_) {
        name += Bold;
    } else if (italic_) {
        name += Italic;
    }

    return name;
}

TextObject::TextObject() : PageObject(Type::Text) {}

ScopedFPDFPageObject TextObject::CreateFPDFInstance(FPDF_DOCUMENT document, FPDF_PAGE page) {
    // Create a scoped Pdfium font object.
    ScopedFPDFFont font(FPDFText_LoadStandardFont(document, font_.GetName().c_str()));
    if (!font) {
        LOGE("Font creation failed");
        return nullptr;
    }

    // Create a scoped Pdfium text object.
    ScopedFPDFPageObject scoped_text_object(
            FPDFPageObj_CreateTextObj(document, font.get(), font_size_));
    if (!scoped_text_object) {
        LOGE("Object creation failed");
        return nullptr;
    }

    // Update attributes of Pdfium text object.
    if (!UpdateFPDFInstance(scoped_text_object.get(), page)) {
        LOGE("Create update failed");
        return nullptr;
    }

    return scoped_text_object;
}

bool TextObject::UpdateFPDFInstance(FPDF_PAGEOBJECT text_object, FPDF_PAGE page) {
    if (!text_object) {
        LOGE("Object NULL");
        return false;
    }

    // Check for type correctness.
    if (FPDFPageObj_GetType(text_object) != FPDF_PAGEOBJ_TEXT) {
        LOGE("TypeCast failed");
        return false;
    }

    // Set the updated text.
    auto fpdf_text = pdfClient_utils::ToFPDFWideString(text_);
    if (text_.size() == 0 || !FPDFText_SetText(text_object, fpdf_text.get())) {
        LOGE("SetText failed");
        return false;
    }

    // Set the updated text render mode.
    if (!FPDFTextObj_SetTextRenderMode(text_object, GetRenderMode(render_mode_))) {
        LOGE("SetTextRenderMode failed");
        return false;
    }

    // Set the updated device matrix.
    if (!SetDeviceToPageMatrix(text_object, page)) {
        LOGE("SetMatrix failed");
        return false;
    }

    // Set updated stroke width.
    if (!FPDFPageObj_SetStrokeWidth(text_object, stroke_width_)) {
        LOGE("SetStrokeWidth failed");
        return false;
    }

    // Set updated stroke color.
    if (!FPDFPageObj_SetStrokeColor(text_object, stroke_color_.r, stroke_color_.g, stroke_color_.b,
                                    stroke_color_.a)) {
        LOGE("SetStrokeColor failed");
        return false;
    }

    // Set the updated fill color.
    if (!FPDFPageObj_SetFillColor(text_object, fill_color_.r, fill_color_.g, fill_color_.b,
                                  fill_color_.a)) {
        LOGE("SetFillColor failed");
        return false;
    }

    return true;
}

bool TextObject::PopulateFromFPDFInstance(FPDF_PAGEOBJECT text_object, FPDF_PAGE page) {
    // Get font.
    std::optional<Font> fontOpt = GetFont(text_object);
    if (!fontOpt) {
        LOGE("GetFont failed");
        return false;
    }
    font_ = *fontOpt;

    // Get font size.
    if (!FPDFTextObj_GetFontSize(text_object, &font_size_)) {
        LOGE("GetFontSize failed");
        return false;
    }

    // Get text.
    std::optional<std::wstring> textOpt = GetText(text_object, page);
    if (!textOpt) {
        LOGE("GetText failed");
        return false;
    }
    text_ = *textOpt;

    // Get render mode.
    render_mode_ = GetRenderMode(FPDFTextObj_GetTextRenderMode(text_object));
    if (render_mode_ == RenderMode::Unknown) {
        LOGE("GetRenderMode unknown");
        return false;
    }

    // Get device matrix.
    if (!GetPageToDeviceMatrix(text_object, page)) {
        LOGE("GetMatrix failed");
        return false;
    }

    // Get stroke width.
    if (!FPDFPageObj_GetStrokeWidth(text_object, &stroke_width_)) {
        LOGE("GetStrokeWidth failed");
        return false;
    }

    // Get stroke color.
    if (!FPDFPageObj_GetStrokeColor(text_object, &stroke_color_.r, &stroke_color_.g,
                                    &stroke_color_.b, &stroke_color_.a)) {
        LOGE("GetStrokeColor failed");
        return false;
    }

    // Get fill color.
    if (!FPDFPageObj_GetFillColor(text_object, &fill_color_.r, &fill_color_.g, &fill_color_.b,
                                  &fill_color_.a)) {
        LOGE("GetFillColor failed");
        return false;
    }

    return true;
}

TextObject::~TextObject() = default;

// Font Mapper
std::unordered_map<std::string, Font> font_mapper = {
        {Courier, Font(Courier, FontFamily::Courier)},
        {Courier + Bold, Font(Courier, FontFamily::Courier, true)},
        {Courier + Oblique, Font(Courier, FontFamily::Courier, false, true)},
        {Courier + BoldOblique, Font(Courier, FontFamily::Courier, true, true)},

        {Helvetica, Font(Helvetica, FontFamily::Helvetica)},
        {Helvetica + Bold, Font(Helvetica, FontFamily::Helvetica, true)},
        {Helvetica + Oblique, Font(Helvetica, FontFamily::Helvetica, false, true)},
        {Helvetica + BoldOblique, Font(Helvetica, FontFamily::Helvetica, true, true)},

        {TimesRoman, Font(TimesRoman, FontFamily::TimesRoman)},
        {Times + Bold, Font(Times, FontFamily::TimesRoman, true)},
        {Times + Italic, Font(Times, FontFamily::TimesRoman, false, true)},
        {Times + BoldItalic, Font(Times, FontFamily::TimesRoman, true, true)},

        {Symbol, Font(Symbol, FontFamily::Symbol)}};

// Standard Font Names.
std::vector<std::string> font_names = {CourierNew, Helvetica, Symbol, TimesNewRoman};

}  // namespace pdfClient