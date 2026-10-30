package com.nexo.app.modules.notificacao.domain;

import java.util.UUID;

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
 * Escolha do usuário sobre receber ou não uma categoria, por canal.
 *
 * <p>A ausência de linha significa "tudo ligado": não se cria uma linha por
 * categoria para cada usuário do sistema só para registrar o padrão. A linha nasce
 * quando o usuário muda alguma coisa.</p>
 */
@Entity
@Table(name = "preferencias_notificacao")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PreferenciaNotificacao extends BaseEntity {

    @Column(name = "id_usuario", nullable = false, updatable = false)
    private UUID idUsuario;

    @Enumerated(EnumType.STRING)
    @Column(name = "categoria", nullable = false, updatable = false, length = 30)
    private CategoriaNotificacao categoria;

    @Column(name = "in_app", nullable = false)
    private boolean inApp = true;

    /**
     * Nasce ligado, como o sininho. O canal é opt-out: quem não quer receber por
     * e-mail desliga na tela de preferências, e é essa escolha que a linha registra.
     * Exigir que cada pessoa ligasse o canal faria toda regra recém-configurada não
     * alcançar ninguém — indistinguível de um defeito.
     */
    @Column(name = "email", nullable = false)
    private boolean email = true;

    public PreferenciaNotificacao(UUID idUsuario, CategoriaNotificacao categoria,
                                  boolean inApp, boolean email) {
        if (idUsuario == null) {
            throw new InvarianteVioladaException("A preferência precisa de um usuário.");
        }
        if (categoria == null) {
            throw new InvarianteVioladaException("A preferência precisa de uma categoria.");
        }
        this.idUsuario = idUsuario;
        this.categoria = categoria;
        this.inApp = inApp;
        this.email = email && categoria.permiteEmail();
    }

    public void ajustar(boolean inApp, boolean email) {
        this.inApp = inApp;
        // O veto da categoria vence a escolha do usuário: uma tela desatualizada não
        // pode gravar e-mail ligado em marcação.
        this.email = email && this.categoria.permiteEmail();
    }

    public boolean permite(CanalNotificacao canal) {
        return switch (canal) {
            case IN_APP -> this.inApp;
            case EMAIL -> this.email;
        };
    }
}
