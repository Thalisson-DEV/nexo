package com.nexo.app.shared.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.nexo.app.shared.infrastructure.security.StompAuthChannelInterceptor;

import lombok.RequiredArgsConstructor;

/**
 * Transporte WebSocket/STOMP da aplicação.
 *
 * <p>Vive no compartilhado porque é transporte, não regra de módulo: hoje as
 * notificações entregam em {@code /user/queue/notificacoes} (fila privada por usuário).</p>
 *
 * <p>O broker é o simples, em memória. Isso basta para uma instância; com mais de
 * uma, cada nó só alcança os clientes conectados a ele — o que a caixa de entrada
 * tolera, já que o push é conveniência e o estado real está no banco, acessível por
 * {@code GET /v1/notificacoes/resumo}. Escalar horizontalmente com push confiável
 * exigiria um relay externo (RabbitMQ/ActiveMQ).</p>
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

    @Value("${ces.cors.allowed-origins}")
    private String allowedOrigins;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // /topic para difusão; /queue para o que é dirigido a um único usuário.
        registry.enableSimpleBroker("/topic", "/queue");

        // Mensagens do Angular para o servidor usariam /app — não usamos por ora
        registry.setApplicationDestinationPrefixes("/app");

        // Traduz /user/queue/... para a sessão cujo Principal corresponde ao destinatário.
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOrigins.split(","))
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }
}
