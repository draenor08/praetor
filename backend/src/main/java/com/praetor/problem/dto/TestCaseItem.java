package com.praetor.problem.dto;

public record TestCaseItem(
        Integer ord,
        String kind,
        String input,
        String expected,
        Integer points) {
}
