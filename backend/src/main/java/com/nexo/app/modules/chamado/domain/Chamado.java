package com.nexo.app.modules.chamado.domain;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "chamados")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Chamado extends BaseEntity {

    private static final int LIMITE_ANEXOS = 10;
    private static final int LIMITE_MENSAGEM = 5_000;

    @Column(name = "protocolo", nullable = false, unique = true, updatable = false, length = 32)
    private String protocolo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_topico", nullable = false)
    private TopicoChamado topico;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_equipe_atendimento", nullable = false)
    private EquipeAtendimento equipeAtendimento;

    @Column(name = "assunto", nullable = false, length = 150)
    private String assunto;

    @Column(name = "descricao", nullable = false, columnDefinition = "TEXT")
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(name = "prioridade", nullable = false, length = 20)
    private PrioridadeChamado prioridade;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private StatusChamado status;

    @Column(name = "usuario_solicitante_id", nullable = false, updatable = false)
    private UUID usuarioSolicitanteId;

    @Column(name = "usuario_responsavel_id")
    private UUID usuarioResponsavelId;

    @Column(name = "solucao", columnDefinition = "TEXT")
    private String solucao;

    @Column(name = "data_abertura", nullable = false, updatable = false)
    private OffsetDateTime dataAbertura;

    @Column(name = "data_limite_primeira_resposta", nullable = false)
    private OffsetDateTime dataLimitePrimeiraResposta;

    @Column(name = "data_limite_resolucao", nullable = false)
    private OffsetDateTime dataLimiteResolucao;

    @Column(name = "pausar_sla_aguardando_solicitante", nullable = false, updatable = false)
    private boolean pausarSlaAguardandoSolicitante;

    @Column(name = "prazo_reabertura_horas", nullable = false, updatable = false)
    private Integer prazoReaberturaHoras;

    @Column(name = "data_primeira_resposta")
    private OffsetDateTime dataPrimeiraResposta;

    @Column(name = "data_inicio_atendimento")
    private OffsetDateTime dataInicioAtendimento;

    @Column(name = "data_inicio_pausa_sla")
    private OffsetDateTime dataInicioPausaSla;

    @Column(name = "total_segundos_pausa_sla", nullable = false)
    private long totalSegundosPausaSla;

    @Column(name = "data_resolucao")
    private OffsetDateTime dataResolucao;

    @Column(name = "data_fechamento")
    private OffsetDateTime dataFechamento;

    @Column(name = "data_ultima_interacao", nullable = false)
    private OffsetDateTime dataUltimaInteracao;

    @JsonManagedReference("chamado-anexos")
    @OneToMany(mappedBy = "chamado", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("dataCriacao ASC")
    private List<AnexoChamado> anexos = new ArrayList<>();

    @JsonManagedReference("chamado-interacoes")
    @OneToMany(mappedBy = "chamado", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("dataEvento ASC")
    private List<InteracaoChamado> interacoes = new ArrayList<>();

    /**
     * O que aconteceu com este chamado nesta unidade de trabalho.
     *
     * <p>Uma única chamada a {@link #atualizarAtendimento} pode encaminhar, reatribuir
     * e resolver de uma vez, e cada um desses fatos interessa a uma pessoa diferente.
     * Devolver a lista é o que permite ao caso de uso traduzir o que de fato ocorreu em
     * avisos, em vez de tentar adivinhar comparando o estado antes e depois — o que
     * perderia justamente o encadeamento (quem era o responsável anterior, por
     * exemplo).</p>
     */
    @Transient
    private final transient List<InteracaoChamado> novasInteracoes = new ArrayList<>();

    public Chamado(TopicoChamado topico, String assunto, String descricao,
                   PrioridadeChamado prioridade, UUID usuarioSolicitanteId) {
        if (topico == null) {
            throw new InvarianteVioladaException("Selecione um tópico para o chamado.");
        }
        topico.exigirDisponivel();
        if (usuarioSolicitanteId == null) {
            throw new InvarianteVioladaException("Não foi possível identificar o solicitante.");
        }
        this.topico = topico;
        this.equipeAtendimento = topico.getEquipePadrao();
        this.equipeAtendimento.exigirDisponivel();
        this.assunto = exigir(assunto, "O assunto do chamado é obrigatório.");
        this.descricao = exigir(descricao, "A descrição do chamado é obrigatória.");
        this.prioridade = prioridade == null ? topico.getPrioridadePadrao() : prioridade;
        this.usuarioSolicitanteId = usuarioSolicitanteId;
        this.status = StatusChamado.ABERTO;
        this.dataAbertura = OffsetDateTime.now();
        this.dataUltimaInteracao = dataAbertura;
        this.dataLimitePrimeiraResposta = dataAbertura.plusHours(topico.getSlaPrimeiraRespostaHoras());
        this.dataLimiteResolucao = dataAbertura.plusHours(topico.getSlaResolucaoHoras());
        this.pausarSlaAguardandoSolicitante = topico.isPausarSlaAguardandoSolicitante();
        this.prazoReaberturaHoras = topico.getPrazoReaberturaHoras();
        this.protocolo = gerarProtocolo(dataAbertura);
        registrar(TipoInteracaoChamado.ABERTURA, "Chamado aberto: " + this.assunto,
                false, usuarioSolicitanteId, null, StatusChamado.ABERTO,
                null, idDaEquipe(equipeAtendimento), null, null);
    }

    public AnexoChamado adicionarAnexo(String nomeArquivo, String contentType,
                                        long tamanhoBytes, String objectKey, UUID usuarioAutorId) {
        exigirNaoFinalizado("anexar evidências");
        if (anexos.stream().filter(AnexoChamado::isAtivo).count() >= LIMITE_ANEXOS) {
            throw new InvarianteVioladaException("O chamado aceita no máximo 10 anexos.");
        }
        AnexoChamado anexo = new AnexoChamado(this, nomeArquivo, contentType, tamanhoBytes, objectKey);
        anexos.add(anexo);
        registrar(TipoInteracaoChamado.EVIDENCIA_ADICIONADA,
                "Evidência adicionada: " + anexo.getNomeArquivo(), false, usuarioAutorId,
                null, null, null, null, null, null);
        return anexo;
    }

    public InteracaoChamado responderPeloSolicitante(UUID solicitanteId, String mensagem) {
        exigirProprietario(solicitanteId);
        if (status == StatusChamado.RESOLVIDO) {
            throw new InvarianteVioladaException("Reabra o chamado antes de enviar uma nova resposta.");
        }
        exigirNaoFinalizado("responder");
        InteracaoChamado interacao = registrar(TipoInteracaoChamado.MENSAGEM_PUBLICA,
                exigirMensagem(mensagem), false, solicitanteId,
                null, null, null, null, null, null);
        if (status == StatusChamado.AGUARDANDO_SOLICITANTE) {
            alterarStatus(StatusChamado.EM_ATENDIMENTO, null, solicitanteId,
                    TipoInteracaoChamado.ALTERACAO_STATUS);
        }
        return interacao;
    }

    public InteracaoChamado registrarInteracaoAtendente(UUID usuarioAtendenteId,
                                                         String mensagem, boolean interno) {
        exigirNaoFinalizado("adicionar uma interação");
        if (status == StatusChamado.RESOLVIDO && !interno) {
            throw new InvarianteVioladaException(
                    "Reabra o chamado antes de enviar uma nova mensagem pública.");
        }
        if (!interno) {
            prepararPrimeiraResposta(usuarioAtendenteId);
        }
        return registrar(interno ? TipoInteracaoChamado.COMENTARIO_INTERNO
                        : TipoInteracaoChamado.MENSAGEM_PUBLICA,
                exigirMensagem(mensagem), interno, usuarioAtendenteId,
                null, null, null, null, null, null);
    }

    /** Atualiza equipe, responsável e/ou status em uma única operação de triagem. */
    public void atualizarAtendimento(StatusChamado novoStatus, EquipeAtendimento novaEquipe,
                                     PrioridadeChamado novaPrioridade, UUID responsavelId,
                                     String solucao, UUID usuarioOperadorId) {
        exigirNaoFinalizado("alterar o atendimento");
        if (novoStatus == null && novaEquipe == null && novaPrioridade == null && responsavelId == null) {
            throw new InvarianteVioladaException(
                    "Informe status, equipe ou responsável para atualizar o atendimento.");
        }

        if (novaPrioridade != null && novaPrioridade != this.prioridade) {
            PrioridadeChamado anterior = this.prioridade;
            this.prioridade = novaPrioridade;
            registrar(TipoInteracaoChamado.ALTERACAO_PRIORIDADE,
                    "Prioridade alterada de " + anterior + " para " + novaPrioridade + ".",
                    false, usuarioOperadorId, null, null, null, null, null, null);
        }

        if (novaEquipe != null && !novaEquipe.equals(this.equipeAtendimento)) {
            novaEquipe.exigirDisponivel();
            UUID equipeAnteriorId = idDaEquipe(this.equipeAtendimento);
            UUID responsavelAnterior = this.usuarioResponsavelId;
            this.equipeAtendimento = novaEquipe;
            this.usuarioResponsavelId = null;
            registrar(TipoInteracaoChamado.ENCAMINHAMENTO,
                    "Chamado encaminhado para a equipe " + novaEquipe.getNome() + ".",
                    false, usuarioOperadorId, null, null, equipeAnteriorId,
                    idDaEquipe(novaEquipe), null, null);
            if (responsavelAnterior != null) {
                registrarAtribuicao(responsavelAnterior, null, usuarioOperadorId);
            }
        }

        if (responsavelId != null && !responsavelId.equals(this.usuarioResponsavelId)) {
            equipeAtendimento.exigirMembro(responsavelId);
            UUID anterior = this.usuarioResponsavelId;
            this.usuarioResponsavelId = responsavelId;
            registrarAtribuicao(anterior, responsavelId, usuarioOperadorId);
        }

        if (novoStatus != null) {
            if (novoStatus == StatusChamado.EM_ATENDIMENTO && this.usuarioResponsavelId == null) {
                equipeAtendimento.exigirMembro(usuarioOperadorId);
                this.usuarioResponsavelId = usuarioOperadorId;
                registrarAtribuicao(null, usuarioOperadorId, usuarioOperadorId);
            }
            TipoInteracaoChamado tipo = status == StatusChamado.RESOLVIDO
                    && novoStatus == StatusChamado.EM_ATENDIMENTO
                    ? TipoInteracaoChamado.REABERTURA
                    : novoStatus == StatusChamado.CANCELADO
                    ? TipoInteracaoChamado.CANCELAMENTO
                    : TipoInteracaoChamado.ALTERACAO_STATUS;
            alterarStatus(novoStatus, solucao, usuarioOperadorId, tipo);
        }
    }

    public void cancelarPeloSolicitante(UUID solicitanteId) {
        exigirProprietario(solicitanteId);
        if (status != StatusChamado.ABERTO && status != StatusChamado.AGUARDANDO_SOLICITANTE) {
            throw new InvarianteVioladaException("O chamado não pode mais ser cancelado pelo solicitante.");
        }
        alterarStatus(StatusChamado.CANCELADO, null, solicitanteId,
                TipoInteracaoChamado.CANCELAMENTO);
    }

    public void reabrirPeloSolicitante(UUID solicitanteId, String motivo) {
        exigirProprietario(solicitanteId);
        if (status != StatusChamado.RESOLVIDO || dataResolucao == null) {
            throw new InvarianteVioladaException("Somente chamados resolvidos podem ser reabertos.");
        }
        if (OffsetDateTime.now().isAfter(dataResolucao.plusHours(prazoReaberturaHoras))) {
            throw new InvarianteVioladaException("O prazo para reabrir este chamado foi encerrado.");
        }
        alterarStatus(StatusChamado.EM_ATENDIMENTO, null, solicitanteId,
                TipoInteracaoChamado.REABERTURA);
        registrar(TipoInteracaoChamado.MENSAGEM_PUBLICA, exigirMensagem(motivo),
                false, solicitanteId, null, null, null, null, null, null);
    }

    /**
     * Entrega as interações criadas desde a última leitura e esvazia a lista, para que
     * um mesmo fato não seja anunciado duas vezes se a entidade continuar em uso.
     */
    public List<InteracaoChamado> drenarNovasInteracoes() {
        List<InteracaoChamado> registradas = List.copyOf(novasInteracoes);
        novasInteracoes.clear();
        return registradas;
    }

    public boolean isSlaPrimeiraRespostaVencido() {
        OffsetDateTime referencia;
        if (dataPrimeiraResposta != null) {
            referencia = dataPrimeiraResposta;
        } else if (status.isFinalizado() && dataFechamento != null) {
            referencia = dataFechamento;
        } else {
            referencia = OffsetDateTime.now();
        }
        return referencia.isAfter(dataLimitePrimeiraResposta);
    }

    public boolean isSlaResolucaoVencido() {
        OffsetDateTime referencia;
        if (dataResolucao != null) {
            referencia = dataResolucao;
        } else if (status.isFinalizado() && dataFechamento != null) {
            referencia = dataFechamento;
        } else if (dataInicioPausaSla != null) {
            referencia = dataInicioPausaSla;
        } else {
            referencia = OffsetDateTime.now();
        }
        return referencia.isAfter(dataLimiteResolucao);
    }

    public boolean isSlaPausado() {
        return dataInicioPausaSla != null;
    }

    private void prepararPrimeiraResposta(UUID usuarioAtendenteId) {
        if (status == StatusChamado.ABERTO) {
            if (usuarioResponsavelId == null) {
                equipeAtendimento.exigirMembro(usuarioAtendenteId);
                usuarioResponsavelId = usuarioAtendenteId;
                registrarAtribuicao(null, usuarioAtendenteId, usuarioAtendenteId);
            }
            alterarStatus(StatusChamado.EM_ATENDIMENTO, null, usuarioAtendenteId,
                    TipoInteracaoChamado.ALTERACAO_STATUS);
        }
        if (dataPrimeiraResposta == null) {
            dataPrimeiraResposta = OffsetDateTime.now();
        }
    }

    private void alterarStatus(StatusChamado novoStatus, String novaSolucao,
                               UUID usuarioAutorId, TipoInteracaoChamado tipoEvento) {
        validarTransicao(novoStatus);
        StatusChamado anterior = this.status;
        OffsetDateTime agora = OffsetDateTime.now();

        if (anterior == StatusChamado.AGUARDANDO_SOLICITANTE) {
            retomarSla(agora);
        }
        if (novoStatus == StatusChamado.AGUARDANDO_SOLICITANTE
                && pausarSlaAguardandoSolicitante) {
            dataInicioPausaSla = agora;
        }
        if (novoStatus == StatusChamado.EM_ATENDIMENTO && dataInicioAtendimento == null) {
            dataInicioAtendimento = agora;
        }
        if (novoStatus == StatusChamado.RESOLVIDO) {
            this.solucao = exigir(novaSolucao, "Descreva a solução antes de resolver o chamado.");
            this.dataResolucao = agora;
            if (this.dataPrimeiraResposta == null) {
                this.dataPrimeiraResposta = agora;
            }
        }
        if (novoStatus == StatusChamado.FECHADO || novoStatus == StatusChamado.CANCELADO) {
            this.dataFechamento = agora;
            if (novoStatus == StatusChamado.FECHADO && this.dataResolucao == null) {
                this.dataResolucao = agora;
            }
        }
        if (anterior == StatusChamado.RESOLVIDO && novoStatus == StatusChamado.EM_ATENDIMENTO) {
            long segundosResolvido = Math.max(0, Duration.between(this.dataResolucao, agora).getSeconds());
            this.dataLimiteResolucao = this.dataLimiteResolucao.plusSeconds(segundosResolvido);
            this.totalSegundosPausaSla += segundosResolvido;
            this.dataResolucao = null;
            this.dataFechamento = null;
            this.solucao = null;
        }
        this.status = novoStatus;
        String mensagemEvento = "Status alterado de " + anterior + " para " + novoStatus + ".";
        if (novoStatus == StatusChamado.RESOLVIDO) {
            mensagemEvento += " Solução: " + this.solucao;
        }
        registrar(tipoEvento, mensagemEvento,
                false, usuarioAutorId, anterior, novoStatus, null, null, null, null);
    }

    private void retomarSla(OffsetDateTime agora) {
        if (dataInicioPausaSla == null) {
            return;
        }
        long segundos = Math.max(0, Duration.between(dataInicioPausaSla, agora).getSeconds());
        totalSegundosPausaSla += segundos;
        dataLimiteResolucao = dataLimiteResolucao.plusSeconds(segundos);
        dataInicioPausaSla = null;
    }

    private void registrarAtribuicao(UUID anterior, UUID novo, UUID usuarioAutorId) {
        registrar(TipoInteracaoChamado.ATRIBUICAO,
                novo == null ? "Responsável removido durante o encaminhamento."
                        : "Responsável pelo chamado atualizado.",
                false, usuarioAutorId, null, null, null, null, anterior, novo);
    }

    private InteracaoChamado registrar(TipoInteracaoChamado tipo, String mensagem,
                                       boolean interno, UUID usuarioAutorId,
                                       StatusChamado statusAnterior, StatusChamado statusNovo,
                                       UUID equipeAnteriorId, UUID equipeNovaId,
                                       UUID responsavelAnteriorId, UUID responsavelNovoId) {
        if (usuarioAutorId == null) {
            throw new InvarianteVioladaException("Não foi possível identificar o autor da interação.");
        }
        InteracaoChamado interacao = new InteracaoChamado(this, tipo, mensagem, interno,
                usuarioAutorId, statusAnterior, statusNovo, equipeAnteriorId, equipeNovaId,
                responsavelAnteriorId, responsavelNovoId);
        interacoes.add(interacao);
        novasInteracoes.add(interacao);
        dataUltimaInteracao = interacao.getDataEvento();
        return interacao;
    }

    private void validarTransicao(StatusChamado novoStatus) {
        if (status == novoStatus) {
            throw new InvarianteVioladaException("O chamado já está no status informado.");
        }
        Set<StatusChamado> permitidos = switch (status) {
            case ABERTO -> Set.of(StatusChamado.EM_ATENDIMENTO, StatusChamado.CANCELADO);
            case EM_ATENDIMENTO -> Set.of(StatusChamado.AGUARDANDO_SOLICITANTE,
                    StatusChamado.RESOLVIDO, StatusChamado.CANCELADO);
            case AGUARDANDO_SOLICITANTE -> Set.of(StatusChamado.EM_ATENDIMENTO,
                    StatusChamado.RESOLVIDO, StatusChamado.CANCELADO);
            case RESOLVIDO -> Set.of(StatusChamado.FECHADO, StatusChamado.EM_ATENDIMENTO);
            case FECHADO, CANCELADO -> Set.of();
        };
        if (!permitidos.contains(novoStatus)) {
            throw new InvarianteVioladaException(
                    "Transição de " + status + " para " + novoStatus + " não é permitida.");
        }
    }

    private void exigirProprietario(UUID usuarioId) {
        if (!usuarioSolicitanteId.equals(usuarioId)) {
            throw new InvarianteVioladaException("Somente o solicitante pode executar esta operação.");
        }
    }

    private void exigirNaoFinalizado(String acao) {
        if (status.isFinalizado()) {
            throw new InvarianteVioladaException(
                    "Não é possível " + acao + " em um chamado finalizado.");
        }
    }

    private static UUID idDaEquipe(EquipeAtendimento equipe) {
        return equipe == null ? null : equipe.getId();
    }

    private static String gerarProtocolo(OffsetDateTime data) {
        String dataFormatada = data.toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE);
        String sufixo = UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toUpperCase();
        return "CH-" + dataFormatada + "-" + sufixo;
    }

    private static String exigirMensagem(String valor) {
        String mensagem = exigir(valor, "A mensagem da interação é obrigatória.");
        if (mensagem.length() > LIMITE_MENSAGEM) {
            throw new InvarianteVioladaException("A mensagem deve ter no máximo 5.000 caracteres.");
        }
        return mensagem;
    }

    private static String exigir(String valor, String mensagem) {
        if (!StringUtils.hasText(valor)) {
            throw new InvarianteVioladaException(mensagem);
        }
        return valor.trim();
    }
}
