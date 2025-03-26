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

#ifndef MEDIAPROVIDER_PDF_JNI_PDFCLIENT_PATH_OBJECT_H_
#define MEDIAPROVIDER_PDF_JNI_PDFCLIENT_PATH_OBJECT_H_

#include <stdint.h>

#include <vector>

#include "cpp/fpdf_scopers.h"
#include "fpdfview.h"
#include "page_object.h"

typedef unsigned int uint;

namespace pdfClient {

class PathObject : public PageObject {
  public:
    PathObject();

    ScopedFPDFPageObject CreateFPDFInstance(FPDF_DOCUMENT document, FPDF_PAGE page) override;
    bool UpdateFPDFInstance(FPDF_PAGEOBJECT path_object, FPDF_PAGE page) override;
    bool PopulateFromFPDFInstance(FPDF_PAGEOBJECT path_object, FPDF_PAGE page) override;

    ~PathObject();

    class Segment {
      public:
        enum class Command {
            Unknown = 0,
            Move,
            Line,
        };

        Command command;
        float x;
        float y;
        bool is_closed;  // Checks if the path_segment is closed

        Segment(Command command, float x, float y, bool is_closed = false)
            : command(command), x(x), y(y), is_closed(is_closed) {}
    };

    bool is_fill_ = false;
    bool is_stroke_ = false;

    std::vector<Segment> segments_;
};

}  // namespace pdfClient

#endif  // MEDIAPROVIDER_PDF_JNI_PDFCLIENT_PATH_OBJECT_H_