package com.nexo.app.modules.sst.domain;

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

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "constatacoes_checklist_governanca_sst")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConstatacaoChecklistGovernancaSst extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_execucao", nullable = false)
    private ExecucaoChecklistGovernancaSst execucao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_resposta", nullable = false)
    private RespostaChecklistGovernancaSst resposta;

    @Column(name = "id_opcao")
    private UUID idOpcao;

    @Column(name = "pergunta_snapshot", nullable = false, length = 500)
    private String perguntaSnapshot;

    @Column(name = "opcao_snapshot", length = 255)
    private String opcaoSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "gravidade", length = 20)
    private GravidadeDesvio gravidade;

    @Column(name = "descricao", nullable = false, length = 2000)
    private String descricao;

    @Column(name = "id_acao", unique = true)
    private UUID idAcao;

    ConstatacaoChecklistGovernancaSst(ExecucaoChecklistGovernancaSst execucao,
                                      RespostaChecklistGovernancaSst resposta,
                                      UUID idOpcao, String perguntaSnapshot,
                                      String opcaoSnapshot, GravidadeDesvio gravidade,
                                      String descricao) {
        this.execucao = Objects.requireNonNull(execucao, "Execução é obrigatória");
        this.resposta = Objects.requireNonNull(resposta, "Resposta é obrigatória");
        this.idOpcao = idOpcao;
        this.perguntaSnapshot = Objects.requireNonNull(perguntaSnapshot, "Pergunta é obrigatória");
        this.opcaoSnapshot = opcaoSnapshot;
        this.gravidade = gravidade;
        this.descricao = Objects.requireNonNull(descricao, "Descrição é obrigatória");
    }

    public void vincularAcao(UUID acaoId) {
        if (idAcao != null) {
            throw new InvarianteVioladaException("Esta constatação já foi convertida em ação.");
        }
        this.idAcao = Objects.requireNonNull(acaoId, "Ação é obrigatória");
    }

    public boolean pertenceA(UUID execucaoId) {
        return execucao.getId().equals(execucaoId);
    }
}
