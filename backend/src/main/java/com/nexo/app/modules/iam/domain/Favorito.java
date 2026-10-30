package com.nexo.app.modules.iam.domain;

import com.nexo.app.shared.domain.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

/**
 * Representa um link favorito salvo pelo usuário para navegação rápida.
 */
@Entity
@Table(name = "favoritos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Favorito extends BaseEntity {

    @NotBlank(message = "O título do favorito é obrigatório")
    @Column(name = "titulo", nullable = false, length = 100)
    private String titulo;

    @NotBlank(message = "A rota do favorito é obrigatória")
    @Column(name = "rota", nullable = false)
    private String rota;

    @Column(name = "icone", length = 100)
    private String icone;

    @NotNull(message = "O usuário proprietário é obrigatório")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    public Favorito(String titulo, String rota, String icone, Usuario usuario) {
        this.titulo = Objects.requireNonNull(titulo, "Título é obrigatório").trim();
        this.rota = Objects.requireNonNull(rota, "Rota é obrigatória").trim();
        this.icone = icone;
        this.usuario = Objects.requireNonNull(usuario, "Usuário é obrigatório");
    }

    /**
     * Atualiza os dados básicos do favorito.
     */
    public void atualizar(String titulo, String rota, String icone) {
        if (titulo != null && !titulo.isBlank()) this.titulo = titulo.trim();
        if (rota != null && !rota.isBlank()) this.rota = rota.trim();
        this.icone = icone;
    }
}
