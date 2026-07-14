package com.praetor.contest.standings;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Tracks which privileged users are currently subscribed to a contest's live standings queue
 * ({@code /user/queue/contest/{id}/standings}) — the set the freeze-aware publisher pushes the LIVE
 * board to during a freeze window.
 *
 * <p>Ref-counted so a user with two tabs (two STOMP sessions/subscriptions) stays registered until
 * the last one goes away. A subscription is identified by {@code sessionId|subscriptionId}; on
 * unsubscribe we drop that one, on disconnect we drop every subscription of the session. All
 * mutation is {@code synchronized} — subscribe/unsubscribe/disconnect events and publisher reads
 * arrive on different threads.
 */
@Component
public class PrivilegedSubscriberRegistry {

    private record Key(Long contestId, String username) {
    }

    /** Active subscription count per (contest, user). */
    private final Map<Key, Integer> counts = new HashMap<>();
    /** subscriptionRef ({@code sessionId|subId}) → the key it registered, for exact cleanup. */
    private final Map<String, Key> bySubscription = new HashMap<>();
    /** sessionId → its subscriptionRefs, so a disconnect can drop them all. */
    private final Map<String, Set<String>> bySession = new HashMap<>();

    public synchronized void add(String sessionId, String subscriptionId, Long contestId, String username) {
        String ref = ref(sessionId, subscriptionId);
        if (bySubscription.containsKey(ref)) {
            return; // idempotent — a duplicate SUBSCRIBE for the same ref
        }
        Key key = new Key(contestId, username);
        bySubscription.put(ref, key);
        bySession.computeIfAbsent(sessionId, k -> new HashSet<>()).add(ref);
        counts.merge(key, 1, Integer::sum);
    }

    public synchronized void removeSubscription(String sessionId, String subscriptionId) {
        drop(ref(sessionId, subscriptionId), sessionId);
    }

    public synchronized void removeSession(String sessionId) {
        Set<String> refs = bySession.remove(sessionId);
        if (refs == null) {
            return;
        }
        for (String ref : Set.copyOf(refs)) {
            drop(ref, sessionId);
        }
    }

    /** Snapshot of the privileged usernames currently watching this contest's live queue. */
    public synchronized Set<String> usernames(Long contestId) {
        Set<String> out = new HashSet<>();
        for (Map.Entry<Key, Integer> e : counts.entrySet()) {
            if (e.getValue() > 0 && e.getKey().contestId().equals(contestId)) {
                out.add(e.getKey().username());
            }
        }
        return out;
    }

    private void drop(String ref, String sessionId) {
        Key key = bySubscription.remove(ref);
        if (key == null) {
            return;
        }
        Set<String> refs = bySession.get(sessionId);
        if (refs != null) {
            refs.remove(ref);
            if (refs.isEmpty()) {
                bySession.remove(sessionId);
            }
        }
        counts.merge(key, -1, Integer::sum);
        if (counts.getOrDefault(key, 0) <= 0) {
            counts.remove(key);
        }
    }

    private static String ref(String sessionId, String subscriptionId) {
        return sessionId + "|" + subscriptionId;
    }
}
