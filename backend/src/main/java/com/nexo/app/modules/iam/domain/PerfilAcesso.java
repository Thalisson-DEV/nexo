package com.nexo.app.modules.iam.domain;

import com.nexo.app.shared.domain.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Representa os perfis de acesso do sistema (Roles).
 * Define o agrupamento de permissões que um usuário pode possuir.
 */
@Entity
@Table(name = "perfis_acesso")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PerfilAcesso extends BaseEntity {

    @NotBlank(message = "O nome do perfil é obrigatório")
    @Size(min = 3, max = 50, message = "O nome do perfil deve ter entre 3 e 50 caracteres")
    @Column(name = "nome", nullable = false, unique = true, length = 50)
    private String nome;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "perfil_permissoes",
            joinColumns = @JoinColumn(name = "id_perfil"),
            inverseJoinColumns = @JoinColumn(name = "id_permissao")
    )
    private Set<Permissao> permissoes = new HashSet<>();

    public PerfilAcesso(String nome) {
        validarNome(nome);
        this.nome = nome.toUpperCase().trim();
    }

    public void atualizar(String novoNome) {
        validarNome(novoNome);
        this.nome = novoNome.toUpperCase().trim();
    }

    /**
     * Substitui de uma vez todas as permissões do perfil. Uma única alteração (e uma única
     * versão) para qualquer quantidade de marcações, em vez de uma por permissão.
     */
    public void definirPermissoes(Collection<Permissao> novas) {
        if (novas == null || novas.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("Lista de permissões inválida");
        }
        this.permissoes.retainAll(novas);
        this.permissoes.addAll(novas);
    }

    public void adicionarPermissao(Permissao permissao) {
        if (permissao == null) {
            throw new IllegalArgumentException("Permissão não pode ser nula");
        }
        this.permissoes.add(permissao);
    }

    public void removerPermissao(Permissao permissao) {
        if (permissao == null) {
            throw new IllegalArgumentException("Permissão não pode ser nula");
        }
        this.permissoes.remove(permissao);
    }

    public Set<Permissao> getPermissoes() {
        return Collections.unmodifiableSet(permissoes);
    }

    private void validarNome(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("Nome do perfil inválido");
        }
    }
}
