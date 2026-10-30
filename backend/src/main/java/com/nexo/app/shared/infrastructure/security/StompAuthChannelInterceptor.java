package com.nexo.app.shared.infrastructure.security;

import java.util.List;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.auth0.jwt.exceptions.JWTVerificationException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Dá identidade à sessão STOMP, para que o servidor possa endereçar mensagens a um
 * usuário específico ({@code /user/queue/...}).
 *
 * <p>O filtro HTTP de segurança não resolve isso: o handshake do SockJS não carrega
 * o cabeçalho {@code Authorization}, e é o frame CONNECT do STOMP que traz o token.
 * Daí a autenticação acontecer aqui, no canal de entrada.</p>
 *
 * <p>Conexão sem token continua válida, apenas anônima: sessão anônima simplesmente
 * nunca é destino de fila privada.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String CABECALHO_AUTORIZACAO = "Authorization";
    private static final String PREFIXO_BEARER = "Bearer ";

    private final TokenService tokenService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        String token = extrairToken(accessor);
        if (token == null) {
            return message;
        }

        try {
            String idUsuario = tokenService.getSubject(token);
            // O nome do Principal é o id do usuário — é ele que o push usa em
            // convertAndSendToUser, então os dois lados precisam falar a mesma chave.
            accessor.setUser(new UsernamePasswordAuthenticationToken(idUsuario, null, List.of()));
        } catch (JWTVerificationException ex) {
            log.debug("CONNECT STOMP recebido com token inválido; a sessão segue anônima.");
        }

        return message;
    }

    private String extrairToken(StompHeaderAccessor accessor) {
        String cabecalho = accessor.getFirstNativeHeader(CABECALHO_AUTORIZACAO);
        if (!StringUtils.hasText(cabecalho)) {
            return null;
        }
        String token = cabecalho.startsWith(PREFIXO_BEARER)
                ? cabecalho.substring(PREFIXO_BEARER.length())
                : cabecalho;
        return StringUtils.hasText(token) ? token.trim() : null;
    }
}
