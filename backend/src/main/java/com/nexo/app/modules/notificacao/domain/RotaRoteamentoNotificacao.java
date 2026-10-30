package com.nexo.app.modules.notificacao.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Uma alternativa ordenada de uma política de roteamento. */
@Entity
@Table(name = "rotas_roteamento_notificacao")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RotaRoteamentoNotificacao extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_politica", nullable = false)
    private PoliticaRoteamentoNotificacao politica;

    @Column(name = "ordem", nullable = false)
    private int ordem;

    @Column(name = "padrao", nullable = false)
    private boolean padrao;

    @Column(name = "id_usuario", nullable = false)
    private UUID usuarioId;

    @OneToMany(mappedBy = "rota", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CondicaoRotaRoteamentoNotificacao> condicoes = new ArrayList<>();

    RotaRoteamentoNotificacao(PoliticaRoteamentoNotificacao politica, int ordem,
                              boolean padrao, UUID usuarioId) {
        if (politica == null) {
            throw new InvarianteVioladaException("A política da rota é obrigatória.");
        }
        if (ordem < 0) {
            throw new InvarianteVioladaException("A ordem da rota não pode ser negativa.");
        }
        if (usuarioId == null) {
            throw new InvarianteVioladaException("Escolha o usuário destinatário da rota.");
        }
        this.politica = politica;
        this.ordem = ordem;
        this.padrao = padrao;
        this.usuarioId = usuarioId;
    }

    public CondicaoRotaRoteamentoNotificacao adicionarCondicao(
            String campo, OperadorCondicao operador, String valor) {
        GatilhoNotificacao gatilho = politica.exigirGatilho();
        if (!gatilho.declaraVariavel(campo)) {
            throw new InvarianteVioladaException(
                    "O gatilho '" + gatilho.getNome() + "' não fornece a informação '" + campo
                            + "'. Disponíveis: " + String.join(", ", gatilho.getVariaveis()) + ".");
        }
        CondicaoRotaRoteamentoNotificacao condicao =
                new CondicaoRotaRoteamentoNotificacao(this, campo, operador, valor);
        this.condicoes.add(condicao);
        return condicao;
    }

    public List<CondicaoRotaRoteamentoNotificacao> condicoesAtivas() {
        return this.condicoes.stream()
                .filter(CondicaoRotaRoteamentoNotificacao::isAtivo)
                .toList();
    }

    public boolean satisfeitaPor(Map<String, Object> contexto) {
        return condicoesAtivas().stream()
                .allMatch(condicao -> condicao.satisfeitaPor(
                        contexto == null ? null : contexto.get(condicao.getCampo())));
    }

    void exigirUtilizavel() {
        if (padrao && !condicoesAtivas().isEmpty()) {
            throw new InvarianteVioladaException("A rota padrão não pode possuir condições.");
        }
        if (!padrao && condicoesAtivas().isEmpty()) {
            throw new InvarianteVioladaException("Uma rota específica deve possuir condições.");
        }
    }
}
