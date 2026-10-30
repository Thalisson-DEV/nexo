package com.nexo.app.modules.notificacao.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.util.StringUtils;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Matriz de decisão reutilizável que escolhe um usuário para um gatilho. */
@Entity
@Table(name = "politicas_roteamento_notificacao")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PoliticaRoteamentoNotificacao extends BaseEntity {

    @Column(name = "chave_gatilho", nullable = false, updatable = false, length = 80)
    private String chaveGatilho;

    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    @Column(name = "descricao", length = 500)
    private String descricao;

    @OneToMany(mappedBy = "politica", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RotaRoteamentoNotificacao> rotas = new ArrayList<>();

    public PoliticaRoteamentoNotificacao(GatilhoNotificacao gatilho, String nome, String descricao) {
        validarGatilho(gatilho);
        this.chaveGatilho = gatilho.getChave();
        atualizar(nome, descricao);
    }

    public void atualizar(String nome, String descricao) {
        if (!StringUtils.hasText(nome)) {
            throw new InvarianteVioladaException("O nome da política é obrigatório.");
        }
        String nomeNormalizado = nome.trim();
        if (nomeNormalizado.length() > 120) {
            throw new InvarianteVioladaException("O nome da política excede 120 caracteres.");
        }
        if (descricao != null && descricao.trim().length() > 500) {
            throw new InvarianteVioladaException("A descrição da política excede 500 caracteres.");
        }
        this.nome = nomeNormalizado;
        this.descricao = StringUtils.hasText(descricao) ? descricao.trim() : null;
    }

    public RotaRoteamentoNotificacao adicionarRota(int ordem, boolean padrao, UUID usuarioId) {
        RotaRoteamentoNotificacao rota = new RotaRoteamentoNotificacao(this, ordem, padrao, usuarioId);
        this.rotas.add(rota);
        return rota;
    }

    public void limparRotas() {
        this.rotas.clear();
    }

    public List<RotaRoteamentoNotificacao> rotasAtivasOrdenadas() {
        return this.rotas.stream()
                .filter(RotaRoteamentoNotificacao::isAtivo)
                .sorted(java.util.Comparator.comparingInt(RotaRoteamentoNotificacao::getOrdem))
                .toList();
    }

    public void exigirUtilizavel() {
        List<RotaRoteamentoNotificacao> ativas = rotasAtivasOrdenadas();
        long padroes = ativas.stream().filter(RotaRoteamentoNotificacao::isPadrao).count();
        if (padroes != 1) {
            throw new InvarianteVioladaException("A política deve possuir exatamente uma rota padrão.");
        }
        if (ativas.stream().noneMatch(rota -> !rota.isPadrao())) {
            throw new InvarianteVioladaException("Defina ao menos uma rota condicional.");
        }
        Set<Integer> ordens = new HashSet<>();
        for (RotaRoteamentoNotificacao rota : ativas) {
            if (!ordens.add(rota.getOrdem())) {
                throw new InvarianteVioladaException("As ordens das rotas não podem se repetir.");
            }
            rota.exigirUtilizavel();
        }
    }

    public GatilhoNotificacao exigirGatilho() {
        return GatilhoNotificacao.porChave(this.chaveGatilho)
                .orElseThrow(() -> new InvarianteVioladaException(
                        "A política aponta para um gatilho inexistente: " + this.chaveGatilho));
    }

    private static void validarGatilho(GatilhoNotificacao gatilho) {
        if (gatilho == null) {
            throw new InvarianteVioladaException("Escolha o gatilho da política.");
        }
        if (gatilho.getModo() != ModoGatilho.POR_OCORRENCIA) {
            throw new InvarianteVioladaException(
                    "Políticas de roteamento exigem um gatilho por ocorrência.");
        }
    }
}
