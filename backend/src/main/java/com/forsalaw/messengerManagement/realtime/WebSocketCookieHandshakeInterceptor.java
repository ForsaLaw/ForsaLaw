package com.forsalaw.messengerManagement.realtime;

import com.forsalaw.security.JwtCookieService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Recopie le JWT du cookie HttpOnly dans les attributs de session WebSocket lors du handshake.
 *
 * <p>Depuis le passage au cookie HttpOnly, le JavaScript ne peut plus lire le JWT et donc plus
 * le placer dans l'en-tete {@code Authorization} de la frame STOMP CONNECT. Le navigateur, lui,
 * envoie automatiquement le cookie lors du handshake HTTP : on le capture ici pour que
 * {@link StompAuthChannelInterceptor} puisse authentifier la frame CONNECT.</p>
 *
 * <p>Cet intercepteur n'authentifie rien et ne refuse jamais le handshake (contrairement a
 * l'ancien JwtHandshakeInterceptor base sur l'URL) : la validation du JWT reste centralisee
 * sur la frame CONNECT.</p>
 */
@Component
@RequiredArgsConstructor
public class WebSocketCookieHandshakeInterceptor implements HandshakeInterceptor {

    /** Cle sous laquelle le JWT du cookie est expose aux attributs de session STOMP. */
    public static final String JWT_SESSION_ATTRIBUTE = "forsalawJwt";

    private final JwtCookieService jwtCookieService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            jwtCookieService.readToken(servletRequest.getServletRequest())
                    .ifPresent(token -> attributes.put(JWT_SESSION_ATTRIBUTE, token));
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // rien
    }
}
