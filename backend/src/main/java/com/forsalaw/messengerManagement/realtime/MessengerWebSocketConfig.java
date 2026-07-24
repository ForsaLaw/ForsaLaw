package com.forsalaw.messengerManagement.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class MessengerWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;
    private final MessengerStompChannelInterceptor stompChannelInterceptor;
    private final WebSocketCookieHandshakeInterceptor cookieHandshakeInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Handshake ouvert (permitAll cote SecurityConfig) ; l'authentification JWT se fait
        // sur la frame STOMP CONNECT (StompAuthChannelInterceptor), plus dans l'URL du handshake.
        // L'intercepteur de handshake ne fait que recopier le cookie HttpOnly dans les attributs
        // de session, pour que la frame CONNECT puisse etre authentifiee sans JWT visible en JS.
        registry.addEndpoint("/ws")
                .addInterceptors(cookieHandshakeInterceptor)
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Ordre important : authentification (frame CONNECT) d'abord, puis controle d'acces (SUBSCRIBE).
        registration.interceptors(stompAuthChannelInterceptor, stompChannelInterceptor);
    }
}
