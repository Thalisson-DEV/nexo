package com.nexo.app.modules.sst.domain;

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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "acoes_governanca_sst")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AcaoGovernancaSst extends BaseEntity {

    @Column(name = "id_corpo", nullable = false)
    private UUID idCorpo;

    @Column(name = "id_base_operacional", nullable = false)
    private UUID idBaseOperacional;

    @Column(name = "id_reuniao")
    private UUID idReuniao;

    @Column(name = "id_execucao_checklist")
    private UUID idExecucaoChecklist;

    @Column(name = "client_request_id")
    private UUID clientRequestId;

    @Column(name = "titulo", nullable = false, length = 180)
    private String titulo;

    @Column(name = "descricao", nullable = false, length = 2000)
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 30)
    private TipoAcaoGovernancaSst tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "prioridade", nullable = false, length = 20)
    private PrioridadeAcaoGovernancaSst prioridade;

    @Column(name = "id_responsavel", nullable = false)
    private UUID idResponsavel;

    @Column(name = "prazo", nullable = false)
    private LocalDate prazo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private StatusAcaoGovernancaSst status;

    @Column(name = "resultado", length = 2000)
    private String resultado;

    @Column(name = "motivo_cancelamento", length = 1000)
    private String motivoCancelamento;

    @Column(name = "data_inicio")
    private OffsetDateTime dataInicio;

    @Column(name = "data_conclusao")
    private OffsetDateTime dataConclusao;

    @Column(name = "id_usuario_conclusao")
    private UUID idUsuarioConclusao;

    @Column(name = "data_cancelamento")
    private OffsetDateTime dataCancelamento;

    @Column(name = "id_usuario_cancelamento")
    private UUID idUsuarioCancelamento;

    public AcaoGovernancaSst(UUID idCorpo, UUID idBaseOperacional, UUID idReuniao,
                            UUID idExecucaoChecklist, UUID clientRequestId, String titulo,
                            String descricao, TipoAcaoGovernancaSst tipo,
                            PrioridadeAcaoGovernancaSst prioridade, UUID idResponsavel,
                            LocalDate prazo) {
        this.idCorpo = Objects.requireNonNull(idCorpo, "Corpo é obrigatório");
        this.idBaseOperacional = Objects.requireNonNull(idBaseOperacional, "Base é obrigatória");
        this.idReuniao = idReuniao;
        this.idExecucaoChecklist = idExecucaoChecklist;
        this.clientRequestId = clientRequestId;
        this.titulo = textoObrigatorio(titulo, "Título", 180);
        this.descricao = textoObrigatorio(descricao, "Descrição", 2000);
        this.tipo = Objects.requireNonNull(tipo, "Tipo é obrigatório");
        this.prioridade = Objects.requireNonNull(prioridade, "Prioridade é obrigatória");
        this.idResponsavel = Objects.requireNonNull(idResponsavel, "Responsável é obrigatório");
        this.prazo = Objects.requireNonNull(prazo, "Prazo é obrigatório");
        this.status = StatusAcaoGovernancaSst.ABERTA;
    }

    public void atualizar(String titulo, String descricao, TipoAcaoGovernancaSst tipo,
                          PrioridadeAcaoGovernancaSst prioridade, UUID idResponsavel,
                          LocalDate prazo) {
        exigirAbertaOuEmAndamento();
        this.titulo = textoObrigatorio(titulo, "Título", 180);
        this.descricao = textoObrigatorio(descricao, "Descrição", 2000);
        this.tipo = Objects.requireNonNull(tipo, "Tipo é obrigatório");
        this.prioridade = Objects.requireNonNull(prioridade, "Prioridade é obrigatória");
        this.idResponsavel = Objects.requireNonNull(idResponsavel, "Responsável é obrigatório");
        this.prazo = Objects.requireNonNull(prazo, "Prazo é obrigatório");
    }

    public void iniciar(OffsetDateTime agora) {
        if (status != StatusAcaoGovernancaSst.ABERTA) {
            throw new InvarianteVioladaException("Somente uma ação aberta pode ser iniciada.");
        }
        status = StatusAcaoGovernancaSst.EM_ANDAMENTO;
        dataInicio = Objects.requireNonNull(agora, "Instante é obrigatório");
    }

    public void concluir(String resultado, UUID idUsuario, OffsetDateTime agora) {
        exigirAbertaOuEmAndamento();
        this.resultado = textoObrigatorio(resultado, "Resultado", 2000);
        this.idUsuarioConclusao = Objects.requireNonNull(idUsuario, "Usuário é obrigatório");
        this.dataConclusao = Objects.requireNonNull(agora, "Instante é obrigatório");
        this.status = StatusAcaoGovernancaSst.CONCLUIDA;
    }

    public void cancelar(String motivo, UUID idUsuario, OffsetDateTime agora) {
        exigirAbertaOuEmAndamento();
        this.motivoCancelamento = textoObrigatorio(motivo, "Motivo do cancelamento", 1000);
        this.idUsuarioCancelamento = Objects.requireNonNull(idUsuario, "Usuário é obrigatório");
        this.dataCancelamento = Objects.requireNonNull(agora, "Instante é obrigatório");
        this.status = StatusAcaoGovernancaSst.CANCELADA;
    }

    public StatusPrazoGovernancaSst statusPrazo(LocalDate hoje) {
        Objects.requireNonNull(hoje, "Data de cálculo é obrigatória");
        if (status == StatusAcaoGovernancaSst.CONCLUIDA || status == StatusAcaoGovernancaSst.CANCELADA) {
            return StatusPrazoGovernancaSst.ENCERRADA;
        }
        if (prazo.isBefore(hoje)) return StatusPrazoGovernancaSst.VENCIDA;
        if (prazo.isEqual(hoje)) return StatusPrazoGovernancaSst.VENCE_HOJE;
        return StatusPrazoGovernancaSst.NO_PRAZO;
    }

    public boolean pertenceAoCorpo(UUID corpoId) {
        return idCorpo.equals(corpoId);
    }

    private void exigirAbertaOuEmAndamento() {
        if (status != StatusAcaoGovernancaSst.ABERTA && status != StatusAcaoGovernancaSst.EM_ANDAMENTO) {
            throw new InvarianteVioladaException("A ação encerrada não pode mais ser alterada.");
        }
    }

    private static String textoObrigatorio(String valor, String campo, int limite) {
        if (valor == null || valor.isBlank()) {
            throw new InvarianteVioladaException(campo + " é obrigatório.");
        }
        String normalizado = valor.trim();
        if (normalizado.length() > limite) {
            throw new InvarianteVioladaException(campo + " excede " + limite + " caracteres.");
        }
        return normalizado;
    }
}
