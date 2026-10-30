package com.nexo.app.modules.chamado.domain;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "equipes_atendimento_chamado")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EquipeAtendimento extends BaseEntity {

    @Column(name = "codigo", nullable = false, unique = true, length = 50)
    private String codigo;

    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    @Column(name = "descricao", length = 500)
    private String descricao;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "equipe_atendimento_chamado_membros",
            joinColumns = @JoinColumn(name = "id_equipe"))
    @Column(name = "usuario_id", nullable = false)
    private Set<UUID> membros = new LinkedHashSet<>();

    public EquipeAtendimento(String codigo, String nome, String descricao, Set<UUID> membros) {
        atualizar(codigo, nome, descricao, membros);
    }

    public void atualizar(String codigo, String nome, String descricao, Set<UUID> membros) {
        this.codigo = exigir(codigo, "O código da equipe é obrigatório.").toUpperCase();
        this.nome = exigir(nome, "O nome da equipe é obrigatório.");
        this.descricao = StringUtils.hasText(descricao) ? descricao.trim() : null;
        this.membros.clear();
        if (membros != null) {
            this.membros.addAll(membros.stream().filter(java.util.Objects::nonNull).toList());
        }
    }

    /** Equipes sem membros configurados aceitam atribuição livre durante a implantação. */
    public void exigirMembro(UUID usuarioId) {
        exigirDisponivel();
        if (usuarioId == null) {
            return;
        }
        if (!membros.isEmpty() && !membros.contains(usuarioId)) {
            throw new InvarianteVioladaException(
                    "O responsável informado não pertence à equipe '" + nome + "'.");
        }
    }

    public void exigirDisponivel() {
        if (!isAtivo()) {
            throw new InvarianteVioladaException("A equipe de atendimento selecionada está inativa.");
        }
    }

    private static String exigir(String valor, String mensagem) {
        if (!StringUtils.hasText(valor)) {
            throw new InvarianteVioladaException(mensagem);
        }
        return valor.trim();
    }
}
