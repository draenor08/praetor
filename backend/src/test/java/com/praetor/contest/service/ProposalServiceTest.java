package com.praetor.contest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.praetor.contest.dto.AcceptProposalRequest;
import com.praetor.contest.dto.ProposeRequest;
import com.praetor.contest.entity.Contest;
import com.praetor.contest.entity.ContestProblemProposal;
import com.praetor.contest.repository.ContestAccessRepository;
import com.praetor.contest.repository.ContestProblemRepository;
import com.praetor.contest.repository.ContestRepository;
import com.praetor.contest.repository.ProposalRepository;
import com.praetor.identity.entity.User;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Setters propose, the admin decides. What matters here is who may do what, and that a problem
 * cannot slip into a contest after it has become readable — the eligibility check is deliberately
 * repeated at accept time, because a draft can be published between the offer and the decision.
 */
class ProposalServiceTest {

    private static final long CONTEST_ID = 5L;
    private static final long PROBLEM_ID = 100L;
    private static final long PROPOSAL_ID = 77L;

    private final ProposalRepository proposalRepo = mock(ProposalRepository.class);
    private final ContestRepository contestRepo = mock(ContestRepository.class);
    private final ContestProblemRepository contestProblemRepo = mock(ContestProblemRepository.class);
    private final ContestAccessRepository accessRepo = mock(ContestAccessRepository.class);

    private final ProposalService service =
            new ProposalService(proposalRepo, contestRepo, contestProblemRepo, accessRepo);

    private User user(long id, String role) {
        User u = new User();
        u.setId(id);
        u.setRole(role);
        return u;
    }

    private Contest contest(boolean callsOpen) {
        Contest c = new Contest("Round", ZonedDateTime.now(), ZonedDateTime.now().plusHours(2), 15, "ICPC");
        c.setCallsOpen(callsOpen);
        return c;
    }

    private ContestProblemProposal proposal(String status) {
        ContestProblemProposal p =
                new ContestProblemProposal(CONTEST_ID, PROBLEM_ID, 2L, "worth a look");
        if (!ContestProblemProposal.PROPOSED.equals(status)) {
            p.decide(status);
        }
        return p;
    }

