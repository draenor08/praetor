package com.praetor.submission.engine.checker;

/**
 * TOKEN match: split both outputs on any run of whitespace and compare the resulting token lists.
 * Whitespace-insensitive, so differing spacing / newline conventions don't matter — only the token
 * sequence does. Empty/leading/trailing whitespace is ignored.
 */
public class TokenChecker implements Checker {

    @Override
    public boolean matches(String actual, String expected) {
        String[] a = tokenize(actual);
        String[] e = tokenize(expected);
        if (a.length != e.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (!a[i].equals(e[i])) {
                return false;
            }
        }
        return true;
    }

    static String[] tokenize(String s) {
        if (s == null) {
            return new String[0];
        }
        String t = s.strip();
        return t.isEmpty() ? new String[0] : t.split("\\s+");
    }
}
