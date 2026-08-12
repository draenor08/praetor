package com.praetor.contest.repository;

import java.time.Instant;

/** One proposal joined to the problem it offers and the setter who offered it. */
public interface ProposalRow {

    Long getId();

    Long getProblemId();

    String getSlug();

    String getTitle();

    Integer getDifficulty();

    String getJudgeMode();

    String getProposedBy();

    String getStatus();

    String getNote();

    Instant getCreatedAt();

    /** How many test cases back the problem — an admin should not accept one with none. */
    Long getTestCases();
}
