package com.forsalaw.messengerManagement.realtime;

import com.forsalaw.security.JwtService;
import com.forsalaw.userManagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * Authentifie la connexion WebSocket au niveau de la frame STOMP CONNECT.
 * Le JWT est lu dans l'en-tete natif "Authorization: Bearer &lt;token&gt;" (et non plus dans
 * l'URL de handshake : evite la fuite du token dans les logs/historique du proxy).
 * Le {@link java.security.Principal} etabli ici via {@code setUser} est reutilise par Spring
 * pour les frames SUBSCRIBE/SEND suivantes de la meme session.
 */
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        // Source principale : cookie HttpOnly capture au handshake (le JS ne peut plus lire le JWT).
        // Repli : en-tete Authorization de la frame CONNECT (clients non navigateur / tests).
        String token = tokenFromHandshakeCookie(accessor);
        if (token == null) {
            token = extractBearer(accessor.getFirstNativeHeader("Authorization"));
        }
        if (token == null || !jwtService.isTokenValid(token)) {
            throw new IllegalArgumentException("Token JWT WebSocket invalide ou absent.");
        }

        String email = jwtService.extractEmail(token);
        var userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty() || !userOpt.get().isActif()) {
            throw new IllegalArgumentException("Compte introuvable ou inactif.");
        }

        String role = jwtService.extractRole(token);
        SimpleGrantedAuthority authority =
                new SimpleGrantedAuthority("ROLE_" + (role == null ? "" : role.toUpperCase()));
        UsernamePasswordAuthenticationToken principal = new UsernamePasswordAuthenticationToken(
                email, null, Collections.singletonList(authority));
        accessor.setUser(principal);
        return message;
    }

    /** JWT depose dans les attributs de session par {@link WebSocketCookieHandshakeInterceptor}. */
    private String tokenFromHandshakeCookie(StompHeaderAccessor accessor) {
        var attributes = accessor.getSessionAttributes();
        if (attributes == null) {
            return null;
        }
        Object token = attributes.get(WebSocketCookieHandshakeInterceptor.JWT_SESSION_ATTRIBUTE);
        return token instanceof String s && !s.isBlank() ? s : null;
    }

    private String extractBearer(String header) {
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
