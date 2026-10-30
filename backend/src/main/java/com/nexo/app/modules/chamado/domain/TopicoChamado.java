package com.nexo.app.modules.chamado.domain;

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
import org.springframework.util.StringUtils;

/** Catálogo empresarial de assuntos que direciona a triagem e define o SLA inicial. */
@Entity
@Table(name = "topicos_chamado")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TopicoChamado extends BaseEntity {

    @Column(name = "codigo", nullable = false, unique = true, length = 50)
    private String codigo;

    @Column(name = "nome", nullable = false, length = 100)
    private String nome;

    @Column(name = "descricao", nullable = false, length = 500)
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(name = "prioridade_padrao", nullable = false, length = 20)
    private PrioridadeChamado prioridadePadrao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_equipe_padrao", nullable = false)
    private EquipeAtendimento equipePadrao;

    @Column(name = "sla_primeira_resposta_horas", nullable = false)
    private Integer slaPrimeiraRespostaHoras;

    @Column(name = "sla_resolucao_horas", nullable = false)
    private Integer slaResolucaoHoras;

    @Column(name = "pausar_sla_aguardando_solicitante", nullable = false)
    private boolean pausarSlaAguardandoSolicitante;

    @Column(name = "prazo_reabertura_horas", nullable = false)
    private Integer prazoReaberturaHoras;

    @Column(name = "ordem_exibicao", nullable = false)
    private Integer ordemExibicao;

    public TopicoChamado(String codigo, String nome, String descricao,
                         PrioridadeChamado prioridadePadrao, EquipeAtendimento equipePadrao,
                         Integer slaPrimeiraRespostaHoras, Integer slaResolucaoHoras,
                         boolean pausarSlaAguardandoSolicitante, Integer prazoReaberturaHoras,
                         Integer ordemExibicao) {
        atualizar(codigo, nome, descricao, prioridadePadrao, equipePadrao,
                slaPrimeiraRespostaHoras, slaResolucaoHoras,
                pausarSlaAguardandoSolicitante, prazoReaberturaHoras, ordemExibicao);
    }

    public void atualizar(String codigo, String nome, String descricao,
                          PrioridadeChamado prioridadePadrao, EquipeAtendimento equipePadrao,
                          Integer slaPrimeiraRespostaHoras, Integer slaResolucaoHoras,
                          boolean pausarSlaAguardandoSolicitante, Integer prazoReaberturaHoras,
                          Integer ordemExibicao) {
        this.codigo = exigir(codigo, "O código do tópico é obrigatório.").toUpperCase();
        this.nome = exigir(nome, "O nome do tópico é obrigatório.");
        this.descricao = exigir(descricao, "A descrição do tópico é obrigatória.");
        if (equipePadrao == null) {
            throw new InvarianteVioladaException("A equipe padrão do tópico é obrigatória.");
        }
        equipePadrao.exigirDisponivel();
        validarPrazo(slaPrimeiraRespostaHoras, "O SLA de primeira resposta");
        validarPrazo(slaResolucaoHoras, "O SLA de resolução");
        validarPrazo(prazoReaberturaHoras, "O prazo de reabertura");
        if (slaPrimeiraRespostaHoras > slaResolucaoHoras) {
            throw new InvarianteVioladaException(
                    "O SLA de primeira resposta não pode superar o SLA de resolução.");
        }
        this.prioridadePadrao = prioridadePadrao == null ? PrioridadeChamado.NORMAL : prioridadePadrao;
        this.equipePadrao = equipePadrao;
        this.slaPrimeiraRespostaHoras = slaPrimeiraRespostaHoras;
        this.slaResolucaoHoras = slaResolucaoHoras;
        this.pausarSlaAguardandoSolicitante = pausarSlaAguardandoSolicitante;
        this.prazoReaberturaHoras = prazoReaberturaHoras;
        this.ordemExibicao = ordemExibicao == null ? 0 : ordemExibicao;
    }

    public void exigirDisponivel() {
        if (!isAtivo()) {
            throw new InvarianteVioladaException("O tópico selecionado não está mais disponível.");
        }
    }

    private static String exigir(String valor, String mensagem) {
        if (!StringUtils.hasText(valor)) {
            throw new InvarianteVioladaException(mensagem);
        }
        return valor.trim();
    }

    private static void validarPrazo(Integer horas, String campo) {
        if (horas == null || horas < 1) {
            throw new InvarianteVioladaException(campo + " deve ser maior que zero.");
        }
    }
}
