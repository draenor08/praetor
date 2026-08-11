package com.praetor.problem.service;

import com.praetor.identity.entity.User;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Single authorization rule for the problem module: authoring is staff work, so
 * {@code PROBLEM_SETTER} and {@code ADMIN} may both do all of it (create, update, delete,
 * archive, and manage test cases). There is deliberately no per-problem ownership check —
 * both roles are trusted staff on this deployment and {@code problems.created_by} stays
 * informational.
 *
 * <p>Kept in one place because the rule previously lived as two private copies (one in
 * {@link ProblemService}, one in {@link TestCaseService}) that had already drifted: both
 * matched {@code PROBLEM_SETTER} only, which locked ADMIN out of the module entirely.
 */
final class ProblemAuthz {

    private static final String ADMIN = "ADMIN";
    private static final String PROBLEM_SETTER = "PROBLEM_SETTER";

    private ProblemAuthz() {
    }

    /**
     * @param action verb phrase completing "only PROBLEM_SETTER or ADMIN may ..." in the 403 body
     */
    static void requireStaff(User user, String action) {
        String role = user == null ? null : user.getRole();
        if (!PROBLEM_SETTER.equals(role) && !ADMIN.equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "only PROBLEM_SETTER or ADMIN may " + action);
        }
    }
}
