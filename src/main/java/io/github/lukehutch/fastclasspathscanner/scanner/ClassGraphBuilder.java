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
 * Copyright (c) 2016 Luke Hutchison
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
package io.github.lukehutch.fastclasspathscanner.scanner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import io.github.lukehutch.fastclasspathscanner.scanner.ClassInfo.ClassType;
import io.github.lukehutch.fastclasspathscanner.utils.GraphvizUtils;

/** Builds the class graph, and provides methods for querying it. */
class ClassGraphBuilder {
    Map<String, ClassInfo> classNameToClassInfo;
    private final ScanSpec scanSpec;
    private final Map<String, ClassLoader[]> classNameToClassLoaders = new HashMap<>();

    /** Called after deserialization. */
    void setFields(final ScanSpec scanSpec, final ScanResult scanResult) {
        for (final ClassInfo classInfo : classNameToClassInfo.values()) {
            classInfo.setFields(scanSpec);
            classInfo.setScanResult(scanResult);
        }
    }

    private static final int PARAM_WRAP_WIDTH = 40;

    /** Builds the class graph, and provides methods for querying it. */
    ClassGraphBuilder(final ScanSpec scanSpec, final Map<String, ClassInfo> classNameToClassInfo) {
        this.scanSpec = scanSpec;
        this.classNameToClassInfo = classNameToClassInfo;
        for (final ClassInfo classInfo : classNameToClassInfo.values()) {
            final ClassLoader[] classLoaders = classInfo.getClassLoaders();
            if (classLoaders != null) {
                classNameToClassLoaders.put(classInfo.getClassName(), classLoaders);
            }
        }
    }

    /** Get a map from class name to ClassInfo for the class. */
    Map<String, ClassInfo> getClassNameToClassInfo() {
        if (scanSpec.enableExternalClasses) {
            return classNameToClassInfo;
        } else {
            // In the case of a strict whitelist, need to remove external classes from the map.
            final Map<String, ClassInfo> classNameToClassInfoFiltered = new HashMap<>();
            for (final Entry<String, ClassInfo> e : classNameToClassInfo.entrySet()) {
                final String className = e.getKey();
                final ClassInfo classInfo = e.getValue();
                if (!classInfo.isExternalClass) {
                    classNameToClassInfoFiltered.put(className, classInfo);
                }
            }
            return classNameToClassInfoFiltered;
        }
    }

    /**
     * Get a map from class name to ClassLoader(s) for the class.
     * 
     * @return The map.
     */
    public Map<String, ClassLoader[]> getClassNameToClassLoaders() {
        return classNameToClassLoaders;
    }

    // -------------------------------------------------------------------------------------------------------------
    // Classes

    private Set<ClassInfo> allClassInfo() {
        return new HashSet<>(classNameToClassInfo.values());
    }

    /**
     * Get the sorted unique names of all classes, interfaces and annotations found during the scan.
     */
    List<String> getNamesOfAllClasses() {
        return ClassInfo.getNamesOfAllClasses(scanSpec, allClassInfo());
    }

    /**
     * Get the sorted unique names of all standard (non-interface/annotation) classes found during the scan.
     */
    List<String> getNamesOfAllStandardClasses() {
        return ClassInfo.getNamesOfAllStandardClasses(scanSpec, allClassInfo());
    }

    /** Return the sorted list of names of all subclasses of the named class. */
    List<String> getNamesOfSubclassesOf(final String className) {
        final ClassInfo classInfo = classNameToClassInfo.get(className);
        if (classInfo == null) {
            return Collections.emptyList();
        } else {
            return classInfo.getNamesOfSubclasses();
        }
    }

    /** Return the sorted list of names of all superclasses of the named class. */
    List<String> getNamesOfSuperclassesOf(final String className) {
        final ClassInfo classInfo = classNameToClassInfo.get(className);
        if (classInfo == null) {
            return Collections.emptyList();
        } else {
            return classInfo.getNamesOfSuperclasses();
        }
    }

    /** Return a sorted list of classes that have a method with the named annotation. */
    List<String> getNamesOfClassesWithMethodAnnotation(final String annotationName) {
        return ClassInfo.getNamesOfClassesWithMethodAnnotation(annotationName, allClassInfo());
    }

