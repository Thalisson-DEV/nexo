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
 * Representa os tipos de anexos documentais de uma obra (ex.: AS BUILT, PROJETO CAD, MEMORIAL DESCRITIVO, ORÇAMENTO, RELATÓRIO FOTOGRÁFICO, OUTROS).
 */
@Entity
@Table(name = "tipos_anexos_obras")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TipoAnexoObra extends BaseEntity {

    @NotBlank(message = "O nome do tipo de anexo é obrigatório")
    @Size(min = 2, max = 60, message = "O nome do tipo de anexo deve ter entre 2 e 60 caracteres")
    @Column(name = "nome", nullable = false, unique = true, length = 60)
    private String nome;

    @Column(name = "descricao", length = 255)
    private String descricao;

    public TipoAnexoObra(String nome, String descricao) {
        validarNome(nome);
        this.nome = nome.trim().toUpperCase(Locale.ROOT);
        this.descricao = descricao != null && !descricao.isBlank() ? descricao.trim() : null;
    }

    public void atualizar(String novoNome, String novaDescricao) {
        if (!isAtivo()) {
            throw new InvarianteVioladaException("Não é possível alterar um tipo de anexo desativado.");
        }
        validarNome(novoNome);
        this.nome = novoNome.trim().toUpperCase(Locale.ROOT);
        this.descricao = novaDescricao != null && !novaDescricao.isBlank() ? novaDescricao.trim() : null;
    }

    private void validarNome(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new InvarianteVioladaException("O nome do tipo de anexo é obrigatório.");
        }
    }
}
