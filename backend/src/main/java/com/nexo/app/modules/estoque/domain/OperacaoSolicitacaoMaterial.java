package com.nexo.app.modules.estoque.domain;

import com.nexo.app.shared.domain.AuditIgnore;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.infrastructure.persistence.CryptoConverterImpl;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Registro imutável de uma entrega, devolução, estorno ou encerramento técnico.
 * As referências e snapshots são copiados do filho para impedir que um
 * replanejamento posterior altere o fato registrado.
 */
@Entity
@Table(name = "operacoes_solicitacao_material")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OperacaoSolicitacaoMaterial {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_solicitacao", nullable = false, updatable = false)
    private SolicitacaoMaterial solicitacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20, updatable = false)
    private TipoOperacaoSolicitacao tipo;

    @Convert(converter = TipoSolicitacaoTecnicaConverter.class)
    @Column(name = "tipo_tecnico", nullable = false, length = 3, updatable = false)
    private TipoSolicitacaoTecnica tipoTecnico;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_obra_snapshot", nullable = false, length = 20, updatable = false)
    private TipoObraSnapshot tipoObraSnapshot;

    @Column(name = "id_ocorrencia", updatable = false)
    private UUID idOcorrencia;

    @Column(name = "codigo_ocorrencia_snapshot", length = 100, updatable = false)
    private String ocorrencia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_reserva", updatable = false)
    private ReservaSolicitacaoTecnica reserva;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_operacao_original", updatable = false)
    private OperacaoSolicitacaoMaterial operacaoOriginal;

    @Column(name = "id_obra", nullable = false, updatable = false)
    private UUID idObra;

    @Column(name = "id_equipe", updatable = false)
    private UUID idEquipe;

    @Column(name = "id_colaborador_saque", updatable = false)
    private UUID idColaboradorSaque;

    @Column(name = "saque_fora_equipe", nullable = false, updatable = false)
    private boolean saqueForaEquipe;

    @Column(name = "id_usuario_operador", nullable = false, updatable = false)
    private UUID idUsuarioOperador;

    @Column(name = "realizada_em", nullable = false, updatable = false)
    private OffsetDateTime realizadaEm;

    @Column(name = "motivo", length = 500, updatable = false)
    private String motivo;

    @AuditIgnore
    @Convert(converter = CryptoConverterImpl.class)
    @Column(name = "assinatura_almox", updatable = false, columnDefinition = "TEXT")
    private String assinaturaAlmox;

    @AuditIgnore
    @Convert(converter = CryptoConverterImpl.class)
    @Column(name = "assinatura_equipe", updatable = false, columnDefinition = "TEXT")
    private String assinaturaEquipe;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem", nullable = false, length = 20, updatable = false)
    private OrigemComandoTecnico origem;

    @OneToMany(mappedBy = "operacao", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemOperacaoSolicitacao> itens = new ArrayList<>();

    public OperacaoSolicitacaoMaterial(
            SolicitacaoMaterial solicitacao,
            TipoOperacaoSolicitacao tipo,
            UUID idEquipe,
            UUID idColaboradorSaque,
            boolean saqueForaEquipe,
            UUID idUsuarioOperador,
            String motivo,
            String assinaturaAlmox,
            String assinaturaEquipe,
            OrigemComandoTecnico origem,
            OperacaoSolicitacaoMaterial operacaoOriginal
    ) {
        this.solicitacao = Objects.requireNonNull(solicitacao, "A solicitacao e obrigatoria");
        if (solicitacao.getContexto() != ContextoSolicitacaoMaterial.TECNICO
                || solicitacao.getTipoTecnico() == null) {
            throw new InvarianteVioladaException("Operacoes exigem uma solicitacao tecnica tipada.");
        }
        this.tipo = Objects.requireNonNull(tipo, "O tipo da operacao e obrigatorio");
        this.tipoTecnico = solicitacao.getTipoTecnico();
        this.tipoObraSnapshot = Objects.requireNonNull(
                solicitacao.getTipoObraSnapshot(), "O tipo da obra da solicitacao e obrigatorio");
        this.idOcorrencia = solicitacao.getIdOcorrencia();
        this.ocorrencia = solicitacao.getOcorrencia();
        this.idObra = Objects.requireNonNull(solicitacao.getIdObra(), "A obra da solicitacao e obrigatoria");
        this.idEquipe = idEquipe;
        this.idColaboradorSaque = idColaboradorSaque;
        this.saqueForaEquipe = saqueForaEquipe;
        this.idUsuarioOperador = Objects.requireNonNull(idUsuarioOperador, "O usuario operador e obrigatorio");
        this.motivo = limpar(motivo);
        this.assinaturaAlmox = limpar(assinaturaAlmox);
        this.assinaturaEquipe = limpar(assinaturaEquipe);
        this.origem = Objects.requireNonNullElse(origem, OrigemComandoTecnico.UI);
        this.operacaoOriginal = operacaoOriginal;
        validarReferenciaOriginal();
        boolean dispensaReservaObrigatoria = tipo == TipoOperacaoSolicitacao.ENCERRAMENTO
                || (solicitacao.getTipoTecnico() != null && solicitacao.getTipoTecnico().isDevolucao());
        this.reserva = dispensaReservaObrigatoria
                ? solicitacao.getReserva()
                : solicitacao.exigirReservaParaMovimentacaoTecnica();
        this.realizadaEm = OffsetDateTime.now();
    }

    public OperacaoSolicitacaoMaterial(
            SolicitacaoMaterial solicitacao,
            TipoOperacaoSolicitacao tipo,
            UUID idEquipe,
            UUID idUsuarioOperador,
            String motivo,
            String assinaturaAlmox,
            String assinaturaEquipe,
            OrigemComandoTecnico origem,
            OperacaoSolicitacaoMaterial operacaoOriginal
    ) {
        this(solicitacao, tipo, idEquipe, null, false, idUsuarioOperador, motivo,
                assinaturaAlmox, assinaturaEquipe, origem, operacaoOriginal);
    }

    public ItemOperacaoSolicitacao adicionarItem(
            ItemSolicitacao item,
            java.math.BigDecimal quantidade,
            String numeroSerie,
            ItemOperacaoSolicitacao itemOperacaoOriginal
    ) {
        CondicaoMaterial condicao = itemOperacaoOriginal == null
                ? CondicaoMaterial.NOVO
                : itemOperacaoOriginal.getCondicao();
        return adicionarItem(item, quantidade, numeroSerie, condicao, itemOperacaoOriginal);
    }

    public ItemOperacaoSolicitacao adicionarItem(
            ItemSolicitacao item,
            java.math.BigDecimal quantidade,
            String numeroSerie,
            CondicaoMaterial condicao,
            ItemOperacaoSolicitacao itemOperacaoOriginal
    ) {
        validarItemCompativel(item, itemOperacaoOriginal);
        CondicaoMaterial condicaoEfetiva = Objects.requireNonNullElse(condicao, CondicaoMaterial.NOVO);
        if (tipo == TipoOperacaoSolicitacao.ESTORNO
                && condicaoEfetiva != itemOperacaoOriginal.getCondicao()) {
            throw new InvarianteVioladaException(
                    "O estorno deve preservar a condicao do item operacional original.");
        }
        ItemOperacaoSolicitacao itemOperacao = new ItemOperacaoSolicitacao(
                this, item, quantidade, numeroSerie, condicaoEfetiva, itemOperacaoOriginal);
        itens.add(itemOperacao);
        return itemOperacao;
    }

    private void validarReferenciaOriginal() {
        if (tipo == TipoOperacaoSolicitacao.ENTREGA
                && (assinaturaAlmox == null || assinaturaEquipe == null)) {
            throw new InvarianteVioladaException(
                    "As assinaturas do almoxarifado e da equipe sao obrigatorias para entrega tecnica.");
        }
        if (tipo == TipoOperacaoSolicitacao.ESTORNO) {
            if (operacaoOriginal == null || operacaoOriginal.tipo == TipoOperacaoSolicitacao.ESTORNO) {
                throw new InvarianteVioladaException("O estorno deve referenciar uma entrega ou devolucao original.");
            }
            if (motivo == null) {
                throw new InvarianteVioladaException("O motivo e obrigatorio para estorno.");
            }
        } else if (operacaoOriginal != null) {
            throw new InvarianteVioladaException("Somente estorno pode referenciar uma operacao original.");
        }
        if (tipo == TipoOperacaoSolicitacao.ENCERRAMENTO && motivo == null) {
            throw new InvarianteVioladaException("O motivo e obrigatorio para encerramento.");
        }
    }

    private void validarItemCompativel(ItemSolicitacao item, ItemOperacaoSolicitacao itemOperacaoOriginal) {
        if (item == null || item.getSolicitacao() != solicitacao) {
            throw new InvarianteVioladaException("O item deve pertencer a solicitacao da operacao.");
        }
        if (tipo == TipoOperacaoSolicitacao.ESTORNO) {
            if (itemOperacaoOriginal == null || itemOperacaoOriginal.getOperacao() != operacaoOriginal) {
                throw new InvarianteVioladaException("Cada item de estorno deve referenciar o item da operacao original.");
            }
        } else if (itemOperacaoOriginal != null) {
            throw new InvarianteVioladaException("Somente itens de estorno podem referenciar item operacional original.");
        }
    }

    private static String limpar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
