package com.praetor.submission.service;

import com.praetor.identity.entity.User;
import com.praetor.identity.repository.UserRepository;
import com.praetor.submission.SubmissionStatus;
import com.praetor.submission.Verdict;
import com.praetor.submission.dto.ResultResponse;
import com.praetor.submission.dto.SubmissionCreatedResponse;
import com.praetor.submission.dto.SubmissionResponse;
import com.praetor.submission.dto.SubmitRequest;
import com.praetor.submission.engine.JudgeService;
import com.praetor.submission.entity.JudgeProblem;
import com.praetor.submission.entity.Submission;
import com.praetor.submission.repository.JudgeProblemRepository;
import com.praetor.submission.repository.RevealView;
import com.praetor.submission.repository.SubmissionRepository;
import com.praetor.submission.repository.SubmissionResultRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SubmissionService {

    private final SubmissionRepository subRepo;
    private final SubmissionResultRepository resultRepo;
    private final JudgeProblemRepository problemRepo;
    private final UserRepository userRepo;
    private final JudgeService judgeService;

    public SubmissionService(SubmissionRepository subRepo, SubmissionResultRepository resultRepo,
                             JudgeProblemRepository problemRepo, UserRepository userRepo,
                             JudgeService judgeService) {
        this.subRepo = subRepo;
        this.resultRepo = resultRepo;
        this.problemRepo = problemRepo;
        this.userRepo = userRepo;
        this.judgeService = judgeService;
    }

    /**
     * Persist a QUEUED submission and hand it to the async judge. NOT {@code @Transactional}: the
     * {@code save} self-commits, so the row is durable BEFORE {@code enqueue} — otherwise the judge
     * thread could query it before the tx commits and find nothing.
     */
    public SubmissionCreatedResponse create(SubmitRequest req, User user) {
        if (!"CPP".equals(req.language())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "unsupported language: " + req.language());
        }
        JudgeProblem problem = problemRepo.findBySlug(req.problemSlug())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "problem not found: " + req.problemSlug()));

        Submission sub = new Submission();
        sub.setUserId(user.getId());
        sub.setProblemId(problem.getId());
        sub.setContestId(req.contestId());
        sub.setLanguage("CPP");
        sub.setSourceCode(req.sourceCode());
        sub.setStatus(SubmissionStatus.QUEUED);
        Submission saved = subRepo.save(sub);

        judgeService.enqueue(saved.getId());
        return new SubmissionCreatedResponse(saved.getId(), saved.getStatus());
    }

    @Transactional(readOnly = true)
    public SubmissionResponse get(Long id, User user) {
        Submission sub = subRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "submission not found"));

        boolean owner = sub.getUserId().equals(user.getId());
        boolean admin = "ADMIN".equals(user.getRole());
        if (!owner && !admin) {
            // 404 (not 403) so a non-owner can't confirm the submission exists
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "submission not found");
        }

        String handle = userRepo.findById(sub.getUserId()).map(User::getUsername).orElse(null);
        String slug = problemRepo.findById(sub.getProblemId()).map(JudgeProblem::getSlug).orElse(null);

        boolean practice = sub.getContestId() == null;

        // SECURITY GATE (feat 3d) — reveal the first failing test case's input/expected/actual ONLY
        // for a fully-judged PRACTICE submission with a real failing verdict. A contest submission
        // (contestId != null) NEVER reveals, regardless of viewer (owner or admin). AC has nothing
        // to reveal; CE persists no per-test rows. Only if this holds do we run the reveal query —
        // hidden test-case data is otherwise never fetched.
        boolean reveal = practice
                && SubmissionStatus.DONE.equals(sub.getStatus())
                && sub.getVerdict() != null
                && !Verdict.AC.equals(sub.getVerdict())
                && !Verdict.CE.equals(sub.getVerdict());

        Integer revealOrd = null;
        String revealInput = null;
        String revealExpected = null;
        String revealActual = null;
        if (reveal) {
            RevealView rv = resultRepo.findFailingReveal(id).stream().findFirst().orElse(null);
            if (rv != null) {
                revealOrd = rv.getOrd();
                revealInput = truncate(rv.getInput(), DISPLAY_CAP);
                revealExpected = truncate(rv.getExpected(), DISPLAY_CAP);
                revealActual = rv.getActualOutput(); // already truncated at capture
            }
        }
        final Integer fOrd = revealOrd;
        final String fIn = revealInput;
        final String fExp = revealExpected;
        final String fAct = revealActual;

        List<ResultResponse> results = resultRepo.findResultViews(id).stream()
                .map(v -> (fOrd != null && fOrd.equals(v.getOrd()))
                        ? new ResultResponse(v.getOrd(), v.getVerdict(), v.getTimeMs(), v.getMemKb(),
                                fIn, fExp, fAct)
                        : new ResultResponse(v.getOrd(), v.getVerdict(), v.getTimeMs(), v.getMemKb(),
                                null, null, null))
                .toList();

        return new SubmissionResponse(
                sub.getId(), handle, slug, sub.getLanguage(), sub.getStatus(), sub.getVerdict(),
                sub.getTimeMs(), sub.getMemKb(), sub.getCompileLog(),
                sub.getCreatedAt() == null ? null : sub.getCreatedAt().toInstant().toString(),
                practice, results);
    }

    /** Display cap on revealed input/expected (actual output is already capped at judge capture). */
    private static final int DISPLAY_CAP = 4096;

    private static String truncate(String s, int cap) {
        if (s == null) {
            return null;
        }
        return s.length() <= cap ? s : s.substring(0, cap);
    }
}
