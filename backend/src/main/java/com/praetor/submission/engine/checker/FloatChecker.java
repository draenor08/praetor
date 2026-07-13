package com.praetor.submission.engine.checker;

/**
 * FLOAT match: tokenize like {@link TokenChecker}, then compare token-by-token. Numeric tokens must
 * agree within {@code eps} (absolute OR relative — {@code |a-e| <= eps} or {@code |a-e| <= eps*|e|},
 * so both tiny and large magnitudes are handled); non-numeric tokens must match exactly. Token
 * counts must match.
 */
public class FloatChecker implements Checker {

    private final double eps;

    public FloatChecker(double eps) {
        this.eps = eps;
    }

    @Override
    public boolean matches(String actual, String expected) {
        String[] a = TokenChecker.tokenize(actual);
        String[] e = TokenChecker.tokenize(expected);
        if (a.length != e.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (!tokenMatches(a[i], e[i])) {
                return false;
            }
        }
        return true;
    }

    private boolean tokenMatches(String actual, String expected) {
        Double ea = parse(expected);
        Double aa = parse(actual);
        if (ea == null || aa == null) {
            return actual.equals(expected); // non-numeric → exact
        }
        double diff = Math.abs(aa - ea);
        return diff <= eps || diff <= eps * Math.abs(ea);
    }

    private static Double parse(String s) {
        try {
            return Double.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
