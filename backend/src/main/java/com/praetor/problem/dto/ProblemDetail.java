package com.praetor.problem.dto;

import java.util.List;

/**
 * GET /api/problems/{slug} — full statement + limits + visible samples.
 *
 * <p>{@code editorial} is null unless the caller has earned it (FR-16): staff always, everyone else
 * only after an accepted submission and only while no contest is using the problem. Withheld by
 * being absent from the payload rather than by a flag the client is trusted to respect.
 */
public record ProblemDetail(
        String slug,
        String title,
        String statement,
        String constraints,
        Integer difficulty,
        Integer timeLimitMs,
        Integer memLimitKb,
        String judgeMode,
        List<String> tags,
        String editorial,
        List<SampleDto> samples) {
}
