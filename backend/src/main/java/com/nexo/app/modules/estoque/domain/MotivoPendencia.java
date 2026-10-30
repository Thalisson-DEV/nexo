package com.nexo.app.modules.estoque.domain;

import com.nexo.app.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "motivos_pendencia")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MotivoPendencia extends BaseEntity {
    @Column(nullable = false, unique = true, length = 100)
    private String nome;

    @Column(nullable = false, length = 255)
    private String descricao;

    public MotivoPendencia(String nome, String descricao) {
        atualizar(nome, descricao);
    }

    public void atualizar(String nome, String descricao) {
        if (nome == null || nome.trim().length() < 3 || nome.trim().length() > 100
                || descricao == null || descricao.trim().length() < 5 || descricao.trim().length() > 255) {
            throw new IllegalArgumentException("Nome ou descrição da pendência inválidos.");
        }
        this.nome = nome.trim();
        this.descricao = descricao.trim();
    }
}
