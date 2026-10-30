package com.nexo.app.modules.financeiro.domain;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Comportamento comum às solicitações de compra e de pagamento: identificação
 * legível, vínculo com a variante de formulário usada, núcleo de dados tipados e
 * o fluxo de autorização pela diretoria.
 *
 * <p>O ciclo de vida está descrito em {@link StatusSolicitacao}. Toda transição
 * passa por um método de intenção — não há setter de status — e cada desfecho
 * grava quem decidiu e quando, para que a trilha de auditoria seja completa sem
 * depender do log genérico.</p>
 *
 * <p>Quem pode decidir é questão de permissão, não de valor: basta a authority
 * {@code financeiro:{compra|pagamento}:aprovar}, cobrada no endpoint. O domínio
 * cuida de <em>quando</em> a decisão é possível e de <em>quanto</em> pode ser
 * autorizado.</p>
 */
@MappedSuperclass
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class SolicitacaoBase extends BaseEntity {

    /**
     * Número sequencial legível, gerado pelo banco. É por ele que as equipes se
     * referem à solicitação — inclusive ao vincular um pagamento a uma compra
     * aprovada, onde pedir um UUID seria inviável no dia a dia.
     */
    @Generated(event = EventType.INSERT)
    @Column(name = "numero", nullable = false, insertable = false, updatable = false)
    private Long numero;

    /** Variante de formulário com que a solicitação foi preenchida. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "formulario_id", nullable = false)
    private FormularioSolicitacao formulario;

    @Embedded
    private NucleoSolicitacao nucleo;

    public NucleoSolicitacao getNucleo() {
        return nucleo != null ? nucleo : new NucleoSolicitacao();
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusSolicitacao status = StatusSolicitacao.RASCUNHO;

    /** Usuário autenticado que abriu a solicitação (nunca vem do payload). */
    @Column(name = "usuario_solicitante_id", nullable = false)
    private UUID usuarioSolicitanteId;

    @Column(name = "data_submissao")
    private OffsetDateTime dataSubmissao;

    // ── Decisão ─────────────────────────────────────────────────────────────────
    @Column(name = "usuario_decisao_id")
    private UUID usuarioDecisaoId;

    @Column(name = "data_decisao")
    private OffsetDateTime dataDecisao;

    /** Valor efetivamente autorizado; pode ser menor que o solicitado. */
    @Column(name = "valor_aprovado", precision = 14, scale = 2)
    private BigDecimal valorAprovado;

    @Column(name = "observacao_decisao", columnDefinition = "TEXT")
    private String observacaoDecisao;

    @Column(name = "motivo_rejeicao", columnDefinition = "TEXT")
    private String motivoRejeicao;

    // ── Cancelamento ────────────────────────────────────────────────────────────
    @Column(name = "usuario_cancelamento_id")
    private UUID usuarioCancelamentoId;

    @Column(name = "data_cancelamento")
    private OffsetDateTime dataCancelamento;

    @Column(name = "motivo_cancelamento", columnDefinition = "TEXT")
    private String motivoCancelamento;

    protected SolicitacaoBase(FormularioSolicitacao formulario, NucleoSolicitacao nucleo, UUID usuarioSolicitanteId) {
        this.formulario = Objects.requireNonNull(formulario, "O formulário da solicitação é obrigatório.");
        this.nucleo = Objects.requireNonNull(nucleo, "Os dados da solicitação são obrigatórios.");
        this.usuarioSolicitanteId = Objects.requireNonNull(usuarioSolicitanteId, "O usuário solicitante é obrigatório.");
        this.status = StatusSolicitacao.RASCUNHO;
    }

    /** Domínio a que esta solicitação pertence, usado para resolver alçadas. */
    public abstract TipoSolicitacao getTipoSolicitacao();

    /**
     * Substitui por completo os dados preenchidos. Só é possível enquanto a
     * solicitação não teve desfecho.
     */
    protected void substituirNucleo(NucleoSolicitacao novoNucleo) {
        exigirEditavel();
        this.nucleo = Objects.requireNonNull(novoNucleo, "Os dados da solicitação são obrigatórios.");
    }

    /**
     * Envia a solicitação para a fila de autorização. Reenviar o que já está
     * submetido é inofensivo e mantém a data original.
     */
    public void submeter() {
        if (status == StatusSolicitacao.SOLICITADO) {
            return;
        }
        if (status != StatusSolicitacao.RASCUNHO) {
            throw new InvarianteVioladaException(
                    "Apenas solicitações em rascunho podem ser submetidas (status atual: " + status + ").");
        }
        if (nucleo.getValor() == null || nucleo.getValor().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvarianteVioladaException("Informe um valor maior que zero antes de submeter a solicitação.");
        }
        this.status = StatusSolicitacao.SOLICITADO;
        this.dataSubmissao = OffsetDateTime.now();
    }

    /**
     * Autoriza a solicitação. O valor autorizado nunca pode superar o solicitado:
     * aprovar mais do que se pediu é decidir sobre algo que ninguém apresentou, e o
     * caminho correto para o excedente é uma nova solicitação.
     *
     * @param valorAprovado valor autorizado; nulo assume o valor solicitado
     */
    public void aprovar(UUID usuarioDecisaoId, BigDecimal valorAprovado, String observacao) {
        exigirAguardandoDecisao("aprovada");
        Objects.requireNonNull(usuarioDecisaoId, "O usuário que aprova é obrigatório.");

        BigDecimal autorizado = valorAprovado != null ? valorAprovado : nucleo.getValor();
        if (autorizado == null || autorizado.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvarianteVioladaException("O valor aprovado deve ser maior que zero.");
        }
        if (autorizado.compareTo(nucleo.valorOuZero()) > 0) {
            throw new InvarianteVioladaException(
                    "O valor aprovado não pode superar o valor solicitado. Abra uma nova solicitação para o excedente.");
        }

        this.status = StatusSolicitacao.APROVADO;
        this.valorAprovado = autorizado;
        this.usuarioDecisaoId = usuarioDecisaoId;
        this.dataDecisao = OffsetDateTime.now();
        this.observacaoDecisao = normalizar(observacao);
    }

    public void rejeitar(UUID usuarioDecisaoId, String motivo) {
        exigirAguardandoDecisao("rejeitada");
        Objects.requireNonNull(usuarioDecisaoId, "O usuário que rejeita é obrigatório.");
        if (!StringUtils.hasText(motivo)) {
            throw new InvarianteVioladaException("Informe o motivo da rejeição.");
        }
        this.status = StatusSolicitacao.REJEITADO;
        this.motivoRejeicao = motivo.trim();
        this.usuarioDecisaoId = usuarioDecisaoId;
        this.dataDecisao = OffsetDateTime.now();
    }

    /**
     * Devolve à fila de decisão uma solicitação aprovada ou rejeitada — o erro de
     * quem decidiu, ou um fato novo que muda a decisão. Volta ao status em que
     * estava antes de ser decidida, com as colunas de decisão limpas; o que elas
     * diziam segue no histórico.
     *
     * @return o que havia antes, para o histórico e para avisar quem tinha decidido
     */
    public DecisaoDesfeita desfazerDecisao(String motivo) {
        if (status != StatusSolicitacao.APROVADO && status != StatusSolicitacao.REJEITADO) {
            throw new InvarianteVioladaException(
                    "Só é possível desfazer a decisão de uma solicitação aprovada ou rejeitada (status atual: "
                            + status + ").");
        }
        if (!StringUtils.hasText(motivo)) {
            throw new InvarianteVioladaException("Informe por que a decisão está sendo desfeita.");
        }
        var anterior = new DecisaoDesfeita(status, usuarioDecisaoId, valorAprovado);
        this.status = StatusSolicitacao.SOLICITADO;
        this.valorAprovado = null;
        this.usuarioDecisaoId = null;
        this.dataDecisao = null;
        this.observacaoDecisao = null;
        this.motivoRejeicao = null;
        return anterior;
    }

    /** Desistência do solicitante, possível enquanto não houver decisão. */
    public void cancelar(UUID usuarioCancelamentoId, String motivo) {
        if (status.isFinalizado()) {
            throw new InvarianteVioladaException(
                    "A solicitação já está " + status + " e não pode ser cancelada.");
        }
        if (!StringUtils.hasText(motivo)) {
            throw new InvarianteVioladaException("Informe o motivo do cancelamento.");
        }
        this.status = StatusSolicitacao.CANCELADO;
        this.motivoCancelamento = motivo.trim();
        this.usuarioCancelamentoId = usuarioCancelamentoId;
        this.dataCancelamento = OffsetDateTime.now();
    }

    /** Valor que vale para o negócio: o autorizado, ou o solicitado se ainda não houve decisão. */
    public BigDecimal valorVigente() {
        return valorAprovado != null ? valorAprovado : nucleo.valorOuZero();
    }

    public boolean isAprovada() {
        return status == StatusSolicitacao.APROVADO;
    }

    /** Impede alterações de conteúdo depois que a solicitação teve desfecho. */
    public void exigirEditavel() {
        if (!status.isEditavel()) {
            throw new InvarianteVioladaException(
                    "A solicitação está " + status + " e não pode mais ser alterada.");
        }
    }

    private void exigirAguardandoDecisao(String acao) {
        if (status != StatusSolicitacao.SOLICITADO) {
            throw new InvarianteVioladaException(
                    "A solicitação precisa estar SOLICITADO para ser " + acao + " (status atual: " + status + ").");
        }
    }

    private static String normalizar(String valor) {
        return StringUtils.hasText(valor) ? valor.trim() : null;
    }
}