    /** Return a sorted list of classes that have a field with the named annotation. */
    List<String> getNamesOfClassesWithFieldAnnotation(final String annotationName) {
        return ClassInfo.getNamesOfClassesWithFieldAnnotation(annotationName, allClassInfo());
    }

    // -------------------------------------------------------------------------------------------------------------
    // Interfaces

    /** Return the sorted unique names of all interface classes found during the scan. */
    List<String> getNamesOfAllInterfaceClasses() {
        return ClassInfo.getNamesOfAllInterfaceClasses(scanSpec, allClassInfo());
    }

    /** Return the sorted list of names of all subinterfaces of the named interface. */
    List<String> getNamesOfSubinterfacesOf(final String interfaceName) {
        final ClassInfo classInfo = classNameToClassInfo.get(interfaceName);
        if (classInfo == null) {
            return Collections.emptyList();
        } else {
            return classInfo.getNamesOfSubinterfaces();
        }
    }

    /** Return the names of all superinterfaces of the named interface. */
    List<String> getNamesOfSuperinterfacesOf(final String interfaceName) {
        final ClassInfo classInfo = classNameToClassInfo.get(interfaceName);
        if (classInfo == null) {
            return Collections.emptyList();
        } else {
            return classInfo.getNamesOfSuperinterfaces();
        }
    }

    /**
     * Return the sorted list of names of all classes implementing the named interface, and their subclasses.
     */
    List<String> getNamesOfClassesImplementing(final String interfaceName) {
        final ClassInfo classInfo = classNameToClassInfo.get(interfaceName);
        if (classInfo == null) {
            return Collections.emptyList();
        } else {
            return classInfo.getNamesOfClassesImplementing();
        }
    }

    // -------------------------------------------------------------------------------------------------------------
    // Annotations

    /** Return the sorted unique names of all annotation classes found during the scan. */
    List<String> getNamesOfAllAnnotationClasses() {
        return ClassInfo.getNamesOfAllAnnotationClasses(scanSpec, allClassInfo());
    }

    /**
     * Return the sorted list of names of all standard classes or non-annotation interfaces with the named class
     * annotation or meta-annotation.
     */
    List<String> getNamesOfClassesWithAnnotation(final String annotationName) {
        final ClassInfo classInfo = classNameToClassInfo.get(annotationName);
        if (classInfo == null) {
            return Collections.emptyList();
        } else {
            return classInfo.getNamesOfClassesWithAnnotation();
        }
    }

    /** Return the sorted list of names of all annotations and meta-annotations on the named class. */
    List<String> getNamesOfAnnotationsOnClass(final String classOrInterfaceOrAnnotationName) {
        final ClassInfo classInfo = classNameToClassInfo.get(classOrInterfaceOrAnnotationName);
        if (classInfo == null) {
            return Collections.emptyList();
        } else {
            return classInfo.getNamesOfAnnotations();
        }
    }

    /**
     * Return the sorted list of names of all annotations and meta-annotations on the named annotation.
     */
    List<String> getNamesOfMetaAnnotationsOnAnnotation(final String annotationName) {
        final ClassInfo classInfo = classNameToClassInfo.get(annotationName);
        if (classInfo == null) {
            return Collections.emptyList();
        } else {
            return classInfo.getNamesOfMetaAnnotations();
        }
    }

    /** Return the names of all annotations that have the named meta-annotation. */
    List<String> getNamesOfAnnotationsWithMetaAnnotation(final String metaAnnotationName) {
        final ClassInfo classInfo = classNameToClassInfo.get(metaAnnotationName);
        if (classInfo == null) {
            return Collections.emptyList();
        } else {
            return classInfo.getNamesOfAnnotationsWithMetaAnnotation();
        }
    }

    // -------------------------------------------------------------------------------------------------------------
    // Class graph visualization

