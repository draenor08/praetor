package com.praetor.contest.standings;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PrivilegedSubscriberRegistryTest {

    private final PrivilegedSubscriberRegistry registry = new PrivilegedSubscriberRegistry();

    @Test
    void twoTabsStayRegisteredUntilLastGoesAway() {
        registry.add("sess1", "sub1", 1L, "admin");
        registry.add("sess2", "sub1", 1L, "admin"); // second tab (different session)
        assertThat(registry.usernames(1L)).containsExactly("admin");

        registry.removeSubscription("sess1", "sub1");
        assertThat(registry.usernames(1L)).containsExactly("admin"); // still one tab

        registry.removeSubscription("sess2", "sub1");
        assertThat(registry.usernames(1L)).isEmpty();
    }

    @Test
    void disconnectDropsAllSubscriptionsOfSession() {
        registry.add("sess1", "sub1", 1L, "admin");
        registry.add("sess1", "sub2", 2L, "admin");
        registry.removeSession("sess1");
        assertThat(registry.usernames(1L)).isEmpty();
        assertThat(registry.usernames(2L)).isEmpty();
    }

    @Test
    void usernamesScopedPerContest() {
        registry.add("s", "a", 1L, "alice");
        registry.add("s", "b", 2L, "bob");
        assertThat(registry.usernames(1L)).containsExactly("alice");
        assertThat(registry.usernames(2L)).containsExactly("bob");
        assertThat(registry.usernames(3L)).isEmpty();
    }

    @Test
    void duplicateSubscribeIsIdempotent() {
        registry.add("s", "a", 1L, "alice");
        registry.add("s", "a", 1L, "alice"); // same ref → no double count
        registry.removeSubscription("s", "a");
        assertThat(registry.usernames(1L)).isEmpty();
    }
}
