package com.nexo.app.modules.iam.application.usecases;

import com.nexo.app.modules.iam.domain.SessaoAutenticacao;
import com.nexo.app.modules.iam.domain.SessaoAutenticacao.Apresentacao;
import com.nexo.app.shared.domain.exception.SessaoInvalidaException;
import com.nexo.app.modules.iam.infrastructure.persistence.SessaoAutenticacaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Ciclo de vida das sessões por aparelho: abrir no login, girar o refresh token na
 * renovação, encerrar no logout e revogar quando a credencial muda.
 *
 * <p>O refresh token tem a forma {@code <idSessao>.<segredo>}. O id localiza a linha sem
 * varrer a tabela; só o SHA-256 do segredo é gravado. SHA-256 sem sal basta aqui porque o
 * segredo tem 256 bits aleatórios: não há dicionário para atacar, ao contrário de senhas.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessaoAutenticacaoUseCases {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int BYTES_SEGREDO = 32;

    private final SessaoAutenticacaoRepository repository;

    @Value("${ces.security.sessao.inatividade-dias:30}")
    private int inatividadeDias;

    @Value("${ces.security.sessao.duracao-maxima-dias:90}")
    private int duracaoMaximaDias;

    /** Token recém-emitido; o segredo em claro só existe neste objeto, a caminho do cookie. */
    public record SessaoEmitida(UUID sessaoId, UUID usuarioId, String refreshToken, OffsetDateTime expiraEm) {}

    @Transactional
    public SessaoEmitida iniciar(UUID usuarioId, String dispositivo) {
        String segredo = novoSegredo();
        var sessao = SessaoAutenticacao.iniciar(usuarioId, dispositivo, hash(segredo),
                OffsetDateTime.now(), inatividade(), Duration.ofDays(duracaoMaximaDias));
        repository.save(sessao);
        return emitida(sessao, segredo);
    }

    /**
     * Gira o refresh token. Roda em transação própria: a revogação por reuso precisa ser
     * gravada mesmo que quem chamou lance a exceção logo depois.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = SessaoInvalidaException.class)
    public SessaoEmitida renovar(String refreshToken) {
        var partes = separar(refreshToken)
                .orElseThrow(() -> new SessaoInvalidaException("Sessão não encontrada. Entre novamente."));
        var agora = OffsetDateTime.now();

        var sessao = repository.buscarParaRenovar(partes.sessaoId())
                .orElseThrow(() -> new SessaoInvalidaException("Sessão não encontrada. Entre novamente."));

        if (!sessao.isValida(agora)) {
            throw new SessaoInvalidaException("Sua sessão expirou. Entre novamente.");
        }

        Apresentacao apresentado = sessao.apresentar(hash(partes.segredo()));
        if (apresentado == Apresentacao.DESCONHECIDO) {
            sessao.revogar(SessaoAutenticacao.MOTIVO_REUSO, agora);
            log.warn("Refresh token antigo reapresentado; sessão {} do usuário {} revogada.",
                    sessao.getId(), sessao.getUsuarioId());
            throw new SessaoInvalidaException("Sessão encerrada por segurança. Entre novamente.");
        }

        String segredo = novoSegredo();
        sessao.renovar(apresentado, hash(segredo), agora, inatividade());
        return emitida(sessao, segredo);
    }

    /** Logout: encerra a sessão do token apresentado. Token inválido não é erro. */
    @Transactional
    public void encerrar(String refreshToken) {
        separar(refreshToken)
                .flatMap(partes -> repository.findById(partes.sessaoId())
                        .filter(sessao -> sessao.apresentar(hash(partes.segredo())) != Apresentacao.DESCONHECIDO))
                .ifPresent(sessao -> sessao.revogar(SessaoAutenticacao.MOTIVO_LOGOUT, OffsetDateTime.now()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revogar(UUID sessaoId, String motivo) {
        repository.findById(sessaoId).ifPresent(sessao -> sessao.revogar(motivo, OffsetDateTime.now()));
    }

    /** Revoga as sessões do usuário, preservando opcionalmente a do aparelho que pediu. */
    @Transactional
    public int revogarTodas(UUID usuarioId, UUID exceto, String motivo) {
        var agora = OffsetDateTime.now();
        var sessoes = repository.buscarAtivasDoUsuario(usuarioId).stream()
                .filter(sessao -> !sessao.getId().equals(exceto))
                .toList();
        sessoes.forEach(sessao -> sessao.revogar(motivo, agora));
        return sessoes.size();
    }

    private SessaoEmitida emitida(SessaoAutenticacao sessao, String segredo) {
        return new SessaoEmitida(sessao.getId(), sessao.getUsuarioId(),
                sessao.getId() + "." + segredo, sessao.getExpiraEm());
    }

    private Duration inatividade() {
        return Duration.ofDays(inatividadeDias);
    }

    private record Partes(UUID sessaoId, String segredo) {}

    private static Optional<Partes> separar(String refreshToken) {
        if (refreshToken == null) return Optional.empty();
        int ponto = refreshToken.indexOf('.');
        if (ponto <= 0 || ponto == refreshToken.length() - 1) return Optional.empty();
        try {
            return Optional.of(new Partes(
                    UUID.fromString(refreshToken.substring(0, ponto)),
                    refreshToken.substring(ponto + 1)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static String novoSegredo() {
        byte[] bytes = new byte[BYTES_SEGREDO];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String segredo) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(segredo.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM", e);
        }
    }
}
