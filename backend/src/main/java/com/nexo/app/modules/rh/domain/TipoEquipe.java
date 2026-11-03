package com.nexo.app.modules.rh.domain;

import com.nexo.app.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

/**
 * Representa os diferentes tipos de equipes operacionais (ex: PESADA, LEVE, COMPRESSOR).
 */
@Entity
@Table(name = "tipos_equipe")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TipoEquipe extends BaseEntity {

    @NotBlank(message = "O nome do tipo de equipe é obrigatório")
    @Size(min = 3, max = 50, message = "O nome do tipo de equipe deve ter entre 3 e 50 caracteres")
    @Column(name = "nome", nullable = false, unique = true, length = 50)
    private String nome;

    @Column(name = "jornada_minutos", nullable = false)
    private Integer jornadaMinutos = 480;

    @Column(name = "plataforma_minutos", nullable = false)
    private Integer plataformaMinutos = 30;

    public TipoEquipe(String nome) {
        validarNome(nome);
        this.nome = nome.toUpperCase();
    }

    public void atualizarNome(String novoNome) {
        validarNome(novoNome);
        validarStatus();
        this.nome = novoNome.toUpperCase();
    }

    /** Jornada e tempo de plataforma que as equipes do tipo usam na programação. Nulo mantém o valor atual. */
    public void definirJornada(Integer jornada, Integer plataforma) {
        validarStatus();
        Integer novaJornada = jornada != null ? jornada : this.jornadaMinutos;
        Integer novaPlataforma = plataforma != null ? plataforma : this.plataformaMinutos;
        Equipe.validarJornada(novaJornada, novaPlataforma);
        this.jornadaMinutos = novaJornada;
        this.plataformaMinutos = novaPlataforma;
    }

    private void validarStatus() {
        if (!isAtivo()) {
            throw new IllegalStateException("Não é possível alterar um registro desativado.");
        }
    }

    private void validarNome(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("Nome do tipo de equipe inválido");
        }
    }
}
