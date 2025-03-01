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

import static android.content.ContentResolver.QUERY_ARG_GROUP_COLUMNS;
import static android.content.ContentResolver.QUERY_ARG_LIMIT;
import static android.content.ContentResolver.QUERY_ARG_OFFSET;
import static android.content.ContentResolver.QUERY_ARG_SORT_COLLATION;
import static android.content.ContentResolver.QUERY_ARG_SORT_COLUMNS;
import static android.content.ContentResolver.QUERY_ARG_SORT_DIRECTION;
import static android.content.ContentResolver.QUERY_ARG_SORT_LOCALE;
import static android.content.ContentResolver.QUERY_ARG_SQL_GROUP_BY;
import static android.content.ContentResolver.QUERY_ARG_SQL_LIMIT;
import static android.content.ContentResolver.QUERY_ARG_SQL_SORT_ORDER;
import static android.content.ContentResolver.QUERY_SORT_DIRECTION_ASCENDING;
import static android.content.ContentResolver.QUERY_SORT_DIRECTION_DESCENDING;

import static com.android.providers.media.util.LegacyLogging.TAG;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteStatement;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

public class LegacyDatabaseUtils {
    /**
     * Bind the given selection with the given selection arguments.
     * <p>
     * Internally assumes that '?' is only ever used for arguments, and doesn't
     * appear as a literal or escaped value.
     * <p>
     * This method is typically useful for trusted code that needs to cook up a
     * fully-bound selection.
     *
     * @hide
     */
    public static @Nullable String bindSelection(@Nullable String selection,
            @Nullable Object... selectionArgs) {
        if (selection == null) return null;
        // If no arguments provided, so we can't bind anything
        if ((selectionArgs == null) || (selectionArgs.length == 0)) return selection;
        // If no bindings requested, so we can shortcut
        if (selection.indexOf('?') == -1) return selection;

        // Track the chars immediately before and after each bind request, to
        // decide if it needs additional whitespace added
        char before = ' ';
        char after = ' ';

        int argIndex = 0;
        final int len = selection.length();
        final StringBuilder res = new StringBuilder(len);
        for (int i = 0; i < len; ) {
            char c = selection.charAt(i++);
            if (c == '?') {
                // Assume this bind request is guarded until we find a specific
                // trailing character below
                after = ' ';

                // Sniff forward to see if the selection is requesting a
                // specific argument index
                int start = i;
                for (; i < len; i++) {
                    c = selection.charAt(i);
                    if (c < '0' || c > '9') {
                        after = c;
                        break;
                    }
                }
                if (start != i) {
                    argIndex = Integer.parseInt(selection.substring(start, i)) - 1;
                }

                // Manually bind the argument into the selection, adding
                // whitespace when needed for clarity
                final Object arg = selectionArgs[argIndex++];
                if (before != ' ' && before != '=') res.append(' ');
                switch (LegacyDatabaseUtils.getTypeOfObject(arg)) {
                    case Cursor.FIELD_TYPE_NULL:
                        res.append("NULL");
                        break;
                    case Cursor.FIELD_TYPE_INTEGER:
                        res.append(((Number) arg).longValue());
                        break;
                    case Cursor.FIELD_TYPE_FLOAT:
                        res.append(((Number) arg).doubleValue());
                        break;
                    case Cursor.FIELD_TYPE_BLOB:
                        throw new IllegalArgumentException("Blobs not supported");
                    case Cursor.FIELD_TYPE_STRING:
                    default:
                        if (arg instanceof Boolean) {
                            // Provide compatibility with legacy applications which may pass
                            // Boolean values in bind args.
                            res.append(((Boolean) arg).booleanValue() ? 1 : 0);
                        } else {
                            res.append('\'');
                            // Escape single quote character while appending the string and reject
                            // invalid unicode.
                            res.append(escapeSingleQuoteAndRejectInvalidUnicode(arg.toString()));
                            res.append('\'');
                        }
                        break;
                }
                if (after != ' ') res.append(' ');
            } else {
                res.append(c);
                before = c;
            }
        }
        return res.toString();
    }

