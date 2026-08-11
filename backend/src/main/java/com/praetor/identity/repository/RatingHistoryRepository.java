package com.praetor.identity.repository;

import com.praetor.identity.entity.RatingHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RatingHistoryRepository
        extends JpaRepository<RatingHistory, Long> {

    List<RatingHistory> findByUserIdOrderByCreatedAtAsc(Long userId);

    boolean existsByContestId(Long contestId);
}
