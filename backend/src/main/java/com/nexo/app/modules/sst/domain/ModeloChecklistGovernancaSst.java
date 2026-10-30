package com.nexo.app.modules.sst.domain;

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

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "modelos_checklist_governanca_sst")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ModeloChecklistGovernancaSst extends BaseEntity {

    @Column(name = "id_formulario", nullable = false)
    private UUID idFormulario;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_corpo", nullable = false, length = 20)
    private TipoCorpoGovernancaSst tipoCorpo;

    @Column(name = "nome", nullable = false, length = 180)
    private String nome;

    @Column(name = "orientacao", length = 1000)
    private String orientacao;

    @Column(name = "referencia_normativa", length = 500)
    private String referenciaNormativa;

    public ModeloChecklistGovernancaSst(UUID idFormulario, TipoCorpoGovernancaSst tipoCorpo,
                                        String nome, String orientacao, String referenciaNormativa) {
        this.idFormulario = Objects.requireNonNull(idFormulario, "Formulário é obrigatório");
        this.tipoCorpo = Objects.requireNonNull(tipoCorpo, "Tipo de corpo é obrigatório");
        atualizar(nome, orientacao, referenciaNormativa);
    }

    public void atualizar(String nome, String orientacao, String referenciaNormativa) {
        if (nome == null || nome.isBlank()) {
            throw new InvarianteVioladaException("O nome do modelo é obrigatório.");
        }
        this.nome = limitar(nome.trim(), 180, "nome");
        this.orientacao = limitarOpcional(orientacao, 1000, "orientação");
        this.referenciaNormativa = limitarOpcional(referenciaNormativa, 500, "referência normativa");
    }

    private static String limitarOpcional(String valor, int maximo, String campo) {
        return valor == null || valor.isBlank() ? null : limitar(valor.trim(), maximo, campo);
    }

    private static String limitar(String valor, int maximo, String campo) {
        if (valor.length() > maximo) {
            throw new InvarianteVioladaException("A " + campo + " excede " + maximo + " caracteres.");
        }
        return valor;
    }
}
