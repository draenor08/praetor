package com.praetor.submission.dto;

/**
 * One test case's outcome in the GET-submission response. {@code input}/{@code expected}/
 * {@code actualOutput} are the feat-3d practice reveal — non-null ONLY on the first failing row of a
 * practice (non-contest) submission; null on every other row and for every contest submission.
 */
public record ResultResponse(int ord, String verdict, Integer timeMs, Integer memKb,
                             String input, String expected, String actualOutput) {
}
