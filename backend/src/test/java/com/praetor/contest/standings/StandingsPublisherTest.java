package com.praetor.contest.standings;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.praetor.contest.dto.StandingsResponse;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class StandingsPublisherTest {

    private final StandingsService service = mock(StandingsService.class);
    private final PrivilegedSubscriberRegistry registry = mock(PrivilegedSubscriberRegistry.class);
    private final SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
    private final StandingsPublisher publisher = new StandingsPublisher(service, registry, messaging);

    private static final String TOPIC = "/topic/contest/1/standings";
    private static final String QUEUE = "/queue/contest/1/standings";

    @Test
    void notFrozen_broadcastsToTopicOnly() {
        StandingsResponse board = new StandingsResponse(1L, false, "t", List.of());
        when(service.snapshot(1L, false)).thenReturn(board);

        publisher.publish(1L);

        verify(messaging).convertAndSend(eq(TOPIC), eq((Object) board));
        verify(service, never()).snapshot(1L, true);              // live board never computed
        verify(messaging, never()).convertAndSendToUser(any(), any(), any());
    }

    @Test
    void frozen_frozenBoardToTopic_liveBoardToPrivilegedQueueOnly() {
        StandingsResponse frozen = new StandingsResponse(1L, true, "t", List.of());
        StandingsResponse live = new StandingsResponse(1L, true, "t2", List.of());
        when(service.snapshot(1L, false)).thenReturn(frozen);
        when(service.snapshot(1L, true)).thenReturn(live);
        when(registry.usernames(1L)).thenReturn(Set.of("admin"));

        publisher.publish(1L);

        // The FROZEN board (not the live one) is what reaches the shared topic.
        verify(messaging).convertAndSend(eq(TOPIC), eq((Object) frozen));
        verify(messaging, never()).convertAndSend(eq(TOPIC), eq((Object) live));
        // The LIVE board goes only to the privileged user's queue.
        verify(messaging).convertAndSendToUser(eq("admin"), eq(QUEUE), eq((Object) live));
    }
}
