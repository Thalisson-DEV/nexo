package com.nexo.app.modules.rh.domain;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.UnidadeFederativa;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Locale;

/**
 * Representa uma Base Operacional (sede regional) da empresa.
 * Vinculada a um Centro de Faturamento (Comercial).
 */
@Entity
@Table(name = "bases_operacionais")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BaseOperacional extends BaseEntity {

    @NotBlank(message = "Nome da base é obrigatório")
    @Size(min = 3, max = 100, message = "O nome da base deve ter entre 3 e 100 caracteres")
    @Column(name = "nome", nullable = false, unique = true)
    private String nome;

    @NotNull(message = "Centro de faturamento é obrigatório")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_centro_custo", nullable = false)
    private CentroFaturamento centroFaturamento;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "raio")
    private Integer raio;

    @Column(name = "uf", length = 2)
    private String uf;

    @Column(name = "endereco", length = 300)
    private String endereco;

    public BaseOperacional(String nome, String uf, String endereco,
                           CentroFaturamento centroFaturamento) {
        validarCampos(nome, uf, endereco, centroFaturamento);
        this.nome = nome.toUpperCase(Locale.ROOT);
        this.uf = UnidadeFederativa.normalizar(uf);
        this.endereco = endereco.trim();
        this.centroFaturamento = centroFaturamento;
        this.raio = 0;
        
        if (!centroFaturamento.isAtivo()) {
            throw new InvarianteVioladaException("Não é possível vincular uma base a um centro de faturamento desativado");
        }
    }

    public void atualizar(String nome, String uf, String endereco,
                          CentroFaturamento centroFaturamento) {
        validarStatus();
        validarCampos(nome, uf, endereco, centroFaturamento);
        this.nome = nome.toUpperCase(Locale.ROOT);
        this.uf = UnidadeFederativa.normalizar(uf);
        this.endereco = endereco.trim();
        this.centroFaturamento = centroFaturamento;
    }

    public void configurarLocalizacao(Double latitude, Double longitude, Integer raio) {
        validarStatus();
        if (raio != null && raio < 0) {
            throw new InvarianteVioladaException("O raio de atuação não pode ser negativo");
        }
        this.latitude = latitude;
        this.longitude = longitude;
        this.raio = raio != null ? raio : 0;
    }

    private void validarStatus() {
        if (!isAtivo()) {
            throw new IllegalStateException("Não é possível alterar um registro desativado.");
        }
    }

    private void validarCampos(String nome, String uf, String endereco,
                               CentroFaturamento centroFaturamento) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("Nome da base operacional inválido");
        }
        UnidadeFederativa.normalizar(uf);
        if (endereco == null || endereco.isBlank() || endereco.trim().length() > 300) {
            throw new IllegalArgumentException("Endereço da base operacional inválido");
        }
        if (centroFaturamento == null) {
            throw new IllegalArgumentException("Centro de faturamento é obrigatório");
        }
    }
}
