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

#include "jni_conversion.h"

#include <android/bitmap.h>
#include <string.h>

#include "image_object.h"
#include "logging.h"
#include "rect.h"
#include "text_object.h"

using pdfClient::Annotation;
using pdfClient::BitmapFormat;
using pdfClient::Color;
using pdfClient::Document;
using pdfClient::Font;
using pdfClient::font_names;
using pdfClient::FreeTextAnnotation;
using pdfClient::HighlightAnnotation;
using pdfClient::ICoordinateConverter;
using pdfClient::ImageObject;
using pdfClient::LinuxFileOps;
using pdfClient::Matrix;
using pdfClient::PageObject;
using pdfClient::PathObject;
using pdfClient::Point_f;
using pdfClient::Rectangle_f;
using pdfClient::Rectangle_i;
using pdfClient::SelectionBoundary;
using pdfClient::StampAnnotation;
using pdfClient::TextObject;
using std::string;
using std::vector;

#define LOG_TAG "jni_conversion"

namespace convert {

namespace {
static const char* kDimensions = "android/graphics/pdf/models/Dimensions";
static const char* kPdfDocument = "android/graphics/pdf/PdfDocumentProxy";
static const char* kLoadPdfResult = "android/graphics/pdf/models/jni/LoadPdfResult";
static const char* kLinkRects = "android/graphics/pdf/models/jni/LinkRects";
static const char* kMatchRects = "android/graphics/pdf/models/jni/MatchRects";
static const char* kSelection = "android/graphics/pdf/models/jni/PageSelection";
static const char* kBoundary = "android/graphics/pdf/models/jni/SelectionBoundary";
static const char* kFormWidgetInfo = "android/graphics/pdf/models/FormWidgetInfo";
static const char* kChoiceOption = "android/graphics/pdf/models/ListItem";
static const char* kGotoLinkDestination =
        "android/graphics/pdf/content/PdfPageGotoLinkContent$Destination";
static const char* kGotoLink = "android/graphics/pdf/content/PdfPageGotoLinkContent";
static const char* kPageObject = "android/graphics/pdf/component/PdfPageObject";
static const char* kTextFont = "android/graphics/pdf/component/PdfPageTextObjectFont";
static const char* kTextObject = "android/graphics/pdf/component/PdfPageTextObject";
static const char* kPathObject = "android/graphics/pdf/component/PdfPagePathObject";
static const char* kImageObject = "android/graphics/pdf/component/PdfPageImageObject";
static const char* kStampAnnotation = "android/graphics/pdf/component/StampAnnotation";
static const char* kPdfAnnotation = "android/graphics/pdf/component/PdfAnnotation";
static const char* kHighlightAnnotation = "android/graphics/pdf/component/HighlightAnnotation";
static const char* kFreeTextAnnotation = "android/graphics/pdf/component/FreeTextAnnotation";

static const char* kBitmap = "android/graphics/Bitmap";
static const char* kBitmapConfig = "android/graphics/Bitmap$Config";
static const char* kColor = "android/graphics/Color";
static const char* kMatrix = "android/graphics/Matrix";
static const char* kPath = "android/graphics/Path";
static const char* kRect = "android/graphics/Rect";
static const char* kRectF = "android/graphics/RectF";
static const char* kInteger = "java/lang/Integer";
static const char* kString = "java/lang/String";
static const char* kObject = "java/lang/Object";
static const char* kArrayList = "java/util/ArrayList";
static const char* kList = "java/util/List";
static const char* kSet = "java/util/Set";
static const char* kIterator = "java/util/Iterator";
static const char* kFloat = "java/lang/Float";

// Helper methods to build up type signatures like "Ljava/lang/Object;" and
// function signatures like "(I)Ljava/lang/Integer;":
string sig(const char* raw) {
    if (strlen(raw) == 1)
        return raw;
    else {
        string res = "L";
        res += raw;
        res += ";";
        return res;
    }
}

// Function to build up type signatures like "Ljava/lang/Object;" and
// function signatures like "(I)Ljava/lang/Integer;":
template <typename... Args>
string funcsig(const char* return_type, const Args... params) {
    vector<const char*> vec = {params...};
    string res = "(";
    for (const char* param : vec) {
        res += sig(param);
    }
    res += ")";
    res += sig(return_type);
    return res;
}

// Classes can move around - if we want a long-lived pointer to one, we have
// get a global reference to it which will be updated if the class moves.
inline jclass GetPermClassRef(JNIEnv* env, const std::string& classname) {
    // NOTE: These references are held for the duration of the process.
    return (jclass)env->NewGlobalRef(env->FindClass(classname.c_str()));
}

// Convert an int to a java.lang.Integer.
jobject ToJavaInteger(JNIEnv* env, const int& i) {
    static jclass integer_class = GetPermClassRef(env, kInteger);
    static jmethodID value_of =
            env->GetStaticMethodID(integer_class, "valueOf", funcsig(kInteger, "I").c_str());
    return env->CallStaticObjectMethod(integer_class, value_of, i);
}

jobject ToJavaString(JNIEnv* env, const std::string& s) {
    return env->NewStringUTF(s.c_str());
}

jobject ToJavaString(JNIEnv* env, const std::wstring& ws) {
    jsize len = ws.length();
    jchar* jchars = new jchar[len + 1];  // Null Termination

    for (size_t i = 0; i < len; ++i) {
        jchars[i] = static_cast<jchar>(ws[i]);
    }
    jchars[len] = 0;

    jstring result = env->NewString(jchars, len);

    delete[] jchars;
    return result;
}

std::wstring ToNativeWideString(JNIEnv* env, jstring java_string) {
    std::wstring value;

    const jchar* raw = env->GetStringChars(java_string, 0);
    jsize len = env->GetStringLength(java_string);

    value.assign(raw, raw + len);

    env->ReleaseStringChars(java_string, raw);
    return value;
}

// Copy a C++ vector to a java ArrayList, using the given function to convert.
template <class T>
jobject ToJavaList(JNIEnv* env, const vector<T>& input,
                   jobject (*ToJavaObject)(JNIEnv* env, const T&)) {
    static jclass arraylist_class = GetPermClassRef(env, kArrayList);
    static jmethodID init = env->GetMethodID(arraylist_class, "<init>", "(I)V");
    static jmethodID add = env->GetMethodID(arraylist_class, "add",
                                                             funcsig("Z", kObject).c_str());

    jobject java_list = env->NewObject(arraylist_class, init, input.size());
    for (size_t i = 0; i < input.size(); i++) {
        jobject java_object = ToJavaObject(env, input[i]);
        env->CallBooleanMethod(java_list, add, java_object);
        env->DeleteLocalRef(java_object);
    }
    return java_list;
}

template <class T>
jobject ToJavaList(JNIEnv* env, const vector<T>& input, ICoordinateConverter* converter,
                   jobject (*ToJavaObject)(JNIEnv* env, const T&, ICoordinateConverter* converter)) {
    static jclass arraylist_class = GetPermClassRef(env, kArrayList);
    static jmethodID init = env->GetMethodID(arraylist_class, "<init>", "(I)V");
    static jmethodID add = env->GetMethodID(arraylist_class, "add", funcsig("Z", kObject).c_str());

    jobject java_list = env->NewObject(arraylist_class, init, input.size());
    for (size_t i = 0; i < input.size(); i++) {
        jobject java_object = ToJavaObject(env, input[i], converter);
        env->CallBooleanMethod(java_list, add, java_object);
        env->DeleteLocalRef(java_object);
    }
    return java_list;
}

// Copy a C++ vector to a java ArrayList, using the given function to convert.
template <class T>
jobject ToJavaList(JNIEnv* env, const vector<T*>& input, ICoordinateConverter* converter,
                   jobject (*ToJavaObject)(JNIEnv* env, const T*,
                                           ICoordinateConverter* converter)) {
    static jclass arraylist_class = GetPermClassRef(env, kArrayList);
    static jmethodID init = env->GetMethodID(arraylist_class, "<init>", "(I)V");
    static jmethodID add = env->GetMethodID(arraylist_class, "add", funcsig("Z", kObject).c_str());

    jobject java_list = env->NewObject(arraylist_class, init, input.size());
    for (size_t i = 0; i < input.size(); i++) {
        jobject java_object = ToJavaObject(env, input[i], converter);
        env->CallBooleanMethod(java_list, add, java_object);
        env->DeleteLocalRef(java_object);
    }
    return java_list;
}

}  // namespace

jobject ToJavaPdfDocument(JNIEnv* env, std::unique_ptr<Document> doc) {
    static jclass pdf_doc_class = GetPermClassRef(env, kPdfDocument);
    static jmethodID init = env->GetMethodID(pdf_doc_class, "<init>", "(JI)V");

    int numPages = doc->NumPages();
    // Transfer ownership of |doc| to the Java object by releasing it.
    return env->NewObject(pdf_doc_class, init, (jlong)doc.release(), numPages);
}

jobject ToJavaLoadPdfResult(JNIEnv* env, const Status status, std::unique_ptr<Document> doc,
                            size_t pdfSizeInByte) {
    static jclass result_class = GetPermClassRef(env, kLoadPdfResult);
    static jmethodID init =
            env->GetMethodID(result_class, "<init>", funcsig("V", "I", kPdfDocument, "F").c_str());

    jobject jPdfDocument = (!doc) ? nullptr : ToJavaPdfDocument(env, std::move(doc));
    jfloat pdfSizeInKb = pdfSizeInByte / 1024.0f;
    return env->NewObject(result_class, init, (jint)status, jPdfDocument, pdfSizeInKb);
}

Document* GetPdfDocPtr(JNIEnv* env, jobject jPdfDocument) {
    static jfieldID pdp_field =
            env->GetFieldID(GetPermClassRef(env, kPdfDocument), "mPdfDocPtr", "J");
    jlong pdf_doc_ptr = env->GetLongField(jPdfDocument, pdp_field);
    return reinterpret_cast<Document*>(pdf_doc_ptr);
}

SelectionBoundary ToNativeBoundary(JNIEnv* env, jobject jBoundary) {
    static jclass boundary_class = GetPermClassRef(env, kBoundary);
    static jfieldID index_field = env->GetFieldID(boundary_class, "mIndex", "I");
    static jfieldID x_field = env->GetFieldID(boundary_class, "mX", "I");
    static jfieldID y_field = env->GetFieldID(boundary_class, "mY", "I");
    static jfieldID rtl_field = env->GetFieldID(boundary_class, "mIsRtl", "Z");

    return SelectionBoundary(
            env->GetIntField(jBoundary, index_field), env->GetIntField(jBoundary, x_field),
            env->GetIntField(jBoundary, y_field), env->GetBooleanField(jBoundary, rtl_field));
}

int ToNativeInteger(JNIEnv* env, jobject jInteger) {
    static jclass integer_class = GetPermClassRef(env, kInteger);
    static jmethodID get_int_value = env->GetMethodID(integer_class, "intValue", "()I");
    return env->CallIntMethod(jInteger, get_int_value);
}

vector<int> ToNativeIntegerVector(JNIEnv* env, jintArray jintArray) {
    jsize size = env->GetArrayLength(jintArray);
    vector<int> output(size);
    env->GetIntArrayRegion(jintArray, jsize{0}, size, &output[0]);
    return output;
}

std::unordered_set<int> ToNativeIntegerUnorderedSet(JNIEnv* env, jintArray jintArray) {
    jsize size = env->GetArrayLength(jintArray);
    vector<int> intermediate(size);
    env->GetIntArrayRegion(jintArray, jsize{0}, size, &intermediate[0]);
    return std::unordered_set<int>(std::begin(intermediate), std::end(intermediate));
}

jobject ToJavaRect(JNIEnv* env, const Rectangle_i& r) {
    static jclass rect_class = GetPermClassRef(env, kRect);
    static jmethodID init = env->GetMethodID(rect_class, "<init>", "(IIII)V");
    return env->NewObject(rect_class, init, r.left, r.top, r.right, r.bottom);
}

jobject ToJavaRectF(JNIEnv* env, const Rectangle_i& r) {
    static jclass rectF_class = GetPermClassRef(env, kRectF);
    static jmethodID init = env->GetMethodID(rectF_class, "<init>", "(FFFF)V");
    return env->NewObject(rectF_class, init, float(r.left), float(r.top), float(r.right),
                          float(r.bottom));
}

jobject ToJavaRectF(JNIEnv* env, const Rectangle_f& r, ICoordinateConverter* converter) {
    static jclass rectF_class = GetPermClassRef(env, kRectF);
    static jmethodID init = env->GetMethodID(rectF_class, "<init>", "(FFFF)V");

    Point_f top_left_corner = converter->PageToDevice({r.left, r.top});
    Point_f bottom_down_corner = converter->PageToDevice({r.right, r.bottom});
    return env->NewObject(rectF_class, init, top_left_corner.x, top_left_corner.y,
                          bottom_down_corner.x, bottom_down_corner.y);
}

Rectangle_f ToNativeRectF(JNIEnv* env, jobject java_rectF, ICoordinateConverter* converter) {
    static jclass rectF_class = GetPermClassRef(env, kRectF);
    static jfieldID left_field = env->GetFieldID(rectF_class, "left", "F");
    static jfieldID top_field = env->GetFieldID(rectF_class, "top", "F");
    static jfieldID right_field = env->GetFieldID(rectF_class, "right", "F");
    static jfieldID bottom_field = env->GetFieldID(rectF_class, "bottom", "F");

    float left = env->GetFloatField(java_rectF, left_field);
    float top = env->GetFloatField(java_rectF, top_field);
    float right = env->GetFloatField(java_rectF, right_field);
    float bottom = env->GetFloatField(java_rectF, bottom_field);

    Point_f top_left_corner = converter->DeviceToPage({left, top});
    Point_f bottom_down_corner = converter->DeviceToPage({right, bottom});

    return Rectangle_f{top_left_corner.x, top_left_corner.y, bottom_down_corner.x,
                       bottom_down_corner.y};
}

jobject ToJavaRects(JNIEnv* env, const vector<Rectangle_i>& rects) {
    return ToJavaList(env, rects, &ToJavaRect);
}

jobject ToJavaDimensions(JNIEnv* env, const Rectangle_i& r) {
    static jclass dim_class = GetPermClassRef(env, kDimensions);
    static jmethodID init = env->GetMethodID(dim_class, "<init>", "(II)V");
    return env->NewObject(dim_class, init, r.Width(), r.Height());
}

jobject ToJavaStrings(JNIEnv* env, const vector<std::string>& strings) {
    return ToJavaList(env, strings, &ToJavaString);
}

jobject ToJavaMatchRects(JNIEnv* env, const vector<Rectangle_i>& rects,
                         const vector<int>& match_to_rect, const vector<int>& char_indexes) {
    static jclass match_rects_class = GetPermClassRef(env, kMatchRects);
    static jmethodID init = env->GetMethodID(match_rects_class, "<init>",
                                             funcsig("V", kList, kList, kList).c_str());
    static jfieldID no_matches_field =
            env->GetStaticFieldID(match_rects_class, "NO_MATCHES", sig(kMatchRects).c_str());
    static jobject no_matches =
            env->NewGlobalRef(env->GetStaticObjectField(match_rects_class, no_matches_field));

    if (rects.empty()) {
        return no_matches;
    }
    jobject java_rects = ToJavaList(env, rects, &ToJavaRect);
    jobject java_m2r = ToJavaList(env, match_to_rect, &ToJavaInteger);
    jobject java_cidx = ToJavaList(env, char_indexes, &ToJavaInteger);
    return env->NewObject(match_rects_class, init, java_rects, java_m2r, java_cidx);
}

jobject ToJavaBoundary(JNIEnv* env, const SelectionBoundary& boundary) {
    static jclass boundary_class = GetPermClassRef(env, kBoundary);
    static jmethodID init = env->GetMethodID(boundary_class, "<init>", "(IIIZ)V");
    return env->NewObject(boundary_class, init, boundary.index, boundary.point.x, boundary.point.y,
                          boundary.is_rtl);
}

jobject ToJavaSelection(JNIEnv* env, const int page, const SelectionBoundary& start,
                        const SelectionBoundary& stop, const vector<Rectangle_i>& rects,
                        const std::string& text) {
    static jclass selection_class = GetPermClassRef(env, kSelection);
    static jmethodID init =
            env->GetMethodID(selection_class, "<init>",
                             funcsig("V", "I", kBoundary, kBoundary, kList, kString).c_str());

    // If rects is empty then it means that the text is empty as well.
    if (rects.empty()) {
        return nullptr;
    }

    jobject java_rects = ToJavaList(env, rects, &ToJavaRect);
    return env->NewObject(selection_class, init, page, ToJavaBoundary(env, start),
                          ToJavaBoundary(env, stop), java_rects, env->NewStringUTF(text.c_str()));
}

jobject ToJavaLinkRects(JNIEnv* env, const vector<Rectangle_i>& rects,
                        const vector<int>& link_to_rect, const vector<std::string>& urls) {
    static jclass link_rects_class = GetPermClassRef(env, kLinkRects);
    static jmethodID init =
            env->GetMethodID(link_rects_class, "<init>", funcsig("V", kList, kList, kList).c_str());
    static jfieldID no_links_field =
            env->GetStaticFieldID(link_rects_class, "NO_LINKS", sig(kLinkRects).c_str());
    static jobject no_links =
            env->NewGlobalRef(env->GetStaticObjectField(link_rects_class, no_links_field));

    if (rects.empty()) {
        return no_links;
    }
    jobject java_rects = ToJavaList(env, rects, &ToJavaRect);
    jobject java_l2r = ToJavaList(env, link_to_rect, &ToJavaInteger);
    jobject java_urls = ToJavaList(env, urls, &ToJavaString);
    return env->NewObject(link_rects_class, init, java_rects, java_l2r, java_urls);
}

jobject ToJavaChoiceOption(JNIEnv* env, const Option& option) {
    static jclass choice_option_class = GetPermClassRef(env, kChoiceOption);
    static jmethodID init =
            env->GetMethodID(choice_option_class, "<init>", funcsig("V", kString, "Z").c_str());
    jobject java_label = ToJavaString(env, option.label);
    return env->NewObject(choice_option_class, init, java_label, option.selected);
}

jobject ToJavaFormWidgetInfo(JNIEnv* env, const FormWidgetInfo& form_action_result) {
    static jclass click_result_class = GetPermClassRef(env, kFormWidgetInfo);

    static jmethodID init = env->GetMethodID(
            click_result_class, "<init>",
            funcsig("V", "I", "I", kRect, "Z", kString, kString, "Z", "Z", "Z", "I", "F", kList)
                    .c_str());

    jobject java_widget_rect = ToJavaRect(env, form_action_result.widget_rect());
    jobject java_text_value = ToJavaString(env, form_action_result.text_value());
    jobject java_accessibility_label = ToJavaString(env, form_action_result.accessibility_label());
    jobject java_choice_options = ToJavaList(env, form_action_result.options(), &ToJavaChoiceOption);

    return env->NewObject(click_result_class, init, form_action_result.widget_type(),
                          form_action_result.widget_index(), java_widget_rect,
                          form_action_result.read_only(), java_text_value, java_accessibility_label,
                          form_action_result.editable_text(), form_action_result.multiselect(),
                          form_action_result.multi_line_text(), form_action_result.max_length(),
                          form_action_result.font_size(), java_choice_options);
}

jobject ToJavaFormWidgetInfos(JNIEnv* env, const std::vector<FormWidgetInfo>& widget_infos) {
    return ToJavaList(env, widget_infos, &ToJavaFormWidgetInfo);
}

jobject ToJavaDestination(JNIEnv* env, const GotoLinkDest dest) {
    static jclass goto_link_dest_class = GetPermClassRef(env, kGotoLinkDestination);
    static jmethodID init = env->GetMethodID(goto_link_dest_class, "<init>",
                                             funcsig("V", "I", "F", "F", "F").c_str());

    return env->NewObject(goto_link_dest_class, init, dest.page_number, dest.x, dest.y, dest.zoom);
}

jobject ToJavaGotoLink(JNIEnv* env, const GotoLink& link) {
    static jclass goto_link_class = GetPermClassRef(env, kGotoLink);
    static jmethodID init = env->GetMethodID(goto_link_class, "<init>",
                                             funcsig("V", kList, kGotoLinkDestination).c_str());

    jobject java_rects = ToJavaList(env, link.rect, &ToJavaRectF);
    jobject goto_link_dest = ToJavaDestination(env, link.dest);

    return env->NewObject(goto_link_class, init, java_rects, goto_link_dest);
}

jobject ToJavaGotoLinks(JNIEnv* env, const vector<GotoLink>& links) {
    return ToJavaList(env, links, &ToJavaGotoLink);
}

void ConvertBgrToRgba(uint32_t* rgba_pixel_array, uint8_t* bgr_pixel_array, size_t rgba_stride,
                      size_t bgr_stride, size_t width, size_t height) {
    for (size_t y = 0; y < height; y++) {
        uint32_t* rgba_row_ptr = rgba_pixel_array + y * (rgba_stride / 4);
        uint8_t* bgr_row_ptr = bgr_pixel_array + y * (bgr_stride);
        for (size_t x = 0; x < width; x++) {
            // Extract BGR components stored.
            uint8_t blue = bgr_row_ptr[x * 3];
            uint8_t green = bgr_row_ptr[x * 3 + 1];
            uint8_t red = bgr_row_ptr[x * 3 + 2];
            // Storing java bitmap components RGBA in little-endian.
            rgba_row_ptr[x] = (0xFF << 24) | (blue << 16) | (green << 8) | red;
        }
    }
}

void ConvertBgraToRgba(uint32_t* rgba_pixel_array, uint8_t* bgra_pixel_array, size_t rgba_stride,
                       size_t bgra_stride, size_t width, size_t height, bool ignore_alpha) {
    for (size_t y = 0; y < height; y++) {
        uint32_t* rgba_row_ptr = rgba_pixel_array + y * (rgba_stride / 4);
        uint8_t* bgra_row_ptr = bgra_pixel_array + y * (bgra_stride);
        for (size_t x = 0; x < width; x++) {
            // Extract BGR components and determine alpha based on ignore_alpha flag.
            uint8_t blue = bgra_row_ptr[x * 4];
            uint8_t green = bgra_row_ptr[x * 4 + 1];
            uint8_t red = bgra_row_ptr[x * 4 + 2];
            uint8_t alpha = ignore_alpha ? 0xFF : bgra_row_ptr[x * 4 + 3];
            // Storing java bitmap components RGBA in little-endian.
            rgba_row_ptr[x] = (alpha << 24) | (blue << 16) | (green << 8) | red;
        }
    }
}

jobject ToJavaBitmap(JNIEnv* env, void* buffer, BitmapFormat bitmap_format, size_t width,
                     size_t height, size_t native_stride) {
    // Find Java Bitmap class
    static jclass bitmap_class = GetPermClassRef(env, kBitmap);

    // Get createBitmap method ID
    static jmethodID create_bitmap = env->GetStaticMethodID(
            bitmap_class, "createBitmap", funcsig(kBitmap, "I", "I", kBitmapConfig).c_str());

    // Get Bitmap.Config.ARGB_8888 field ID
    static jclass bitmap_config_class = GetPermClassRef(env, kBitmapConfig);
    static jfieldID argb8888_field =
            env->GetStaticFieldID(bitmap_config_class, "ARGB_8888", sig(kBitmapConfig).c_str());
    static jobject argb8888 =
            env->NewGlobalRef(env->GetStaticObjectField(bitmap_config_class, argb8888_field));

    // Create a Java Bitmap object
    jobject java_bitmap =
            env->CallStaticObjectMethod(bitmap_class, create_bitmap, width, height, argb8888);

    // Copy the buffer data into java bitmap.
    AndroidBitmapInfo bitmap_info;
    AndroidBitmap_getInfo(env, java_bitmap, &bitmap_info);
    size_t java_stride = bitmap_info.stride;

    void* bitmap_pixels;
    if (AndroidBitmap_lockPixels(env, java_bitmap, &bitmap_pixels) < 0) {
        return NULL;
    }

    uint32_t* java_pixel_array = static_cast<uint32_t*>(bitmap_pixels);
    uint8_t* native_pixel_array = static_cast<uint8_t*>(buffer);
    switch (bitmap_format) {
        case BitmapFormat::BGR: {
            ConvertBgrToRgba(java_pixel_array, native_pixel_array, java_stride, native_stride,
                             width, height);
            break;
        }
        case BitmapFormat::BGRA: {
            ConvertBgraToRgba(java_pixel_array, native_pixel_array, java_stride, native_stride,
                              width, height, false);
            break;
        }
        case BitmapFormat::BGRx: {
            ConvertBgraToRgba(java_pixel_array, native_pixel_array, java_stride, native_stride,
                              width, height, true);
            break;
        }
        default: {
            LOGE("Bitmap format unknown!");
            AndroidBitmap_unlockPixels(env, java_bitmap);
            return NULL;
        }
    }

    AndroidBitmap_unlockPixels(env, java_bitmap);

    return java_bitmap;
}

int ToJavaColorInt(Color color) {
    // Get ARGB values from Native Color
    uint A = color.a;
    uint R = color.r;
    uint G = color.g;
    uint B = color.b;

    // Make ARGB  java color int
    int java_color_int = (A & 0xFF) << 24 | (R & 0xFF) << 16 | (G & 0xFF) << 8 | (B & 0xFF);

    return java_color_int;
}

jobject ToJavaColor(JNIEnv* env, Color color) {
    // Find Java Color class
    static jclass color_class = GetPermClassRef(env, kColor);

    // Get valueOf method ID
    static jmethodID value_of =
            env->GetStaticMethodID(color_class, "valueOf", funcsig(kColor, "I").c_str());

    // Make ARGB  java color int
    int java_color_int = ToJavaColorInt(color);

    // Create a Java Color Object.
    jobject java_color = env->CallStaticObjectMethod(color_class, value_of, java_color_int);

    return java_color;
}

jfloatArray ToJavaFloatArray(JNIEnv* env, const float arr[], size_t length) {
    // Create Java float Array.
    jfloatArray java_float_array = env->NewFloatArray(length);

    // Copy data from the C++ float Array to the Java float Array
    env->SetFloatArrayRegion(java_float_array, 0, length, arr);

    return java_float_array;
}

jobject ToJavaMatrix(JNIEnv* env, const Matrix matrix) {
    // Find Java Matrix class
    static jclass matrix_class = GetPermClassRef(env, kMatrix);
    // Get the constructor method ID
    static jmethodID init = env->GetMethodID(matrix_class, "<init>", funcsig("V").c_str());

    // Create Java Matrix object.
    jobject java_matrix = env->NewObject(matrix_class, init);

    // Create Transform Array.
    float transform[9] = {matrix.a, matrix.c, matrix.e, matrix.b, matrix.d, matrix.f, 0, 0, 1};

    // Convert to Java floatArray.
    jfloatArray java_float_array =
            ToJavaFloatArray(env, transform, sizeof(transform) / sizeof(transform[0]));

    // Matrix setValues.
    static jmethodID set_values = env->GetMethodID(matrix_class, "setValues", "([F)V");
    env->CallVoidMethod(java_matrix, set_values, java_float_array);

    return java_matrix;
}

jobject ToJavaPath(JNIEnv* env, const std::vector<PathObject::Segment>& segments,
                   ICoordinateConverter* converter) {
    // Find Java Path class.
    static jclass path_class = GetPermClassRef(env, kPath);
    // Get the constructor methodID.
    static jmethodID init = env->GetMethodID(path_class, "<init>", funcsig("V").c_str());

    // Create Java Path object.
    jobject java_path = env->NewObject(path_class, init);

    // Set Path Segments in Java.
    for (auto& segment : segments) {
        // Get PageToDevice Coordinates
        Point_f output = converter->PageToDevice({segment.x, segment.y});
        switch (segment.command) {
            case PathObject::Segment::Command::Move: {
                static jmethodID move_to =
                        env->GetMethodID(path_class, "moveTo", funcsig("V", "F", "F").c_str());
                env->CallVoidMethod(java_path, move_to, output.x, output.y);
                break;
            }
            case PathObject::Segment::Command::Line: {
                static jmethodID line_to =
                        env->GetMethodID(path_class, "lineTo", funcsig("V", "F", "F").c_str());
                env->CallVoidMethod(java_path, line_to, output.x, output.y);
                break;
            }
            default:
                break;
        }
        // Check if segment isClosed.
        if (segment.is_closed) {
            static jmethodID close = env->GetMethodID(path_class, "close", funcsig("V").c_str());
            env->CallVoidMethod(java_path, close);
        }
    }

    return java_path;
}

jobject ToJavaPdfTextObject(JNIEnv* env, const TextObject* text_object) {
    // Find Java PdfTextObject Class.
    static jclass text_object_class = GetPermClassRef(env, kTextObject);

    // Create Java Text String from TextObject Data String.
    jobject java_string = ToJavaString(env, text_object->text_);

    // Get Native Font Object Data.
    int font_family = static_cast<int>(text_object->font_.GetFamily());
    bool bold = text_object->font_.IsBold();
    bool italic = text_object->font_.IsItalic();

    // Create Java TextObjectFont Instance.
    static jclass text_font_class = GetPermClassRef(env, kTextFont);
    static jmethodID init_text_font =
            env->GetMethodID(text_font_class, "<init>", funcsig("V", "I", "Z", "Z").c_str());
    jobject java_font = env->NewObject(text_font_class, init_text_font, font_family, bold, italic);

    // Create Java PdfTextObject Instance.
    static jmethodID init_text_object = env->GetMethodID(
            text_object_class, "<init>", funcsig("V", kString, kTextFont, "F").c_str());
    float font_size = text_object->font_size_;
    jobject java_text_object =
            env->NewObject(text_object_class, init_text_object, java_string, java_font, font_size);

    // Set Java PdfTextObject Render Mode.
    int render_mode = static_cast<int>(text_object->render_mode_);
    static jmethodID set_render_mode = env->GetMethodID(text_object_class, "setRenderMode", "(I)V");
    env->CallVoidMethod(java_text_object, set_render_mode, render_mode);

    // Set Java PdfTextObject Fill Color.
    static jmethodID set_fill_color = env->GetMethodID(text_object_class, "setFillColor", "(I)V");
    env->CallVoidMethod(java_text_object, set_fill_color, ToJavaColorInt(text_object->fill_color_));

    // Set Java PdfTextObject Stroke Color.
    static jmethodID set_stroke_color =
            env->GetMethodID(text_object_class, "setStrokeColor", "(I)V");
    env->CallVoidMethod(java_text_object, set_stroke_color,
                        ToJavaColorInt(text_object->stroke_color_));

    // Set Java PdfTextObject Stroke Width.
    static jmethodID set_stroke_width =
            env->GetMethodID(text_object_class, "setStrokeWidth", "(F)V");
    env->CallVoidMethod(java_text_object, set_stroke_width, text_object->stroke_width_);

    return java_text_object;
}

jobject ToJavaPdfPathObject(JNIEnv* env, const PathObject* path_object,
                            ICoordinateConverter* converter) {
    // Find Java PdfPathObject Class.
    static jclass path_object_class = GetPermClassRef(env, kPathObject);
    // Get Constructor Id.
    static jmethodID init_path =
            env->GetMethodID(path_object_class, "<init>", funcsig("V", kPath).c_str());

    // Create Java Path from Native PathSegments.
    jobject java_path = ToJavaPath(env, path_object->segments_, converter);

    // Create Java PdfPathObject Instance.
    jobject java_path_object = env->NewObject(path_object_class, init_path, java_path);

    // Set Java PdfPathObject FillColor.
    if (path_object->is_fill_) {
        static jmethodID set_fill_color =
                env->GetMethodID(path_object_class, "setFillColor", funcsig("V", "I").c_str());

        env->CallVoidMethod(java_path_object, set_fill_color,
                            ToJavaColorInt(path_object->fill_color_));
    }

    // Set Java PdfPathObject StrokeColor.
    if (path_object->is_stroke_) {
        static jmethodID set_stroke_color =
                env->GetMethodID(path_object_class, "setStrokeColor", funcsig("V", "I").c_str());

        env->CallVoidMethod(java_path_object, set_stroke_color,
                            ToJavaColorInt(path_object->stroke_color_));
    }

    // Set Java Stroke Width.
    static jmethodID set_stroke_width =
            env->GetMethodID(path_object_class, "setStrokeWidth", "(F)V");
    env->CallVoidMethod(java_path_object, set_stroke_width, path_object->stroke_width_);

    return java_path_object;
}

jobject ToJavaPdfImageObject(JNIEnv* env, const ImageObject* image_object) {
    // Find Java ImageObject Class.
    static jclass image_object_class = GetPermClassRef(env, kImageObject);
    // Get Constructor Id.
    static jmethodID init_image =
            env->GetMethodID(image_object_class, "<init>", funcsig("V", kBitmap).c_str());

    // Create Java Bitmap from Native Bitmap Buffer.
    void* buffer = image_object->GetBitmapBuffer();
    BitmapFormat bitmap_format = image_object->bitmap_format_;
    size_t width = image_object->width_;
    size_t height = image_object->height_;
    int stride = FPDFBitmap_GetStride(image_object->bitmap_.get());
    jobject java_bitmap = ToJavaBitmap(env, buffer, bitmap_format, width, height, stride);
    if (java_bitmap == NULL) {
        LOGE("To java bitmap conversion failed!");
        return NULL;
    }

    // Create Java PdfImageObject Instance.
    jobject java_image_object = env->NewObject(image_object_class, init_image, java_bitmap);

    return java_image_object;
}

jobject ToJavaPdfPageObject(JNIEnv* env, const PageObject* page_object,
                            ICoordinateConverter* converter) {
    // Check for Native Supported Object.
    if (!page_object) {
        return NULL;
    }

    jobject java_page_object = NULL;

    switch (page_object->GetType()) {
        case PageObject::Type::Path: {
            const PathObject* path_object = static_cast<const PathObject*>(page_object);
            java_page_object = ToJavaPdfPathObject(env, path_object, converter);
            break;
        }
        case PageObject::Type::Image: {
            const ImageObject* image_object = static_cast<const ImageObject*>(page_object);
            java_page_object = ToJavaPdfImageObject(env, image_object);
            break;
        }
        default:
            break;
    }

    // If no PageObject was created, return null
    if (java_page_object == NULL) {
        return NULL;
    }

    // Find Java PageObject Class.
    static jclass page_object_class = GetPermClassRef(env, kPageObject);

    // Set Java PdfPageObject Matrix.
    static jmethodID set_matrix =
            env->GetMethodID(page_object_class, "setMatrix", funcsig("V", kMatrix).c_str());
    env->CallVoidMethod(java_page_object, set_matrix,
                        ToJavaMatrix(env, page_object->device_matrix_));

    return java_page_object;
}

jobject ToJavaPdfPageObjects(JNIEnv* env, const vector<PageObject*>& page_objects,
                             ICoordinateConverter* converter) {
    return ToJavaList(env, page_objects, converter, &ToJavaPdfPageObject);
}

Color ToNativeColor(jint java_color_int) {
    // Decoding RGBA components
    unsigned int red = (java_color_int >> 16) & 0xFF;
    unsigned int green = (java_color_int >> 8) & 0xFF;
    unsigned int blue = java_color_int & 0xFF;
    unsigned int alpha = (java_color_int >> 24) & 0xFF;

    return Color(red, green, blue, alpha);
}

Color ToNativeColor(JNIEnv* env, jobject java_color) {
    // Find Java Color class
    static jclass color_class = GetPermClassRef(env, kColor);

    // Get the color as an ARGB integer
    jmethodID get_color_int = env->GetMethodID(color_class, "toArgb", funcsig("I").c_str());
    jint java_color_int = env->CallIntMethod(java_color, get_color_int);

    return ToNativeColor(java_color_int);
}

std::unique_ptr<TextObject> ToNativeTextObject(JNIEnv* env, jobject java_text_object) {
    // Create TextObject Data Instance.
    auto text_object = std::make_unique<TextObject>();

    // Get Ref to Java PdfTextObject Class.
    static jclass text_object_class = GetPermClassRef(env, kTextObject);

    // Get Java PdfTextObject Font.
    static jmethodID get_text_font =
            env->GetMethodID(text_object_class, "getFont", funcsig(kTextFont).c_str());
    jobject java_text_font = env->CallObjectMethod(java_text_object, get_text_font);

    // Find Java PdfTextObjectFont Class.
    static jclass text_font_class = GetPermClassRef(env, kTextFont);

    // Get the Font Family for the PdfTextObjectFont.
    static jmethodID get_font_family = env->GetMethodID(text_font_class, "getFontFamily", "()I");
    jint font_family = env->CallIntMethod(java_text_font, get_font_family);

    // Is PdfTextObjectFont Bold.
    static jmethodID is_bold = env->GetMethodID(text_font_class, "isBold", "()Z");
    jboolean bold = env->CallBooleanMethod(java_text_font, is_bold);

    // Is PdfTextObjectFont Italic.
    static jmethodID is_italic = env->GetMethodID(text_font_class, "isItalic", "()Z");
    jboolean italic = env->CallBooleanMethod(java_text_font, is_italic);

    // Set TextObject Data Font.
    if (font_family < 0 || font_family >= font_names.size()) {
        return nullptr;
    }
    text_object->font_ =
            Font(font_names[font_family], static_cast<Font::Family>(font_family), bold, italic);

    // Get Java PdfTextObject font size.
    static jmethodID get_font_size = env->GetMethodID(text_object_class, "getFontSize", "()F");
    jfloat font_size = env->CallFloatMethod(java_text_object, get_font_size);

    // Set TextObject Data font size.
    text_object->font_size_ = font_size;

    // Get Java PdfTextObject Text.
    static jmethodID get_text =
            env->GetMethodID(text_object_class, "getText", funcsig(kString).c_str());
    jstring java_text = static_cast<jstring>(env->CallObjectMethod(java_text_object, get_text));

    // Set TextObject Data Text.
    text_object->text_ = ToNativeWideString(env, java_text);

    // Get Java PdfTextObject RenderMode.
    static jmethodID get_render_mode = env->GetMethodID(text_object_class, "getRenderMode", "()I");
    jint render_mode = env->CallIntMethod(java_text_object, get_render_mode);

    // Set TextObject Data RenderMode.
    switch (static_cast<TextObject::RenderMode>(render_mode)) {
        case TextObject::RenderMode::Fill: {
            text_object->render_mode_ = TextObject::RenderMode::Fill;
            break;
        }
        case TextObject::RenderMode::Stroke: {
            text_object->render_mode_ = TextObject::RenderMode::Stroke;
            break;
        }
        case TextObject::RenderMode::FillStroke: {
            text_object->render_mode_ = TextObject::RenderMode::FillStroke;
            break;
        }
        default: {
            text_object->render_mode_ = TextObject::RenderMode::Unknown;
            break;
        }
    }

    // Get Java PdfTextObject Fill Color.
    static jmethodID get_fill_color = env->GetMethodID(text_object_class, "getFillColor", "()I");
    jint java_fill_color = env->CallIntMethod(java_text_object, get_fill_color);

    // Set TextObject Data Fill Color
    text_object->fill_color_ = ToNativeColor(java_fill_color);

    // Get Java PdfTextObject Stroke Color.
    static jmethodID get_stroke_color =
            env->GetMethodID(text_object_class, "getStrokeColor", "()I");
    jint java_stroke_color = env->CallIntMethod(java_text_object, get_stroke_color);

    // Set TextObject Data Stroke Color.
    text_object->stroke_color_ = ToNativeColor(java_stroke_color);

    // Get Java PdfTextObject Stroke Width.
    static jmethodID get_stroke_width =
            env->GetMethodID(text_object_class, "getStrokeWidth", "()F");
    jfloat stroke_width = env->CallFloatMethod(java_text_object, get_stroke_width);

    // Set TextObject Data Stroke Width.
    text_object->stroke_width_ = stroke_width;

    return text_object;
}

std::unique_ptr<PathObject> ToNativePathObject(JNIEnv* env, jobject java_path_object,
                                               ICoordinateConverter* converter) {
    // Create PathObject Data Instance.
    auto path_object = std::make_unique<PathObject>();

    // Get Ref to Java PathObject Class.
    static jclass path_object_class = GetPermClassRef(env, kPathObject);

    // Get Path from Java PathObject.
    static jmethodID to_path =
            env->GetMethodID(path_object_class, "toPath", funcsig(kPath).c_str());
    jobject java_path = env->CallObjectMethod(java_path_object, to_path);

    // Find Java Path Class.
    static jclass path_class = GetPermClassRef(env, kPath);

    // Get the Approximate Array for the Path.
    static jmethodID approximate = env->GetMethodID(path_class, "approximate", "(F)[F");
    // The acceptable error while approximating a Path Curve with a line.
    static const float acceptable_error = 0.5f;
    jfloatArray java_approximate =
            (jfloatArray)env->CallObjectMethod(java_path, approximate, acceptable_error);
    const jsize size = env->GetArrayLength(java_approximate);

    // Copy Java Array to Native Array
    float path_approximate[size];
    env->GetFloatArrayRegion(java_approximate, 0, size, path_approximate);

    // Set PathObject Data PathSegments.
    auto& segments = path_object->segments_;
    for (int i = 0; i < size; i += 3) {
        // Get DeviceToPage Coordinates
        Point_f output =
                converter->DeviceToPage({path_approximate[i + 1], path_approximate[i + 2]});
        if (i == 0 || path_approximate[i] == path_approximate[i - 3]) {
            segments.emplace_back(PathObject::Segment::Command::Move, output.x, output.y);
        } else {
            segments.emplace_back(PathObject::Segment::Command::Line, output.x, output.y);
        }
    }

    // Get Java PathObject Fill Color.
    static jmethodID get_fill_color =
            env->GetMethodID(path_object_class, "getFillColor", funcsig("I").c_str());
    jint java_fill_color = env->CallIntMethod(java_path_object, get_fill_color);

    // Set PathObject Data Fill Mode and Fill Color
    path_object->is_fill_ = (java_fill_color != 0);
    if (path_object->is_fill_) {
        path_object->fill_color_ = ToNativeColor(java_fill_color);
    }

    // Get Java PathObject Stroke Color.
    static jmethodID get_stroke_color =
            env->GetMethodID(path_object_class, "getStrokeColor", funcsig("I").c_str());
    jint java_stroke_color = env->CallIntMethod(java_path_object, get_stroke_color);

    // Set PathObject Data Stroke Mode and Stroke Color.
    path_object->is_stroke_ = (java_stroke_color != 0);
    if (path_object->is_stroke_) {
        path_object->stroke_color_ = ToNativeColor(java_stroke_color);
    }

    // Get Java PathObject Stroke Width.
    static jmethodID get_stroke_width =
            env->GetMethodID(path_object_class, "getStrokeWidth", funcsig("F").c_str());
    jfloat stroke_width = env->CallFloatMethod(java_path_object, get_stroke_width);

    // Set PathObject Data Stroke Width.
    path_object->stroke_width_ = stroke_width;

    return path_object;
}

void CopyRgbaToBgra(uint8_t* rgba_pixel_array, size_t rgba_stride, uint32_t* bgra_pixel_array,
                    size_t bgra_stride, size_t width, size_t height) {
    for (size_t y = 0; y < height; y++) {
        uint8_t* rgba_row_ptr = rgba_pixel_array + y * rgba_stride;
        uint32_t* bgra_row_ptr = bgra_pixel_array + y * (bgra_stride / 4);
        for (size_t x = 0; x < width; x++) {
            // Extract RGBA components stored.
            uint8_t red = rgba_row_ptr[x * 4];
            uint8_t green = rgba_row_ptr[x * 4 + 1];
            uint8_t blue = rgba_row_ptr[x * 4 + 2];
            uint8_t alpha = rgba_row_ptr[x * 4 + 3];
            // Storing native bitmap components BGRA in little-endian.
            bgra_row_ptr[x] = (alpha << 24) | (red << 16) | (green << 8) | blue;
        }
    }
}

std::unique_ptr<ImageObject> ToNativeImageObject(JNIEnv* env, jobject java_image_object) {
    // Create ImageObject Data Instance.
    auto image_object = std::make_unique<ImageObject>();

    // Get Ref to Java ImageObject Class.
    static jclass image_object_class = GetPermClassRef(env, kImageObject);

    // Get the bitmap from the Java ImageObject.
    static jmethodID get_bitmap =
            env->GetMethodID(image_object_class, "getBitmap", funcsig(kBitmap).c_str());
    jobject java_bitmap = env->CallObjectMethod(java_image_object, get_bitmap);

    // Get android bitmap info.
    AndroidBitmapInfo bitmap_info;
    AndroidBitmap_getInfo(env, java_bitmap, &bitmap_info);
    if (bitmap_info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("Android bitmap is not in RGBA_8888 format");
        return nullptr;
    }
    size_t bitmap_width = bitmap_info.width;
    size_t bitmap_height = bitmap_info.height;
    size_t java_stride = bitmap_info.stride;

    // Create ImageObject data bitmap.
    image_object->bitmap_ = ScopedFPDFBitmap(FPDFBitmap_Create(bitmap_width, bitmap_height, 1));
    size_t native_stride = FPDFBitmap_GetStride(image_object->bitmap_.get());

    // Copy pixels from android bitmap.
    void* bitmap_pixels;
    if (AndroidBitmap_lockPixels(env, java_bitmap, &bitmap_pixels) < 0) {
        LOGE("Android bitmap lock pixels failed!");
        return nullptr;
    }

    uint8_t* java_pixel_array = static_cast<uint8_t*>(bitmap_pixels);
    uint32_t* native_pixel_array = static_cast<uint32_t*>(image_object->GetBitmapBuffer());

    CopyRgbaToBgra(java_pixel_array, java_stride, native_pixel_array, native_stride, bitmap_width,
                   bitmap_height);

    AndroidBitmap_unlockPixels(env, java_bitmap);

    return image_object;
}

std::unique_ptr<PageObject> ToNativePageObject(JNIEnv* env, jobject java_page_object,
                                               ICoordinateConverter* converter) {
    // Find Java PageObject class and GetType
    static jclass page_object_class = GetPermClassRef(env, kPageObject);
    static jmethodID get_type = env->GetMethodID(page_object_class, "getPdfObjectType", "()I");
    jint page_object_type = env->CallIntMethod(java_page_object, get_type);

    // Pointer to PageObject
    std::unique_ptr<PageObject> page_object = nullptr;

    switch (static_cast<PageObject::Type>(page_object_type)) {
        case PageObject::Type::Path: {
            page_object = ToNativePathObject(env, java_page_object, converter);
            break;
        }
        case PageObject::Type::Image: {
            page_object = ToNativeImageObject(env, java_page_object);
            break;
        }
        default:
            break;
    }

    if (!page_object) {
        return nullptr;
    }

    // Get Matrix from Java PageObject.
    static jmethodID get_matrix = env->GetMethodID(page_object_class, "getMatrix", "()[F");
    jfloatArray java_matrix_array =
                             (jfloatArray)env->CallObjectMethod(java_page_object, get_matrix);

    // Copy Java Array to Native Array
    float transform[9];
    env->GetFloatArrayRegion(java_matrix_array, 0, 9, transform);

    // Set PageObject Data Matrix.
    page_object->device_matrix_ = {transform[0 /*kMScaleX*/], transform[3 /*kMSkewY*/],
                                   transform[1 /*kMSkewX*/],  transform[4 /*kMScaleY*/],
                                   transform[2 /*kMTransX*/], transform[5 /*kMTransY*/]};

    return page_object;
}

jobject ToJavaPageAnnotations(JNIEnv* env, const vector<Annotation*>& annotations,
                              ICoordinateConverter* converter) {
    return ToJavaList(env, annotations, converter, &ToJavaPageAnnotation);
}

jobject ToJavaStampAnnotation(JNIEnv* env, const Annotation* annotation,
                              ICoordinateConverter* converter) {
    // Cast to StampAnnotation
    const StampAnnotation* stamp_annotation = static_cast<const StampAnnotation*>(annotation);
    jobject java_bounds = ToJavaRectF(env, stamp_annotation->GetBounds(), converter);

    // Find Java StampAnnotation Class.
    static jclass stamp_annotation_class = GetPermClassRef(env, kStampAnnotation);
    // Get Constructor Id.
    static jmethodID init =
            env->GetMethodID(stamp_annotation_class, "<init>", funcsig("V", kRectF).c_str());

    // Create Java StampAnnotation Instance.
    jobject java_annotation = env->NewObject(stamp_annotation_class, init, java_bounds);

    // Add page objects to stamp annotation

    // Get methodId for addObject
    static jmethodID add_object = env->GetMethodID(stamp_annotation_class, "addObject",
                                                   funcsig("V", kPageObject).c_str());

    std::vector<PageObject*> page_objects = stamp_annotation->GetObjects();

    for (const auto& page_object : page_objects) {
        jobject java_page_object = ToJavaPdfPageObject(env, page_object, converter);
        env->CallVoidMethod(java_annotation, add_object, java_page_object);
    }
    return java_annotation;
}

jobject ToJavaHighlightAnnotation(JNIEnv* env, const Annotation* annotation,
                                  ICoordinateConverter* converter) {
    // Cast to HighlightAnnotation
    const HighlightAnnotation* highlight_annotation =
            static_cast<const HighlightAnnotation*>(annotation);
    jobject java_bounds =
            ToJavaList(env, highlight_annotation->GetBounds(), converter, &ToJavaRectF);

    // Find Java HighlightAnnotation Class.
    static jclass highlight_annotation_class = GetPermClassRef(env, kHighlightAnnotation);
    // Get Constructor Id.
    static jmethodID init =
            env->GetMethodID(highlight_annotation_class, "<init>", funcsig("V", kList).c_str());

    // Create Java HighlightAnnotation Instance.
    jobject java_annotation = env->NewObject(highlight_annotation_class, init, java_bounds);

    // Get and set highlight color
    // Get method Id for setColor.
    static jmethodID set_color =
            env->GetMethodID(highlight_annotation_class, "setColor", funcsig("V", "I").c_str());
    // call setColor
    env->CallVoidMethod(java_annotation, set_color,
                        ToJavaColorInt(highlight_annotation->GetColor()));

    return java_annotation;
}

jobject ToJavaFreeTextAnnotation(JNIEnv* env, const Annotation* annotation,
                                 ICoordinateConverter* converter) {
    // Cast to FreeText Annotation
    const FreeTextAnnotation* freetext_annotation =
            static_cast<const FreeTextAnnotation*>(annotation);

    jobject java_bounds = ToJavaRectF(env, freetext_annotation->GetBounds(), converter);
    // Find Java FreeTextAnnotation class.
    static jclass freetext_annotation_class = GetPermClassRef(env, kFreeTextAnnotation);
    // Get Constructor Id.
    static jmethodID init = env->GetMethodID(freetext_annotation_class, "<init>",
                                             funcsig("V", kRectF, kString).c_str());

    // Get Java String for text content.
    jobject java_string = ToJavaString(env, freetext_annotation->GetTextContent());
    // Create Java FreeTextAnnotation Object.
    jobject java_freetext_annotation =
            env->NewObject(freetext_annotation_class, init, java_bounds, java_string);

    // Set Text color.
    static jmethodID set_text_color =
            env->GetMethodID(freetext_annotation_class, "setTextColor", funcsig("V", "I").c_str());
    // call setTextColor
    env->CallVoidMethod(java_freetext_annotation, set_text_color,
                        ToJavaColorInt(freetext_annotation->GetTextColor()));

    // Set Background color.
    static jmethodID set_background_color = env->GetMethodID(
            freetext_annotation_class, "setBackgroundColor", funcsig("V", "I").c_str());
    // call setBackgroundColor
    env->CallVoidMethod(java_freetext_annotation, set_background_color,
                        ToJavaColorInt(freetext_annotation->GetBackgroundColor()));

    return java_freetext_annotation;
}

jobject ToJavaPageAnnotation(JNIEnv* env, const Annotation* annotation,
                             ICoordinateConverter* converter) {
    if (!annotation) {
        return NULL;
    }

    jobject java_annotation = nullptr;

    switch (annotation->GetType()) {
        case Annotation::Type::Stamp: {
            java_annotation = ToJavaStampAnnotation(env, annotation, converter);
            break;
        }
        case Annotation::Type::Highlight: {
            java_annotation = ToJavaHighlightAnnotation(env, annotation, converter);
            break;
        }
        case Annotation::Type::FreeText: {
            java_annotation = ToJavaFreeTextAnnotation(env, annotation, converter);
            break;
        }
        default:
            break;
    }

    return java_annotation;
}

std::unique_ptr<Annotation> ToNativeStampAnnotation(JNIEnv* env, jobject java_annotation,
                                                    ICoordinateConverter* converter) {
    // Get Ref to Java StampAnnotation Class.
    static jclass stamp_annotation_class = GetPermClassRef(env, kStampAnnotation);

    jmethodID get_bounds =
            env->GetMethodID(stamp_annotation_class, "getBounds", funcsig(kRectF).c_str());
    jobject java_bounds = env->CallObjectMethod(java_annotation, get_bounds);
    Rectangle_f native_bounds = ToNativeRectF(env, java_bounds, converter);

    // Create StampAnnotation Instance.
    auto stamp_annotation = std::make_unique<StampAnnotation>(native_bounds);

    // Get PdfPageObjects from stamp annotation
    static jmethodID get_objects =
            env->GetMethodID(stamp_annotation_class, "getObjects", funcsig(kList).c_str());
    jobject java_page_objects = env->CallObjectMethod(java_annotation, get_objects);

    jclass list_class = env->FindClass(kList);
    jmethodID size_method = env->GetMethodID(list_class, "size", funcsig("I").c_str());
    jmethodID get_method = env->GetMethodID(list_class, "get", funcsig(kObject, "I").c_str());

    jint listSize = env->CallIntMethod(java_page_objects, size_method);
    for (int i = 0; i < listSize; i++) {
        jobject java_page_object = env->CallObjectMethod(java_page_objects, get_method, i);
        std::unique_ptr<PageObject> native_page_object =
                ToNativePageObject(env, java_page_object, converter);
        stamp_annotation->AddObject(std::move(native_page_object));
    }
    return stamp_annotation;
}

std::unique_ptr<Annotation> ToNativeHighlightAnnotation(JNIEnv* env, jobject java_annotation,
                                                        ICoordinateConverter* converter) {
    // Get Ref to Java HighlightAnnotation Class.
    static jclass highlight_annotation_class = GetPermClassRef(env, kHighlightAnnotation);

    jmethodID get_bounds =
            env->GetMethodID(highlight_annotation_class, "getBounds", funcsig(kList).c_str());
    jobject java_bounds = env->CallObjectMethod(java_annotation, get_bounds);

    vector<Rectangle_f> native_bounds;

    jclass list_class = env->FindClass(kList);
    jmethodID size_method = env->GetMethodID(list_class, "size", funcsig("I").c_str());
    jmethodID get_method = env->GetMethodID(list_class, "get", funcsig(kObject, "I").c_str());

    jint listSize = env->CallIntMethod(java_bounds, size_method);
    for (int i = 0; i < listSize; i++) {
        jobject java_bound = env->CallObjectMethod(java_bounds, get_method, i);
        Rectangle_f native_bound = ToNativeRectF(env, java_bound, converter);
        native_bounds.push_back(native_bound);
    }

    // Create HighlightAnnotation Instance.
    auto highlight_annotation = std::make_unique<HighlightAnnotation>(native_bounds);

    // Get and set highlight color

    // Get methodId for getColor
    static jmethodID get_color =
            env->GetMethodID(highlight_annotation_class, "getColor", funcsig("I").c_str());
    jint java_color_int = env->CallIntMethod(java_annotation, get_color);

    highlight_annotation->SetColor(ToNativeColor(java_color_int));

    return highlight_annotation;
}

std::unique_ptr<Annotation> ToNativeFreeTextAnnotation(JNIEnv* env, jobject java_annotation,
                                                       ICoordinateConverter* converter) {
    // Get Ref to Java FreeTextAnnotation Class.
    static jclass freetext_annotation_class = GetPermClassRef(env, kFreeTextAnnotation);

    jmethodID get_bounds =
            env->GetMethodID(freetext_annotation_class, "getBounds", funcsig(kRectF).c_str());
    jobject java_bounds = env->CallObjectMethod(java_annotation, get_bounds);
    Rectangle_f native_bounds = ToNativeRectF(env, java_bounds, converter);

    // Create FreeTextAnnotation Instance.
    auto freetext_annotation = std::make_unique<FreeTextAnnotation>(native_bounds);

    // Get the TextContent from Java layer.
    static jmethodID get_text_content =
            env->GetMethodID(freetext_annotation_class, "getTextContent", funcsig(kString).c_str());
    auto java_text_content =
            static_cast<jstring>(env->CallObjectMethod(java_annotation, get_text_content));

    // Set the TextContent
    std::wstring native_text_content = ToNativeWideString(env, java_text_content);
    freetext_annotation->SetTextContent(native_text_content);

    // Get the text color
    static jmethodID get_text_color =
            env->GetMethodID(freetext_annotation_class, "getTextColor", funcsig("I").c_str());
    jint java_text_color_int = env->CallIntMethod(java_annotation, get_text_color);

    freetext_annotation->SetTextColor(ToNativeColor(java_text_color_int));

    // Get the background color
    static jmethodID get_background_color =
            env->GetMethodID(freetext_annotation_class, "getBackgroundColor", funcsig("I").c_str());
    jint java_background_color_int = env->CallIntMethod(java_annotation, get_background_color);

    freetext_annotation->SetBackgroundColor(ToNativeColor(java_background_color_int));

    return freetext_annotation;
}

std::unique_ptr<Annotation> ToNativePageAnnotation(JNIEnv* env, jobject java_annotation,
                                                   ICoordinateConverter* converter) {
    // Find Java PdfAnnotation class and GetType
    static jclass annotation_class = GetPermClassRef(env, kPdfAnnotation);
    static jmethodID get_type =
            env->GetMethodID(annotation_class, "getPdfAnnotationType", funcsig("I").c_str());
    jint annotation_type = env->CallIntMethod(java_annotation, get_type);

    std::unique_ptr<Annotation> annotation = nullptr;

    switch (static_cast<Annotation::Type>(annotation_type)) {
        case Annotation::Type::Stamp: {
            annotation = ToNativeStampAnnotation(env, java_annotation, converter);
            break;
        }
        case Annotation::Type::Highlight: {
            annotation = ToNativeHighlightAnnotation(env, java_annotation, converter);
            break;
        }
        case Annotation::Type::FreeText: {
            annotation = ToNativeFreeTextAnnotation(env, java_annotation, converter);
            break;
        }
        default:
            break;
    }

    return annotation;
}

}  // namespace convert