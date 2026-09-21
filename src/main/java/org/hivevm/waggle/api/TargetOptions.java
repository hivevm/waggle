// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.waggle.api;

import java.util.List;

/**
 * The settings that only one target language has anything to do with.
 *
 * <p>One record rather than three: a grammar names its target once, and the Java side reads exactly
 * three of these values — the Rust module when filling in its default, the Java imports when
 * building the parser's context, and the Java package when deciding where a file goes. The rest are
 * read by templates, by name (ADR-0005). Three records for three readers would be structure without
 * a decision behind it.
 *
 * @param language       the target this grammar generates for
 * @param javaPackage    {@code JAVA_PACKAGE}: the package of the generated Java sources
 * @param javaImports    {@code JAVA_IMPORTS}: the imports copied into the generated parser;
 *                       the option is written as one comma-separated string and stored as a list
 * @param rustModule     {@code RUST_MODULE}: the module the generated Rust lives in
 * @param cppNamespace   {@code CPP_NAMESPACE}: the namespace of the generated C++
 * @param cppStackLimit  {@code CPP_STACK_LIMIT}: the C++ parser's stack guard, or empty for none
 * @param baseParser     {@code BASE_PARSER}: the class the generated parser extends
 * @param baseLexer      {@code BASE_LEXER}: the class the generated token manager extends
 */
public record TargetOptions(Language language, String javaPackage, List<String> javaImports,
                            String rustModule, String cppNamespace, String cppStackLimit,
                            String baseParser, String baseLexer) {

    public static TargetOptions from(WaggleOptions options) {
        return new TargetOptions(options.getOutputLanguage(),
                options.getJavaPackageName(),
                TargetOptions.imports(options),
                options.stringValue(Waggle.JJPARSER_RUST_MODULE),
                options.stringValue(Waggle.JJPARSER_CPP_NAMESPACE),
                options.stringValue(Waggle.JJPARSER_CPP_STACK_LIMIT),
                options.stringValue(Waggle.JJPARSER_BASE_PARSER),
                options.stringValue(Waggle.JJPARSER_BASE_LEXER));
    }

    @SuppressWarnings("unchecked")
    private static List<String> imports(WaggleOptions options) {
        var value = options.get(Waggle.JJPARSER_JAVA_IMPORTS);
        return (value instanceof List<?> list) ? List.copyOf((List<String>) list) : List.of();
    }
}
