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

#define LOG_TAG "page_object"

namespace pdfClient {

PageObject::PageObject(Type type) : type_(type) {}

PageObject::Type PageObject::GetType() const {
    return type_;
}

PageObject::~PageObject() = default;

}  // namespace pdfClient