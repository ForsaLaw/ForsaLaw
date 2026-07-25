package com.forsalaw.messengerManagement.realtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Slf4j
public class MessengerWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;
    private final MessengerStompChannelInterceptor stompChannelInterceptor;
    private final WebSocketCookieHandshakeInterceptor cookieHandshakeInterceptor;

    /**
     * false => broker en memoire (dev, tests, instance unique).
     * true  => relais vers un broker STOMP externe (RabbitMQ), indispensable des qu'il y a
     * plusieurs instances : avec le broker en memoire, un message publie par l'instance A
     * n'atteint jamais un client connecte a l'instance B.
     */
    @Value("${forsalaw.websocket.broker.relay-enabled:false}")
    private boolean relayEnabled;

    @Value("${forsalaw.websocket.broker.host:localhost}")
    private String relayHost;

    @Value("${forsalaw.websocket.broker.port:61613}")
    private int relayPort;

    @Value("${forsalaw.websocket.broker.virtual-host:/}")
    private String virtualHost;

    @Value("${forsalaw.websocket.broker.client-login:forsalaw}")
    private String clientLogin;

    @Value("${forsalaw.websocket.broker.client-passcode:forsalaw}")
    private String clientPasscode;

    @Value("${forsalaw.websocket.broker.system-login:forsalaw}")
    private String systemLogin;

    @Value("${forsalaw.websocket.broker.system-passcode:forsalaw}")
    private String systemPasscode;

    @Value("${forsalaw.websocket.broker.heartbeat-ms:10000}")
    private long heartbeatMs;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");

        if (!relayEnabled) {
            registry.enableSimpleBroker("/topic");
            log.info("WebSocket : broker STOMP en memoire (mono-instance).");
            return;
        }

        // Attention : le relais mappe /topic/X sur l'echange amq.topic avec X comme CLE DE
        // ROUTAGE. Nos destinations sont des chaines exactes sans joker, donc le comportement
        // est identique au broker en memoire ; un futur abonnement avec joker ne le serait pas
        // (le separateur AMQP est le point, pas le slash).
        registry.enableStompBrokerRelay("/topic")
                .setRelayHost(relayHost)
                .setRelayPort(relayPort)
                .setVirtualHost(virtualHost)
                .setClientLogin(clientLogin)
                .setClientPasscode(clientPasscode)
                .setSystemLogin(systemLogin)
                .setSystemPasscode(systemPasscode)
                .setSystemHeartbeatSendInterval(heartbeatMs)
                .setSystemHeartbeatReceiveInterval(heartbeatMs);

        log.info("WebSocket : relais STOMP externe vers {}:{} (vhost {}).", relayHost, relayPort, virtualHost);
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
