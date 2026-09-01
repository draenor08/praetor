package com.praetor.identity.repository;

import com.praetor.identity.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Optional<User> findByUsernameOrEmail(String username, String email);

    /**
     * Every user in one statement, for the callers that hold a whole set of handles — rating a
     * finished contest, above all. Replaces a {@code findByUsername} per participant.
     */
    List<User> findByUsernameIn(Collection<String> usernames);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}
