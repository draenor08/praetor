package com.praetor.problem.dto;

public record ProblemRequest(
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
        /** Create it as a draft: archived and unpublished, so a contest can still use it. */
        Boolean draft) {
}
