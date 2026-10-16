package ma.crm.config;

import lombok.RequiredArgsConstructor;
import ma.crm.security.JwtAuthFilter;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.security.Principal;

/**
 * Notifications temps réel (STOMP sur WebSocket). Le client s'abonne à /user/queue/notifications ;
 * l'authentification se fait à la trame CONNECT avec l'en-tête « Authorization: Bearer ... ».
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtAuthFilter jwtAuthFilter;
    private final AppProperties props;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOrigins(props.cors().allowedOrigins().toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue", "/topic");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null) {
                    return message;
                }
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String header = accessor.getFirstNativeHeader("Authorization");
                    String token = header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
                    Authentication auth = token == null ? null : jwtAuthFilter.resolve(token)
                            .map(u -> (Authentication) new UsernamePasswordAuthenticationToken(u.email(), null, u.getAuthorities()))
                            .orElse(null);
                    if (auth == null) {
                        throw new IllegalArgumentException("Jeton invalide");
                    }
                    accessor.setUser(auth);
                } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    // Seules les files personnelles sont autorisées : un utilisateur ne peut pas écouter celles des autres.
                    Principal user = accessor.getUser();
                    String destination = accessor.getDestination();
                    if (user == null || destination == null || !destination.startsWith("/user/queue/")) {
                        throw new IllegalArgumentException("Abonnement refusé");
                    }
                }
                return message;
            }
        });
    }
}
