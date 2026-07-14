package com.praetor.contest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.ZonedDateTime;
import java.util.List;

/** POST /api/contests body (ADMIN). Cross-field checks (endsAt>startsAt, unique labels) are in the service. */
public record CreateContestRequest(
        @NotBlank String title,
        @NotNull ZonedDateTime startsAt,
        @NotNull ZonedDateTime endsAt,
        @NotNull @Min(0) Integer freezeMin,
        @NotNull @Pattern(regexp = "ICPC|POINTS", message = "scoring must be ICPC or POINTS") String scoring,
        @NotEmpty @Valid List<ContestProblemSpec> problems) {
}
