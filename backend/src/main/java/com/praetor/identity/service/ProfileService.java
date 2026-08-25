package com.praetor.identity.service;

import com.praetor.identity.dto.ProfileSolveStats;
import com.praetor.identity.entity.User;
import com.praetor.identity.repository.UserRepository;
import com.praetor.submission.Verdict;
import com.praetor.submission.repository.SubmissionRepository;
import com.praetor.submission.repository.VerdictCountView;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Solve statistics for a profile (FR-25).
 *
 * <p>The numbers come from one verdict tally plus one distinct-problem count, both carrying the
 * same contest-end filter (see {@link SubmissionRepository#tallyVerdictsForUser(Long)}). Deriving
 * {@code attempted} and {@code accuracy} from the tally rather than querying them separately keeps
 * that filter in two places instead of four — this endpoint is readable by any authenticated user,
 * so a counter that forgot the filter would leak a frozen contest.
 */
@Service
public class ProfileService {

    /** Verdicts are reported commonest-first, ties alphabetical, so the response is stable. */
    private static final Comparator<VerdictCountView> BY_COUNT_DESC =
            Comparator.comparingLong(VerdictCountView::getTotal).reversed()
                    .thenComparing(VerdictCountView::getVerdict);

    private final UserRepository userRepository;
    private final SubmissionRepository submissionRepository;

    public ProfileService(UserRepository userRepository, SubmissionRepository submissionRepository) {
        this.userRepository = userRepository;
        this.submissionRepository = submissionRepository;
    }

    @Transactional(readOnly = true)
    public ProfileSolveStats getSolveStats(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "user not found: " + username));

        List<VerdictCountView> tally = submissionRepository.tallyVerdictsForUser(user.getId());

        Map<String, Long> byVerdict = new LinkedHashMap<>();
        tally.stream().sorted(BY_COUNT_DESC)
                .forEach(row -> byVerdict.put(row.getVerdict(), row.getTotal()));

        long attempted = byVerdict.values().stream().mapToLong(Long::longValue).sum();
        long accepted = byVerdict.getOrDefault(Verdict.AC, 0L);
        long solved = submissionRepository.countDistinctSolvedProblems(user.getId());

        return new ProfileSolveStats(solved, attempted, accuracy(accepted, attempted), byVerdict);
    }

    /**
     * Accepted over attempted, rounded to four places. Rounding is presentational only — it keeps
     * artefacts like {@code 0.30000000000000004} out of the JSON without pretending to a precision
     * the ratio does not have.
     */
    private static double accuracy(long accepted, long attempted) {
        if (attempted == 0) {
            return 0.0;
        }
        return Math.round(((double) accepted / attempted) * 10_000d) / 10_000d;
    }
}
