package com.praetor.problem.dto;

import java.util.List;

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
        /** Replaces the problem's tags outright; null leaves them untouched, empty clears them. */
        List<String> tags,
        /** Create it as a draft: archived and unpublished, so a contest can still use it. */
        Boolean draft) {
}
