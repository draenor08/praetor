package com.praetor.contest.repository;

/**
 * A contest's problem slot joined to the identity its link needs. Native projection over the problem
 * module's table on purpose — the contest module never references the {@code Problem} entity, the
 * same insulation {@link StandingsRepository} keeps over {@code submissions}.
 */
public interface ContestProblemRow {

    String getLabel();

    int getOrd();

    Long getProblemId();

    String getSlug();

    String getTitle();
}
