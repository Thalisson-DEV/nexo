package com.nexo.app.modules.frota.domain;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Tipo de implemento (cesto aéreo, guindauto, carroceria, reboque...). */
@Entity
@Table(name = "frota_tipos_implemento")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TipoImplemento extends BaseEntity {

    @Column(name = "nome", nullable = false, length = 80)
    private String nome;

    @Column(name = "descricao", length = 255)
    private String descricao;

    public TipoImplemento(String nome, String descricao) {
        preencher(nome, descricao);
    }

    public void atualizar(String nome, String descricao) {
        if (!isAtivo()) {
            throw new InvarianteVioladaException("Não é possível alterar um tipo de implemento desativado.");
        }
        preencher(nome, descricao);
    }

    private void preencher(String nome, String descricao) {
        this.nome = Textos.obrigatorio(nome, "nome do tipo de implemento", 80).toUpperCase();
        this.descricao = Textos.opcional(descricao, 255);
    }
}
