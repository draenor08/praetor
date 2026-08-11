package com.praetor.identity.repository;

import com.praetor.identity.entity.Rating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RatingRepository extends JpaRepository<Rating, Long> {

    long countByValueGreaterThan(Integer value);
}
