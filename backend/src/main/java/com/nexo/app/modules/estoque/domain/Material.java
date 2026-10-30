package com.nexo.app.modules.estoque.domain;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.infrastructure.persistence.CryptoConverterImpl;
import com.nexo.app.shared.infrastructure.security.HashUtils;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;
import java.util.UUID;

/**
 * Representa o cadastro mestre de materiais.
 */
@Entity
@Table(name = "materiais")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Material extends BaseEntity {

    @NotBlank(message = "O código do material é obrigatório")
    @Size(min = 3, max = 100, message = "O código deve ter entre 3 e 100 caracteres")
    @Column(name = "cod_material", nullable = false, unique = true)
    private String codigo;

    @NotBlank(message = "A descrição é obrigatória")
    @Size(min = 3, max = 355, message = "A descrição deve ter entre 3 e 355 caracteres")
    @Column(name = "descricao", nullable = false)
    private String descricao;

    @Size(min = 1, max = 10, message = "A unidade de medida deve ter entre 1 e 10 caracteres")
    @Column(name = "unidade_medida", length = 10)
    private String unidadeMedida;

    @Size(min = 4, max = 4, message = "A flag de supr ou matr deve ter entre 3 e 4 caracteres")
    @Column(name = "supr_matr_flag", length = 4)
    private String suprMatrFlag;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_avaliacao_tecnica")
    private AvaliacaoMaterial avaliacaoTecnica;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_centro_investimento")
    private CentroInvestimento centroInvestimento;

    @NotNull(message = "O processo é obrigatório")
    @Column(name = "id_processo", nullable = false)
    private UUID idProcesso;

    @Column(name = "detalha_serie")
    private boolean detalhaSerie = false;

    /** Compatibilidade de leitura durante a migração; classificação não é persistida. */
    @Deprecated
    public Material(String codigo, String descricao, String unidadeMedida, String suprMatrFlag,
                    AvaliacaoMaterial avaliacao, CentroInvestimento centro, boolean detalhaSerie, UUID idProcesso) {
        validarCodigo(codigo);
        validarDescricao(descricao);
        this.codigo = codigo.toUpperCase().trim();
        this.descricao = descricao;
        this.unidadeMedida = unidadeMedida != null ? unidadeMedida.toUpperCase().trim() : null;
        this.suprMatrFlag = suprMatrFlag != null ? suprMatrFlag.toUpperCase().trim() : null;
        vincularAvaliacao(avaliacao);
        vincularCentroInvestimento(centro);
        vincularProcesso(idProcesso);
        this.detalhaSerie = detalhaSerie;
    }

    public void atualizar(String codigo, String descricao, String unidadeMedida, String suprMatrFlag, boolean detalhaSerie) {
        validarStatus();
        validarCodigo(codigo);
        validarDescricao(descricao);
        this.codigo = codigo;
        this.descricao = descricao;
        this.unidadeMedida = unidadeMedida != null ? unidadeMedida.toUpperCase().trim() : null;
        this.suprMatrFlag = suprMatrFlag != null ? suprMatrFlag.toUpperCase().trim() : null;
        this.detalhaSerie = detalhaSerie;
    }

    public void vincularAvaliacao(AvaliacaoMaterial avaliacao) {
        if (avaliacao != null && !avaliacao.isAtivo()) {
            throw new InvarianteVioladaException("Não é possível vincular uma avaliação desativada.");
        }
        this.avaliacaoTecnica = avaliacao;
    }

    public void vincularCentroInvestimento(CentroInvestimento centro) {
        if (centro != null && !centro.isAtivo()) {
            throw new InvarianteVioladaException("Não é possível vincular um centro de investimento desativado.");
        }
        this.centroInvestimento = centro;
    }

    public void vincularProcesso(UUID idProcesso) {
        this.idProcesso = Objects.requireNonNull(idProcesso, "O processo de negócio é obrigatório");
    }

    private void validarCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            throw new IllegalArgumentException("Código do material inválido");
        }
    }

    private void validarDescricao(String desc) {
        if (desc == null || desc.isBlank()) {
            throw new IllegalArgumentException("Descrição do material é obrigatória");
        }
    }

    private void validarStatus() {
        if (!isAtivo()) {
            throw new IllegalStateException("Não é possível alterar um registro desativado.");
        }
    }
}
