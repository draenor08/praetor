package com.praetor.contest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /api/contests/{id}/proposals/{proposalId}/accept — the admin's decision, which needs the
 * label the problem will wear in the contest. Two characters, matching the CHAR(2) column.
 */
public record AcceptProposalRequest(
        @NotBlank @Size(max = 2, message = "label is at most 2 characters") String label) {
}