    private static String escapeSingleQuoteAndRejectInvalidUnicode(@NonNull String target) {
        final int len = target.length();
        final StringBuilder res = new StringBuilder(len);
        boolean lastHigh = false;

        for (int i = 0; i < len; ) {
            final char c = target.charAt(i++);

            if (lastHigh != Character.isLowSurrogate(c)) {
                Log.e(TAG, "Invalid surrogate in string " + target);
                throw new IllegalArgumentException("Invalid surrogate in string " + target);
            }

            lastHigh = Character.isHighSurrogate(c);

            // Escape the single quotes by duplicating them
            if (c == '\'') {
                res.append(c);
            }

            res.append(c);
        }

        if (lastHigh) {
            Log.e(TAG, "Invalid surrogate in string " + target);
            throw new IllegalArgumentException("Invalid surrogate in string " + target);
        }

        return res.toString();
    }

    /**
     * Returns data type of the given object's value.
     *<p>
     * Returned values are
     * <ul>
     *   <li>{@link Cursor#FIELD_TYPE_NULL}</li>
     *   <li>{@link Cursor#FIELD_TYPE_INTEGER}</li>
     *   <li>{@link Cursor#FIELD_TYPE_FLOAT}</li>
     *   <li>{@link Cursor#FIELD_TYPE_STRING}</li>
     *   <li>{@link Cursor#FIELD_TYPE_BLOB}</li>
     *</ul>
     *</p>
     *
     * @param obj the object whose value type is to be returned
     * @return object value type
     * @hide
     */
    public static int getTypeOfObject(Object obj) {
        if (obj == null) {
            return Cursor.FIELD_TYPE_NULL;
        } else if (obj instanceof byte[]) {
            return Cursor.FIELD_TYPE_BLOB;
        } else if (obj instanceof Float || obj instanceof Double) {
            return Cursor.FIELD_TYPE_FLOAT;
        } else if (obj instanceof Long || obj instanceof Integer
                || obj instanceof Short || obj instanceof Byte) {
            return Cursor.FIELD_TYPE_INTEGER;
        } else {
            return Cursor.FIELD_TYPE_STRING;
        }
    }

    /**
     * Simple attempt to balance the given SQL expression by adding parenthesis
     * when needed.
     * <p>
     * Since this is only used for recovering from abusive apps, we're not
     * interested in trying to build a fully valid SQL parser up in Java. It'll
     * give up when it encounters complex SQL, such as string literals.
     */
    public static @Nullable String maybeBalance(@Nullable String sql) {
        if (sql == null) return null;

        int count = 0;
        char literal = '\0';
        for (int i = 0; i < sql.length(); i++) {
            final char c = sql.charAt(i);

            if (c == '\'' || c == '"') {
                if (literal == '\0') {
                    // Start literal
                    literal = c;
                } else if (literal == c) {
                    // End literal
                    literal = '\0';
                }
            }

            if (literal == '\0') {
                if (c == '(') {
                    count++;
                } else if (c == ')') {
                    count--;
                }
            }
        }
        while (count > 0) {
            sql = sql + ")";
            count--;
        }
        while (count < 0) {
            sql = "(" + sql;
            count++;
        }
        return sql;
    }

    private static void resolveGroupBy(@NonNull Bundle queryArgs,
            @NonNull Consumer<String> honored) {
        final String[] columns = queryArgs.getStringArray(QUERY_ARG_GROUP_COLUMNS);
        if (columns != null && columns.length != 0) {
            String groupBy = TextUtils.join(", ", columns);
            honored.accept(QUERY_ARG_GROUP_COLUMNS);

            queryArgs.putString(QUERY_ARG_SQL_GROUP_BY, groupBy);
        } else {
            honored.accept(QUERY_ARG_SQL_GROUP_BY);
        }
    }