    private void labelClassNodeHTML(final ClassInfo ci, final String shape, final String boxBgColor,
            final boolean showFields, final boolean showMethods, final StringBuilder buf) {
        buf.append("[shape=" + shape + ",style=filled,fillcolor=\"#" + boxBgColor + "\",label=");
        buf.append("<");
        buf.append("<table border='0' cellborder='0' cellspacing='1'>");

        // Class modifiers
        buf.append("<tr><td>" + ci.getModifiersStr() + " "
                + (ci.isEnum() ? "enum"
                        : ci.isAnnotation() ? "@interface" : ci.isInterface() ? "interface" : "class")
                + "</td></tr>");

        // Package name
        final String className = ci.getClassName();
        final int dotIdx = className.lastIndexOf('.');
        if (dotIdx > 0) {
            buf.append("<tr><td><b>");
            GraphvizUtils.htmlEncode(className.substring(0, dotIdx + 1), buf);
            buf.append("</b></td></tr>");
        }

        // Class name
        buf.append("<tr><td><font point-size='24'><b>");
        GraphvizUtils.htmlEncode(className.substring(dotIdx + 1), buf);
        buf.append("</b></font></td></tr>");

        // Create a color that matches the box background color, but is darker
        final float darkness = 0.8f;
        final int r = (int) (Integer.parseInt(boxBgColor.substring(0, 2), 16) * darkness);
        final int g = (int) (Integer.parseInt(boxBgColor.substring(2, 4), 16) * darkness);
        final int b = (int) (Integer.parseInt(boxBgColor.substring(4, 6), 16) * darkness);
        final String darkerColor = String.format("#%s%s%s%s%s%s", Integer.toString(r >> 4, 16),
                Integer.toString(r & 0xf, 16), Integer.toString(g >> 4, 16), Integer.toString(g & 0xf, 16),
                Integer.toString(b >> 4, 16), Integer.toString(b & 0xf, 16));

        // Class annotations
        if (ci.annotationInfo != null && ci.annotationInfo.size() > 0) {
            buf.append("<tr><td colspan='3' bgcolor='" + darkerColor
                    + "'><font point-size='12'><b>ANNOTATIONS</b></font></td></tr>");
            final List<AnnotationInfo> annotationInfoSorted = new ArrayList<>(ci.annotationInfo);
            Collections.sort(annotationInfoSorted, new Comparator<AnnotationInfo>() {
                @Override
                public int compare(final AnnotationInfo a1, final AnnotationInfo a2) {
                    return a1.getAnnotationName().compareTo(a2.getAnnotationName());
                }
            });
            for (final AnnotationInfo ai : annotationInfoSorted) {
                buf.append("<tr>");
                buf.append("<td align='center' valign='top'>");
                GraphvizUtils.htmlEncode(ai.toString(), buf);
                buf.append("</td></tr>");
            }
        }

        // Fields
        if (showFields && ci.fieldInfo != null && ci.fieldInfo.size() > 0) {
            buf.append("<tr><td colspan='3' bgcolor='" + darkerColor + "'><font point-size='12'><b>"
                    + (scanSpec.ignoreFieldVisibility ? "" : "PUBLIC ") + "FIELDS</b></font></td></tr>");
            buf.append("<tr><td cellpadding='0'>");
            buf.append("<table border='0' cellborder='0'>");
            final List<FieldInfo> fieldInfoSorted = new ArrayList<>(ci.fieldInfo);
            Collections.sort(fieldInfoSorted, new Comparator<FieldInfo>() {
                @Override
                public int compare(final FieldInfo f1, final FieldInfo f2) {
                    return f1.getFieldName().compareTo(f2.getFieldName());
                }
            });
            for (final FieldInfo fi : fieldInfoSorted) {
                buf.append("<tr>");
                buf.append("<td align='right' valign='top'>");

                // Field Annotations
                for (final AnnotationInfo ai : fi.getAnnotationInfo()) {
                    if (buf.charAt(buf.length() - 1) != ' ') {
                        buf.append(' ');
                    }
                    GraphvizUtils.htmlEncode(ai.toString(), buf);
                }

                // Field modifiers
                if (scanSpec.ignoreFieldVisibility) {
                    if (buf.charAt(buf.length() - 1) != ' ') {
                        buf.append(' ');
                    }
                    buf.append(fi.getModifierStr());
                }

                // Field type
                if (buf.charAt(buf.length() - 1) != ' ') {
                    buf.append(' ');
                }
                GraphvizUtils.htmlEncode(fi.getTypeSignatureOrTypeDescriptor().toString(), buf);
                buf.append("</td>");

                // Field name
                buf.append("<td align='left' valign='top'><b>");
                GraphvizUtils.htmlEncode(fi.getFieldName(), buf);
                buf.append("</b></td></tr>");
            }
            buf.append("</table>");
            buf.append("</td></tr>");
        }

        // Methods
        if (showMethods && ci.methodInfo != null && ci.methodInfo.size() > 0) {
            buf.append("<tr><td cellpadding='0'>");
            buf.append("<table border='0' cellborder='0'>");
            buf.append("<tr><td colspan='3' bgcolor='" + darkerColor + "'><font point-size='12'><b>"
                    + (scanSpec.ignoreMethodVisibility ? "" : "PUBLIC ") + "METHODS</b></font></td></tr>");
            final List<MethodInfo> methodInfoSorted = new ArrayList<>(ci.methodInfo);
            Collections.sort(methodInfoSorted, new Comparator<MethodInfo>() {
                @Override
                public int compare(final MethodInfo f1, final MethodInfo f2) {
                    return f1.getMethodName().compareTo(f2.getMethodName());
                }
            });
            for (final MethodInfo mi : methodInfoSorted) {
                // Don't list static initializer blocks
                if (!mi.getMethodName().equals("<clinit>")) {
                    buf.append("<tr>");

                    // Method annotations
                    // TODO: wrap this cell if the contents get too long
                    buf.append("<td align='right' valign='top'>");
                    for (final AnnotationInfo ai : mi.getAnnotationInfo()) {
                        if (buf.charAt(buf.length() - 1) != ' ') {
                            buf.append(' ');
                        }
                        GraphvizUtils.htmlEncode(ai.toString(), buf);
                    }

                    // Method modifiers
                    if (scanSpec.ignoreMethodVisibility) {
                        if (buf.charAt(buf.length() - 1) != ' ') {
                            buf.append(' ');
                        }
                        buf.append(mi.getModifiersStr());
                    }

                    // Method return type
                    if (buf.charAt(buf.length() - 1) != ' ') {
                        buf.append(' ');
                    }
                    if (!mi.getMethodName().equals("<init>")) {
                        // Don't list return type for constructors
                        GraphvizUtils.htmlEncode(mi.getResultType().toString(), buf);
                    } else {
                        buf.append("<b>&lt;constructor&gt;</b>");
                    }
                    buf.append("</td>");

                    // Method name
                    buf.append("<td align='left' valign='top'>");
                    buf.append("<b>");
                    if (mi.getMethodName().equals("<init>")) {
                        // Show class name for constructors
                        GraphvizUtils.htmlEncode(
                                mi.getClassName().substring(mi.getClassName().lastIndexOf('.') + 1), buf);
                    } else {
                        GraphvizUtils.htmlEncode(mi.getMethodName(), buf);
                    }
                    buf.append("</b>&nbsp;");
                    buf.append("</td>");

                    // Method parameters
                    buf.append("<td align='left' valign='top'>");
                    buf.append('(');
                    if (mi.getNumParameters() != 0) {
                        final MethodParameterInfo[] paramInfo = mi.getParameterInfo();
                        for (int i = 0, wrapPos = 0; i < paramInfo.length; i++) {
                            if (i > 0) {
                                buf.append(", ");
                                wrapPos += 2;
                            }
                            if (wrapPos > PARAM_WRAP_WIDTH) {
                                buf.append("</td></tr><tr><td></td><td></td><td align='left' valign='top'>");
                                wrapPos = 0;
                            }

                            // Param annotation
                            final AnnotationInfo[] paramAnnotationInfo = paramInfo[i].getAnnotationInfo();
                            if (paramAnnotationInfo != null) {
                                for (final AnnotationInfo ai : paramAnnotationInfo) {
                                    final String ais = ai.toString();
                                    if (!ais.isEmpty()) {
                                        if (buf.charAt(buf.length() - 1) != ' ') {
                                            buf.append(' ');
                                        }
                                        GraphvizUtils.htmlEncode(ais, buf);
                                        wrapPos += 1 + ais.length();
                                        if (wrapPos > PARAM_WRAP_WIDTH) {
                                            buf.append(
                                                    "</td></tr><tr><td></td><td></td><td align='left' valign='top'>");
                                            wrapPos = 0;
                                        }
                                    }
                                }
                            }

                            // Param type
                            final String paramTypeStr = paramInfo[i].getTypeSignatureOrTypeDescriptor().toString();
                            GraphvizUtils.htmlEncode(paramTypeStr, buf);
                            wrapPos += paramTypeStr.length();

                            // Param name
                            final String paramName = paramInfo[i].getName();
                            if (paramName != null) {
                                buf.append(" <B>");
                                GraphvizUtils.htmlEncode(paramName, buf);
                                wrapPos += 1 + paramName.length();
                                buf.append("</B>");
                            }
                        }
                    }
                    buf.append(')');
                    buf.append("</td></tr>");
                }
            }
            buf.append("</table>");
            buf.append("</td></tr>");
        }
        buf.append("</table>");
        buf.append(">]");
    }

