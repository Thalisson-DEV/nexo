package com.nexo.app.modules.contrato.domain;

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
 * Representa uma ação comercial (serviço) executada em campo.
 */
@Entity
@Table(name = "acoes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Acao extends BaseEntity {

    @NotBlank(message = "Código da ação é obrigatório")
    @Size(min = 3, max = 20, message = "O nome da ação deve ter entre 3 e 20 caracteres")
    @Column(name = "cod_acao", nullable = false, unique = true, length = 20)
    private String codigo;

    @NotBlank(message = "Nome da ação é obrigatório")
    @Size(min = 3, max = 255, message = "O nome da ação deve ter entre 3 e 255 caracteres")
    @Column(name = "nome", nullable = false, length = 255)
    private String nome;

    public Acao(String codigo, String nome) {
        if (codigo == null || codigo.isBlank()) throw new IllegalArgumentException("Código inválido");
        if (nome == null || nome.isBlank()) throw new IllegalArgumentException("Nome inválido");
        this.codigo = codigo.toUpperCase().trim();
        this.nome = nome.toUpperCase();
    }

    public void atualizar(String codigo, String nome) {
        validarStatus();
        if (codigo == null || codigo.isBlank()) throw new IllegalArgumentException("Código inválido");
        if (nome == null || nome.isBlank()) throw new IllegalArgumentException("Nome inválido");
        this.codigo = codigo.toUpperCase();
        this.nome = nome.toUpperCase();
    }

    private void validarStatus() {
        if (!isAtivo()) {
            throw new IllegalStateException("Não é possível alterar um registro desativado.");
        }
    }
}
