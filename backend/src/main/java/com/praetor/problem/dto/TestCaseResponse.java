package com.praetor.problem.dto;

public record TestCaseResponse(
        Long id,
        Integer ord,
        String kind,
        String input,
        String expected,
        Integer points) {
}