    private List<ClassInfo> lookup(final Set<String> classNames) {
        final List<ClassInfo> classInfoNodes = new ArrayList<>();
        for (final String className : classNames) {
            final ClassInfo classInfo = classNameToClassInfo.get(className);
            if (classInfo != null) {
                classInfoNodes.add(classInfo);
            }
        }
        return classInfoNodes;
    }

    /**
     * Generates a .dot file which can be fed into GraphViz for layout and visualization of the class graph. The
     * sizeX and sizeY parameters are the image output size to use (in inches) when GraphViz is asked to render the
     * .dot file.
     */
    String generateClassGraphDotFile(final float sizeX, final float sizeY, final boolean showFields,
            final boolean showMethods) {
        final StringBuilder buf = new StringBuilder();
        buf.append("digraph {\n");
        buf.append("size=\"" + sizeX + "," + sizeY + "\";\n");
        buf.append("layout=dot;\n");
        buf.append("rankdir=\"BT\";\n");
        buf.append("overlap=false;\n");
        buf.append("splines=true;\n");
        buf.append("pack=true;\n");
        buf.append("graph [fontname = \"Courier, Regular\"]\n");
        buf.append("node [fontname = \"Courier, Regular\"]\n");
        buf.append("edge [fontname = \"Courier, Regular\"]\n");

        final Set<ClassInfo> standardClassNodes = ClassInfo.filterClassInfo(allClassInfo(),
                /* removeExternalClassesIfStrictWhitelist = */ true, scanSpec, ClassType.STANDARD_CLASS);
        final ClassInfo objectClass = classNameToClassInfo.get("java.lang.Object");
        if (objectClass != null) {
            // java.lang.Object should never be shown
            standardClassNodes.remove(objectClass);
        }
        final Set<ClassInfo> interfaceNodes = ClassInfo.filterClassInfo(allClassInfo(),
                /* removeExternalClassesIfStrictWhitelist = */ true, scanSpec, ClassType.IMPLEMENTED_INTERFACE);
        final Set<ClassInfo> annotationNodes = ClassInfo.filterClassInfo(allClassInfo(),
                /* removeExternalClassesIfStrictWhitelist = */ true, scanSpec, ClassType.ANNOTATION);

        for (final ClassInfo node : standardClassNodes) {
            buf.append("\"").append(node.getClassName()).append("\"");
            labelClassNodeHTML(node, "box", "fff2b6", showFields, showMethods, buf);
            buf.append(";\n");
        }

        for (final ClassInfo node : interfaceNodes) {
            buf.append("\"").append(node.getClassName()).append("\"");
            labelClassNodeHTML(node, "diamond", "b6e7ff", showFields, showMethods, buf);
            buf.append(";\n");
        }

        for (final ClassInfo node : annotationNodes) {
            buf.append("\"").append(node.getClassName()).append("\"");
            labelClassNodeHTML(node, "oval", "f3c9ff", showFields, showMethods, buf);
            buf.append(";\n");
        }

        final Set<ClassInfo> allVisibleNodes = new HashSet<>();
        allVisibleNodes.addAll(standardClassNodes);
        allVisibleNodes.addAll(interfaceNodes);
        allVisibleNodes.addAll(annotationNodes);

        buf.append("\n");
        for (final ClassInfo classNode : standardClassNodes) {
            final ClassInfo directSuperclassNode = classNode.getDirectSuperclass();
            if (directSuperclassNode != null && allVisibleNodes.contains(directSuperclassNode)) {
                // class --> superclass
                buf.append("  \"" + classNode.getClassName() + "\" -> \"" + directSuperclassNode.getClassName()
                        + "\" [arrowsize=2.5]\n");
            }
            for (final ClassInfo implementedInterfaceNode : classNode.getDirectlyImplementedInterfaces()) {
                if (allVisibleNodes.contains(implementedInterfaceNode)) {
                    // class --<> implemented interface
                    buf.append("  \"" + classNode.getClassName() + "\" -> \""
                            + implementedInterfaceNode.getClassName() + "\" [arrowhead=diamond, arrowsize=2.5]\n");
                }
            }
            for (final ClassInfo fieldTypeNode : lookup(
                    classNode.getClassNamesReferencedInFieldTypeDescriptors())) {
                if (allVisibleNodes.contains(fieldTypeNode)) {
                    // class --[ ] field type (open box)
                    buf.append("  \"" + fieldTypeNode.getClassName() + "\" -> \"" + classNode.getClassName()
                            + "\" [arrowtail=obox, arrowsize=2.5, dir=back]\n");
                }
            }
            for (final ClassInfo fieldTypeNode : lookup(
                    classNode.getClassNamesReferencedInMethodTypeDescriptors())) {
                if (allVisibleNodes.contains(fieldTypeNode)) {
                    // class --[#] method type (filled box)
                    buf.append("  \"" + fieldTypeNode.getClassName() + "\" -> \"" + classNode.getClassName()
                            + "\" [arrowtail=box, arrowsize=2.5, dir=back]\n");
                }
            }
        }
        for (final ClassInfo interfaceNode : interfaceNodes) {
            for (final ClassInfo superinterfaceNode : interfaceNode.getDirectSuperinterfaces()) {
                if (allVisibleNodes.contains(superinterfaceNode)) {
                    // interface --<> superinterface
                    buf.append("  \"" + interfaceNode.getClassName() + "\" -> \""
                            + superinterfaceNode.getClassName() + "\" [arrowhead=diamond, arrowsize=2.5]\n");
                }
            }
        }
        for (final ClassInfo annotationNode : annotationNodes) {
            for (final ClassInfo annotatedClassNode : annotationNode.getClassesWithDirectAnnotation()) {
                if (allVisibleNodes.contains(annotatedClassNode)) {
                    // annotated class --o annotation
                    buf.append("  \"" + annotatedClassNode.getClassName() + "\" -> \""
                            + annotationNode.getClassName() + "\" [arrowhead=dot, arrowsize=2.5]\n");
                }
            }
            for (final ClassInfo annotatedClassNode : annotationNode.getAnnotationsWithDirectMetaAnnotation()) {
                if (allVisibleNodes.contains(annotatedClassNode)) {
                    // annotation --o meta-annotation
                    buf.append("  \"" + annotatedClassNode.getClassName() + "\" -> \""
                            + annotationNode.getClassName() + "\" [arrowhead=dot, arrowsize=2.5]\n");
                }
            }
            for (final ClassInfo classWithMethodAnnotationNode : annotationNode
                    .getClassesWithDirectMethodAnnotation()) {
                if (allVisibleNodes.contains(classWithMethodAnnotationNode)) {
                    // class with method annotation --o method annotation
                    buf.append("  \"" + classWithMethodAnnotationNode.getClassName() + "\" -> \""
                            + annotationNode.getClassName() + "\" [arrowhead=odot, arrowsize=2.5]\n");
                }
            }
            for (final ClassInfo classWithMethodAnnotationNode : annotationNode.getClassesWithFieldAnnotation()) {
                if (allVisibleNodes.contains(classWithMethodAnnotationNode)) {
                    // class with field annotation --o method annotation
                    buf.append("  \"" + classWithMethodAnnotationNode.getClassName() + "\" -> \""
                            + annotationNode.getClassName() + "\" [arrowhead=odot, arrowsize=2.5]\n");
                }
            }
        }
        buf.append("}");
        return buf.toString();
    }
}
