package com.praetor.contest.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** One problem slot in a create-contest request. */
public record ContestProblemSpec(
        @NotNull Long problemId,
        @NotNull @Pattern(regexp = "[A-Za-z0-9]{1,2}", message = "label must be 1-2 alphanumerics") String label,
        @NotNull Integer ord) {
}
