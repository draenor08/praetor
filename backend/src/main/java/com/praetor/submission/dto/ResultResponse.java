package com.praetor.submission.dto;

/** One test case's outcome in the GET-submission response. */
public record ResultResponse(int ord, String verdict, Integer timeMs, Integer memKb) {
}
