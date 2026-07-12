package com.praetor.problem.dto;

import java.util.List;

/** GET /api/problems/{slug} — full statement + limits + visible samples. */
public record ProblemDetail(
        String slug,
        String title,
        String statement,
        String constraints,
        Integer difficulty,
        Integer timeLimitMs,
        Integer memLimitKb,
        String judgeMode,
        List<SampleDto> samples) {
}
