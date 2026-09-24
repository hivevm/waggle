// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.core;

import java.math.BigInteger;
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
public class Version implements Comparable<Version> {

    private static final String PATTERN =
            "(?<major>\\d+)\\.(?<minor>\\d+)(?:\\.(?<patch>\\d+))?(?:-(?<name>[a-zA-Z0-9.]+))?(?:\\+(?<build>[a-zA-Z0-9.]+))?";

    private static final Pattern PARSE = Pattern.compile(Version.PATTERN);
    private static final Pattern MATCH = Pattern.compile("^" + Version.PATTERN + "$");
    private static final Pattern FORMAT = Pattern.compile("(0+)\\.(0+)(?:\\.(0+))?(?:-(0+))?(?:\\+(0+))?");

    private final int major;
    private final int minor;
    private final int patch;

    private final String name;
    private final String build;

    /**
     * Constructs an instance of {@link Version}.
     */
    protected Version(int major, int minor, int patch, String name, String build) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.name = name;
        this.build = build;
    }

    /**
     * Gets the major number.
     */
    public final int getMajor() {
        return this.major;
    }

    /**
     * Gets the minor number.
     */
    public final int getMinor() {
        return this.minor;
    }

    /**
     * Gets the patch number.
     */
    public final int getPatch() {
        return this.patch;
    }

    /**
     * Gets the pre-release name.
     */
    public final String getName() {
        return this.name;
    }

    /**
     * Gets the build text.
     */
    public final String getBuild() {
        return this.build;
    }

    /**
     * Compares this {@link Version} with the specified {@link Version} for order, by semantic
     * version precedence: the lower version sorts first, a missing patch counts as 0, a pre-release
     * sorts before its release, and the build metadata is ignored.
     *
     * <p>The order used to be reversed -- a newer version compared as smaller -- and the
     * pre-release was not looked at.
     */
    @Override
    public int compareTo(Version other) {
        int result = Integer.compare(getMajor(), other.getMajor());
        if (result == 0) {
            result = Integer.compare(getMinor(), other.getMinor());
        }
        if (result == 0) {
            result = Integer.compare(Math.max(getPatch(), 0), Math.max(other.getPatch(), 0));
        }
        return (result != 0) ? result : Version.comparePreRelease(getName(), other.getName());
    }

    /** Pre-release precedence per semver 2.0.0 section 11.4; none sorts after any. */
    private static int comparePreRelease(String a, String b) {
        if ((a == null) || (b == null)) {
            return (a == b) ? 0 : ((a == null) ? 1 : -1);
        }

        String[] left = a.split("\\.");
        String[] right = b.split("\\.");
        for (int i = 0; (i < left.length) && (i < right.length); i++) {
            boolean leftNumeric = left[i].chars().allMatch(Character::isDigit);
            boolean rightNumeric = right[i].chars().allMatch(Character::isDigit);
            int result;
            if (leftNumeric && rightNumeric) {
                result = new BigInteger(left[i]).compareTo(new BigInteger(right[i]));
            } else if (leftNumeric != rightNumeric) {
                result = leftNumeric ? -1 : 1;
            } else {
                result = left[i].compareTo(right[i]);
            }
            if (result != 0) {
                return result;
            }
        }
        return Integer.compare(left.length, right.length);
    }

    /**
     * Returns a string representation of the version.
     */
    @Override
    public final String toString() {
        StringBuilder buffer = new StringBuilder();
        buffer.append(getMajor());
        buffer.append(".");
        buffer.append(getMinor());
        if (getPatch() > -1) {
            buffer.append(".");
            buffer.append(getPatch());
        }
        if (getName() != null) {
            buffer.append("-");
            buffer.append(getName());
        }
        if (getBuild() != null) {
            buffer.append("+");
            buffer.append(getBuild());
        }
        return buffer.toString();
    }

    /**
     * Returns a string representation of the version, using the provided format.
     */
    public final String toString(String format) {
        Matcher matcher = Version.FORMAT.matcher(format);
        if (!matcher.find()) {
            return toString();
        }

        StringBuilder buffer = new StringBuilder();
        String text = "%0" + matcher.group(1).length() + "d.%0" + matcher.group(2).length() + "d";
        buffer.append(String.format(text, getMajor(), getMinor()));
        if (matcher.group(3) != null) {
            text = ".%0" + matcher.group(3).length() + "d";
            buffer.append(String.format(text, Math.max(getPatch(), 0)));
        }
        if ((matcher.group(4) != null) && (getName() != null)) {
            buffer.append("-");
            buffer.append(getName());
        }
        if ((matcher.group(5) != null) && (getBuild() != null)) {
            buffer.append("+");
            buffer.append(getBuild());
        }
        return buffer.toString();
    }

    /**
     * Creates a new instance of {@link Version}
     */
    public static Version of(int major, int minor) {
        return Version.of(major, minor, -1, null, null);
    }

    /**
     * Creates a new instance of {@link Version}
     */
    public static Version of(int major, int minor, int patch) {
        return Version.of(major, minor, patch, null, null);
    }

    /**
     * Creates a new instance of {@link Version}
     */
    public static Version of(int major, int minor, String pre, String build) {
        return Version.of(major, minor, -1, pre, build);
    }

    /**
     * Creates a new instance of {@link Version}
     */
    public static Version of(int major, int minor, int patch, String pre, String build) {
        return new Version(major, minor, patch, pre, build);
    }

    /**
     * Parses a {@link Version} from the text. Instead of the {@link #parse(String)}, the method
     * expects an exact matching of the version without any preceding and succeeding character.
     */
    public static Version of(String text) throws IllegalArgumentException {
        return Version.parse(text, Version.MATCH);
    }

    /**
     * Parses a new instance of {@link Version}
     */
    public static Version parse(String text) throws IllegalArgumentException {
        return Version.parse(text, Version.PARSE);
    }

    /**
     * Parses a new instance of {@link Version}. The provided pattern must contain named groups with
     * the names: major, minor, patch, name, build.
     */
    public static Version parse(String text, Pattern pattern) throws IllegalArgumentException {
        if (text == null) {
            return null;
        }

        var matcher = pattern.matcher(text);
        if (!matcher.find()) {
            throw new IllegalArgumentException("'" + text + "' is not a valid version");
        }

        int major = Integer.parseInt(matcher.group("major"));
        int minor = Integer.parseInt(matcher.group("minor"));
        int patch = (matcher.group("patch") == null) ? -1 : Integer.parseInt(matcher.group("patch"));
        return Version.of(major, minor, patch, matcher.group("name"), matcher.group("build"));
    }
}