/*
 * This file is part of FastClasspathScanner.
 *
 * Author: Luke Hutchison
 *
 * Hosted at: https://github.com/lukehutch/fast-classpath-scanner
 *
 * --
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2018 Luke Hutchison
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without
 * limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO
 * EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package io.github.lukehutch.fastclasspathscanner.json;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Utils for Java serialization and deserialization. */
public class JSONUtils {
    /**
     * JSON object key name for objects that are linked to from more than one object. Key name is only used if the
     * class that a JSON object was serialized from does not have its own id field annotated with {@link Id}.
     */
    static final String ID_KEY = "__ID";

    /** JSON object reference id prefix. */
    static final String ID_PREFIX = "[#";

    /** JSON object reference id suffix. */
    static final String ID_SUFFIX = "]";

    // -------------------------------------------------------------------------------------------------------------

    // See http://www.json.org/ under "string"
    private static final String[] JSON_CHAR_REPLACEMENTS = new String[256];
    static {
        for (int c = 0; c < 256; c++) {
            if (c == 32) {
                c = 127;
            }
            final int nibble1 = c >> 4;
            final char hexDigit1 = nibble1 <= 9 ? (char) ('0' + nibble1) : (char) ('A' + nibble1 - 10);
            final int nibble0 = c & 0xf;
            final char hexDigit0 = nibble0 <= 9 ? (char) ('0' + nibble0) : (char) ('A' + nibble0 - 10);
            JSON_CHAR_REPLACEMENTS[c] = "\\u00" + Character.toString(hexDigit1) + Character.toString(hexDigit0);
        }
        JSON_CHAR_REPLACEMENTS['"'] = "\\\"";
        JSON_CHAR_REPLACEMENTS['\\'] = "\\\\";
        JSON_CHAR_REPLACEMENTS['\n'] = "\\n";
        JSON_CHAR_REPLACEMENTS['\r'] = "\\r";
        JSON_CHAR_REPLACEMENTS['\t'] = "\\t";
        JSON_CHAR_REPLACEMENTS['\b'] = "\\b";
        JSON_CHAR_REPLACEMENTS['\f'] = "\\f";
    }

    /** Escape a string to be surrounded in double quotes in JSON. */
    static void escapeJSONString(final String unsafeStr, final StringBuilder buf) {
        if (unsafeStr == null) {
            return;
        }
        // Fast path
        boolean needsEscaping = false;
        for (int i = 0, n = unsafeStr.length(); i < n; i++) {
            final char c = unsafeStr.charAt(i);
            if (c > 0xff || JSON_CHAR_REPLACEMENTS[c] != null) {
                needsEscaping = true;
                break;
            }
        }
        if (!needsEscaping) {
            buf.append(unsafeStr);
            return;
        }
        // Slow path
        for (int i = 0, n = unsafeStr.length(); i < n; i++) {
            final char c = unsafeStr.charAt(i);
            if (c > 0xff) {
                buf.append("\\u");
                final int nibble3 = ((c) & 0xf000) >> 12;
                buf.append(nibble3 <= 9 ? (char) ('0' + nibble3) : (char) ('A' + nibble3 - 10));
                final int nibble2 = ((c) & 0xf00) >> 8;
                buf.append(nibble2 <= 9 ? (char) ('0' + nibble2) : (char) ('A' + nibble2 - 10));
                final int nibble1 = ((c) & 0xf0) >> 4;
                buf.append(nibble1 <= 9 ? (char) ('0' + nibble1) : (char) ('A' + nibble1 - 10));
                final int nibble0 = ((c) & 0xf);
                buf.append(nibble0 <= 9 ? (char) ('0' + nibble0) : (char) ('A' + nibble0 - 10));
            } else {
                final String replacement = JSON_CHAR_REPLACEMENTS[c];
                if (replacement == null) {
                    buf.append(c);
                } else {
                    buf.append(replacement);
                }
            }
        }
    }

