package com.praetor.contest.standings;

import com.praetor.contest.dto.StandingsResponse;
import com.praetor.identity.entity.User;
import java.security.Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

/**
 * Wires the privileged live-standings channel {@code /user/queue/contest/{id}/standings}.
 *
 * <p>When a PRIVILEGED user (ADMIN/PROBLEM_SETTER) subscribes, we (1) register them in the
 * {@link PrivilegedSubscriberRegistry} so the publisher pushes them the live board during a freeze,
 * and (2) immediately send the current live snapshot to the same destination — closing the
 * subscribe/first-push race (the simple broker has no buffer, and during a freeze the live board is
 * never broadcast to a topic, so a late subscriber would otherwise see nothing until the next
 * judged submission).
 *
 * <p>A NON-privileged subscriber to this queue is ignored entirely: not registered, sent nothing —
 * the live board must never reach a contestant. Contestants use the broadcast
 * {@code /topic/contest/{id}/standings} (frozen during a freeze) plus the GET snapshot.
 *
 * <p>Role is read straight off the authenticated principal ({@code User.getRole()} = {@code USER} /
 * {@code ADMIN} / {@code PROBLEM_SETTER}) — NOT the Spring authorities, which are {@code ROLE_}-
 * prefixed and would mis-classify everyone.
 */
@Component
public class StandingsSubscribeListener {

    private static final Logger log = LoggerFactory.getLogger(StandingsSubscribeListener.class);
    private static final String PREFIX = "/user/queue/contest/";
    private static final String SUFFIX = "/standings";

    private final PrivilegedSubscriberRegistry registry;
    private final StandingsService standingsService;
    private final SimpMessagingTemplate messaging;

    public StandingsSubscribeListener(PrivilegedSubscriberRegistry registry,
                                      StandingsService standingsService, SimpMessagingTemplate messaging) {
        this.registry = registry;
        this.standingsService = standingsService;
        this.messaging = messaging;
    }

    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Long contestId = contestIdOf(accessor.getDestination());
        if (contestId == null) {
            return;
        }
        User user = principalUser(accessor.getUser());
        if (user == null || isPlainUser(user)) {
            return; // anonymous or contestant → no live channel
        }
        registry.add(accessor.getSessionId(), accessor.getSubscriptionId(), contestId, user.getUsername());
        StandingsResponse live = standingsService.snapshot(contestId, true);
        log.debug("live standings snapshot for contest {} to {}", contestId, user.getUsername());
        messaging.convertAndSendToUser(user.getUsername(), queue(contestId), live);
    }

    @EventListener
    public void onUnsubscribe(SessionUnsubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        registry.removeSubscription(accessor.getSessionId(), accessor.getSubscriptionId());
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        registry.removeSession(event.getSessionId());
    }

    /** Parse {@code {id}} from {@code /user/queue/contest/{id}/standings}, or null if not that dest. */
    private Long contestIdOf(String destination) {
        if (destination == null || !destination.startsWith(PREFIX) || !destination.endsWith(SUFFIX)) {
            return null;
        }
        String middle = destination.substring(PREFIX.length(), destination.length() - SUFFIX.length());
        try {
            return Long.parseLong(middle);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static User principalUser(Principal principal) {
        if (principal instanceof Authentication auth && auth.getPrincipal() instanceof User user) {
            return user;
        }
        return null;
    }

    private static boolean isPlainUser(User user) {
        return "USER".equals(user.getRole());
    }

    private static String queue(Long contestId) {
        return "/queue/contest/" + contestId + SUFFIX;
    }
}
