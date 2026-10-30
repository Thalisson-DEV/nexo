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
 * Representa os agentes ou fiscais responsáveis da concessionária/cliente vinculados às obras.
 */
@Entity
@Table(name = "agentes_responsaveis_obras")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgenteResponsavel extends BaseEntity {

    @NotBlank(message = "O nome do agente responsável é obrigatório")
    @Size(min = 2, max = 150, message = "O nome do agente deve ter entre 2 e 150 caracteres")
    @Column(name = "nome", nullable = false, length = 150)
    private String nome;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "telefone", length = 30)
    private String telefone;

    @Column(name = "empresa_orgao", length = 100)
    private String empresaOrgao;

    public AgenteResponsavel(String nome, String email, String telefone, String empresaOrgao) {
        validarNome(nome);
        this.nome = nome.trim().toUpperCase(Locale.ROOT);
        this.email = email != null && !email.isBlank() ? email.trim().toLowerCase(Locale.ROOT) : null;
        this.telefone = telefone != null && !telefone.isBlank() ? telefone.trim() : null;
        this.empresaOrgao = empresaOrgao != null && !empresaOrgao.isBlank() ? empresaOrgao.trim().toUpperCase(Locale.ROOT) : null;
    }

    public void atualizar(String novoNome, String novoEmail, String novoTelefone, String novaEmpresaOrgao) {
        if (!isAtivo()) {
            throw new InvarianteVioladaException("Não é possível alterar um agente desativado.");
        }
        validarNome(novoNome);
        this.nome = novoNome.trim().toUpperCase(Locale.ROOT);
        this.email = novoEmail != null && !novoEmail.isBlank() ? novoEmail.trim().toLowerCase(Locale.ROOT) : null;
        this.telefone = novoTelefone != null && !novoTelefone.isBlank() ? novoTelefone.trim() : null;
        this.empresaOrgao = novaEmpresaOrgao != null && !novaEmpresaOrgao.isBlank() ? novaEmpresaOrgao.trim().toUpperCase(Locale.ROOT) : null;
    }

    private void validarNome(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new InvarianteVioladaException("O nome do agente responsável é obrigatório.");
        }
    }
}
