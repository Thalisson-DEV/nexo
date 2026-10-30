package com.nexo.app.modules.estoque.domain;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Representa um material específico dentro de uma solicitação.
 */
@Entity
@Table(name = "itens_solicitacao")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItemSolicitacao extends BaseEntity {

    @JsonBackReference
    @NotNull(message = "A solicitação é obrigatória")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_solicitacao", nullable = false)
    private SolicitacaoMaterial solicitacao;

    @NotNull(message = "O material é obrigatório")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_material", nullable = false)
    private Material material;

    @NotNull(message = "A quantidade é obrigatória")
    @Min(value = 0, message = "A quantidade deve ser maior ou igual a zero")
    @Max(value = 10000, message = "A quantidade deve ser menor ou igual a 100000")
    @Column(name = "quantidade", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantidade;

    @NotNull(message = "A quantidade entregue é obrigatória")
    @Min(value = 0, message = "A quantidade deve ser maior ou igual a zero")
    @Max(value = 10000, message = "A quantidade deve ser menor ou igual a 100000")
    @Column(name = "quantidade_entregue", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantidadeEntregue;

    @Column(name = "num_serie")
    private String numSerie;

    /** Séries que o almoxarifado definiu para este item antes da entrega. */
    @OneToMany(mappedBy = "itemSolicitacao")
    @org.hibernate.annotations.BatchSize(size = 100)
    private java.util.List<ReservaSerieSolicitacao> reservasSerie = new java.util.ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_motivo_pendencia")
    private MotivoPendencia motivoPendencia;

    @Column(name = "justificativa_pendencia", length = 2000)
    private String justificativaPendencia;

    public boolean possuiPendencia() { return motivoPendencia != null; }

    public void registrarPendencia(MotivoPendencia motivo) {
        SolicitacaoMaterial.validarMotivo(motivo);
        this.motivoPendencia = motivo;
        this.justificativaPendencia = null;
    }

    public void registrarPendencia(MotivoPendencia motivo, String justificativa) {
        if (justificativa == null || justificativa.isBlank()) {
            registrarPendencia(motivo);
            return;
        }
        SolicitacaoMaterial.validarPendencia(motivo, justificativa);
        this.motivoPendencia = motivo;
        this.justificativaPendencia = justificativa.trim();
    }

    public void removerPendencia() {
        this.motivoPendencia = null;
        this.justificativaPendencia = null;
    }

    @Column(name = "quantidade_entregue_bruta", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantidadeEntregueBruta = BigDecimal.ZERO;

    @Column(name = "quantidade_entrega_estornada", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantidadeEntregaEstornada = BigDecimal.ZERO;

    @Column(name = "quantidade_devolvida_bruta", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantidadeDevolvidaBruta = BigDecimal.ZERO;

    @Column(name = "quantidade_devolucao_estornada", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantidadeDevolucaoEstornada = BigDecimal.ZERO;

    protected ItemSolicitacao(SolicitacaoMaterial solicitacao, Material material, BigDecimal quantidade) {
        this.solicitacao = solicitacao;
        this.material = material;
        this.quantidade = quantidade;
        this.quantidadeEntregue = BigDecimal.ZERO;
    }

    public void registrarEntrega(BigDecimal qtd, String numSerie) {
        if (qtd == null || qtd.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("A quantidade a entregar deve ser positiva");
        }

        // Regra de Série: Se detalha série, exige número. Se não, proíbe.
        if (material.isDetalhaSerie()) {
            if (numSerie == null || numSerie.isBlank()) {
                throw new InvarianteVioladaException("O material '" + material.getCodigo() + "' exige número de série na entrega.");
            }
            if (qtd.compareTo(BigDecimal.ONE) != 0) {
                throw new InvarianteVioladaException("Materiais com controle de série devem ser entregues um a um (Qtd: 1).");
            }
            this.numSerie = numSerie; // Registra a série do último item entregue (ou único)
        } else if (numSerie != null && !numSerie.isBlank()) {
            throw new InvarianteVioladaException("O material '" + material.getCodigo() + "' não permite número de série.");
        }

        BigDecimal novaQtdEntregueBruta = this.quantidadeEntregueBruta.add(qtd);
        if (novaQtdEntregueBruta.subtract(this.quantidadeEntregaEstornada).compareTo(this.quantidade) > 0) {
            throw new InvarianteVioladaException(
                "A quantidade entregue não pode ultrapassar a quantidade solicitada para o material: " + material.getCodigo()
            );
        }
        this.quantidadeEntregueBruta = novaQtdEntregueBruta;
        this.quantidadeEntregue = getQuantidadeEntregaLiquida();
    }

    /** Números de série reservados que ainda valem, em ordem. */
    public java.util.List<String> getSeriesReservadas() {
        return reservasSerie.stream()
                .filter(ReservaSerieSolicitacao::isVigente)
                .map(r -> r.getSaldoSerie().getNumSerie())
                .sorted()
                .toList();
    }

    public BigDecimal getQuantidadePendente() {
        return this.quantidade.subtract(getQuantidadeOperadaLiquida());
    }

    public boolean isTotalmenteEntregue() {
        return getQuantidadePendente().signum() <= 0;
    }

    public BigDecimal getQuantidadeEntregaLiquida() {
        return quantidadeEntregueBruta.subtract(quantidadeEntregaEstornada);
    }

    public BigDecimal getQuantidadeDevolucaoLiquida() {
        return quantidadeDevolvidaBruta.subtract(quantidadeDevolucaoEstornada);
    }

    public BigDecimal getQuantidadeOperadaLiquida() {
        if (solicitacao.getTipoTecnico() != null && solicitacao.getTipoTecnico().isAdicao()) {
            return getQuantidadeEntregaLiquida();
        }
        if (solicitacao.getTipoTecnico() != null && solicitacao.getTipoTecnico().isDevolucao()) {
            return getQuantidadeDevolucaoLiquida();
        }
        return quantidadeEntregue;
    }

    public boolean possuiFatoOperacional() {
        return quantidadeEntregueBruta.signum() > 0 || quantidadeDevolvidaBruta.signum() > 0;
    }

    public void replanejar(
            Material novoMaterial,
            BigDecimal novaQuantidade
    ) {
        if (novaQuantidade == null || novaQuantidade.signum() <= 0) {
            throw new IllegalArgumentException("A nova quantidade solicitada deve ser positiva");
        }
        if (novaQuantidade.compareTo(getQuantidadeOperadaLiquida()) < 0) {
            throw new InvarianteVioladaException(
                    "A nova quantidade nao pode ser menor que a quantidade liquida ja confirmada.");
        }
        boolean alteraIdentidade = !java.util.Objects.equals(
                this.material == null ? null : this.material.getId(),
                novoMaterial == null ? null : novoMaterial.getId())
                ;
        if (possuiFatoOperacional() && alteraIdentidade) {
            throw new InvarianteVioladaException(
                    "Material de item com fato operacional nao pode ser alterado.");
        }
        this.material = java.util.Objects.requireNonNull(novoMaterial, "O material e obrigatorio");
        if (alteraIdentidade) {
            removerPendencia();
        }
        this.quantidade = novaQuantidade;
    }

    public void registrarEntregaTecnica(BigDecimal quantidadeOperada) {
        exigirTipo(TipoSolicitacaoTecnica::isAdicao);
        validarQuantidadePositiva(quantidadeOperada);
        BigDecimal novaBruta = quantidadeEntregueBruta.add(quantidadeOperada);
        if (novaBruta.subtract(quantidadeEntregaEstornada).compareTo(quantidade) > 0) {
            throw new InvarianteVioladaException("A entrega tecnica excede a quantidade pendente do item.");
        }
        quantidadeEntregueBruta = novaBruta;
        // Projecao legada preservada para consumidores comerciais e de leitura existentes.
        quantidadeEntregue = getQuantidadeEntregaLiquida();
    }

    public void registrarDevolucaoTecnica(BigDecimal quantidadeOperada) {
        exigirTipo(TipoSolicitacaoTecnica::isDevolucao);
        validarQuantidadePositiva(quantidadeOperada);
        BigDecimal novaBruta = quantidadeDevolvidaBruta.add(quantidadeOperada);
        if (novaBruta.subtract(quantidadeDevolucaoEstornada).compareTo(quantidade) > 0) {
            throw new InvarianteVioladaException("A devolucao tecnica excede a quantidade pendente do item.");
        }
        quantidadeDevolvidaBruta = novaBruta;
    }

    public void registrarEstornoEntregaTecnica(BigDecimal quantidadeEstornada) {
        exigirTipo(TipoSolicitacaoTecnica::isAdicao);
        validarQuantidadePositiva(quantidadeEstornada);
        BigDecimal novoEstorno = quantidadeEntregaEstornada.add(quantidadeEstornada);
        if (novoEstorno.compareTo(quantidadeEntregueBruta) > 0) {
            throw new InvarianteVioladaException("O estorno excede a entrega bruta do item.");
        }
        quantidadeEntregaEstornada = novoEstorno;
        quantidadeEntregue = getQuantidadeEntregaLiquida();
    }

    public void registrarEstornoDevolucaoTecnica(BigDecimal quantidadeEstornada) {
        exigirTipo(TipoSolicitacaoTecnica::isDevolucao);
        validarQuantidadePositiva(quantidadeEstornada);
        BigDecimal novoEstorno = quantidadeDevolucaoEstornada.add(quantidadeEstornada);
        if (novoEstorno.compareTo(quantidadeDevolvidaBruta) > 0) {
            throw new InvarianteVioladaException("O estorno excede a devolucao bruta do item.");
        }
        quantidadeDevolucaoEstornada = novoEstorno;
    }

    private void exigirTipo(java.util.function.Predicate<TipoSolicitacaoTecnica> predicado) {
        if (solicitacao.getTipoTecnico() == null || !predicado.test(solicitacao.getTipoTecnico())) {
            throw new InvarianteVioladaException("A operacao nao e compativel com o tipo da solicitacao.");
        }
    }

    private void validarQuantidadePositiva(BigDecimal valor) {
        if (valor == null || valor.signum() <= 0) {
            throw new IllegalArgumentException("A quantidade da operacao deve ser positiva");
        }
    }

    public void atualizarQuantidade(BigDecimal novaQuantidade) {
        if (novaQuantidade == null || novaQuantidade.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("A nova quantidade solicitada deve ser positiva");
        }
        if (novaQuantidade.compareTo(getQuantidadeEntregaLiquida()) < 0) {
            throw new InvarianteVioladaException(
                "A nova quantidade solicitada (" + novaQuantidade + ") não pode ser menor que a quantidade já entregue (" + this.quantidadeEntregue + ") para o material: " + material.getCodigo()
            );
        }
        this.quantidade = novaQuantidade;
    }

    public void registrarEstorno(BigDecimal qtd) {
        if (qtd == null || qtd.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("A quantidade a estornar deve ser positiva");
        }
        if (getQuantidadeEntregaLiquida().compareTo(qtd) < 0) {
            throw new InvarianteVioladaException(
                "A quantidade a estornar (" + qtd + ") não pode ser superior à quantidade já entregue (" + this.quantidadeEntregue + ") para o material: " + material.getCodigo()
            );
        }
        this.quantidadeEntregaEstornada = this.quantidadeEntregaEstornada.add(qtd);
        this.quantidadeEntregue = getQuantidadeEntregaLiquida();
        
        // Se o estorno for total do item com série, removemos o registro da série
        if (this.quantidadeEntregue.compareTo(BigDecimal.ZERO) == 0 && material.isDetalhaSerie()) {
            this.numSerie = null;
        }
    }
}
