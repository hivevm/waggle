// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The {@link Version} implements the semantic version syntax
 * ({@link https://semver.org/spec/v2.0.0.html}. Depending on the software the major.minor.patch are
 * interpreted differently.
 * <p>
 * On the API the interpretation of the version number is following:
 *
 * <pre>
 * - MAJOR version when you make incompatible API changes,
 * - MINOR version when you add functionality in a backwards compatible manner, and
 * - PATCH version when you make backwards compatible bug fixes.
 * </pre>
 * <p>
 * On a released client software the version number is interpreted as following:
 *
 * <pre>
 * - MAJOR defines the year of release,
 * - MINOR defines the month of release
 * - PATCH version when you make backwards compatible bug fixes.
 * </pre>
 * <p>
 * For the interpretation of a full version text see the Backus–Naur Form Grammar from the
 * specification.
 * <p>
 * E.g.:
 *
 * <pre>
 *   19.12
 *   19.12.1
 *   19.12.1-rc1
 *   19.12-beta1+build.1.2
 *   19.04
 *   19.4+build.1.2
 * </pre>
 */
public final class Version {

    private static final Pattern PARSE = Pattern.compile(
            "(?<major>\\d+)\\.(?<minor>\\d+)(?:\\.(?<patch>\\d+))?(?:-(?<name>[a-zA-Z0-9.]+))?(?:\\+(?<build>[a-zA-Z0-9.]+))?");
    private static final Pattern FORMAT = Pattern.compile("(0+)\\.(0+)(?:\\.(0+))?(?:-(0+))?(?:\\+(0+))?");

    private final int major;
    private final int minor;
    private final int patch;

    private final String name;
    private final String build;

    private Version(int major, int minor, int patch, String name, String build) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.name = name;
        this.build = build;
    }

    /**
     * Returns a string representation of the version.
     */
    @Override
    public String toString() {
        StringBuilder buffer = new StringBuilder();
        buffer.append(this.major);
        buffer.append(".");
        buffer.append(this.minor);
        if (this.patch > -1) {
            buffer.append(".");
            buffer.append(this.patch);
        }
        if (this.name != null) {
            buffer.append("-");
            buffer.append(this.name);
        }
        if (this.build != null) {
            buffer.append("+");
            buffer.append(this.build);
        }
        return buffer.toString();
    }

    /**
     * Returns a string representation of the version, using the provided format.
     */
    public String toString(String format) {
        Matcher matcher = Version.FORMAT.matcher(format);
        if (!matcher.find()) {
            return toString();
        }

        StringBuilder buffer = new StringBuilder();
        String text = "%0" + matcher.group(1).length() + "d.%0" + matcher.group(2).length() + "d";
        buffer.append(String.format(text, this.major, this.minor));
        if (matcher.group(3) != null) {
            text = ".%0" + matcher.group(3).length() + "d";
            buffer.append(String.format(text, Math.max(this.patch, 0)));
        }
        if ((matcher.group(4) != null) && (this.name != null)) {
            buffer.append("-");
            buffer.append(this.name);
        }
        if ((matcher.group(5) != null) && (this.build != null)) {
            buffer.append("+");
            buffer.append(this.build);
        }
        return buffer.toString();
    }

    /**
     * Parses the first version found in {@code text}; {@code null} for {@code null}.
     */
    public static Version parse(String text) throws IllegalArgumentException {
        if (text == null) {
            return null;
        }

        var matcher = Version.PARSE.matcher(text);
        if (!matcher.find()) {
            throw new IllegalArgumentException("'" + text + "' is not a valid version");
        }

        int major = Integer.parseInt(matcher.group("major"));
        int minor = Integer.parseInt(matcher.group("minor"));
        int patch = (matcher.group("patch") == null) ? -1 : Integer.parseInt(matcher.group("patch"));
        return new Version(major, minor, patch, matcher.group("name"), matcher.group("build"));
    }
}
