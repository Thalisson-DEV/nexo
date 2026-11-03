package com.nexo.app.modules.estoque.domain;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.AuditIgnore;
import com.nexo.app.shared.infrastructure.persistence.CryptoConverterImpl;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Registra o histórico detalhado de todas as entradas, saídas e transferências de materiais.
 * Atua como uma trilha de auditoria para os saldos.
 */
@Entity
@Table(name = "movimentacoes_estoque")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MovimentacaoEstoque extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_estoque_origem")
    private Estoque estoqueOrigem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_estoque_destino")
    private Estoque estoqueDestino;

    @NotNull(message = "O material é obrigatório")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_material", nullable = false)
    private Material material;

    @NotNull(message = "A quantidade é obrigatória")
    @Column(name = "quantidade", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantidade;

    @NotNull(message = "O tipo de movimentação é obrigatório")
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_movimentacao", nullable = false, length = 30)
    private TipoMovimentacao tipo;

    @NotNull(message = "O usuário operador é obrigatório")
    @Column(name = "id_usuario_operador", nullable = false)
    private UUID idUsuarioOperador;

    @Column(name = "id_referencia_operacao")
    private UUID idReferenciaOperacao;

    @Column(name = "data_hora", nullable = false)
    private OffsetDateTime dataHora;

    @Column(name = "num_serie")
    private String numSerie;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_solicitacao")
    private SolicitacaoMaterial solicitacao;

    @Convert(converter = TipoSolicitacaoTecnicaConverter.class)
    @Column(name = "tipo_tecnico", length = 3)
    private TipoSolicitacaoTecnica tipoTecnico;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_obra_snapshot", length = 20)
    private TipoObraSnapshot tipoObraSnapshot;

    @Column(name = "id_ocorrencia")
    private UUID idOcorrencia;

    @Column(name = "codigo_ocorrencia_snapshot", length = 100)
    private String ocorrencia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_reserva")
    private ReservaSolicitacaoTecnica reserva;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_operacao")
    private OperacaoSolicitacaoMaterial operacaoSolicitacao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_item_operacao")
    private ItemOperacaoSolicitacao itemOperacao;

    @Column(name = "id_obra")
    private UUID idObra;

    @Column(name = "id_equipe")
    private UUID idEquipe;

    @NotNull(message = "A condição do material é obrigatória")
    @Enumerated(EnumType.STRING)
    @Column(name = "condicao", nullable = false, length = 20)
    private CondicaoMaterial condicao;

    @AuditIgnore
    @Convert(converter = CryptoConverterImpl.class)
    @Column(name = "assinatura_entregador", columnDefinition = "TEXT")
    private String assinaturaEntregador;

    @AuditIgnore
    @Convert(converter = CryptoConverterImpl.class)
    @Column(name = "assinatura_recebedor", columnDefinition = "TEXT")
    private String assinaturaRecebedor;

    public void registrarAssinaturas(String assinaturaEntregador, String assinaturaRecebedor) {
        this.assinaturaEntregador = assinaturaEntregador;
        this.assinaturaRecebedor = assinaturaRecebedor;
    }

    public enum TipoMovimentacao {
        ENTRADA_AVULSA, 
        BAIXA_AVULSA, 
        TRANSFERENCIA,
        ENTREGA,
        ESTORNO,
        APLICACAO,
        RETIRADA,
        DEVOLUCAO
    }

    public MovimentacaoEstoque(Estoque origem, Estoque destino, Material material, BigDecimal quantidade, 
                               TipoMovimentacao tipo, CondicaoMaterial condicao, UUID idUsuarioOperador, UUID idReferenciaOperacao, String numSerie) {
        this(origem, destino, material, quantidade, tipo, condicao, idUsuarioOperador, idReferenciaOperacao, numSerie, OffsetDateTime.now());
    }

    public MovimentacaoEstoque(Estoque origem, Estoque destino, Material material, BigDecimal quantidade, 
                               TipoMovimentacao tipo, CondicaoMaterial condicao, UUID idUsuarioOperador, UUID idReferenciaOperacao, String numSerie, OffsetDateTime dataHora) {
        validarMovimentacao(origem, destino, material, quantidade, tipo, condicao, idUsuarioOperador, numSerie);
        this.estoqueOrigem = origem;
        this.estoqueDestino = destino;
        this.material = material;
        this.quantidade = quantidade;
        this.tipo = tipo;
        this.condicao = condicao != null ? condicao : CondicaoMaterial.NOVO;
        this.idUsuarioOperador = idUsuarioOperador;
        this.idReferenciaOperacao = idReferenciaOperacao != null ? idReferenciaOperacao : UUID.randomUUID();
        this.numSerie = numSerie;
        this.dataHora = dataHora != null ? dataHora : OffsetDateTime.now();
    }

    public static MovimentacaoEstoque criarTecnica(
            Estoque origem,
            Estoque destino,
            ItemOperacaoSolicitacao itemOperacao,
            TipoMovimentacao tipo,
            UUID idUsuarioOperador
    ) {
        return criarTecnica(origem, destino, itemOperacao, tipo,
                itemOperacao == null ? CondicaoMaterial.NOVO : itemOperacao.getCondicao(),
                idUsuarioOperador);
    }

    public static MovimentacaoEstoque criarTecnica(
            Estoque origem,
            Estoque destino,
            ItemOperacaoSolicitacao itemOperacao,
            TipoMovimentacao tipo,
            CondicaoMaterial condicao,
            UUID idUsuarioOperador
    ) {
        OperacaoSolicitacaoMaterial operacao = Objects.requireNonNull(
                itemOperacao, "O item operacional e obrigatorio").getOperacao();
        MovimentacaoEstoque movimentacao = new MovimentacaoEstoque(
                origem, destino, itemOperacao.getMaterial(), itemOperacao.getQuantidade(), tipo,
                Objects.requireNonNullElse(condicao, CondicaoMaterial.NOVO),
                idUsuarioOperador, operacao.getId(), itemOperacao.getNumeroSerie());
        movimentacao.solicitacao = operacao.getSolicitacao();
        movimentacao.tipoTecnico = operacao.getTipoTecnico();
        movimentacao.tipoObraSnapshot = operacao.getTipoObraSnapshot();
        movimentacao.idOcorrencia = operacao.getIdOcorrencia();
        movimentacao.ocorrencia = operacao.getOcorrencia();
        movimentacao.reserva = operacao.getReserva();
        movimentacao.operacaoSolicitacao = operacao;
        movimentacao.itemOperacao = itemOperacao;
        movimentacao.idObra = operacao.getIdObra();
        movimentacao.idEquipe = operacao.getIdEquipe();
        return movimentacao;
    }

    public static MovimentacaoEstoque criarComercial(
            SolicitacaoMaterial solicitacao,
            Estoque origem,
            Estoque destino,
            Material material,
            BigDecimal quantidade,
            TipoMovimentacao tipo,
            UUID idUsuarioOperador,
            UUID idReferenciaOperacao,
            String numeroSerie
    ) {
        if (solicitacao == null || solicitacao.getContexto() != ContextoSolicitacaoMaterial.COMERCIAL) {
            throw new InvarianteVioladaException("A movimentacao comercial exige uma solicitacao comercial.");
        }
        MovimentacaoEstoque movimentacao = new MovimentacaoEstoque(
                origem, destino, material, quantidade, tipo, CondicaoMaterial.NOVO,
                idUsuarioOperador, idReferenciaOperacao, numeroSerie);
        movimentacao.solicitacao = solicitacao;
        movimentacao.tipoTecnico = solicitacao.getTipoTecnico();
        movimentacao.idEquipe = solicitacao.getIdEquipeSaque();
        return movimentacao;
    }

    public static MovimentacaoEstoque criarDevolucaoComercial(
            Estoque origemCampo,
            Estoque destinoFisico,
            Material material,
            BigDecimal quantidade,
            CondicaoMaterial condicao,
            UUID idEquipe,
            UUID idUsuarioOperador,
            UUID idReferenciaOperacao,
            String numeroSerie
    ) {
        MovimentacaoEstoque movimentacao = new MovimentacaoEstoque(
                origemCampo, destinoFisico, material, quantidade, TipoMovimentacao.DEVOLUCAO,
                condicao != null ? condicao : CondicaoMaterial.NOVO,
                idUsuarioOperador, idReferenciaOperacao, numeroSerie);
        movimentacao.tipoTecnico = TipoSolicitacaoTecnica.DEVOLUCAO_COMUM;
        movimentacao.idEquipe = idEquipe;
        return movimentacao;
    }

    public static MovimentacaoEstoque criarDevolucaoTecnica(
            Estoque origemObra,
            Estoque destinoFisico,
            Material material,
            BigDecimal quantidade,
            CondicaoMaterial condicao,
            ContextoDevolucaoTecnica contexto,
            UUID idUsuarioOperador,
            UUID idReferenciaOperacao,
            String numeroSerie
    ) {
        MovimentacaoEstoque movimentacao = new MovimentacaoEstoque(
                origemObra, destinoFisico, material, quantidade, TipoMovimentacao.DEVOLUCAO, condicao,
                idUsuarioOperador, idReferenciaOperacao, numeroSerie);
        movimentacao.aplicar(contexto);
        return movimentacao;
    }

    /** Material devolvido que o sistema não sabia estar na obra: entra nela antes de sair. */
    public static MovimentacaoEstoque criarEntradaPorDevolucaoTecnica(
            Estoque estoqueObra,
            Material material,
            BigDecimal quantidade,
            CondicaoMaterial condicao,
            ContextoDevolucaoTecnica contexto,
            UUID idUsuarioOperador,
            UUID idReferenciaOperacao,
            String numeroSerie
    ) {
        MovimentacaoEstoque movimentacao = new MovimentacaoEstoque(
                null, estoqueObra, material, quantidade, TipoMovimentacao.ENTRADA_AVULSA, condicao,
                idUsuarioOperador, idReferenciaOperacao, numeroSerie);
        movimentacao.aplicar(contexto);
        return movimentacao;
    }

    private void aplicar(ContextoDevolucaoTecnica contexto) {
        this.tipoTecnico = contexto.tipoTecnico();
        this.idObra = contexto.idObra();
        this.tipoObraSnapshot = contexto.tipoObra();
        this.idOcorrencia = contexto.idOcorrencia();
        this.ocorrencia = contexto.ocorrencia();
        this.idEquipe = contexto.idEquipe();
    }

    /** O que liga a movimentação direta à obra, já que não há solicitação por trás. */
    public record ContextoDevolucaoTecnica(
            TipoSolicitacaoTecnica tipoTecnico,
            UUID idObra,
            TipoObraSnapshot tipoObra,
            UUID idOcorrencia,
            String ocorrencia,
            UUID idEquipe
    ) {
    }

    private void validarMovimentacao(Estoque origem, Estoque destino, Material material, BigDecimal quantidade, 
                                     TipoMovimentacao tipo, CondicaoMaterial condicao, UUID idUsuarioOperador, String numSerie) {
        if (material == null) throw new IllegalArgumentException("Material é obrigatório");
        if (condicao == null) throw new IllegalArgumentException("Condição do material é obrigatória");
        if (quantidade == null || quantidade.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("A quantidade deve ser positiva");
        }
        if (tipo == null) throw new IllegalArgumentException("Tipo de movimentação é obrigatório");
        if (idUsuarioOperador == null) throw new IllegalArgumentException("ID do usuário operador é obrigatório");

        // Regra de Série: Se detalha série, exige número. Se não, proíbe.
        if (material.isDetalhaSerie()) {
            if (numSerie == null || numSerie.isBlank()) {
                throw new InvarianteVioladaException("O material '" + material.getCodigo() + "' exige número de série.");
            }
            if (quantidade.compareTo(BigDecimal.ONE) != 0) {
                throw new InvarianteVioladaException("Materiais com controle de série devem ser movimentados um a um.");
            }
        } else if (numSerie != null && !numSerie.isBlank()) {
            throw new InvarianteVioladaException("O material '" + material.getCodigo() + "' não permite número de série.");
        }

        if (tipo == TipoMovimentacao.TRANSFERENCIA) {
            if (origem == null || destino == null) {
                throw new IllegalArgumentException("Origem e Destino são obrigatórios para transferências");
            }
            if (origem.getId() != null && Objects.equals(origem.getId(), destino.getId())) {
                throw new IllegalArgumentException("Origem e Destino não podem ser iguais");
            }
        }
        if (tipo == TipoMovimentacao.ENTREGA && destino == null) {
            throw new IllegalArgumentException("Destino é obrigatório para entrega");
        }
        // RETIRADA é o material retirado em campo pela equipe: entra no estoque
        // da equipe (destino) como sucata; não há estoque de origem.
        if (tipo == TipoMovimentacao.RETIRADA && destino == null) {
            throw new IllegalArgumentException("Destino é obrigatório para retirada de campo");
        }
        if (tipo == TipoMovimentacao.DEVOLUCAO) {
            if (origem == null || destino == null) {
                throw new IllegalArgumentException("Origem e Destino são obrigatórios para devolução");
            }
            if (origem.getId() != null && Objects.equals(origem.getId(), destino.getId())) {
                throw new IllegalArgumentException("Origem e Destino não podem ser iguais");
            }
        }
    }
}
