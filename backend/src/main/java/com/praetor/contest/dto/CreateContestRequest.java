package com.praetor.contest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * POST /api/contests body (ADMIN). Cross-field checks (endsAt>startsAt, unique labels, problem
 * eligibility) are in the service.
 *
 * <p>{@code problems} may be empty when {@code callsOpen} is true: that is a contest created to
 * collect proposals from setters, whose problem set arrives later.
 */
public record CreateContestRequest(
        @NotBlank String title,
        @NotNull ZonedDateTime startsAt,
        @NotNull ZonedDateTime endsAt,
        @NotNull @Min(0) Integer freezeMin,
        @NotNull @Pattern(regexp = "ICPC|POINTS", message = "scoring must be ICPC or POINTS") String scoring,
        @NotNull @Valid List<ContestProblemSpec> problems,
        /** Open the contest to setter proposals right away. */
        Boolean callsOpen) {
}
