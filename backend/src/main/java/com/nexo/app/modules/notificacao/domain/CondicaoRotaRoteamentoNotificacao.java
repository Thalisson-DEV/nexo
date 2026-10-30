package com.nexo.app.modules.notificacao.domain;

import org.springframework.util.StringUtils;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Condição segura de uma rota de destinatário. */
@Entity
@Table(name = "condicoes_rota_roteamento_notificacao")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CondicaoRotaRoteamentoNotificacao extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_rota", nullable = false)
    private RotaRoteamentoNotificacao rota;

    @Column(name = "campo", nullable = false, length = 60)
    private String campo;

    @Enumerated(EnumType.STRING)
    @Column(name = "operador", nullable = false, length = 20)
    private OperadorCondicao operador;

    @Column(name = "valor", nullable = false, length = 500)
    private String valor;

    CondicaoRotaRoteamentoNotificacao(RotaRoteamentoNotificacao rota, String campo,
                                      OperadorCondicao operador, String valor) {
        if (rota == null) {
            throw new InvarianteVioladaException("A rota da condição é obrigatória.");
        }
        if (!StringUtils.hasText(campo)) {
            throw new InvarianteVioladaException("Escolha o campo da condição.");
        }
        ComparadorCondicao.validar(operador, valor);
        this.rota = rota;
        this.campo = campo.trim();
        this.operador = operador;
        this.valor = valor.trim();
    }

    public boolean satisfeitaPor(Object valorDoContexto) {
        return ComparadorCondicao.satisfeitaPor(operador, valor, valorDoContexto);
    }
}
