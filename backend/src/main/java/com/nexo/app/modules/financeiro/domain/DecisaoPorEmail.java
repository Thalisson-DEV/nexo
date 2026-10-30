package com.nexo.app.modules.financeiro.domain;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Um link de decisão enviado por e-mail a um aprovador.
 *
 * <p>Vale para uma solicitação, um usuário e uma decisão, dentro do prazo. O token em
 * si não fica aqui — só o hash dele —, e a solicitação e a alçada são conferidas de
 * novo no uso: o link prova quem recebeu o e-mail, não que a decisão ainda cabe.</p>
 */
@Entity
@Table(name = "financeiro_decisao_email")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DecisaoPorEmail {

    /** Quanto tempo o link vale depois do envio. */
    public static final Duration VALIDADE = Duration.ofDays(7);

    /** As decisões possíveis pelo link. */
    public enum Acao {
        APROVAR,
        REJEITAR
    }

    @Id
    private UUID id;

    @Column(name = "hash_token", nullable = false, length = 64, updatable = false)
    private String hashToken;

    @Column(name = "id_solicitacao", nullable = false, updatable = false)
    private UUID idSolicitacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_solicitacao", nullable = false, length = 20, updatable = false)
    private TipoSolicitacao tipoSolicitacao;

    @Column(name = "id_usuario", nullable = false, updatable = false)
    private UUID idUsuario;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "expira_em", nullable = false, updatable = false)
    private OffsetDateTime expiraEm;

    @Column(name = "usado_em")
    private OffsetDateTime usadoEm;

    @Enumerated(EnumType.STRING)
    @Column(name = "acao", length = 20)
    private Acao acao;

    /** Dois cliques simultâneos no mesmo link: só um grava. */
    @Version
    @Column(name = "versao")
    private Long versao;

    public DecisaoPorEmail(String hashToken, SolicitacaoBase solicitacao, UUID idUsuario, OffsetDateTime agora) {
        this.id = UUID.randomUUID();
        this.hashToken = Objects.requireNonNull(hashToken, "O hash do token é obrigatório.");
        Objects.requireNonNull(solicitacao, "A solicitação é obrigatória.");
        this.idSolicitacao = solicitacao.getId();
        this.tipoSolicitacao = solicitacao.getTipoSolicitacao();
        this.idUsuario = Objects.requireNonNull(idUsuario, "O destinatário é obrigatório.");
        this.criadoEm = Objects.requireNonNull(agora);
        this.expiraEm = agora.plus(VALIDADE);
    }

    public boolean expirado(OffsetDateTime agora) {
        return !agora.isBefore(expiraEm);
    }

    public boolean usado() {
        return usadoEm != null;
    }

    /** Consome o link. Depois disso ele só informa o que foi decidido por ele. */
    public void usar(Acao acao, OffsetDateTime agora) {
        if (usado()) {
            throw new InvarianteVioladaException("Este link já foi usado para uma decisão.");
        }
        if (expirado(agora)) {
            throw new InvarianteVioladaException("Este link expirou.");
        }
        this.acao = Objects.requireNonNull(acao, "Informe a decisão.");
        this.usadoEm = agora;
    }
}
