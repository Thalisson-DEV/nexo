package com.nexo.app.modules.financeiro.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Um ponto da linha do tempo de uma compra ou pagamento.
 *
 * <p>Existe porque as colunas de decisão da solicitação guardam só o estado atual:
 * desfazer uma aprovação as apaga. Aqui fica o que aconteceu, na ordem em que
 * aconteceu, sem nunca ser reescrito — corrigir uma decisão é registrar outra.</p>
 */
@Entity
@Table(name = "financeiro_solicitacao_evento")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventoSolicitacaoFinanceira {

    @Id
    private UUID id;

    @Column(name = "id_solicitacao", nullable = false, updatable = false)
    private UUID idSolicitacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_solicitacao", nullable = false, length = 20, updatable = false)
    private TipoSolicitacao tipoSolicitacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 30, updatable = false)
    private TipoEventoSolicitacao tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_anterior", length = 20, updatable = false)
    private StatusSolicitacao statusAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_novo", nullable = false, length = 20, updatable = false)
    private StatusSolicitacao statusNovo;

    /** Quem fez a transição; nulo apenas em registros reconstruídos sem autor. */
    @Column(name = "id_usuario", updatable = false)
    private UUID idUsuario;

    /** Valor em jogo: o solicitado na abertura e no envio, o autorizado na aprovação. */
    @Column(name = "valor", precision = 14, scale = 2, updatable = false)
    private BigDecimal valor;

    /** Observação da aprovação ou motivo da rejeição, do cancelamento ou do desfazer. */
    @Column(name = "texto", columnDefinition = "TEXT", updatable = false)
    private String texto;

    @Column(name = "ocorrido_em", nullable = false, updatable = false)
    private OffsetDateTime ocorridoEm;

    public EventoSolicitacaoFinanceira(SolicitacaoBase solicitacao, TipoEventoSolicitacao tipo,
                                       StatusSolicitacao statusAnterior, UUID idUsuario,
                                       BigDecimal valor, String texto) {
        Objects.requireNonNull(solicitacao, "A solicitação é obrigatória.");
        this.id = UUID.randomUUID();
        this.idSolicitacao = Objects.requireNonNull(solicitacao.getId(), "A solicitação precisa estar salva.");
        this.tipoSolicitacao = solicitacao.getTipoSolicitacao();
        this.tipo = Objects.requireNonNull(tipo, "O tipo do evento é obrigatório.");
        this.statusAnterior = statusAnterior;
        this.statusNovo = solicitacao.getStatus();
        this.idUsuario = idUsuario;
        this.valor = valor;
        this.texto = StringUtils.hasText(texto) ? texto.trim() : null;
        this.ocorridoEm = OffsetDateTime.now();
    }
}
