package com.pfe.Security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;

import java.security.Principal;
import java.util.function.Supplier;

@Configuration
public class WebSocketAuthorizationConfig {

    @Bean
    public AuthorizationManager<Message<?>> messageAuthorizationManager() {
        return new AuthorizationManager<>() {
            @Override
            public AuthorizationDecision authorize(
                    Supplier<? extends Authentication> authenticationSupplier,
                    Message<?> message
            ) {
                StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
                SimpMessageType messageType = accessor.getMessageType();

                System.out.println("[WS AUTHZ] type=" + messageType + ", destination=" + accessor.getDestination());

                if (messageType == SimpMessageType.CONNECT
                        || messageType == SimpMessageType.DISCONNECT
                        || messageType == SimpMessageType.UNSUBSCRIBE
                        || messageType == SimpMessageType.HEARTBEAT) {
                    return new AuthorizationDecision(true);
                }

                Authentication authentication = null;

                Principal user = accessor.getUser();
                if (user instanceof Authentication authFromAccessor) {
                    authentication = authFromAccessor;
                }

                if (authentication == null
                        || authentication instanceof AnonymousAuthenticationToken
                        || !authentication.isAuthenticated()) {
                    Authentication fallback = authenticationSupplier.get();
                    if (fallback != null
                            && !(fallback instanceof AnonymousAuthenticationToken)
                            && fallback.isAuthenticated()) {
                        authentication = fallback;
                    }
                }

                System.out.println("[WS AUTHZ] accessorUser=" + accessor.getUser());
                System.out.println("[WS AUTHZ] authentication=" + authentication);
                if (authentication != null) {
                    System.out.println("[WS AUTHZ] authorities=" + authentication.getAuthorities());
                }

                if (authentication == null || !authentication.isAuthenticated()) {
                    return new AuthorizationDecision(false);
                }

                String destination = accessor.getDestination();

                if (destination != null &&
                        (destination.startsWith("/user/") || destination.startsWith("/queue/"))) {
                    return new AuthorizationDecision(true);
                }

                if (destination != null) {
                    if (destination.equals("/topic/notifications/ROLE_ADMIN")) {
                        return new AuthorizationDecision(hasAuthority(authentication, "ROLE_ADMIN"));
                    }
                    if (destination.equals("/topic/notifications/ROLE_STT")) {
                        return new AuthorizationDecision(hasAuthority(authentication, "ROLE_STT"));
                    }
                    if (destination.equals("/topic/notifications/ROLE_TECH")) {
                        return new AuthorizationDecision(hasAuthority(authentication, "ROLE_TECH"));
                    }
                    if (destination.equals("/topic/notifications/ROLE_ZM")) {
                        return new AuthorizationDecision(hasAuthority(authentication, "ROLE_ZM"));
                    }
                    if (destination.equals("/topic/notifications/ROLE_CRM_BOUTIQUE")) {
                        return new AuthorizationDecision(hasAuthority(authentication, "ROLE_CRM_BOUTIQUE"));
                    }
                }

                return new AuthorizationDecision(false);
            }
        };
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        return authentication.getAuthorities().stream()
                .anyMatch(granted -> authority.equals(granted.getAuthority()));
    }
}