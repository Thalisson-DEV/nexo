package com.nexo.app.modules.sst.domain;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Raiz do agregado de execução de uma inspeção de segurança lançada a partir de um
 * {@link FormularioInspecao}. Vincula o processo, o inspetor (usuário logado), o
 * veículo e a equipe (opcionais), os colaboradores inspecionados, as respostas e os
 * desvios (não-conformidades) identificados.
 *
 * <p>Invariante central: todo colaborador atribuído a um desvio deve estar entre os
 * colaboradores marcados como inspecionados nesta inspeção.</p>
 */
@Entity
@Table(name = "inspecoes_seguranca")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InspecaoSeguranca extends BaseEntity {

    @NotNull
    @Column(name = "id_formulario", nullable = false)
    private UUID idFormulario;

    @NotNull
    @Column(name = "id_tipo_inspecao", nullable = false)
    private UUID idTipoInspecao;

    @NotNull
    @Column(name = "id_processo", nullable = false)
    private UUID idProcesso;

    @NotNull
    @Column(name = "id_inspetor", nullable = false)
    private UUID idInspetor;

    @Column(name = "id_veiculo")
    private UUID idVeiculo;

    @Column(name = "id_equipe")
    private UUID idEquipe;

    @Column(name = "id_base_operacional")
    private UUID idBaseOperacional;

    @NotNull
    @Column(name = "data_realizacao", nullable = false)
    private OffsetDateTime dataRealizacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusInspecao status;

    @Column(name = "observacoes", length = 1000)
    private String observacoes;

    @Column(name = "latitude", precision = 10, scale = 8)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 11, scale = 8)
    private BigDecimal longitude;

    @JsonManagedReference
    @OneToMany(mappedBy = "inspecao", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ColaboradorInspecionado> colaboradoresInspecionados = new ArrayList<>();

    @JsonManagedReference
    @OneToMany(mappedBy = "inspecao", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RespostaInspecao> respostas = new ArrayList<>();

    @JsonManagedReference
    @OneToMany(mappedBy = "inspecao", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DesvioSeguranca> desvios = new ArrayList<>();

    @JsonManagedReference
    @OneToMany(mappedBy = "inspecao", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EvidenciaInspecao> evidencias = new ArrayList<>();

    public InspecaoSeguranca(UUID idFormulario, UUID idTipoInspecao, UUID idProcesso, UUID idInspetor,
                             UUID idVeiculo, UUID idEquipe, UUID idBaseOperacional, OffsetDateTime dataRealizacao,
                             String observacoes) {
        this.idFormulario = Objects.requireNonNull(idFormulario, "ID do formulário é obrigatório");
        this.idTipoInspecao = Objects.requireNonNull(idTipoInspecao, "ID do tipo de inspeção é obrigatório");
        this.idProcesso = Objects.requireNonNull(idProcesso, "ID do processo é obrigatório");
        this.idInspetor = Objects.requireNonNull(idInspetor, "ID do inspetor é obrigatório");
        this.dataRealizacao = Objects.requireNonNull(dataRealizacao, "Data de realização é obrigatória");
        this.idVeiculo = idVeiculo;
        this.idEquipe = idEquipe;
        this.idBaseOperacional = Objects.requireNonNull(idBaseOperacional, "ID da base operacional é obrigatório");
        this.observacoes = observacoes;
        this.status = StatusInspecao.EM_ANDAMENTO;
    }

    /**
     * Registra a geolocalização da inspeção (opcional).
     */
    public void registrarGeolocalizacao(BigDecimal latitude, BigDecimal longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    /**
     * Atualiza os dados de cabeçalho da inspeção e sincroniza os colaboradores inspecionados.
     * Invariante 1: a inspeção deve manter ao menos um colaborador inspecionado.
     * Invariante 2: nenhum colaborador com desvio atribuído pode ser removido da lista de inspecionados.
     */
    public void atualizarCabecalho(UUID idTipoInspecao, UUID idProcesso, UUID idBaseOperacional,
                                  UUID idVeiculo, UUID idEquipe, OffsetDateTime dataRealizacao,
                                  String observacoes, BigDecimal latitude, BigDecimal longitude,
                                  List<UUID> novosColaboradores) {
        this.idTipoInspecao = Objects.requireNonNull(idTipoInspecao, "Tipo de inspeção é obrigatório");
        this.idProcesso = Objects.requireNonNull(idProcesso, "Processo é obrigatório");
        this.idBaseOperacional = Objects.requireNonNull(idBaseOperacional, "Base operacional é obrigatória");
        this.dataRealizacao = Objects.requireNonNull(dataRealizacao, "Data de realização é obrigatória");
        this.idVeiculo = idVeiculo;
        this.idEquipe = idEquipe;
        this.observacoes = observacoes;
        this.latitude = latitude;
        this.longitude = longitude;

        if (novosColaboradores == null || novosColaboradores.isEmpty()) {
            throw new InvarianteVioladaException("A inspeção deve conter ao menos um colaborador inspecionado.");
        }

        java.util.Set<UUID> novosSet = new java.util.LinkedHashSet<>(novosColaboradores);

        for (DesvioSeguranca desvio : this.desvios) {
            for (DesvioColaborador cd : desvio.getColaboradores()) {
                if (!novosSet.contains(cd.getIdColaborador())) {
                    throw new InvarianteVioladaException(
                            "Não é possível remover o colaborador " + cd.getIdColaborador()
                                    + " da inspeção, pois ele possui desvio(s) atribuído(s).");
                }
            }
        }

        this.colaboradoresInspecionados.removeIf(c -> !novosSet.contains(c.getIdColaborador()));

        for (UUID idColab : novosColaboradores) {
            if (!colaboradorEstaSendoInspecionado(idColab)) {
                this.colaboradoresInspecionados.add(new ColaboradorInspecionado(this, idColab));
            }
        }
    }

    /**
     * Marca um colaborador como inspecionado. Não permite duplicidade.
     */
    public void adicionarColaboradorInspecionado(UUID idColaborador) {
        Objects.requireNonNull(idColaborador, "ID do colaborador é obrigatório");
        boolean jaInspecionado = colaboradoresInspecionados.stream()
                .anyMatch(c -> c.getIdColaborador().equals(idColaborador));
        if (jaInspecionado) {
            throw new InvarianteVioladaException("O colaborador " + idColaborador + " já foi adicionado à inspeção.");
        }
        this.colaboradoresInspecionados.add(new ColaboradorInspecionado(this, idColaborador));
    }

    /**
     * Registra a resposta a uma pergunta. Cada pergunta só pode ser respondida uma vez.
     */
    public RespostaInspecao responder(UUID idPergunta, List<UUID> opcoesSelecionadas,
                                      String valorTexto, BigDecimal valorNumero, LocalDate valorData) {
        Objects.requireNonNull(idPergunta, "ID da pergunta é obrigatório");
        boolean jaRespondida = respostas.stream().anyMatch(r -> r.getIdPergunta().equals(idPergunta));
        if (jaRespondida) {
            throw new InvarianteVioladaException("A pergunta " + idPergunta + " já foi respondida nesta inspeção.");
        }

        RespostaInspecao resposta = new RespostaInspecao(this, idPergunta, valorTexto, valorNumero, valorData);
        if (opcoesSelecionadas != null) {
            opcoesSelecionadas.forEach(resposta::selecionarOpcao);
        }
        this.respostas.add(resposta);
        return resposta;
    }

    /**
     * Registra um desvio (não-conformidade) atribuído a um ou mais colaboradores.
     * Invariante: os colaboradores responsáveis devem estar entre os inspecionados.
     */
    public DesvioSeguranca registrarDesvio(UUID idPergunta, UUID idOpcaoResposta, GravidadeDesvio gravidade,
                                           String descricao, String acaoCorretiva, LocalDate prazo,
                                           List<UUID> idsColaboradores) {
        return registrarDesvio(idPergunta, idOpcaoResposta, gravidade, descricao, acaoCorretiva, prazo, idsColaboradores, StatusDesvio.ABERTO);
    }

    /**
     * Registra um desvio (não-conformidade) com status de tratamento informado (in-loco / tratado na hora).
     */
    public DesvioSeguranca registrarDesvio(UUID idPergunta, UUID idOpcaoResposta, GravidadeDesvio gravidade,
                                           String descricao, String acaoCorretiva, LocalDate prazo,
                                           List<UUID> idsColaboradores, StatusDesvio status) {
        if (idsColaboradores == null || idsColaboradores.isEmpty()) {
            throw new InvarianteVioladaException("Um desvio deve ser atribuído a pelo menos um colaborador.");
        }

        for (UUID idColaborador : idsColaboradores) {
            if (!colaboradorEstaSendoInspecionado(idColaborador)) {
                throw new InvarianteVioladaException(
                        "Só é possível atribuir o desvio a colaboradores marcados como inspecionados. "
                                + "O colaborador " + idColaborador + " não está na lista de inspecionados.");
            }
        }

        StatusDesvio statusFinal = status != null ? status : StatusDesvio.ABERTO;
        OffsetDateTime dataResolucao = statusFinal == StatusDesvio.RESOLVIDO
                ? (this.getDataRealizacao() != null ? this.getDataRealizacao() : OffsetDateTime.now())
                : null;
        LocalDate prazoFinal = (statusFinal == StatusDesvio.RESOLVIDO && prazo == null && this.getDataRealizacao() != null)
                ? this.getDataRealizacao().toLocalDate()
                : prazo;

        DesvioSeguranca desvio = new DesvioSeguranca(
                this, idPergunta, idOpcaoResposta, gravidade, descricao, acaoCorretiva, prazoFinal, statusFinal, dataResolucao);
        idsColaboradores.forEach(desvio::adicionarColaborador);
        this.desvios.add(desvio);
        return desvio;
    }

    /**
     * Vincula uma evidência fotográfica já enviada ao armazenamento.
     */
    public void vincularEvidencia(EvidenciaInspecao evidencia) {
        Objects.requireNonNull(evidencia, "A evidência é obrigatória");
        evidencia.vincular(this);
        this.evidencias.add(evidencia);
    }

    /**
     * Atualiza dados gerais do cabeçalho da inspeção e sincroniza os colaboradores inspecionados.
     */
    public void atualizarCompleto(UUID idFormulario, UUID idTipoInspecao, UUID idProcesso, UUID idBaseOperacional,
                                  UUID idVeiculo, UUID idEquipe, OffsetDateTime dataRealizacao,
                                  String observacoes, BigDecimal latitude, BigDecimal longitude,
                                  List<UUID> novosColaboradores) {
        if (idFormulario != null) {
            this.idFormulario = idFormulario;
        }
        this.idTipoInspecao = Objects.requireNonNull(idTipoInspecao, "Tipo de inspeção é obrigatório");
        this.idProcesso = Objects.requireNonNull(idProcesso, "Processo é obrigatório");
        this.idBaseOperacional = Objects.requireNonNull(idBaseOperacional, "Base operacional é obrigatória");
        this.dataRealizacao = Objects.requireNonNull(dataRealizacao, "Data de realização é obrigatória");
        this.idVeiculo = idVeiculo;
        this.idEquipe = idEquipe;
        this.observacoes = observacoes;
        this.latitude = latitude;
        this.longitude = longitude;

        if (novosColaboradores == null || novosColaboradores.isEmpty()) {
            throw new InvarianteVioladaException("A inspeção deve conter ao menos um colaborador inspecionado.");
        }

        java.util.Set<UUID> novosSet = new java.util.LinkedHashSet<>(novosColaboradores);
        this.colaboradoresInspecionados.removeIf(c -> !novosSet.contains(c.getIdColaborador()));
        for (UUID idColab : novosColaboradores) {
            if (!colaboradorEstaSendoInspecionado(idColab)) {
                this.colaboradoresInspecionados.add(new ColaboradorInspecionado(this, idColab));
            }
        }
    }

    /**
     * Sincroniza a resposta a uma pergunta. Se já respondida, atualiza; senão, cria nova.
     */
    public void sincronizarResposta(UUID idPergunta, List<UUID> opcoesSelecionadas,
                                    String valorTexto, BigDecimal valorNumero, LocalDate valorData) {
        Objects.requireNonNull(idPergunta, "ID da pergunta é obrigatório");
        RespostaInspecao existente = respostas.stream()
                .filter(r -> r.getIdPergunta().equals(idPergunta))
                .findFirst()
                .orElse(null);

        if (existente != null) {
            existente.atualizar(opcoesSelecionadas, valorTexto, valorNumero, valorData);
        } else {
            responder(idPergunta, opcoesSelecionadas, valorTexto, valorNumero, valorData);
        }
    }

    /**
     * Remove respostas que não constam no conjunto de perguntas respondidas.
     */
    public void removerRespostasNaoPresentes(java.util.Set<UUID> perguntasAtuais) {
        this.respostas.removeIf(r -> !perguntasAtuais.contains(r.getIdPergunta()));
    }

    /**
     * Sincroniza um desvio existente ou cria um novo se não existir.
     */
    public DesvioSeguranca sincronizarDesvio(UUID idPergunta, UUID idOpcaoResposta, GravidadeDesvio gravidade,
                                             String descricao, String acaoCorretiva, LocalDate prazo,
                                             List<UUID> idsColaboradores, StatusDesvio status) {
        if (idsColaboradores == null || idsColaboradores.isEmpty()) {
            throw new InvarianteVioladaException("Um desvio deve ser atribuído a pelo menos um colaborador.");
        }
        for (UUID idColaborador : idsColaboradores) {
            if (!colaboradorEstaSendoInspecionado(idColaborador)) {
                throw new InvarianteVioladaException(
                        "Só é possível atribuir o desvio a colaboradores marcados como inspecionados. "
                                + "O colaborador " + idColaborador + " não está na lista de inspecionados.");
            }
        }

        DesvioSeguranca existente = desvios.stream()
                .filter(d -> d.getIdPergunta().equals(idPergunta)
                        && Objects.equals(d.getIdOpcaoResposta(), idOpcaoResposta))
                .findFirst()
                .orElse(null);

        if (existente != null) {
            existente.atualizar(descricao, acaoCorretiva, prazo, status, idsColaboradores);
            return existente;
        } else {
            return registrarDesvio(idPergunta, idOpcaoResposta, gravidade, descricao, acaoCorretiva, prazo, idsColaboradores, status);
        }
    }

    /**
     * Remove desvios que não constam no conjunto atual de (idPergunta:idOpcaoResposta).
     */
    public void removerDesviosNaoPresentes(java.util.Set<String> chavesAtuais) {
        this.desvios.removeIf(d -> !chavesAtuais.contains(d.getIdPergunta() + ":" + d.getIdOpcaoResposta()));
    }

    /**
     * Sincroniza as evidências fotográficas da inspeção.
     */
    public void sincronizarEvidencias(List<EvidenciaInspecao> novasEvidencias) {
        if (novasEvidencias == null || novasEvidencias.isEmpty()) {
            throw new InvarianteVioladaException("A inspeção deve manter ao menos uma evidência fotográfica.");
        }
        java.util.Set<UUID> novosIds = novasEvidencias.stream().map(EvidenciaInspecao::getId).collect(java.util.stream.Collectors.toSet());
        for (EvidenciaInspecao e : new ArrayList<>(this.evidencias)) {
            if (!novosIds.contains(e.getId())) {
                e.desvincular();
                this.evidencias.remove(e);
            }
        }
        for (EvidenciaInspecao nova : novasEvidencias) {
            if (this.evidencias.stream().noneMatch(e -> e.getId().equals(nova.getId()))) {
                vincularEvidencia(nova);
            }
        }
        if (this.evidencias.isEmpty()) {
            throw new InvarianteVioladaException("A inspeção deve manter ao menos uma evidência fotográfica.");
        }
    }

    /**
     * Valida invariantes do agregado após edição completa.
     */
    public void validarInvariantesAposEdicao() {
        if (colaboradoresInspecionados.isEmpty()) {
            throw new InvarianteVioladaException("A inspeção deve conter ao menos um colaborador inspecionado.");
        }
        if (evidencias.isEmpty()) {
            throw new InvarianteVioladaException("A inspeção deve manter ao menos uma evidência fotográfica.");
        }
        for (DesvioSeguranca desvio : this.desvios) {
            for (DesvioColaborador cd : desvio.getColaboradores()) {
                if (!colaboradorEstaSendoInspecionado(cd.getIdColaborador())) {
                    throw new InvarianteVioladaException(
                            "O colaborador " + cd.getIdColaborador() + " atribuído ao desvio não está entre os inspecionados.");
                }
            }
        }
    }

    /**
     * Desvincula uma evidência. A última não sai: a inspeção já foi contabilizada em
     * metas e dashboards, e sem foto ela deixa de ser comprovável.
     */
    public void removerEvidencia(EvidenciaInspecao evidencia) {
        if (this.evidencias.size() <= 1) {
            throw new InvarianteVioladaException(
                    "A inspeção deve manter ao menos uma evidência fotográfica. Anexe outra antes de remover esta.");
        }
        evidencia.desvincular();
        this.evidencias.remove(evidencia);
    }

    /**
     * Conclui a inspeção. Exige ao menos um colaborador inspecionado, ao menos uma
     * evidência fotográfica, e não pode ser concluída mais de uma vez.
     */
    public void concluir() {
        if (this.status == StatusInspecao.CONCLUIDA) {
            throw new InvarianteVioladaException("Esta inspeção já foi concluída.");
        }
        if (colaboradoresInspecionados.isEmpty()) {
            throw new InvarianteVioladaException("Não é possível concluir uma inspeção sem colaboradores inspecionados.");
        }
        if (evidencias.isEmpty()) {
            throw new InvarianteVioladaException(
                    "Não é possível concluir uma inspeção sem ao menos uma evidência fotográfica.");
        }
        this.status = StatusInspecao.CONCLUIDA;
    }

    private boolean colaboradorEstaSendoInspecionado(UUID idColaborador) {
        return colaboradoresInspecionados.stream()
                .anyMatch(c -> c.getIdColaborador().equals(idColaborador));
    }
}
