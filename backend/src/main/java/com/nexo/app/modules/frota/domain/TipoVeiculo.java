package com.nexo.app.modules.frota.domain;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Tipo de veículo (caminhão, picape, utilitário...). Planos de preventiva e modelos de
 * checklist das próximas fases são definidos por tipo.
 */
@Entity
@Table(name = "frota_tipos_veiculo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TipoVeiculo extends BaseEntity {

    @Column(name = "nome", nullable = false, length = 80)
    private String nome;

    @Column(name = "descricao", length = 255)
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(name = "categoria_cnh_minima", length = 2)
    private CategoriaCnh categoriaCnhMinima;

    public TipoVeiculo(String nome, String descricao, CategoriaCnh categoriaCnhMinima) {
        preencher(nome, descricao, categoriaCnhMinima);
    }

    public void atualizar(String nome, String descricao, CategoriaCnh categoriaCnhMinima) {
        if (!isAtivo()) {
            throw new InvarianteVioladaException("Não é possível alterar um tipo de veículo desativado.");
        }
        preencher(nome, descricao, categoriaCnhMinima);
    }

    private void preencher(String nome, String descricao, CategoriaCnh categoriaCnhMinima) {
        this.nome = Textos.obrigatorio(nome, "nome do tipo de veículo", 80).toUpperCase();
        this.descricao = Textos.opcional(descricao, 255);
        this.categoriaCnhMinima = categoriaCnhMinima;
    }
}
