package com.praetor.profile.service;

import com.praetor.identity.entity.User;
import com.praetor.identity.repository.UserRepository;
import com.praetor.profile.dto.ProfileSolveStats;
import com.praetor.submission.repository.SubmissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

    private final UserRepository userRepository;
    private final SubmissionRepository submissionRepository;

    public ProfileService(UserRepository userRepository, SubmissionRepository submissionRepository) {
        this.userRepository = userRepository;
        this.submissionRepository = submissionRepository;
    }

    @Transactional(readOnly = true)
    public ProfileSolveStats getSolveStats(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        long submissions = submissionRepository.countByUserId(user.getId());
        long solved = submissionRepository.countDistinctSolvedProblemsByUser(user.getId());
        double acceptance = submissions == 0 ? 0.0 : ((double) solved) / ((double) submissions);

        return new ProfileSolveStats(user.getUsername(), solved, submissions, acceptance);
    }
}
