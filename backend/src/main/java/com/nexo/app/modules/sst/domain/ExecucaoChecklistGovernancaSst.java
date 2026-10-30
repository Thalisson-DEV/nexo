package com.nexo.app.modules.sst.domain;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "execucoes_checklist_governanca_sst")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExecucaoChecklistGovernancaSst extends BaseEntity {

    @Column(name = "id_modelo", nullable = false)
    private UUID idModelo;

    @Column(name = "id_formulario", nullable = false)
    private UUID idFormulario;

    @Column(name = "id_corpo", nullable = false)
    private UUID idCorpo;

    @Column(name = "id_base_operacional", nullable = false)
    private UUID idBaseOperacional;

    @Column(name = "id_executor", nullable = false)
    private UUID idExecutor;

    @Column(name = "client_request_id")
    private UUID clientRequestId;

    @Column(name = "data_execucao", nullable = false)
    private OffsetDateTime dataExecucao;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusExecucaoChecklistGovernancaSst status;

    @Column(name = "observacoes", length = 2000)
    private String observacoes;

    @Column(name = "data_conclusao")
    private OffsetDateTime dataConclusao;

    @OrderBy("dataCriacao ASC")
    @OneToMany(mappedBy = "execucao", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<RespostaChecklistGovernancaSst> respostas = new ArrayList<>();

    @OrderBy("dataCriacao ASC")
    @OneToMany(mappedBy = "execucao", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ConstatacaoChecklistGovernancaSst> constatacoes = new ArrayList<>();

    public ExecucaoChecklistGovernancaSst(UUID idModelo, UUID idFormulario, UUID idCorpo,
                                          UUID idBaseOperacional, UUID idExecutor,
                                          UUID clientRequestId, OffsetDateTime dataExecucao,
                                          String observacoes) {
        this.idModelo = Objects.requireNonNull(idModelo, "Modelo é obrigatório");
        this.idFormulario = Objects.requireNonNull(idFormulario, "Formulário é obrigatório");
        this.idCorpo = Objects.requireNonNull(idCorpo, "Corpo é obrigatório");
        this.idBaseOperacional = Objects.requireNonNull(idBaseOperacional, "Base é obrigatória");
        this.idExecutor = Objects.requireNonNull(idExecutor, "Executor é obrigatório");
        this.clientRequestId = clientRequestId;
        this.dataExecucao = Objects.requireNonNull(dataExecucao, "Data de execução é obrigatória");
        this.observacoes = normalizarOpcional(observacoes, 2000);
        this.status = StatusExecucaoChecklistGovernancaSst.RASCUNHO;
    }

    public void substituirRespostas(List<RespostaChecklistGovernancaSst.NovaResposta> novas) {
        exigirRascunho();
        respostas.clear();
        constatacoes.clear();
        for (RespostaChecklistGovernancaSst.NovaResposta nova : novas) {
            RespostaChecklistGovernancaSst resposta = new RespostaChecklistGovernancaSst(this, nova);
            respostas.add(resposta);
            resposta.getOpcoes().stream()
                    .filter(OpcaoRespostaChecklistGovernancaSst::isNaoConforme)
                    .forEach(opcao -> constatacoes.add(new ConstatacaoChecklistGovernancaSst(
                            this, resposta, opcao.getIdOpcao(), resposta.getPerguntaSnapshot(),
                            opcao.getOpcaoSnapshot(), opcao.getGravidade(),
                            "Não conformidade identificada em: " + resposta.getPerguntaSnapshot())));
        }
    }

    /**
     * Remove o rascunho anterior antes de uma nova gravação. A camada de aplicação
     * faz flush entre esta etapa e a inclusão para não colidir com a chave única
     * (execução, pergunta) durante a ordenação de comandos do Hibernate.
     */
    public void limparRespostas() {
        exigirRascunho();
        respostas.clear();
        constatacoes.clear();
    }

    public void concluir(OffsetDateTime agora) {
        exigirRascunho();
        if (respostas.stream().anyMatch(RespostaChecklistGovernancaSst::obrigatoriaSemResposta)) {
            throw new InvarianteVioladaException(
                    "Existem itens obrigatórios sem resposta. Complete-os antes de concluir o checklist.");
        }
        status = StatusExecucaoChecklistGovernancaSst.CONCLUIDA;
        dataConclusao = Objects.requireNonNull(agora, "Instante é obrigatório");
    }

    public void cancelar() {
        exigirRascunho();
        status = StatusExecucaoChecklistGovernancaSst.CANCELADA;
    }

    public boolean pertenceAoCorpo(UUID corpoId) {
        return idCorpo.equals(corpoId);
    }

    private void exigirRascunho() {
        if (status != StatusExecucaoChecklistGovernancaSst.RASCUNHO) {
            throw new InvarianteVioladaException("Somente um checklist em rascunho pode ser alterado.");
        }
    }

    private static String normalizarOpcional(String valor, int limite) {
        if (valor == null || valor.isBlank()) return null;
        String normalizado = valor.trim();
        if (normalizado.length() > limite) {
            throw new InvarianteVioladaException("A observação excede " + limite + " caracteres.");
        }
        return normalizado;
    }
}
