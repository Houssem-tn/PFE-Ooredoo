package com.pfe.config;

import com.pfe.Security.WebSocketAuthChannelInterceptor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationEventPublisher;
import org.springframework.security.authorization.SpringAuthorizationEventPublisher;
import org.springframework.security.messaging.access.intercept.AuthorizationChannelInterceptor;
import org.springframework.security.messaging.context.SecurityContextChannelInterceptor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthChannelInterceptor webSocketAuthChannelInterceptor;
    private final AuthorizationManager<Message<?>> messageAuthorizationManager;
    private final ApplicationContext applicationContext;

    public WebSocketConfig(WebSocketAuthChannelInterceptor webSocketAuthChannelInterceptor,
                           AuthorizationManager<Message<?>> messageAuthorizationManager,
                           ApplicationContext applicationContext) {
        this.webSocketAuthChannelInterceptor = webSocketAuthChannelInterceptor;
        this.messageAuthorizationManager = messageAuthorizationManager;
        this.applicationContext = applicationContext;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Native WebSocket endpoint
        registry.addEndpoint("/ws-notifications")
                .setAllowedOriginPatterns(
                        "http://localhost:4200",
                        "http://127.0.0.1:4200"
                );

        // Optional: keep SockJS fallback on a separate endpoint
        registry.addEndpoint("/ws-notifications-sockjs")
                .setAllowedOriginPatterns(
                        "http://localhost:4200",
                        "http://127.0.0.1:4200"
                )
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        AuthorizationChannelInterceptor authz =
                new AuthorizationChannelInterceptor(messageAuthorizationManager);

        AuthorizationEventPublisher publisher =
                new SpringAuthorizationEventPublisher(applicationContext);

        authz.setAuthorizationEventPublisher(publisher);

        registration.interceptors(
                webSocketAuthChannelInterceptor,
                new SecurityContextChannelInterceptor(),
                authz
        );
    }
}