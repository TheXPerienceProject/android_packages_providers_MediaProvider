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

#include "page.h"

#include <android-base/file.h>
#include <android/api-level.h>
#include <gtest/gtest.h>

#include <memory>
#include <string>

// Goes first due to conflicts.
#include "document.h"
#include "image_object.h"
#include "page_object.h"
#include "path_object.h"
#include "rect.h"
#include "text_object.h"
// #include "file/base/path.h"
#include "cpp/fpdf_scopers.h"
#include "fpdfview.h"

namespace {

using ::pdfClient::Annotation;
using ::pdfClient::Color;
using ::pdfClient::CourierNew;
using ::pdfClient::Document;
using ::pdfClient::Font;
using ::pdfClient::font_names;
using ::pdfClient::FreeTextAnnotation;
using ::pdfClient::Helvetica;
using ::pdfClient::ImageObject;
using ::pdfClient::Matrix;
using ::pdfClient::Page;
using ::pdfClient::PageObject;
using ::pdfClient::PathObject;
using ::pdfClient::Rectangle_i;
using ::pdfClient::StampAnnotation;
using ::pdfClient::Symbol;
using ::pdfClient::TextObject;
using ::pdfClient::TimesNewRoman;

static const std::string kTestdata = "testdata";
static const std::string kSekretNoPassword = "sekret_no_password.pdf";
static const std::string kPageObject = "page_object.pdf";
static const std::string kAnnotation = "annotation.pdf";

std::string GetTestDataDir() {
    return android::base::GetExecutableDirectory();
}

std::string GetTestFile(std::string filename) {
    return GetTestDataDir() + "/" + kTestdata + "/" + filename;
}

ScopedFPDFDocument LoadTestDocument(const std::string filename) {
    return ScopedFPDFDocument(FPDF_LoadDocument(GetTestFile(filename).c_str(), nullptr));
}

// Note on coordinates used in below tests:
// This document has height == 792. Due to constraints of rect.h functions
// that require top < bottom, top/bottom are flipped from what page
// coordinates normally would be in these examples. So expected values when
// we consume the rectangles in this test are: top = (792 - bottom),
// bottom = (792 - top).

/*
 * Test that when a single rectangle is passed to NotifyInvalidRect
 * invalid_rect_ will match its coordinates.
 */
TEST(Test, NotifyInvalidRectSingleRectTest) {
    Document doc(LoadTestDocument(kSekretNoPassword), false);

    std::shared_ptr<Page> page = doc.GetPage(0);
    EXPECT_FALSE(page->HasInvalidRect());
    page->NotifyInvalidRect(pdfClient::IntRect(100, 100, 200, 200));

    EXPECT_TRUE(page->HasInvalidRect());
    Rectangle_i expected = Rectangle_i{100, 592, 200, 692};
    ASSERT_EQ(expected, page->ConsumeInvalidRect());
}

/*
 * Tests the coalescing of rectangles. Result should be the minimal rectangle
 * that covers all rectangles that have been added.
 */
TEST(Test, NotifyInvalidRectCoalesceTest) {
    Document doc(LoadTestDocument(kSekretNoPassword), false);

    std::shared_ptr<Page> page = doc.GetPage(0);
    EXPECT_FALSE(page->HasInvalidRect());

    page->NotifyInvalidRect(pdfClient::IntRect(100, 100, 200, 200));
    page->NotifyInvalidRect(pdfClient::IntRect(400, 100, 500, 200));
    page->NotifyInvalidRect(pdfClient::IntRect(100, 400, 200, 500));
    EXPECT_TRUE(page->HasInvalidRect());
    Rectangle_i expected = Rectangle_i{100, 292, 500, 692};
    ASSERT_EQ(expected, page->ConsumeInvalidRect());
}

/*
 * Tests adding a rectangle to invalid_rect_ whose area is already covered by
 * the existing rect. Should not change boundaries.
 */
TEST(Test, NotifyInvalidRectAlreadyCoveredTest) {
    Document doc(LoadTestDocument(kSekretNoPassword), false);

    std::shared_ptr<Page> page = doc.GetPage(0);
    EXPECT_FALSE(page->HasInvalidRect());

    page->NotifyInvalidRect(pdfClient::IntRect(100, 100, 200, 200));
    page->NotifyInvalidRect(pdfClient::IntRect(400, 100, 500, 200));
    page->NotifyInvalidRect(pdfClient::IntRect(100, 400, 200, 500));
    // add a rectangle that's already covered by existing one
    page->NotifyInvalidRect(pdfClient::IntRect(400, 400, 500, 500));
    EXPECT_TRUE(page->HasInvalidRect());
    Rectangle_i expected = Rectangle_i{100, 292, 500, 692};
    ASSERT_EQ(expected, page->ConsumeInvalidRect());
}

/**
 * Try calling NotifyInvalidRect with negative indices. No error should be
 * thrown. Confirm all rectangles have been ignored by the page.
 */
TEST(Test, NotifyInvalidRectNegativeIndicesTest) {
    Document doc(LoadTestDocument(kSekretNoPassword), false);
    std::shared_ptr<Page> page = doc.GetPage(0);

    page->NotifyInvalidRect(pdfClient::IntRect(-100, 100, 200, 200));
    page->NotifyInvalidRect(pdfClient::IntRect(400, -100, 500, 200));
    page->NotifyInvalidRect(pdfClient::IntRect(100, 400, -200, 500));
    page->NotifyInvalidRect(pdfClient::IntRect(400, 400, 500, -500));
    EXPECT_FALSE(page->HasInvalidRect());
}

/**
 * Try calling NotifyInvalidRect with empty rectangles. No error should be
 * thrown. Confirm all rectangles have been ignored by the page.
 */
TEST(Test, NotifyInvalidRectEmptyRectanglesTest) {
    Document doc(LoadTestDocument(kSekretNoPassword), false);
    std::shared_ptr<Page> page = doc.GetPage(0);

    page->NotifyInvalidRect(pdfClient::IntRect(100, 200, 100, 500));
    page->NotifyInvalidRect(pdfClient::IntRect(100, 400, 500, 400));
    page->NotifyInvalidRect(pdfClient::Rectangle_i{100, 200, 0, 500});
    page->NotifyInvalidRect(pdfClient::Rectangle_i{100, 400, 500, 0});
    EXPECT_FALSE(page->HasInvalidRect());
}

/**
 * Test that calling ConsumeInvalidRect resets the rectangle in the Page.
 */
TEST(Test, ConsumeInvalidRectResetsRectTest) {
    Document doc(LoadTestDocument(kSekretNoPassword), false);
    std::shared_ptr<Page> page = doc.GetPage(0);

    // doesn't have one
    EXPECT_FALSE(page->HasInvalidRect());
    page->NotifyInvalidRect(pdfClient::IntRect(100, 100, 200, 200));

    // now has one
    page->NotifyInvalidRect(pdfClient::IntRect(100, 100, 200, 200));
    EXPECT_TRUE(page->HasInvalidRect());

    // no longer has one
    page->ConsumeInvalidRect();
    EXPECT_FALSE(page->HasInvalidRect());

    // if we call Consume anyway we will receive empty rect
    Rectangle_i expected = Rectangle_i{0, 0, 0, 0};
    ASSERT_EQ(expected, page->ConsumeInvalidRect());
}

TEST(Test, InvalidPageNumberTest) {
    if (android_get_device_api_level() < __ANDROID_API_S__) {
        // Pdf client is not exposed on R and for some unknown reason this test fails
        // on non-64 architectures. See - b/381994039
        // We could disable all the tests here for R.
        GTEST_SKIP();
    }
    Document doc(LoadTestDocument(kSekretNoPassword), false);
    // The document has only one page but we fetch the second one.
    std::shared_ptr<Page> page = doc.GetPage(1);

    // The above call succeeds and returns a non-null ptr.
    ASSERT_NE(nullptr, page);
    // Even though the underlying pointer is null.
    ASSERT_EQ(nullptr, page->Get());

    // Rest of the calls should give some default values.
    EXPECT_EQ(-1, page->NumChars());
    std::string str_with_null = "\0";
    EXPECT_EQ(1, page->GetTextUtf8().size());  // Returns "\0"
    EXPECT_EQ('\0', page->GetUnicode(0));
    EXPECT_EQ(0, page->Width());
    EXPECT_EQ(0, page->Height());

    Rectangle_i expected = Rectangle_i{0, 0, 0, 0};
    EXPECT_EQ(expected, page->Dimensions());
    EXPECT_EQ(false, page->HasInvalidRect());
    EXPECT_EQ(0, page->GetGotoLinks().size());
    // The following should not crash, we do not expect anything in return.
    page->InitializeFormFilling();
    page->TerminateFormFilling();
}

TEST(Test, GetPageObjectsTest) {
    Document doc(LoadTestDocument(kPageObject), false);

    std::shared_ptr<Page> page = doc.GetPage(0);
    std::vector<PageObject*> pageObjects = page->GetPageObjects();

    // Check for PageObjects size.
    ASSERT_EQ(3, pageObjects.size());
    // Check for the first PageObject to be ImageObject.
    ASSERT_EQ(PageObject::Type::Image, pageObjects[0]->GetType());
    // Check for the second PageObject to be PathObject.
    ASSERT_EQ(PageObject::Type::Path, pageObjects[1]->GetType());
    // Check for the third PageObject to be TextObject.
    ASSERT_EQ(PageObject::Type::Text, pageObjects[2]->GetType());
}

TEST(Test, AddImagePageObjectTest) {
    Document doc(LoadTestDocument(kPageObject), false);
    std::shared_ptr<Page> page = doc.GetPage(0);

    std::vector<PageObject*> initialPageObjects = page->GetPageObjects();

    // Create Image Object.
    auto imageObject = std::make_unique<ImageObject>();

    // Create FPDF Bitmap.
    imageObject->bitmap_ = ScopedFPDFBitmap(FPDFBitmap_Create(100, 100, 1));
    FPDFBitmap_FillRect(imageObject->bitmap_.get(), 0, 0, 100, 100, 0xFF000000);

    // Set Matrix.
    imageObject->matrix_ = {1.0f, 0, 0, 1.0f, 0, 0};

    // Add the page object.
    ASSERT_EQ(page->AddPageObject(std::move(imageObject)), initialPageObjects.size());

    // Get Updated PageObjects
    std::vector<PageObject*> updatedPageObjects = page->GetPageObjects(true);

    // Assert that the size has increased by one.
    ASSERT_EQ(initialPageObjects.size() + 1, updatedPageObjects.size());
    // Check for the first PageObject to be ImageObject.
    ASSERT_EQ(PageObject::Type::Image, updatedPageObjects[0]->GetType());
    // Check for the second PageObject to be PathObject.
    ASSERT_EQ(PageObject::Type::Path, updatedPageObjects[1]->GetType());
    // Check for the third PageObject to be TextObject.
    ASSERT_EQ(PageObject::Type::Text, updatedPageObjects[2]->GetType());
    // Check for the fourth PageObject to be ImageObject.
    ASSERT_EQ(PageObject::Type::Image, updatedPageObjects[3]->GetType());
}

TEST(Test, AddPathPageObject) {
    Document doc(LoadTestDocument(kPageObject), false);
    std::shared_ptr<Page> page = doc.GetPage(0);

    std::vector<PageObject*> initialPageObjects = page->GetPageObjects();

    // Create Path Object.
    auto pathObject = std::make_unique<PathObject>();

    // Command Simple Path
    pathObject->segments_.emplace_back(PathObject::Segment::Command::Move, 0.0f, 0.0f);
    pathObject->segments_.emplace_back(PathObject::Segment::Command::Line, 100.0f, 150.0f);
    pathObject->segments_.emplace_back(PathObject::Segment::Command::Line, 150.0f, 150.0f);

    // Set Draw Mode
    pathObject->is_fill_ = false;
    pathObject->is_stroke_ = true;

    // Set PathObject Matrix.
    pathObject->matrix_ = {1.0f, 0, 0, 1.0f, 0, 0};

    // Add the page object.
    ASSERT_EQ(page->AddPageObject(std::move(pathObject)), initialPageObjects.size());

    // Get Updated PageObjects
    std::vector<PageObject*> updatedPageObjects = page->GetPageObjects(true);

    // Assert that the size has increased by one.
    ASSERT_EQ(initialPageObjects.size() + 1, updatedPageObjects.size());
    // Check for the first PageObject to be ImageObject.
    ASSERT_EQ(PageObject::Type::Image, updatedPageObjects[0]->GetType());
    // Check for the second PageObject to be PathObject.
    ASSERT_EQ(PageObject::Type::Path, updatedPageObjects[1]->GetType());
    // Check for the third PageObject to be TextObject.
    ASSERT_EQ(PageObject::Type::Text, updatedPageObjects[2]->GetType());
    // Check for the fourth PageObject to be PathObject.
    ASSERT_EQ(PageObject::Type::Path, updatedPageObjects[3]->GetType());
}

TEST(Test, AddTextPageObject) {
    Document doc(LoadTestDocument(kPageObject), false);
    std::shared_ptr<Page> page = doc.GetPage(0);

    std::vector<PageObject*> initialPageObjects = page->GetPageObjects();

    int page_objects_size = initialPageObjects.size();

    // Assert font_names vector contains font name as per right order.
    ASSERT_EQ(font_names[0], CourierNew);
    ASSERT_EQ(font_names[1], Helvetica);
    ASSERT_EQ(font_names[2], Symbol);
    ASSERT_EQ(font_names[3], TimesNewRoman);

    for (int index = 0; index < font_names.size(); index++) {
        // Create Text Object.
        auto textObject = std::make_unique<TextObject>();

        // Set Font.
        textObject->font_ = Font(font_names[index], static_cast<Font::Family>(index), true, true);

        // Set Font Size.
        textObject->font_size_ = 10.0f;

        // Set Text.
        textObject->text_ = L"Hello World!";

        // Set Text Render Mode.
        textObject->render_mode_ = TextObject::RenderMode::Stroke;

        // Set TextObject Color.
        textObject->fill_color_ = Color(0, 0, 255, 255);

        // Set TextObject Matrix.
        textObject->matrix_ = {1.0f, 0, 0, 1.0f, 0, 0};

        // Add the page object.
        if (index != 2) {
            ASSERT_EQ(page->AddPageObject(std::move(textObject)), page_objects_size++);
        } else {
            // Symbol-BoldItalic is not a font.
            ASSERT_EQ(page->AddPageObject(std::move(textObject)), -1);
        }
    }

    // Get Updated PageObjects
    std::vector<PageObject*> updatedPageObjects = page->GetPageObjects(true);

    // Assert that the size has increased by three.
    ASSERT_EQ(initialPageObjects.size() + 3, updatedPageObjects.size());
    // Check for the first PageObject to be ImageObject.
    ASSERT_EQ(PageObject::Type::Image, updatedPageObjects[0]->GetType());
    // Check for the second PageObject to be PathObject.
    ASSERT_EQ(PageObject::Type::Path, updatedPageObjects[1]->GetType());
    // Check for the third PageObject to be TextObject.
    ASSERT_EQ(PageObject::Type::Text, updatedPageObjects[2]->GetType());
    // Check for the added PageObjects to be TextObjects.
    for (int index = 3; index < page_objects_size; index++) {
        ASSERT_EQ(PageObject::Type::Text, updatedPageObjects[index]->GetType());
    }
}

TEST(Test, RemovePageObjectTest) {
    Document doc(LoadTestDocument(kPageObject), false);

    std::shared_ptr<Page> page = doc.GetPage(0);

    std::vector<PageObject*> initialPageObjects = page->GetPageObjects();

    // Remove a pageObject
    EXPECT_TRUE(page->RemovePageObject(0));
    // Get Updated PageObjects after removal
    std::vector<PageObject*> updatedPageObjects = page->GetPageObjects(true);

    ASSERT_EQ(initialPageObjects.size() - 1, updatedPageObjects.size());
}

TEST(Test, UpdateImagePageObjectTest) {
    Document doc(LoadTestDocument(kPageObject), false);
    std::shared_ptr<Page> page = doc.GetPage(0);

    // Get initial page objects.
    std::vector<PageObject*> initialPageObjects = page->GetPageObjects();

    // Create Image Object.
    auto imageObject = std::make_unique<ImageObject>();

    // Create FPDF Bitmap.
    imageObject->bitmap_ = ScopedFPDFBitmap(FPDFBitmap_Create(100, 110, 1));
    FPDFBitmap_FillRect(imageObject->bitmap_.get(), 0, 0, 100, 110, 0xFF0000FF);

    // Set Matrix.
    Matrix update_matrix = {2.0f, 0, 0, 2.0f, 0, 0};
    imageObject->matrix_ = update_matrix;

    // Update the page object.
    EXPECT_TRUE(page->UpdatePageObject(0, std::move(imageObject)));

    // Get the updated page objects.
    std::vector<PageObject*> updatedPageObjects = page->GetPageObjects(true);

    // Check for size equality.
    ASSERT_EQ(initialPageObjects.size(), updatedPageObjects.size());

    // Check for updated bitmap.
    ASSERT_EQ(FPDFBitmap_GetWidth(static_cast<ImageObject*>(updatedPageObjects[0])->bitmap_.get()),
              100);
    ASSERT_EQ(FPDFBitmap_GetHeight(static_cast<ImageObject*>(updatedPageObjects[0])->bitmap_.get()),
              110);

    // Check for updated matrix.
    ASSERT_EQ(updatedPageObjects[0]->matrix_, update_matrix);
}

TEST(Test, UpdatePathPageObjectTest) {
    Document doc(LoadTestDocument(kPageObject), false);
    std::shared_ptr<Page> page = doc.GetPage(0);

    // Get initial page objects.
    std::vector<PageObject*> initialPageObjects = page->GetPageObjects();

    // Create Path Object.
    auto pathObject = std::make_unique<PathObject>();

    // Update fill Color.
    Color update_fill_color = Color(255, 0, 0, 255);
    pathObject->fill_color_ = update_fill_color;

    // Update Draw Mode.
    pathObject->is_fill_ = true;
    pathObject->is_stroke_ = false;

    // Set Matrix.
    Matrix update_matrix = {2.0f, 0, 0, 2.0f, 0, 0};
    pathObject->matrix_ = update_matrix;

    // Update the page object.
    EXPECT_TRUE(page->UpdatePageObject(1, std::move(pathObject)));

    // Get the updated page objects.
    std::vector<PageObject*> updatedPageObjects = page->GetPageObjects(true);

    // Check for updated fill Color.
    ASSERT_EQ(updatedPageObjects[1]->fill_color_, update_fill_color);

    // Check for updated Draw Mode.
    ASSERT_EQ(static_cast<PathObject*>(updatedPageObjects[1])->is_fill_, true);
    ASSERT_EQ(static_cast<PathObject*>(updatedPageObjects[1])->is_stroke_, false);

    // Check for updated matrix.
    ASSERT_EQ(updatedPageObjects[1]->matrix_, update_matrix);
}

TEST(Test, UpdateTextPageObjectTest) {
    Document doc(LoadTestDocument(kPageObject), false);
    std::shared_ptr<Page> page = doc.GetPage(0);

    // Get initial page objects.
    std::vector<PageObject*> initialPageObjects = page->GetPageObjects();

    // Check for third page object to be text object.
    ASSERT_EQ(PageObject::Type::Text, initialPageObjects[2]->GetType());

    // Check initial text object data.
    TextObject* initialTextObject = static_cast<TextObject*>(initialPageObjects[2]);

    // Check initial text.
    ASSERT_EQ(initialTextObject->text_, L"Hello World");

    // Check initial render mode.
    ASSERT_EQ(initialTextObject->render_mode_, TextObject::RenderMode::Fill);

    // Check for initial matrix.
    ASSERT_EQ(initialTextObject->matrix_, Matrix(1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 30.0f));

    // Check for initial fill color.
    ASSERT_EQ(initialTextObject->fill_color_, Color(0, 255, 0, 255));

    // Create Text Object.
    auto textObject = std::make_unique<TextObject>();

    // Update the text.
    std::wstring update_text = L"Hello PDF!";
    textObject->text_ = update_text;

    // Set Text Render Mode.
    textObject->render_mode_ = TextObject::RenderMode::FillStroke;

    // Update text Color.
    Color update_fill_color = Color(0, 0, 255, 255);
    textObject->fill_color_ = update_fill_color;

    // Set Matrix.
    Matrix update_matrix = {2.0f, 5.0f, 0, 3.0f, 4.0f, 10.0f};
    textObject->matrix_ = update_matrix;

    // Update the page object.
    EXPECT_TRUE(page->UpdatePageObject(2, std::move(textObject)));

    // Get the updated page objects.
    std::vector<PageObject*> updatedPageObjects = page->GetPageObjects(true);

    // Check for updated text.
    ASSERT_EQ(static_cast<TextObject*>(updatedPageObjects[2])->text_, update_text);

    // Check for updated text render mode.
    ASSERT_EQ(static_cast<TextObject*>(updatedPageObjects[2])->render_mode_,
              TextObject::RenderMode::FillStroke);

    // Check for updated fill Color.
    ASSERT_EQ(updatedPageObjects[2]->fill_color_, update_fill_color);

    // Check for updated matrix.
    ASSERT_EQ(updatedPageObjects[2]->matrix_, update_matrix);
}

TEST(Test, GetPageAnnotationsTest) {
    Document doc(LoadTestDocument(kAnnotation), false);

    std::shared_ptr<Page> page = doc.GetPage(0);
    std::vector<Annotation*> annotations = page->GetPageAnnotations();

    // Check for number of annotations
    ASSERT_EQ(1, annotations.size());

    // Check for the first Annotation to be StampAnnotation.
    ASSERT_EQ(Annotation::Type::Stamp, annotations[0]->GetType());

    StampAnnotation* stamp_annotation = static_cast<StampAnnotation*>(annotations[0]);
    std::vector<PageObject*> pageObjects = stamp_annotation->GetObjects();

    // Check for number of page objects inside stamp annotation
    ASSERT_EQ(2, pageObjects.size());

    // Check for the first PageObject to be ImageObject.
    ASSERT_EQ(PageObject::Type::Image, pageObjects[0]->GetType());
    // Check for the second PageObject to be PathObject.
    ASSERT_EQ(PageObject::Type::Path, pageObjects[1]->GetType());
}

TEST(Test, AddStampAnnotationTest) {
    Document doc(LoadTestDocument(kAnnotation), false);
    std::shared_ptr<Page> page = doc.GetPage(0);

    std::vector<Annotation*> initialAnnotations = page->GetPageAnnotations();

    // Bounds for stamp annotation
    Rectangle_f bounds = pdfClient::Rectangle_f{0, 300, 200, 0};
    // Create Stamp Annotation
    auto stampAnnotation = std::make_unique<StampAnnotation>(bounds);

    // Insert image page object
    auto imageObject = std::make_unique<ImageObject>();

    // Create FPDF Bitmap.
    imageObject->bitmap_ = ScopedFPDFBitmap(FPDFBitmap_Create(100, 100, 1));
    FPDFBitmap_FillRect(imageObject->bitmap_.get(), 0, 0, 100, 100, 0xFF000000);

    // Set Matrix.
    imageObject->matrix_ = {1.0f, 0, 0, 1.0f, 0, 0};

    // Add the page object.
    stampAnnotation->AddObject(std::move(imageObject));

    // Create Path Object.
    auto pathObject = std::make_unique<PathObject>();

    // Command Simple Path
    pathObject->segments_.emplace_back(PathObject::Segment::Command::Move, 0.0f, 0.0f);
    pathObject->segments_.emplace_back(PathObject::Segment::Command::Line, 100.0f, 150.0f);
    pathObject->segments_.emplace_back(PathObject::Segment::Command::Line, 150.0f, 150.0f);

    // Set Draw Mode
    pathObject->is_fill_ = false;
    pathObject->is_stroke_ = true;

    // Set PathObject Matrix.
    pathObject->matrix_ = {1.0f, 0, 0, 1.0f, 0, 0};

    // Add the page object.
    stampAnnotation->AddObject(std::move(pathObject));

    // Add the stamp annotation.
    ASSERT_EQ(page->AddPageAnnotation(std::move(stampAnnotation)), initialAnnotations.size());

    // Get Updated annotations
    std::vector<Annotation*> updatedAnnotations = page->GetPageAnnotations();

    // Assert that the size has increased by one.
    ASSERT_EQ(initialAnnotations.size() + 1, updatedAnnotations.size());
    // Check for the first Annotation to be StampAnnotation
    ASSERT_EQ(Annotation::Type::Stamp, updatedAnnotations[0]->GetType());
    // Check for the second Annotation to be StampAnnotation.
    ASSERT_EQ(Annotation::Type::Stamp, updatedAnnotations[1]->GetType());

    // Check for the page objects inside stamp annotation
    StampAnnotation* stamp_annotation = static_cast<StampAnnotation*>(updatedAnnotations[1]);
    std::vector<PageObject*> pageObjects = stamp_annotation->GetObjects();

    // Check for number of page objects inside stamp annotation
    ASSERT_EQ(2, pageObjects.size());

    // Check for the first PageObject to be ImageObject.
    ASSERT_EQ(PageObject::Type::Image, pageObjects[0]->GetType());
    // Check for the second PageObject to be PathObject.
    ASSERT_EQ(PageObject::Type::Path, pageObjects[1]->GetType());
}

TEST(Test, RemovePageAnnotationTest) {
    Document doc(LoadTestDocument(kAnnotation), false);

    std::shared_ptr<Page> page = doc.GetPage(0);

    std::vector<Annotation*> initialAnnotations = page->GetPageAnnotations();

    EXPECT_TRUE(initialAnnotations.size() > 0);
    // Remove an annotation
    EXPECT_TRUE(page->RemovePageAnnotation(0));
    // Get Updated annotations after removal
    std::vector<Annotation*> updatedAnnotations = page->GetPageAnnotations();

    ASSERT_EQ(initialAnnotations.size() - 1, updatedAnnotations.size());
}

TEST(Test, AddFreeTextAnnotationTest) {
    Document doc(LoadTestDocument(kAnnotation), false);
    std::shared_ptr<Page> page = doc.GetPage(0);

    std::vector<Annotation*> annotations = page->GetPageAnnotations();

    // Check for number of annotations (Pdf contains 1 Stamp Annotation)
    ASSERT_EQ(1, annotations.size());

    // Create and Add a FreeText Annotation
    Rectangle_f bounds = pdfClient::Rectangle_f{0, 300, 200, 0};
    auto freeTextAnnotation = std::make_unique<FreeTextAnnotation>(bounds);

    std::wstring textContent = L"Hello World";
    Color textColor = Color(255, 0, 0, 255);
    Color backgroundColor = Color(0, 255, 0, 255);
    freeTextAnnotation->SetTextContent(textContent);
    freeTextAnnotation->SetTextColor(textColor);
    freeTextAnnotation->SetBackgroundColor(backgroundColor);

    ASSERT_EQ(page->AddPageAnnotation(std::move(freeTextAnnotation)), annotations.size());

    annotations = page->GetPageAnnotations();
    ASSERT_EQ(2, annotations.size());

    ASSERT_EQ(Annotation::Type::Stamp, annotations[0]->GetType());
    ASSERT_EQ(Annotation::Type::FreeText, annotations[1]->GetType());
    FreeTextAnnotation* addedFreeTextAnnotation = static_cast<FreeTextAnnotation*>(annotations[1]);

    ASSERT_EQ(addedFreeTextAnnotation->GetTextContent(), textContent);
    // Assert TextColor
    ASSERT_EQ(addedFreeTextAnnotation->GetTextColor().r, textColor.r);
    ASSERT_EQ(addedFreeTextAnnotation->GetTextColor().g, textColor.g);
    ASSERT_EQ(addedFreeTextAnnotation->GetTextColor().b, textColor.b);
    ASSERT_EQ(addedFreeTextAnnotation->GetTextColor().a, textColor.a);

    // Assert BackGround Color
    ASSERT_EQ(addedFreeTextAnnotation->GetBackgroundColor().r, backgroundColor.r);
    ASSERT_EQ(addedFreeTextAnnotation->GetBackgroundColor().g, backgroundColor.g);
    ASSERT_EQ(addedFreeTextAnnotation->GetBackgroundColor().b, backgroundColor.b);
    ASSERT_EQ(addedFreeTextAnnotation->GetBackgroundColor().a, backgroundColor.a);
}

TEST(Test, UpdateFreeTextAnnotationTest) {
    Document doc(LoadTestDocument(kAnnotation), false);
    std::shared_ptr<Page> page = doc.GetPage(0);
    std::vector<Annotation*> annotations = page->GetPageAnnotations();

    // Create and Add a FreeText Annotation
    Rectangle_f bounds = pdfClient::Rectangle_f{0, 300, 200, 0};
    auto freeTextAnnotation = std::make_unique<FreeTextAnnotation>(bounds);

    std::wstring textContent = L"Hello World";
    Color textColor = Color(255, 0, 0, 255);
    Color backgroundColor = Color(0, 255, 0, 255);
    freeTextAnnotation->SetTextContent(textContent);
    freeTextAnnotation->SetTextColor(textColor);
    freeTextAnnotation->SetBackgroundColor(backgroundColor);

    ASSERT_EQ(page->AddPageAnnotation(std::move(freeTextAnnotation)), annotations.size());

    freeTextAnnotation = std::make_unique<FreeTextAnnotation>(bounds);
    std::wstring newTextContent = L"Bye World";
    Color newTextColor = Color(0, 0, 255, 255);
    Color newBackgroundColor = Color(255, 255, 255, 255);
    freeTextAnnotation->SetTextContent(newTextContent);
    freeTextAnnotation->SetTextColor(newTextColor);
    freeTextAnnotation->SetBackgroundColor(newBackgroundColor);

    page->UpdatePageAnnotation(1, std::move(freeTextAnnotation));

    annotations = page->GetPageAnnotations();
    FreeTextAnnotation* updatedAnnotation = static_cast<FreeTextAnnotation*>(annotations[1]);

    ASSERT_EQ(updatedAnnotation->GetTextContent(), newTextContent);
    // Assert Updated TextColor
    ASSERT_EQ(updatedAnnotation->GetTextColor().r, newTextColor.r);
    ASSERT_EQ(updatedAnnotation->GetTextColor().g, newTextColor.g);
    ASSERT_EQ(updatedAnnotation->GetTextColor().b, newTextColor.b);
    ASSERT_EQ(updatedAnnotation->GetTextColor().a, newTextColor.a);

    // Assert Updated BackGround Color
    ASSERT_EQ(updatedAnnotation->GetBackgroundColor().r, newBackgroundColor.r);
    ASSERT_EQ(updatedAnnotation->GetBackgroundColor().g, newBackgroundColor.g);
    ASSERT_EQ(updatedAnnotation->GetBackgroundColor().b, newBackgroundColor.b);
    ASSERT_EQ(updatedAnnotation->GetBackgroundColor().a, newBackgroundColor.a);
}

}  // namespace