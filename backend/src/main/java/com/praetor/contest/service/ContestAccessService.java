package com.praetor.contest.service;

import com.praetor.contest.dto.EligibleProblemDto;
import com.praetor.contest.repository.ContestAccessRepository;
import com.praetor.identity.entity.User;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The contest embargo: a problem that belongs to a contest which has not ended yet is withheld from
 * everyone except staff and the contest's own registered participants (and those only once it is
 * actually running). When the last contest using the problem ends, it returns to public practice.
 *
 * <p>One rule, three consumers — the public problem list, the problem statement, and submission
 * creation — so a participant cannot reach an embargoed statement by typing its slug, and a
 * non-participant cannot submit to it. Kept in the contest module because contests and registrations
 * are its tables; the problem and submission modules call in, never the other way round.
 */
@Service
public class ContestAccessService {

    private final ContestAccessRepository accessRepo;

    public ContestAccessService(ContestAccessRepository accessRepo) {
        this.accessRepo = accessRepo;
    }

    /**
     * True if this user may see and submit to the problem right now.
     *
     * @param user the caller, or {@code null} for an anonymous reader
     */
    @Transactional(readOnly = true)
    public boolean mayAccessProblem(Long problemId, User user) {
        if (isStaff(user)) {
            return true;
        }
        if (!accessRepo.existsUnendedContestForProblem(problemId)) {
            return true;
        }
        return user != null
                && accessRepo.existsRunningRegisteredContestForProblem(problemId, user.getId());
    }

    /** Staff author and run the contests, so the embargo never applies to them. */
    public boolean isStaff(User user) {
        return user != null && !"USER".equals(user.getRole());
    }

    /**
     * May a contest use this problem? Only a draft nobody has been able to read, that no other
     * contest has claimed. Publication is one-way, so this never becomes true again once false.
     */
    @Transactional(readOnly = true)
    public boolean isEligibleForContest(Long problemId) {
        return accessRepo.isEligibleForContest(problemId);
    }

    /** Why not — for an error a setter can act on. */
    @Transactional(readOnly = true)
    public String ineligibleReason(Long problemId) {
        String reason = accessRepo.ineligibleReason(problemId);
        return reason == null ? "no such problem" : reason;
    }

    /** Draft problems a contest may still use. */
    @Transactional(readOnly = true)
    public List<EligibleProblemDto> eligiblePool() {
        return accessRepo.findEligiblePool().stream()
                .map(r -> new EligibleProblemDto(r.getProblemId(), r.getSlug(), r.getTitle(),
                        r.getDifficulty(), r.getJudgeMode(), r.getAuthor(),
                        r.getTestCases() == null ? 0L : r.getTestCases()))
                .toList();
    }
}
