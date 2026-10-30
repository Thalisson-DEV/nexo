package com.nexo.app.modules.estoque.domain;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;
import java.util.UUID;

/**
 * Representa um local de armazenamento de materiais, podendo ser um depósito físico 
 * central ou o estoque móvel de uma equipe em campo.
 */
@Entity
@Table(name = "estoques")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Estoque extends BaseEntity {

    @NotBlank(message = "O nome identificador do estoque é obrigatório")
    @Size(min = 3, max = 100, message = "O nome deve ter entre 3 e 100 caracteres")
    @Column(name = "nome_identificador", nullable = false, length = 100)
    private String nomeIdentificador;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_tipo_estoque", nullable = false)
    private TipoEstoque tipoEstoque;

    /**
     * Referência ao ID da Equipe (Módulo RH).
     * Mantido como UUID para garantir o isolamento entre módulos.
     */
    @Column(name = "id_equipe")
    private UUID idEquipe;

    /**
     * Base operacional de saque (Módulo RH): base onde este estoque recebe seus materiais.
     * Mantida como UUID para preservar o isolamento entre módulos (mesmo padrão de idEquipe).
     */
    @Column(name = "id_base_operacional")
    private UUID idBaseOperacional;

    /** UUID da obra cuja custodia patrimonial este estoque representa. */
    @Column(name = "id_obra")
    private UUID idObra;

    public Estoque(String nomeIdentificador, TipoEstoque tipoEstoque) {
        validarNome(nomeIdentificador);
        vincularTipoEstoque(tipoEstoque);
        this.nomeIdentificador = nomeIdentificador.toUpperCase().trim();
    }

    public void atualizarNome(String novoNome) {
        validarStatus();
        validarNome(novoNome);
        this.nomeIdentificador = novoNome.toUpperCase().trim();
    }

    /**
     * Vincula o estoque a um tipo específico.
     * Se o tipo for alterado e não for mais 'CAMPO', o vínculo com a equipe é removido.
     */
    public void vincularTipoEstoque(TipoEstoque tipo) {
        validarStatus();
        if (tipo == null) {
            throw new IllegalArgumentException("Tipo de estoque é obrigatório");
        }
        if (!tipo.isAtivo()) {
            throw new InvarianteVioladaException("Não é possível vincular um tipo de estoque desativado.");
        }
        
        this.tipoEstoque = tipo;

        // Se o novo tipo não for CAMPO, removemos qualquer vínculo de equipe existente
        if (!isTipoCampo()) {
            this.idEquipe = null;
        }
    }

    /**
     * Vincula uma equipe ao estoque.
     * Regra de Negócio: Apenas estoques do tipo 'CAMPO' podem possuir equipe vinculada.
     */
    public void vincularEquipe(UUID idEquipe) {
        validarStatus();
        if (!isTipoCampo()) {
            throw new InvarianteVioladaException("Apenas estoques do tipo CAMPO podem ser vinculados a uma equipe.");
        }
        this.idEquipe = Objects.requireNonNull(idEquipe, "O ID da equipe é obrigatório para estoques de CAMPO");
    }

    public void desvincularEquipe() {
        validarStatus();
        this.idEquipe = null;
    }

    /**
     * Vincula a base operacional de saque (onde este estoque recebe seus materiais).
     */
    public void vincularBaseOperacional(UUID idBaseOperacional) {
        validarStatus();
        this.idBaseOperacional = Objects.requireNonNull(idBaseOperacional, "A base operacional de saque é obrigatória");
    }

    /** Vincula uma obra somente a um estoque patrimonial do tipo OBRA. */
    public void vincularObra(UUID idObra) {
        validarStatus();
        if (!isTipoObra()) {
            throw new InvarianteVioladaException("Apenas estoques do tipo OBRA podem ser vinculados a uma obra.");
        }
        this.idObra = Objects.requireNonNull(idObra, "O ID da obra e obrigatorio para estoques de OBRA");
    }

    public boolean isTipoCampo() {
        return this.tipoEstoque != null && "CAMPO".equalsIgnoreCase(this.tipoEstoque.getNome());
    }

    public boolean isTipoObra() {
        return this.tipoEstoque != null && "OBRA".equalsIgnoreCase(this.tipoEstoque.getNome());
    }

    private void validarNome(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("Nome identificador do estoque inválido");
        }
    }

    private void validarStatus() {
        if (!isAtivo()) {
            throw new IllegalStateException("Não é possível alterar um estoque desativado.");
        }
    }
}
