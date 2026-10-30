package com.nexo.app.modules.estoque.domain;

import com.nexo.app.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Representa os motivos de rejeição padronizados para solicitações de material.
 */
@Entity
@Table(name = "motivos_rejeicao")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MotivoRejeicao extends BaseEntity {

    @NotBlank(message = "O nome do motivo de rejeição é obrigatório")
    @Size(min = 3, max = 100, message = "O nome deve ter entre 3 e 100 caracteres")
    @Column(name = "nome", nullable = false, unique = true, length = 100)
    private String nome;

    @NotBlank(message = "A descrição do motivo de rejeição é obrigatória")
    @Size(min = 5, max = 255, message = "A descrição deve ter entre 5 e 255 caracteres")
    @Column(name = "descricao", nullable = false, length = 255)
    private String descricao;

    public MotivoRejeicao(String nome, String descricao) {
        validarNome(nome);
        validarDescricao(descricao);
        this.nome = nome.trim();
        this.descricao = descricao.trim();
    }

    public void atualizar(String novoNome, String novaDescricao) {
        validarStatus();
        validarNome(novoNome);
        validarDescricao(novaDescricao);
        this.nome = novoNome.trim();
        this.descricao = novaDescricao.trim();
    }

    private void validarNome(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("Nome do motivo de rejeição inválido");
        }
    }

    private void validarDescricao(String descricao) {
        if (descricao == null || descricao.isBlank()) {
            throw new IllegalArgumentException("Descrição do motivo de rejeição inválida");
        }
    }

    private void validarStatus() {
        if (!isAtivo()) {
            throw new IllegalStateException("Não é possível alterar um registro desativado.");
        }
    }
}