    private static void resolveSortOrder(@NonNull Bundle queryArgs,
            @NonNull Consumer<String> honored,
            @NonNull Function<String, String> collatorFactory) {
        final String[] columns = queryArgs.getStringArray(QUERY_ARG_SORT_COLUMNS);
        if (columns != null && columns.length != 0) {
            String sortOrder = TextUtils.join(", ", columns);
            honored.accept(QUERY_ARG_SORT_COLUMNS);

            if (queryArgs.containsKey(QUERY_ARG_SORT_LOCALE)) {
                final String collatorName = collatorFactory.apply(
                        queryArgs.getString(QUERY_ARG_SORT_LOCALE));
                sortOrder += " COLLATE " + collatorName;
                honored.accept(QUERY_ARG_SORT_LOCALE);
            } else {
                // Interpret PRIMARY and SECONDARY collation strength as no-case collation based
                // on their javadoc descriptions.
                final int collation = queryArgs.getInt(
                        QUERY_ARG_SORT_COLLATION, java.text.Collator.IDENTICAL);
                switch (collation) {
                    case java.text.Collator.IDENTICAL:
                        honored.accept(QUERY_ARG_SORT_COLLATION);
                        break;
                    case java.text.Collator.PRIMARY:
                    case java.text.Collator.SECONDARY:
                        sortOrder += " COLLATE NOCASE";
                        honored.accept(QUERY_ARG_SORT_COLLATION);
                        break;
                }
            }

            final int sortDir = queryArgs.getInt(QUERY_ARG_SORT_DIRECTION, Integer.MIN_VALUE);
            switch (sortDir) {
                case QUERY_SORT_DIRECTION_ASCENDING:
                    sortOrder += " ASC";
                    honored.accept(QUERY_ARG_SORT_DIRECTION);
                    break;
                case QUERY_SORT_DIRECTION_DESCENDING:
                    sortOrder += " DESC";
                    honored.accept(QUERY_ARG_SORT_DIRECTION);
                    break;
            }

            queryArgs.putString(QUERY_ARG_SQL_SORT_ORDER, sortOrder);
        } else {
            honored.accept(QUERY_ARG_SQL_SORT_ORDER);
        }
    }

    private static void resolveLimit(@NonNull Bundle queryArgs,
            @NonNull Consumer<String> honored) {
        final int limit = queryArgs.getInt(QUERY_ARG_LIMIT, Integer.MIN_VALUE);
        if (limit != Integer.MIN_VALUE) {
            String limitString = Integer.toString(limit);
            honored.accept(QUERY_ARG_LIMIT);

            final int offset = queryArgs.getInt(QUERY_ARG_OFFSET, Integer.MIN_VALUE);
            if (offset != Integer.MIN_VALUE) {
                limitString += " OFFSET " + offset;
                honored.accept(QUERY_ARG_OFFSET);
            }

            queryArgs.putString(QUERY_ARG_SQL_LIMIT, limitString);
        } else {
            honored.accept(QUERY_ARG_SQL_LIMIT);
        }
    }

    private static void bindArgs(@NonNull SQLiteStatement st, @Nullable Object[] bindArgs) {
        if (bindArgs == null) return;

        for (int i = 0; i < bindArgs.length; i++) {
            final Object bindArg = bindArgs[i];
            switch (getTypeOfObject(bindArg)) {
                case Cursor.FIELD_TYPE_NULL:
                    st.bindNull(i + 1);
                    break;
                case Cursor.FIELD_TYPE_INTEGER:
                    st.bindLong(i + 1, ((Number) bindArg).longValue());
                    break;
                case Cursor.FIELD_TYPE_FLOAT:
                    st.bindDouble(i + 1, ((Number) bindArg).doubleValue());
                    break;
                case Cursor.FIELD_TYPE_BLOB:
                    st.bindBlob(i + 1, (byte[]) bindArg);
                    break;
                case Cursor.FIELD_TYPE_STRING:
                default:
                    if (bindArg instanceof Boolean) {
                        // Provide compatibility with legacy
                        // applications which may pass Boolean values in
                        // bind args.
                        st.bindLong(i + 1, ((Boolean) bindArg).booleanValue() ? 1 : 0);
                    } else {
                        st.bindString(i + 1, bindArg.toString());
                    }
                    break;
            }
        }
    }

    public static boolean parseBoolean(@Nullable Object value, boolean def) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        } else if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        } else if (value instanceof String) {
            final String stringValue = ((String) value).toLowerCase(Locale.ROOT);
            return (!"false".equals(stringValue) && !"0".equals(stringValue));
        } else {
            return def;
        }
    }

    public static boolean getAsBoolean(@NonNull Bundle extras,
            @NonNull String key, boolean def) {
        return parseBoolean(extras.get(key), def);
    }

    public static boolean getAsBoolean(@NonNull ContentValues values,
            @NonNull String key, boolean def) {
        return parseBoolean(values.get(key), def);
    }

    public static long getAsLong(@NonNull ContentValues values,
            @NonNull String key, long def) {
        final Long value = values.getAsLong(key);
        return (value != null) ? value : def;
    }
}
