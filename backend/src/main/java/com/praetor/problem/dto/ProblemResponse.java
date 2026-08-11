package com.praetor.problem.dto;

public record ProblemResponse(
        Long id,
        String slug,
        String title,
        String statement,
        String constraints,
        Integer difficulty,
        Integer timeLimitMs,
        Integer memLimitKb,
        String judgeMode,
        Double floatEps,
        String checkerCode,
        String editorial,
        Long createdBy) {
}