    /** Escape a string to be surrounded in double quotes in JSON. */
    public static String escapeJSONString(final String unsafeStr) {
        if (unsafeStr == null) {
            return unsafeStr;
        }
        // Fast path
        boolean needsEscaping = false;
        for (int i = 0, n = unsafeStr.length(); i < n; i++) {
            final char c = unsafeStr.charAt(i);
            if (c > 0xff || JSON_CHAR_REPLACEMENTS[c] != null) {
                needsEscaping = true;
                break;
            }
        }
        if (!needsEscaping) {
            return unsafeStr;
        }
        // Slow path
        final StringBuilder buf = new StringBuilder(unsafeStr.length() * 2);
        for (int i = 0, n = unsafeStr.length(); i < n; i++) {
            final char c = unsafeStr.charAt(i);
            if (c > 0xff) {
                buf.append("\\u");
                final int nibble3 = ((c) & 0xf000) >> 12;
                buf.append(nibble3 <= 9 ? (char) ('0' + nibble3) : (char) ('A' + nibble3 - 10));
                final int nibble2 = ((c) & 0xf00) >> 8;
                buf.append(nibble2 <= 9 ? (char) ('0' + nibble2) : (char) ('A' + nibble2 - 10));
                final int nibble1 = ((c) & 0xf0) >> 4;
                buf.append(nibble1 <= 9 ? (char) ('0' + nibble1) : (char) ('A' + nibble1 - 10));
                final int nibble0 = ((c) & 0xf);
                buf.append(nibble0 <= 9 ? (char) ('0' + nibble0) : (char) ('A' + nibble0 - 10));
            } else {
                final String replacement = JSON_CHAR_REPLACEMENTS[c];
                if (replacement == null) {
                    buf.append(c);
                } else {
                    buf.append(replacement);
                }
            }
        }
        return buf.toString();
    }

    // -------------------------------------------------------------------------------------------------------------

    /** Lookup table for fast indenting */
    private static final String[] INDENT_LEVELS = new String[17];
    static {
        final StringBuilder buf = new StringBuilder();
        for (int i = 0; i < INDENT_LEVELS.length; i++) {
            INDENT_LEVELS[i] = buf.toString();
            buf.append(' ');
        }
    }

