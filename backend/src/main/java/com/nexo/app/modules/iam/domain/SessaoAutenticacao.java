package com.nexo.app.modules.iam.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Sessão de um aparelho, renovável por refresh token rotativo.
 *
 * <p>Não estende {@code BaseEntity} de propósito: é estado técnico de segurança, renovado
 * a cada hora por aparelho. A auditoria JSONB gravaria um log por renovação, com o hash do
 * token no snapshot.</p>
 *
 * <p>A rotação aceita o token imediatamente anterior como retentativa. Em campo a resposta
 * da renovação se perde com frequência: o servidor já girou o token, o aparelho não recebeu
 * o novo e volta com o antigo. Se o anterior chega, o atual nunca foi apresentado (quem o
 * recebeu, se alguém recebeu, nunca o usou), então é seguro emitir outro no lugar dele.
 * Um token mais antigo que o anterior só aparece se alguém o copiou: a sessão é revogada.</p>
 */
@Entity
@Table(name = "sessoes_autenticacao")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SessaoAutenticacao {

    public static final String MOTIVO_LOGOUT = "LOGOUT";
    public static final String MOTIVO_REUSO = "TOKEN_REUTILIZADO";
    public static final String MOTIVO_USUARIO_INATIVO = "USUARIO_INATIVO";
    public static final String MOTIVO_SENHA_ALTERADA = "SENHA_ALTERADA";

    /** Como um segredo apresentado se relaciona com os tokens desta sessão. */
    public enum Apresentacao { ATUAL, ANTERIOR, DESCONHECIDO }

    @Id
    private UUID id;

    @Version
    private Long versao;

    @Column(name = "id_usuario", nullable = false, updatable = false)
    private UUID usuarioId;

    @Column(name = "hash_token_atual", nullable = false, length = 64)
    private String hashTokenAtual;

    @Column(name = "hash_token_anterior", length = 64)
    private String hashTokenAnterior;

    @Column(name = "dispositivo", length = 255)
    private String dispositivo;

    @Column(name = "criada_em", nullable = false, updatable = false)
    private OffsetDateTime criadaEm;

    @Column(name = "ultima_renovacao_em", nullable = false)
    private OffsetDateTime ultimaRenovacaoEm;

    @Column(name = "expira_em", nullable = false)
    private OffsetDateTime expiraEm;

    @Column(name = "expira_em_maximo", nullable = false, updatable = false)
    private OffsetDateTime expiraEmMaximo;

    @Column(name = "revogada_em")
    private OffsetDateTime revogadaEm;

    @Column(name = "motivo_revogacao", length = 40)
    private String motivoRevogacao;

    public static SessaoAutenticacao iniciar(UUID usuarioId, String dispositivo, String hashToken,
                                             OffsetDateTime agora, Duration inatividade, Duration duracaoMaxima) {
        var sessao = new SessaoAutenticacao();
        sessao.id = UUID.randomUUID();
        sessao.usuarioId = Objects.requireNonNull(usuarioId, "Usuário é obrigatório");
        sessao.hashTokenAtual = Objects.requireNonNull(hashToken, "Hash do token é obrigatório");
        sessao.dispositivo = truncar(dispositivo);
        sessao.criadaEm = agora;
        sessao.ultimaRenovacaoEm = agora;
        sessao.expiraEmMaximo = agora.plus(duracaoMaxima);
        sessao.expiraEm = menor(agora.plus(inatividade), sessao.expiraEmMaximo);
        return sessao;
    }

    public boolean isValida(OffsetDateTime agora) {
        return revogadaEm == null && agora.isBefore(expiraEm);
    }

    /** Compara em tempo constante: o hash não deve vazar pelo tempo de resposta. */
    public Apresentacao apresentar(String hashToken) {
        if (iguais(hashTokenAtual, hashToken)) return Apresentacao.ATUAL;
        if (iguais(hashTokenAnterior, hashToken)) return Apresentacao.ANTERIOR;
        return Apresentacao.DESCONHECIDO;
    }

    /**
     * Gira o token. Apresentado o atual, ele vira o anterior; apresentado o anterior
     * (retentativa), só o atual, que nunca chegou a ser usado, é substituído.
     */
    public void renovar(Apresentacao apresentado, String novoHash, OffsetDateTime agora, Duration inatividade) {
        if (!isValida(agora)) {
            throw new IllegalStateException("Sessão encerrada não pode ser renovada");
        }
        switch (apresentado) {
            case ATUAL -> {
                this.hashTokenAnterior = this.hashTokenAtual;
                this.hashTokenAtual = Objects.requireNonNull(novoHash);
            }
            case ANTERIOR -> this.hashTokenAtual = Objects.requireNonNull(novoHash);
            case DESCONHECIDO -> throw new IllegalStateException("Token desconhecido não renova a sessão");
        }
        this.ultimaRenovacaoEm = agora;
        this.expiraEm = menor(agora.plus(inatividade), expiraEmMaximo);
    }

    public void revogar(String motivo, OffsetDateTime agora) {
        if (revogadaEm != null) return;
        this.revogadaEm = agora;
        this.motivoRevogacao = motivo;
    }

    private static boolean iguais(String esperado, String recebido) {
        if (esperado == null || recebido == null) return false;
        return MessageDigest.isEqual(
                esperado.getBytes(StandardCharsets.US_ASCII),
                recebido.getBytes(StandardCharsets.US_ASCII));
    }

    private static OffsetDateTime menor(OffsetDateTime a, OffsetDateTime b) {
        return a.isBefore(b) ? a : b;
    }

    private static String truncar(String valor) {
        if (valor == null || valor.isBlank()) return null;
        return valor.length() > 255 ? valor.substring(0, 255) : valor;
    }
}
