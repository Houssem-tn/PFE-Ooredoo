package com.pfe.Security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final UserDetailsServiceImpl userDetailsService;

    public WebSocketAuthChannelInterceptor(JwtService jwtService,
                                           UserDetailsServiceImpl userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                throw new IllegalArgumentException("Missing or invalid Authorization header");
            }

            String token = authHeader.substring(7);

            if (!jwtService.isValid(token)) {
                throw new IllegalArgumentException("Invalid JWT token");
            }

            String email = jwtService.extractSubject(token);
            if (email == null) {
                throw new IllegalArgumentException("JWT subject is missing");
            }

            UserDetails userDetails = userDetailsService.loadUserByUsername(email);

            // Consistency with JwtAuthFilter (REST): a STOMP token whose version
            // is behind the live account (e.g. after a password reset) must be
            // rejected so an old WebSocket connection cannot be (re)established
            // with a stale JWT.
            if (userDetails instanceof AppUserDetails appUserDetails
                    && jwtService.extractTokenVersion(token) != appUserDetails.getTokenVersion()) {
                throw new IllegalArgumentException("Invalid JWT token");
            }

            // Consistency with JwtAuthFilter (REST): a disabled/banned account
            // must not be able to open a WebSocket session either, even with
            // an otherwise-valid JWT issued before the ban.
            if (!userDetails.isEnabled()) {
                throw new IllegalArgumentException("Account is disabled");
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );

            log.debug("WS CONNECT authenticated user={}", email);

            accessor.setUser(authentication);
        }

        return message;
    }
}