    /** Indent (depth * indentWidth) spaces. */
    static void indent(final int depth, final int indentWidth, final StringBuilder buf) {
        final int maxIndent = INDENT_LEVELS.length - 1;
        for (int d = depth * indentWidth; d > 0;) {
            final int n = Math.min(d, maxIndent);
            buf.append(INDENT_LEVELS[n]);
            d -= n;
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /** Get a field value, appropriately handling primitive-typed fields. */
    static Object getFieldValue(final Object containingObj, final Field field)
            throws IllegalArgumentException, IllegalAccessException {
        final Class<?> fieldType = field.getType();
        if (fieldType == Integer.TYPE) {
            return Integer.valueOf(field.getInt(containingObj));
        } else if (fieldType == Long.TYPE) {
            return Long.valueOf(field.getLong(containingObj));
        } else if (fieldType == Short.TYPE) {
            return Short.valueOf(field.getShort(containingObj));
        } else if (fieldType == Double.TYPE) {
            return Double.valueOf(field.getDouble(containingObj));
        } else if (fieldType == Float.TYPE) {
            return Float.valueOf(field.getFloat(containingObj));
        } else if (fieldType == Boolean.TYPE) {
            return Boolean.valueOf(field.getBoolean(containingObj));
        } else if (fieldType == Byte.TYPE) {
            return Byte.valueOf(field.getByte(containingObj));
        } else if (fieldType == Character.TYPE) {
            return Character.valueOf(field.getChar(containingObj));
        } else {
            return field.get(containingObj);
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * Return true for classes that can be equal to a basic value type (types that can be converted directly to and
     * from string representation).
     */
    static boolean isBasicValueType(final Class<?> cls) {
        return cls == String.class //
                || cls == Integer.class || cls == Integer.TYPE //
                || cls == Long.class || cls == Long.TYPE //
                || cls == Short.class || cls == Short.TYPE //
                || cls == Float.class || cls == Float.TYPE //
                || cls == Double.class || cls == Double.TYPE //
                || cls == Byte.class || cls == Byte.TYPE //
                || cls == Character.class || cls == Character.TYPE //
                || cls == Boolean.class || cls == Boolean.TYPE //
                || cls.isEnum();
    }

    /** Return true for types that can be converted directly to and from string representation. */
    static boolean isBasicValueType(final Type type) {
        if (type instanceof Class<?>) {
            return isBasicValueType((Class<?>) type);
        } else if (type instanceof ParameterizedType) {
            return isBasicValueType(((ParameterizedType) type).getRawType());
        } else {
            return false;
        }
    }

    /** Return true for objects that can be converted directly to and from string representation. */
    static boolean isBasicValueType(final Object obj) {
        return obj == null || obj instanceof String || obj instanceof Integer || obj instanceof Boolean
                || obj instanceof Long || obj instanceof Float || obj instanceof Double || obj instanceof Short
                || obj instanceof Byte || obj instanceof Character || obj.getClass().isEnum();
    }

    /**
     * Return true for classes that are collections or arrays (i.e. objects that are convertible to a JSON array).
     */
    static boolean isCollectionOrArray(final Class<?> cls) {
        return Collection.class.isAssignableFrom(cls) || cls.isArray();
    }

    /**
     * Return true for objects that are collections or arrays (i.e. objects that are convertible to a JSON array).
     */
    static boolean isCollectionOrArray(final Object obj) {
        final Class<? extends Object> cls = obj.getClass();
        return Collection.class.isAssignableFrom(cls) || cls.isArray();
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * Get the raw type from a Type.
     * 
     * @throws IllegalArgumentException
     *             if passed a TypeVariable or anything other than a {@code Class<?>} reference or
     *             {@link ParameterizedType}.
     */
    static Class<?> getRawType(final Type type) {
        if (type instanceof Class<?>) {
            return (Class<?>) type;
        } else if (type instanceof ParameterizedType) {
            return (Class<?>) ((ParameterizedType) type).getRawType();
        } else {
            throw new IllegalArgumentException("Illegal type: " + type);
        }
    }

    /** Return true if the field is accessible, or can be made accessible (and make it accessible if so). */
    static boolean isAccessibleOrMakeAccessible(final AccessibleObject fieldOrConstructor) {
        // Make field accessible if needed
        @SuppressWarnings("deprecation")
        final AtomicBoolean isAccessible = new AtomicBoolean(fieldOrConstructor.isAccessible());
        if (!isAccessible.get()) {
            try {
                fieldOrConstructor.setAccessible(true);
                isAccessible.set(true);
            } catch (final Exception e) {
                AccessController.doPrivileged(new PrivilegedAction<Void>() {
                    @Override
                    public Void run() {
                        try {
                            fieldOrConstructor.setAccessible(true);
                            isAccessible.set(true);
                        } catch (final Exception e) {
                        }
                        return null;
                    }
                });
            }
        }
        return isAccessible.get();
    }

    /**
     * Check if a field is serializable. Don't serialize transient, final, synthetic, or inaccessible fields.
     * 
     * <p>
     * N.B. Tries to set field to accessible, which will require an "opens" declarations from modules that want to
     * allow this introspection.
     */
    static boolean fieldIsSerializable(final Field field, final boolean onlySerializePublicFields) {
        final int modifiers = field.getModifiers();
        if ((!onlySerializePublicFields || Modifier.isPublic(modifiers)) && !Modifier.isTransient(modifiers)
                && !Modifier.isFinal(modifiers) && ((modifiers & 0x1000 /* synthetic */) == 0)) {
            return JSONUtils.isAccessibleOrMakeAccessible(field);
        }
        return false;
    }

    // -------------------------------------------------------------------------------------------------------------

    /** An implementation of {@link ParameterizedType}, used to replace type variables with concrete types. */
    static class ParameterizedTypeImpl implements ParameterizedType {
        private final Type[] actualTypeArguments;
        private final Class<?> rawType;
        private final Type ownerType;

        public static final Type MAP_OF_UNKNOWN_TYPE = new ParameterizedTypeImpl(Map.class,
                new Type[] { Object.class, Object.class }, null);
        public static final Type LIST_OF_UNKNOWN_TYPE = new ParameterizedTypeImpl(List.class,
                new Type[] { Object.class }, null);

        ParameterizedTypeImpl(final Class<?> rawType, final Type[] actualTypeArguments, final Type ownerType) {
            this.actualTypeArguments = actualTypeArguments;
            this.rawType = rawType;
            this.ownerType = (ownerType != null) ? ownerType : rawType.getDeclaringClass();
            if (rawType.getTypeParameters().length != actualTypeArguments.length) {
                throw new IllegalArgumentException("Argument length mismatch");
            }
        }

        @Override
        public Type[] getActualTypeArguments() {
            return actualTypeArguments.clone();
        }

        @Override
        public Class<?> getRawType() {
            return rawType;
        }

        @Override
        public Type getOwnerType() {
            return ownerType;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ParameterizedType)) {
                return false;
            }
            final ParameterizedType other = (ParameterizedType) o;

            final Type otherOwnerType = other.getOwnerType();
            final Type otherRawType = other.getRawType();

            return Objects.equals(ownerType, otherOwnerType) && Objects.equals(rawType, otherRawType)
                    && Arrays.equals(actualTypeArguments, other.getActualTypeArguments());
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(actualTypeArguments) ^ Objects.hashCode(ownerType) ^ Objects.hashCode(rawType);
        }

        @Override
        public String toString() {
            final StringBuilder buf = new StringBuilder();
            if (ownerType == null) {
                buf.append(rawType.getName());
            } else {
                if (ownerType instanceof Class) {
                    buf.append(((Class<?>) ownerType).getName());
                } else {
                    buf.append(ownerType.toString());
                }
                buf.append("$");
                if (ownerType instanceof ParameterizedTypeImpl) {
                    final String simpleName = rawType.getName()
                            .replace(((ParameterizedTypeImpl) ownerType).rawType.getName() + "$", "");
                    buf.append(simpleName);
                } else {
                    buf.append(rawType.getSimpleName());
                }
            }
            if (actualTypeArguments != null && actualTypeArguments.length > 0) {
                buf.append("<");
                boolean first = true;
                for (final Type t : actualTypeArguments) {
                    if (first) {
                        first = false;
                    } else {
                        buf.append(", ");
                    }
                    buf.append(t.getTypeName());
                }
                buf.append(">");
            }
            return buf.toString();
        }
    }

    /** An implementation of {@link GenericArrayType}, used to replace type variables with concrete types. */
    static class GenericArrayTypeImpl implements GenericArrayType {
        private final Type genericComponentType;

        GenericArrayTypeImpl(final Type componentType) {
            genericComponentType = componentType;
        }

        @Override
        public Type getGenericComponentType() {
            return genericComponentType;
        }

        @Override
        public String toString() {
            final Type componentType = getGenericComponentType();
            final StringBuilder buf = new StringBuilder();

            if (componentType instanceof Class) {
                buf.append(((Class<?>) componentType).getName());
            } else {
                buf.append(componentType.toString());
            }
            buf.append("[]");
            return buf.toString();
        }

        @Override
        public boolean equals(final Object o) {
            if (o instanceof GenericArrayType) {
                final GenericArrayType other = (GenericArrayType) o;
                return Objects.equals(genericComponentType, other.getGenericComponentType());
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(genericComponentType);
        }
    }
}
