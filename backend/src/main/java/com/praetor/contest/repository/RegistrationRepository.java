package com.praetor.contest.repository;

import com.praetor.contest.entity.Registration;
import com.praetor.contest.entity.RegistrationId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RegistrationRepository extends JpaRepository<Registration, RegistrationId> {

    /** All registrations for a contest (the standings row set). Property path `id.contestId`. */
    List<Registration> findByIdContestId(Long contestId);
}
