package com.nexo.app.modules.rh.domain;

import com.nexo.app.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

/**
 * Representa os processos de negócio da empresa (EX: COMERCIAL, LINHA VIVA, MANUTENÇÃO, etc.)
 */
@Entity
@Table(name = "processos_negocio")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessoNegocio extends BaseEntity {

    @NotBlank(message = "O nome do processo é obrigatório")
    @Column(name = "nome", nullable = false, unique = true, length = 50)
    private String nome;

    public ProcessoNegocio(String nome) {
        validarNome(nome);
        this.nome = nome.toUpperCase();
    }

    public void atualizarNome(String novoNome) {
        validarNome(novoNome);
        validarStatus();
        this.nome = novoNome.toUpperCase();
    }

    private void validarStatus() {
        if (!isAtivo()) {
            throw new IllegalStateException("Não é possível alterar um registro desativado.");
        }
    }

    private void validarNome(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("Nome do processo inválido");
        }
    }
}