    private void assertStatus(Throwable t, HttpStatus expected) {
        assertThat(t).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) t).getStatusCode()).isEqualTo(expected);
    }

    // ---- proposing ----

    @Test
    void propose_contestNotAcceptingProposals_409() {
        when(contestRepo.findById(CONTEST_ID)).thenReturn(Optional.of(contest(false)));

        assertStatus(
                catchThrowable(() -> service.propose(
                        CONTEST_ID, new ProposeRequest(PROBLEM_ID, null), user(2L, "PROBLEM_SETTER"))),
                HttpStatus.CONFLICT);

        verify(proposalRepo, never()).save(any());
    }

    @Test
    void propose_contestant_403() {
        assertStatus(
                catchThrowable(() -> service.propose(
                        CONTEST_ID, new ProposeRequest(PROBLEM_ID, null), user(9L, "USER"))),
                HttpStatus.FORBIDDEN);

        // refused before the contest is even loaded
        verify(contestRepo, never()).findById(anyLong());
    }

    @Test
    void propose_publishedProblem_409() {
        when(contestRepo.findById(CONTEST_ID)).thenReturn(Optional.of(contest(true)));
        when(proposalRepo.existsByContestIdAndProblemId(CONTEST_ID, PROBLEM_ID)).thenReturn(false);
        when(accessRepo.isEligibleForContest(PROBLEM_ID)).thenReturn(false);
        when(accessRepo.ineligibleReason(PROBLEM_ID)).thenReturn("has been publicly visible since 2026-01-01");

        assertStatus(
                catchThrowable(() -> service.propose(
                        CONTEST_ID, new ProposeRequest(PROBLEM_ID, null), user(2L, "PROBLEM_SETTER"))),
                HttpStatus.CONFLICT);

        verify(proposalRepo, never()).save(any());
    }

    @Test
    void propose_sameProblemTwice_409() {
        when(contestRepo.findById(CONTEST_ID)).thenReturn(Optional.of(contest(true)));
        when(proposalRepo.existsByContestIdAndProblemId(CONTEST_ID, PROBLEM_ID)).thenReturn(true);

        assertStatus(
                catchThrowable(() -> service.propose(
                        CONTEST_ID, new ProposeRequest(PROBLEM_ID, null), user(2L, "PROBLEM_SETTER"))),
                HttpStatus.CONFLICT);

        verify(proposalRepo, never()).save(any());
    }

    // ---- deciding ----

    @Test
    void accept_nonAdmin_403_andNothingJoinsTheContest() {
        assertStatus(
                catchThrowable(() -> service.accept(
                        CONTEST_ID, PROPOSAL_ID, new AcceptProposalRequest("A"), user(2L, "PROBLEM_SETTER"))),
                HttpStatus.FORBIDDEN);

        verify(contestProblemRepo, never()).save(any());
    }

    @Test
    void accept_problemPublishedSinceProposing_409() {
        when(proposalRepo.findById(PROPOSAL_ID)).thenReturn(Optional.of(proposal(ContestProblemProposal.PROPOSED)));
        when(accessRepo.isEligibleForContest(PROBLEM_ID)).thenReturn(false);
        when(accessRepo.ineligibleReason(PROBLEM_ID)).thenReturn("has been publicly visible since 2026-01-01");

        assertStatus(
                catchThrowable(() -> service.accept(
                        CONTEST_ID, PROPOSAL_ID, new AcceptProposalRequest("A"), user(1L, "ADMIN"))),
                HttpStatus.CONFLICT);

        // the whole point of re-checking: a statement that leaked in the meantime stays out
        verify(contestProblemRepo, never()).save(any());
    }

    @Test
    void accept_labelAlreadyUsed_409() {
        when(proposalRepo.findById(PROPOSAL_ID)).thenReturn(Optional.of(proposal(ContestProblemProposal.PROPOSED)));
        when(accessRepo.isEligibleForContest(PROBLEM_ID)).thenReturn(true);
        when(contestProblemRepo.existsByIdContestIdAndLabel(CONTEST_ID, "A")).thenReturn(true);

        assertStatus(
                catchThrowable(() -> service.accept(
                        CONTEST_ID, PROPOSAL_ID, new AcceptProposalRequest("a"), user(1L, "ADMIN"))),
                HttpStatus.CONFLICT);

        verify(contestProblemRepo, never()).save(any());
    }

    @Test
    void accept_alreadyDecided_409() {
        when(proposalRepo.findById(PROPOSAL_ID)).thenReturn(Optional.of(proposal(ContestProblemProposal.REJECTED)));

        assertStatus(
                catchThrowable(() -> service.accept(
                        CONTEST_ID, PROPOSAL_ID, new AcceptProposalRequest("A"), user(1L, "ADMIN"))),
                HttpStatus.CONFLICT);

        verify(contestProblemRepo, never()).save(any());
    }

    @Test
    void accept_proposalFromAnotherContest_404() {
        ContestProblemProposal other =
                new ContestProblemProposal(999L, PROBLEM_ID, 2L, null);
        when(proposalRepo.findById(PROPOSAL_ID)).thenReturn(Optional.of(other));

        assertStatus(
                catchThrowable(() -> service.accept(
                        CONTEST_ID, PROPOSAL_ID, new AcceptProposalRequest("A"), user(1L, "ADMIN"))),
                HttpStatus.NOT_FOUND);
    }

    @Test
    void reject_leavesTheProblemAvailableElsewhere() {
        ContestProblemProposal p = proposal(ContestProblemProposal.PROPOSED);
        when(proposalRepo.findById(PROPOSAL_ID)).thenReturn(Optional.of(p));
        when(proposalRepo.findRowsByContestId(CONTEST_ID)).thenReturn(List.of());

        catchThrowable(() -> service.reject(CONTEST_ID, PROPOSAL_ID, user(1L, "ADMIN")));

        assertThat(p.getStatus()).isEqualTo(ContestProblemProposal.REJECTED);
        assertThat(p.getDecidedAt()).isNotNull();
        // rejecting must not touch the problem itself — it stays a draft, usable by another contest
        verify(accessRepo, never()).isEligibleForContest(anyLong());
        verify(contestProblemRepo, never()).save(any());
    }

    @Test
    void listForContest_contestant_403() {
        assertStatus(
                catchThrowable(() -> service.listForContest(CONTEST_ID, user(9L, "USER"))),
                HttpStatus.FORBIDDEN);

        verify(proposalRepo, never()).findRowsByContestId(anyLong());
    }

    @Test
    void listForContest_anonymous_403() {
        assertStatus(
                catchThrowable(() -> service.listForContest(CONTEST_ID, null)),
                HttpStatus.FORBIDDEN);
    }

    @Test
    void ineligibleReason_isCarriedIntoTheMessage() {
        when(contestRepo.findById(CONTEST_ID)).thenReturn(Optional.of(contest(true)));
        when(proposalRepo.existsByContestIdAndProblemId(anyLong(), anyLong())).thenReturn(false);
        when(accessRepo.isEligibleForContest(PROBLEM_ID)).thenReturn(false);
        when(accessRepo.ineligibleReason(PROBLEM_ID)).thenReturn("already used by a contest");

        Throwable t = catchThrowable(() -> service.propose(
                CONTEST_ID, new ProposeRequest(PROBLEM_ID, null), user(2L, "PROBLEM_SETTER")));

        // a setter should be told why, not just refused
        assertThat(t).hasMessageContaining("already used by a contest");
    }
}
