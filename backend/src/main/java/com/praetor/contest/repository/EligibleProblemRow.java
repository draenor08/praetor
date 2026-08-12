package com.praetor.contest.repository;

/** A draft problem a contest may still use, as the creation page needs to show it. */
public interface EligibleProblemRow {

    Long getProblemId();

    String getSlug();

    String getTitle();

    Integer getDifficulty();

    String getJudgeMode();

    /** Username of whoever authored it, so an admin knows whose draft they are taking. */
    String getAuthor();

    /** A draft with zero test cases cannot be judged — worth seeing before accepting it. */
    Long getTestCases();
}
