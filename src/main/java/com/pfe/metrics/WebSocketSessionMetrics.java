package com.pfe.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks currently-open STOMP sessions on both endpoints WebSocketConfig
 * registers (/ws-notifications and /ws-notifications-sockjs — Spring
 * publishes the same SessionConnectedEvent/SessionDisconnectEvent for
 * either transport, so one listener covers both). No per-session/user
 * label — just a single gauge, since a per-user label would be
 * unbounded/identifying (see BusinessMetrics' own class-level comment on
 * why labels stay bounded).
 */
@Component
public class WebSocketSessionMetrics {

    private final AtomicInteger activeSessions = new AtomicInteger(0);

    public WebSocketSessionMetrics(MeterRegistry registry) {
        Gauge.builder("websocket_active_sessions", activeSessions, AtomicInteger::get)
                .description("STOMP sessions currently connected to /ws-notifications or /ws-notifications-sockjs.")
                .register(registry);
    }

    @EventListener
    public void onConnect(SessionConnectedEvent event) {
        activeSessions.incrementAndGet();
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        activeSessions.updateAndGet(current -> Math.max(0, current - 1));
    }
}
