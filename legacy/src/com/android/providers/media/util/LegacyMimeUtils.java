/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.providers.media.util;

import androidx.annotation.Nullable;

import java.util.Locale;

public class LegacyMimeUtils {

    /**
     * Returns true if MIME type represents a subtitle
     *
     * @param mimeType MIME type string
     * @return true if mimeType matches a subtitle type
     */
    public static boolean isSubtitleMimeType(@Nullable String mimeType) {
        if (mimeType == null) return false;
        switch (mimeType.toLowerCase(Locale.ROOT)) {
            case "application/lrc":
            case "application/smil+xml":
            case "application/ttml+xml":
            case "application/x-extension-cap":
            case "application/x-extension-srt":
            case "application/x-extension-sub":
            case "application/x-extension-vtt":
            case "application/x-subrip":
            case "text/vtt":
                return true;
            default:
                return false;
        }
    }

    /**
     * Returns true if MIME type represents a document
     *
     * @param mimeType MIME type string
     * @return true if mimeType is text or a known document format
     */
    public static boolean isDocumentMimeType(@Nullable String mimeType) {
        if (mimeType == null) return false;

        if (LegacyStringUtils.startsWithIgnoreCase(mimeType, "text/")) return true;

        switch (mimeType.toLowerCase(Locale.ROOT)) {
            case "application/epub+zip":
            case "application/msword":
            case "application/pdf":
            case "application/rtf":
            case "application/vnd.ms-excel":
            case "application/vnd.ms-excel.addin.macroenabled.12":
            case "application/vnd.ms-excel.sheet.binary.macroenabled.12":
            case "application/vnd.ms-excel.sheet.macroenabled.12":
            case "application/vnd.ms-excel.template.macroenabled.12":
            case "application/vnd.ms-powerpoint":
            case "application/vnd.ms-powerpoint.addin.macroenabled.12":
            case "application/vnd.ms-powerpoint.presentation.macroenabled.12":
            case "application/vnd.ms-powerpoint.slideshow.macroenabled.12":
            case "application/vnd.ms-powerpoint.template.macroenabled.12":
            case "application/vnd.ms-word.document.macroenabled.12":
            case "application/vnd.ms-word.template.macroenabled.12":
            case "application/vnd.oasis.opendocument.chart":
            case "application/vnd.oasis.opendocument.database":
            case "application/vnd.oasis.opendocument.formula":
            case "application/vnd.oasis.opendocument.graphics":
            case "application/vnd.oasis.opendocument.graphics-template":
            case "application/vnd.oasis.opendocument.presentation":
            case "application/vnd.oasis.opendocument.presentation-template":
            case "application/vnd.oasis.opendocument.spreadsheet":
            case "application/vnd.oasis.opendocument.spreadsheet-template":
            case "application/vnd.oasis.opendocument.text":
            case "application/vnd.oasis.opendocument.text-master":
            case "application/vnd.oasis.opendocument.text-template":
            case "application/vnd.oasis.opendocument.text-web":
            case "application/vnd.openxmlformats-officedocument.presentationml.presentation":
            case "application/vnd.openxmlformats-officedocument.presentationml.slideshow":
            case "application/vnd.openxmlformats-officedocument.presentationml.template":
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet":
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.template":
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document":
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.template":
            case "application/vnd.stardivision.calc":
            case "application/vnd.stardivision.chart":
            case "application/vnd.stardivision.draw":
            case "application/vnd.stardivision.impress":
            case "application/vnd.stardivision.impress-packed":
            case "application/vnd.stardivision.mail":
            case "application/vnd.stardivision.math":
            case "application/vnd.stardivision.writer":
            case "application/vnd.stardivision.writer-global":
            case "application/vnd.sun.xml.calc":
            case "application/vnd.sun.xml.calc.template":
            case "application/vnd.sun.xml.draw":
            case "application/vnd.sun.xml.draw.template":
            case "application/vnd.sun.xml.impress":
            case "application/vnd.sun.xml.impress.template":
            case "application/vnd.sun.xml.math":
            case "application/vnd.sun.xml.writer":
            case "application/vnd.sun.xml.writer.global":
            case "application/vnd.sun.xml.writer.template":
            case "application/x-mspublisher":
                return true;
            default:
                return false;
        }
    }

}
