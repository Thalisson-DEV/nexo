package com.nexo.app.shared.infrastructure.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class TokenService {

    /** Claim com o id da sessão do aparelho que emitiu o token de acesso. */
    public static final String CLAIM_SESSAO = "sid";

    @Value("${ces.security.jwt.secret}")
    private String secret;
    @Value("${ces.security.jwt.expiration}")
    private Integer expirationTime;

    private static final String ISSUER = "ces-api";

    public String gerarToken(UUID userId, String nome, UUID equipeId, UUID estoqueId, List<String> roles,
                             String tipoUsuario, UUID sessaoId) {
        Algorithm algoritmo = Algorithm.HMAC256(secret);
        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject(userId.toString())
                .withClaim("nome", nome)
                .withClaim("equipeId", equipeId != null ? equipeId.toString() : null)
                .withClaim("estoqueId", estoqueId != null ? estoqueId.toString() : null)
                .withClaim("roles", roles)
                .withClaim("tipoUsuario", tipoUsuario)
                .withClaim(CLAIM_SESSAO, sessaoId != null ? sessaoId.toString() : null)
                .withIssuedAt(Instant.now())
                .withExpiresAt(dataExpiracao())
                .sign(algoritmo);
    }

    public String getSubject(String tokenJWT) {
        return verificar(tokenJWT).getSubject();
    }

    /** Sessão do aparelho que emitiu o token; vazio em tokens anteriores às sessões. */
    public Optional<UUID> getSessaoId(String tokenJWT) {
        String sid = verificar(tokenJWT).getClaim(CLAIM_SESSAO).asString();
        return sid == null ? Optional.empty() : Optional.of(UUID.fromString(sid));
    }

    private DecodedJWT verificar(String tokenJWT) {
        Algorithm algoritmo = Algorithm.HMAC256(secret);
        return JWT.require(algoritmo)
                .withIssuer(ISSUER)
                .build()
                .verify(tokenJWT);
    }

    /*
     * Instant, não LocalDateTime com offset fixo: o servidor roda em UTC, e somar minutos a
     * um horário local interpretado como -03:00 estendia o token em 3 horas.
     */
    private Instant dataExpiracao() {
        return Instant.now().plus(Duration.ofMinutes(expirationTime));
    }
}
