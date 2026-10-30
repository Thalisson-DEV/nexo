package com.nexo.app.modules.obras.domain;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Locale;

/**
 * Representa os status operacionais possíveis de uma obra (ex.: PLANEJADA, EM EXECUÇÃO, CONCLUÍDA, PARALISADA, CANCELADA).
 */
@Entity
@Table(name = "status_obras")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StatusObra extends BaseEntity {

    @NotBlank(message = "O nome do status é obrigatório")
    @Size(min = 2, max = 60, message = "O nome do status deve ter entre 2 e 60 caracteres")
    @Column(name = "nome", nullable = false, unique = true, length = 60)
    private String nome;

    @Column(name = "ordem", nullable = false)
    private Integer ordem = 0;

    @Column(name = "concluida", nullable = false)
    private boolean concluida = false;

    public StatusObra(String nome, Integer ordem, boolean concluida) {
        validarNome(nome);
        this.nome = nome.trim().toUpperCase(Locale.ROOT);
        this.ordem = ordem != null ? ordem : 0;
        this.concluida = concluida;
    }

    public void atualizar(String novoNome, Integer novaOrdem, boolean novaConcluida) {
        if (!isAtivo()) {
            throw new InvarianteVioladaException("Não é possível alterar um status desativado.");
        }
        validarNome(novoNome);
        this.nome = novoNome.trim().toUpperCase(Locale.ROOT);
        this.ordem = novaOrdem != null ? novaOrdem : 0;
        this.concluida = novaConcluida;
    }

    private void validarNome(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new InvarianteVioladaException("O nome do status da obra é obrigatório.");
        }
    }
}
