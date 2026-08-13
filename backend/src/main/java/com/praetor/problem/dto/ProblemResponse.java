package com.praetor.problem.dto;

import java.util.List;

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
        List<String> tags,
        Long createdBy,
        boolean archived) {
}
