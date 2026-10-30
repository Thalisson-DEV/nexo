package com.nexo.app.modules.desconto.domain;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "desconto")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Desconto extends BaseEntity {

    @Column(name = "colaborador_id", nullable = false)
    private UUID colaboradorId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "motivo_desconto_id", nullable = false)
    private MotivoDesconto motivo;

    @Column(name = "observacoes", columnDefinition = "TEXT")
    private String observacoes;

    @Column(name = "quantidade_parcelas", nullable = false)
    private Integer quantidadeParcelas;

    @Column(name = "valor_sugerido", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorSugerido = BigDecimal.ZERO;

    @Column(name = "valor_validado", precision = 12, scale = 2)
    private BigDecimal valorValidado;

    @Column(name = "valor_baixado", precision = 12, scale = 2)
    private BigDecimal valorBaixado;

    @Column(name = "usuario_solicitante_id", nullable = false)
    private UUID usuarioSolicitanteId;

    @Column(name = "usuario_validador_id")
    private UUID usuarioValidadorId;

    @Column(name = "usuario_baixa_id")
    private UUID usuarioBaixaId;

    @Column(name = "data_ocorrencia", nullable = false)
    private OffsetDateTime dataOcorrencia;

    @Column(name = "data_validacao")
    private OffsetDateTime dataValidacao;

    @Column(name = "data_baixa")
    private OffsetDateTime dataBaixa;

    @Column(name = "motivo_rejeicao", columnDefinition = "TEXT")
    private String motivoRejeicao;

    @Column(name = "usuario_rejeicao_id")
    private UUID usuarioRejeicaoId;

    @Column(name = "data_rejeicao")
    private OffsetDateTime dataRejeicao;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StatusDesconto status = StatusDesconto.SOLICITADO;

    @OneToMany(mappedBy = "desconto", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DescontoItem> itens = new ArrayList<>();

    @OneToMany(mappedBy = "desconto", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DescontoAnexo> anexos = new ArrayList<>();
    public Desconto(UUID colaboradorId, MotivoDesconto motivo, String observacoes, Integer quantidadeParcelas, OffsetDateTime dataOcorrencia, UUID usuarioSolicitanteId) {
        if (colaboradorId == null) throw new IllegalArgumentException("Colaborador não pode ser nulo.");
        if (motivo == null) throw new IllegalArgumentException("Motivo de desconto não pode ser nulo.");
        if (quantidadeParcelas == null || quantidadeParcelas < 1) throw new IllegalArgumentException("Quantidade de parcelas deve ser maior que zero.");
        if (dataOcorrencia == null) throw new IllegalArgumentException("Data da ocorrência não pode ser nula.");
        if (usuarioSolicitanteId == null) throw new IllegalArgumentException("Usuário solicitante não pode ser nulo.");
        
        if (quantidadeParcelas > motivo.getTetoParcelas()) {
            throw new InvarianteVioladaException("A quantidade de parcelas não pode ser superior ao teto do motivo (" + motivo.getTetoParcelas() + ").");
        }

        this.colaboradorId = colaboradorId;
        this.motivo = motivo;
        this.observacoes = observacoes;
        this.quantidadeParcelas = quantidadeParcelas;
        this.dataOcorrencia = dataOcorrencia;
        this.usuarioSolicitanteId = usuarioSolicitanteId;
    }

    public void adicionarItem(String descricaoMaterial, TipoEquipamento tipoEquipamento, BigDecimal valor) {
        if (this.status != StatusDesconto.SOLICITADO) {
            throw new InvarianteVioladaException("Não é possível adicionar itens a um desconto que não está no status SOLICITADO.");
        }
        this.itens.add(new DescontoItem(this, descricaoMaterial, tipoEquipamento, valor));
        recalcularValorSugerido();
    }

    public void atualizarItem(UUID itemId, String descricaoMaterial, TipoEquipamento tipoEquipamento, BigDecimal valor) {
        if (this.status != StatusDesconto.SOLICITADO) {
            throw new InvarianteVioladaException("Não é possível alterar itens de um desconto que não está no status SOLICITADO.");
        }
        DescontoItem item = this.itens.stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new InvarianteVioladaException("Item não encontrado neste desconto."));
        item.atualizar(descricaoMaterial, tipoEquipamento, valor);
        recalcularValorSugerido();
    }

    public void removerItem(UUID itemId) {
        if (this.status != StatusDesconto.SOLICITADO) {
            throw new InvarianteVioladaException("Não é possível remover itens de um desconto que não está no status SOLICITADO.");
        }
        this.itens.removeIf(i -> i.getId().equals(itemId));
        recalcularValorSugerido();
    }

    /**
     * O valor sugerido é sempre o somatório dos itens lançados na solicitação.
     * Como os itens só podem ser alterados no status SOLICITADO, o valor fica
     * naturalmente congelado a partir da validação.
     */
    private void recalcularValorSugerido() {
        this.valorSugerido = this.itens.stream()
                .filter(DescontoItem::isAtivo)
                .map(DescontoItem::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void adicionarAnexo(String nomeArquivo, String url, String contentType, Long tamanhoBytes) {
        if (this.status == StatusDesconto.BAIXADO || this.status == StatusDesconto.REJEITADO) {
            throw new InvarianteVioladaException("Não é possível adicionar anexos a um desconto que já foi finalizado.");
        }
        this.anexos.add(new DescontoAnexo(this, nomeArquivo, url, contentType, tamanhoBytes));
    }

    public void removerAnexo(UUID anexoId) {
        if (this.status == StatusDesconto.BAIXADO || this.status == StatusDesconto.REJEITADO) {
            throw new InvarianteVioladaException("Não é possível remover anexos de um desconto que já foi finalizado.");
        }
        this.anexos.removeIf(a -> a.getId().equals(anexoId));
    }

    /**
     * O termo de desconto é totalmente alterável enquanto não tiver sido validado,
     * inclusive o colaborador a quem ele se destina.
     */
    public void atualizar(UUID colaboradorId, MotivoDesconto motivo, String observacoes, Integer quantidadeParcelas, OffsetDateTime dataOcorrencia) {
        if (this.status != StatusDesconto.SOLICITADO) {
            throw new InvarianteVioladaException("Apenas descontos no status SOLICITADO podem ser atualizados.");
        }
        if (colaboradorId == null) throw new IllegalArgumentException("Colaborador não pode ser nulo.");
        if (motivo == null) throw new IllegalArgumentException("Motivo de desconto não pode ser nulo.");
        if (quantidadeParcelas == null || quantidadeParcelas < 1) throw new IllegalArgumentException("Quantidade de parcelas deve ser maior que zero.");
        if (dataOcorrencia == null) throw new IllegalArgumentException("Data da ocorrência não pode ser nula.");

        if (quantidadeParcelas > motivo.getTetoParcelas()) {
            throw new InvarianteVioladaException("A quantidade de parcelas não pode ser superior ao teto do motivo (" + motivo.getTetoParcelas() + ").");
        }

        this.colaboradorId = colaboradorId;
        this.motivo = motivo;
        this.observacoes = observacoes;
        this.quantidadeParcelas = quantidadeParcelas;
        this.dataOcorrencia = dataOcorrencia;
    }

    public void validar(UUID usuarioValidadorId, BigDecimal valorValidado) {
        if (this.status != StatusDesconto.SOLICITADO) {
            throw new InvarianteVioladaException("O desconto precisa estar no status SOLICITADO para ser validado.");
        }
        if (this.itens.isEmpty()) {
            throw new InvarianteVioladaException("Não é possível validar um desconto sem itens.");
        }
        if (usuarioValidadorId == null) throw new IllegalArgumentException("Usuário validador não pode ser nulo.");
        if (valorValidado == null || valorValidado.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("O valor validado não pode ser nulo ou negativo.");
        }

        this.status = StatusDesconto.VALIDADO;
        this.valorValidado = valorValidado;
        this.usuarioValidadorId = usuarioValidadorId;
        this.dataValidacao = OffsetDateTime.now();
    }

    public void baixar(UUID usuarioBaixaId, BigDecimal valorBaixado) {
        if (this.status != StatusDesconto.VALIDADO) {
            throw new InvarianteVioladaException("O desconto precisa estar no status VALIDADO para ser baixado.");
        }
        if (usuarioBaixaId == null) throw new IllegalArgumentException("Usuário responsável pela baixa não pode ser nulo.");
        if (valorBaixado == null || valorBaixado.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("O valor baixado não pode ser nulo ou negativo.");
        }

        this.status = StatusDesconto.BAIXADO;
        this.valorBaixado = valorBaixado;
        this.usuarioBaixaId = usuarioBaixaId;
        this.dataBaixa = OffsetDateTime.now();
    }

    public void rejeitar(UUID usuarioRejeicaoId, String motivoRejeicao) {
        if (this.status != StatusDesconto.SOLICITADO) {
            throw new InvarianteVioladaException("Apenas descontos no status SOLICITADO podem ser rejeitados.");
        }
        if (motivoRejeicao == null || motivoRejeicao.trim().isEmpty()) {
            throw new IllegalArgumentException("O motivo da rejeição não pode ser vazio.");
        }
        this.status = StatusDesconto.REJEITADO;
        this.usuarioRejeicaoId = usuarioRejeicaoId;
        this.motivoRejeicao = motivoRejeicao;
        this.dataRejeicao = OffsetDateTime.now();
    }
}
