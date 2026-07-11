package com.praetor.ws;

import com.praetor.submission.dto.SubmissionStatusEvent;
import com.praetor.submission.entity.Submission;
import com.praetor.submission.repository.SubmissionRepository;
import java.security.Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

/**
 * Replays the current submission status the moment a client subscribes to
 * {@code /user/queue/submission/{id}}. The simple broker doesn't buffer, so a status push that
 * fired between the 202 and the client's subscribe would be lost — this closes that race (the GET
 * endpoint is the other source of truth).
 */
@Component
public class SubmissionSubscribeListener {

    private static final Logger log = LoggerFactory.getLogger(SubmissionSubscribeListener.class);
    private static final String PREFIX = "/user/queue/submission/";

    private final SubmissionRepository subRepo;
    private final SimpMessagingTemplate messaging;

    public SubmissionSubscribeListener(SubmissionRepository subRepo, SimpMessagingTemplate messaging) {
        this.subRepo = subRepo;
        this.messaging = messaging;
    }

    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String destination = accessor.getDestination();
        Principal user = accessor.getUser();
        if (destination == null || user == null || !destination.startsWith(PREFIX)) {
            return;
        }
        Long id;
        try {
            id = Long.parseLong(destination.substring(PREFIX.length()));
        } catch (NumberFormatException e) {
            return;
        }
        Submission sub = subRepo.findById(id).orElse(null);
        if (sub == null) {
            return;
        }
        log.debug("replaying status of submission {} to {}", id, user.getName());
        messaging.convertAndSendToUser(user.getName(), "/queue/submission/" + id,
                new SubmissionStatusEvent(id, sub.getStatus(), sub.getVerdict()));
    }
}
