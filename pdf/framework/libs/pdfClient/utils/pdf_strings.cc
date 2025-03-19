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

#include "pdf_strings.h"

namespace pdfClient_utils {
ScopedFPDFWChar ToFPDFWideString(const std::wstring& wstr) {
    size_t length = wstr.size() + 1;
    ScopedFPDFWChar result = std::make_unique<FPDF_WCHAR[]>(length);

    size_t i = 0;
    for (const wchar_t& w : wstr) {
        result[i++] = static_cast<FPDF_WCHAR>(w);
    }
    result[i] = 0;
    return result;
}

std::wstring ToWideString(const FPDF_WCHAR* buffer, unsigned long text_length) {
    std::wstring textContent;
    size_t textContentSize = (text_length / sizeof(FPDF_WCHAR)) - 1;
    textContent.reserve(textContentSize);
    for (int i = 0; i < textContentSize; i++) {
        textContent.push_back(buffer[i]);
    }
    return textContent;
}
}  // namespace pdfClient_utils
