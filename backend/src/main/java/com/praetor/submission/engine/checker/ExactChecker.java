package com.praetor.submission.engine.checker;

/**
 * EXACT match, tolerant of trailing whitespace: strip trailing whitespace per line and drop trailing
 * blank lines, then compare line-by-line. So {@code "5"} and {@code "5\n"} match, but internal
 * spacing and line breaks are significant.
 */
public class ExactChecker implements Checker {

    @Override
    public boolean matches(String actual, String expected) {
        return normalize(actual).equals(normalize(expected));
    }

    static String normalize(String s) {
        if (s == null) {
            return "";
        }
        String[] lines = s.split("\n", -1);
        int end = lines.length;
        while (end > 0 && lines[end - 1].strip().isEmpty()) {
            end--;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < end; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(stripTrailing(lines[i]));
        }
        return sb.toString();
    }

    private static String stripTrailing(String line) {
        int end = line.length();
        while (end > 0 && Character.isWhitespace(line.charAt(end - 1))) {
            end--;
        }
        return line.substring(0, end);
    }
}
