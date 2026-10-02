// Copyright 2024 HiveVM.ORG. All rights reserved.
// Copyright (c) 2006, Sun Microsystems, Inc. All rights reserved.
// Copyright 2011 Google Inc. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause
//
// Derived from JavaCC 7.0.12: org/javacc/parser/RStringLiteral.java, org/javacc/parser/NfaState.java

package org.hivevm.waggle.codegen.rust;

import org.hivevm.waggle.api.Options;
import org.hivevm.waggle.api.OptionsContext;
import org.hivevm.waggle.api.Language;
import org.hivevm.waggle.codegen.LexerGenerator;
import org.hivevm.waggle.codegen.LexState;
import org.hivevm.waggle.codegen.StringLiteralDfaEmitter;
import org.hivevm.waggle.lexer.LexerData;
import org.hivevm.waggle.lexer.LexerPlan.KindSet;
import org.hivevm.source.TemplateSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Generate lexer.
 */
class RustLexerGenerator extends LexerGenerator {

    public RustLexerGenerator() {
        super(Language.RUST);
    }

    @Override
    protected String identifier(String name) {
        return RustIdentifier.of(name);
    }

    @Override
    protected StringLiteralDfaEmitter newStringLiteralDfaEmitter() {
        return new RustStringLiteralDfaEmitter(this);
    }

    @Override
    protected final void generate(LexerData data, OptionsContext options) {
        var images = RustLexerGenerator.getStrLiteralImageList(data.plan().shape().images());
        options.add("LITERAL_IMAGES", images).set("LITERAL_IMAGE_NAME", s -> s);
        options.set("LITERAL_IMAGES_LENGTH", images.size());
        options.set("STATE_NAMES_LENGTH", data.plan().shape().stateNames().size());

        RustTemplate.LEXER.render(options);
    }

    // ---------------------------------------------------------------- dialect

    @Override
    public String charEquals(int c) {
        return "self.cur_char == " + c;
    }

    @Override
    public String charNotEquals(int c) {
        return "self.cur_char != " + c;
    }

    @Override
    public String bitIsSet(long mask) {
        return "(" + toHexString(mask) + " & l) != 0";
    }

    @Override
    public String bitIsClear(long mask) {
        return "(" + toHexString(mask) + " & l) == 0";
    }

    @Override
    public String canMove(int method) {
        return "jj_can_move_" + method + "(hi_byte, i1, i2, l1, l2)";
    }

    /** Rust writes the state tables as bare arrays, without the surrounding braces Java needs. */
    @Override
    public String noStateSet() {
        return "";
    }

    @Override
    public String stateSet(CharSequence body) {
        return body.toString();
    }

    @Override
    public String rowOpen() {
        return "&[";
    }

    @Override
    public String rowClose() {
        return "]";
    }

    @Override
    public String toHexString(long value) {
        return "0x" + Long.toHexString(value);
    }

    @Override
    protected TemplateSet.Source<Options> getConstantsTemplate() {
        return RustTemplate.PARSER_CONSTANTS;
    }

    /**
     * The literal image of each token kind, as the content of a Rust string literal; {@code null}
     * where a kind has none, which the template renders as the empty string.
     *
     * <p>This used to write JavaCC's octal escapes in a form Rust does not know, so {@code "if"}
     * came out as {@code "0o151;0o146;"} -- which compiled, and became the image of every keyword.
     */
    private static List<String> getStrLiteralImageList(List<String> images) {
        var list = new ArrayList<String>();
        for (var image : images) {
            list.add((image == null) ? null : RustLexerGenerator.toRustStringContent(image));
        }
        return list;
    }

    private static String toRustStringContent(String image) {
        // Code points, not chars: Rust has no escape for half a surrogate pair.
        var text = new StringBuilder();
        image.codePoints().forEach(c -> {
            if ((c == '"') || (c == '\\')) {
                text.append('\\').appendCodePoint(c);
            } else if ((c >= 0x20) && (c < 0x7f)) {
                text.appendCodePoint(c);
            } else {
                text.append("\\u{").append(Integer.toHexString(c)).append('}');
            }
        });
        return text.toString();
    }

    @Override
    public String longZero() {
        return "0";
    }

    @Override
    public String activeParameters(int maxKindsReqd) {
        return IntStream.range(0, maxKindsReqd).mapToObj(i -> "active" + i + ": " + longType())
                .collect(Collectors.joining(", "));
    }

    /**
     * A state index is a {@code usize} in Rust, so -1 cannot mean "no state". {@code usize::MAX}
     * does: it reaches no arm of the state dispatch, exactly as -1 reaches no case in Java.
     */
    @Override
    public String noState() {
        return "usize::MAX";
    }

    @Override
    public String longType() {
        return "u64";
    }

    @Override
    public String moveNfaName(LexState lex) {
        return "self.jj_move_nfa" + lex.suffix();
    }

    @Override
    public String stopStringLiteralDfaName(LexState lex) {
        return "self.jjStopStringLiteralDfa" + lex.suffix();
    }

    /** {@code jjtoToken} is a constant in Rust, and constants are SCREAMING_SNAKE_CASE. */
    @Override
    protected String kindVectorName(KindSet set) {
        return RustLexerGenerator.constantName(super.kindVectorName(set));
    }

    /** A name as Rust spells a constant. */
    private static String constantName(String name) {
        return name.replaceAll("(?<=[a-z])(?=[A-Z])", "_").toUpperCase(Locale.ROOT);
    }

    @Override
    public String imageSeparator(int i, int last) {
        return ",";
    }

}
