package com.nexo.app.modules.estoque.domain;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.nexo.app.shared.domain.AuditIgnore;
import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.infrastructure.persistence.CryptoConverterImpl;
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

import com.nexo.app.modules.obras.ObraApi.ObraPublica;
import com.nexo.app.modules.obras.ObraApi.OcorrenciaPublica;

/**
 * Representa um pedido de materiais feito por uma equipe ou setor ao almoxarifado.
 * Segue um fluxo de aprovação e entrega com assinaturas eletrônicas cifradas.
 */
@Entity
@Table(name = "solicitacoes_material")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SolicitacaoMaterial extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_estoque_origem")
    private Estoque estoqueOrigem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_estoque_destino")
    private Estoque estoqueDestino;

    @Enumerated(EnumType.STRING)
    @Column(name = "contexto", length = 20)
    private ContextoSolicitacaoMaterial contexto = ContextoSolicitacaoMaterial.COMERCIAL;

    @Convert(converter = TipoSolicitacaoTecnicaConverter.class)
    @Column(name = "tipo_tecnico", length = 3)
    private TipoSolicitacaoTecnica tipoTecnico = TipoSolicitacaoTecnica.ADICAO_COMUM;

    @Column(name = "id_obra")
    private UUID idObra;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_obra_snapshot", length = 20)
    private TipoObraSnapshot tipoObraSnapshot;

    @Column(name = "id_ocorrencia")
    private UUID idOcorrencia;

    @Column(name = "codigo_ocorrencia_snapshot", length = 100)
    private String ocorrencia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_almoxarifado_atendimento")
    private Estoque almoxarifadoAtendimento;

    @Column(name = "id_equipe_saque")
    private UUID idEquipeSaque;

    @Column(name = "id_colaborador_saque")
    private UUID idColaboradorSaque;

    @Column(name = "saque_fora_equipe", nullable = false)
    private boolean saqueForaEquipe;

    @Column(name = "data_prevista_retirada")
    private LocalDate dataPrevistaRetirada;

    @Column(name = "observacao", length = 1000)
    private String observacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_separacao", length = 30)
    private StatusSeparacao statusSeparacao;

    @Column(name = "separacao_iniciada_em")
    private OffsetDateTime separacaoIniciadaEm;

    @Column(name = "id_usuario_inicio_separacao")
    private UUID idUsuarioInicioSeparacao;

    @Column(name = "separacao_concluida_em")
    private OffsetDateTime separacaoConcluidaEm;

    @Column(name = "id_usuario_conclusao_separacao")
    private UUID idUsuarioConclusaoSeparacao;

    @Column(name = "encerrada_em")
    private OffsetDateTime encerradaEm;

    @Column(name = "id_usuario_encerramento")
    private UUID idUsuarioEncerramento;

    @Column(name = "motivo_encerramento", length = 500)
    private String motivoEncerramento;

    @NotNull(message = "O requisitante é obrigatório")
    @Column(name = "id_requisitante", nullable = false)
    private UUID idRequisitante;

    @Column(name = "id_aprovador")
    private UUID idAprovador;

    @Column(name = "id_entregador_almox")
    private UUID idEntregadorAlmox;

    @NotNull(message = "O status da solicitação é obrigatório")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private StatusSolicitacao status;

    @AuditIgnore
    @Convert(converter = CryptoConverterImpl.class)
    @Column(name = "assinatura_almox")
    private String assinaturaAlmox; // 🔒 Cifrada

    @AuditIgnore
    @Convert(converter = CryptoConverterImpl.class)
    @Column(name = "assinatura_equipe")
    private String assinaturaEquipe; // 🔒 Cifrada

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_motivo_rejeicao")
    private MotivoRejeicao motivoRejeicao;

    @Column(name = "justificativa_rejeicao", columnDefinition = "TEXT")
    private String justificativaRejeicao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_motivo_pendencia")
    private MotivoPendencia motivoPendencia;

    @Column(name = "justificativa_pendencia", length = 2000)
    private String justificativaPendencia;

    @NotNull(message = "A data de solicitação é obrigatória")
    @Column(name = "data_solicitacao", nullable = false)
    private OffsetDateTime dataSolicitacao;

    @Column(name = "data_verificacao")
    private OffsetDateTime dataVerificacao;

    @Column(name = "data_entrega")
    private OffsetDateTime dataEntrega;

    @JsonManagedReference
    @OneToMany(mappedBy = "solicitacao", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemSolicitacao> itens = new ArrayList<>();

    @OneToOne(mappedBy = "solicitacao", fetch = FetchType.LAZY)
    private ReservaSolicitacaoTecnica reserva;

    public enum StatusSolicitacao {
        PENDENTE, 
        APROVADO, 
        REJEITADO,
        ENTREGUE_PARCIAL,
        ENTREGUE,
        DEVOLVIDO_PARCIAL,
        DEVOLVIDO,
        ESTORNADO,
        CANCELADO
    }

    public boolean possuiPendencia() {
        if (itens != null && !itens.isEmpty()) {
            return itens.stream().anyMatch(ItemSolicitacao::possuiPendencia);
        }
        return motivoPendencia != null;
    }

    /** Nomes distintos dos motivos de pendência abertos (da solicitação e dos itens), em ordem alfabética. */
    public List<String> nomesMotivosPendencia() {
        java.util.stream.Stream<MotivoPendencia> dosItens = itens == null
                ? java.util.stream.Stream.empty()
                : itens.stream().map(ItemSolicitacao::getMotivoPendencia);
        return java.util.stream.Stream.concat(java.util.stream.Stream.ofNullable(motivoPendencia), dosItens)
                .filter(Objects::nonNull)
                .map(MotivoPendencia::getNome)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }

    public void registrarPendencia(MotivoPendencia motivo) {
        validarMotivo(motivo);
        this.motivoPendencia = motivo;
        this.justificativaPendencia = null;
    }

    public void registrarPendencia(MotivoPendencia motivo, String justificativa) {
        if (justificativa == null || justificativa.isBlank()) {
            registrarPendencia(motivo);
            return;
        }
        validarPendencia(motivo, justificativa);
        this.motivoPendencia = motivo;
        this.justificativaPendencia = justificativa.trim();
    }

    public void removerPendencia() {
        this.motivoPendencia = null;
        this.justificativaPendencia = null;
    }

    public MotivoPendencia getMotivoPendencia() {
        if (itens != null && !itens.isEmpty()) {
            boolean temItemComPendencia = itens.stream().anyMatch(ItemSolicitacao::possuiPendencia);
            if (!temItemComPendencia) {
                return null;
            }
            if (this.motivoPendencia != null) {
                return this.motivoPendencia;
            }
            return itens.stream()
                    .filter(ItemSolicitacao::possuiPendencia)
                    .map(ItemSolicitacao::getMotivoPendencia)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        }
        return this.motivoPendencia;
    }

    public String getJustificativaPendencia() {
        if (itens != null && !itens.isEmpty()) {
            boolean temItemComPendencia = itens.stream().anyMatch(ItemSolicitacao::possuiPendencia);
            if (!temItemComPendencia) {
                return null;
            }
            if (this.justificativaPendencia != null) {
                return this.justificativaPendencia;
            }
            return itens.stream()
                    .filter(ItemSolicitacao::possuiPendencia)
                    .map(ItemSolicitacao::getJustificativaPendencia)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        }
        return this.justificativaPendencia;
    }

    static void validarMotivo(MotivoPendencia motivo) {
        if (motivo == null || !motivo.isAtivo()) {
            throw new InvarianteVioladaException("Informe um motivo ativo.");
        }
    }

    static void validarPendencia(MotivoPendencia motivo, String justificativa) {
        validarMotivo(motivo);
        if (justificativa == null || justificativa.trim().length() < 5 || justificativa.trim().length() > 2000) {
            throw new InvarianteVioladaException("Informe um motivo ativo e uma justificativa de 5 a 2000 caracteres.");
        }
    }

    public SolicitacaoMaterial(Estoque estoqueOrigem, Estoque estoqueDestino, UUID idRequisitante) {
        validarEstoque(estoqueOrigem);
        validarEstoque(estoqueDestino);
        if (estoqueOrigem.getId().equals(estoqueDestino.getId())) {
            throw new InvarianteVioladaException("O estoque de origem e destino não podem ser o mesmo.");
        }
        this.estoqueOrigem = estoqueOrigem;
        this.estoqueDestino = estoqueDestino;
        this.idRequisitante = Objects.requireNonNull(idRequisitante, "ID do requisitante é obrigatório");
        this.contexto = ContextoSolicitacaoMaterial.COMERCIAL;
        this.tipoTecnico = TipoSolicitacaoTecnica.ADICAO_COMUM;
        this.status = StatusSolicitacao.PENDENTE;
        this.dataSolicitacao = OffsetDateTime.now();
    }

    public SolicitacaoMaterial(
            Estoque estoqueOrigem,
            Estoque estoqueDestino,
            UUID idRequisitante,
            LocalDate dataPrevistaRetirada) {
        this(estoqueOrigem, estoqueDestino, idRequisitante, dataPrevistaRetirada,
                estoqueDestino == null ? null : estoqueDestino.getIdEquipe(), null, false);
    }

    public SolicitacaoMaterial(
            Estoque estoqueOrigem,
            Estoque estoqueDestino,
            UUID idRequisitante,
            LocalDate dataPrevistaRetirada,
            UUID idEquipeSaque,
            UUID idColaboradorSaque,
            boolean saqueForaEquipe) {
        this(estoqueOrigem, estoqueDestino, idRequisitante);
        this.dataPrevistaRetirada = Objects.requireNonNull(
                dataPrevistaRetirada, "A data prevista de saque e obrigatoria");
        this.idEquipeSaque = Objects.requireNonNull(
                idEquipeSaque, "A equipe de saque e obrigatoria");
        this.idColaboradorSaque = idColaboradorSaque;
        this.saqueForaEquipe = idColaboradorSaque != null && saqueForaEquipe;
    }

    public void definirObservacaoComercial(String valor) {
        if (contexto != ContextoSolicitacaoMaterial.COMERCIAL) {
            throw new InvarianteVioladaException("A observacao comercial exige solicitacao comercial.");
        }
        if (valor != null && valor.length() > 1000) {
            throw new InvarianteVioladaException("A observacao deve ter ate 1000 caracteres.");
        }
        this.observacao = valor == null || valor.isBlank() ? null : valor.trim();
    }

    /**
     * Comercial: quem aprova ou entrega pode trocar o almoxarifado que atende. Só antes da
     * primeira saída: o estorno devolve o material à origem da solicitação.
     */
    public void trocarEstoqueOrigemComercial(Estoque novaOrigem) {
        if (contexto != ContextoSolicitacaoMaterial.COMERCIAL) {
            throw new InvarianteVioladaException("A troca de origem exige solicitacao comercial.");
        }
        Objects.requireNonNull(novaOrigem, "O estoque de origem e obrigatorio");
        if (estoqueOrigem != null && novaOrigem.getId().equals(estoqueOrigem.getId())) {
            return;
        }
        if (!novaOrigem.isAtivo()
                || novaOrigem.getTipoEstoque() == null
                || !"FISICO".equalsIgnoreCase(novaOrigem.getTipoEstoque().getNome())) {
            throw new InvarianteVioladaException("O estoque de origem deve estar ativo e ser do tipo FISICO.");
        }
        if (estoqueDestino != null && novaOrigem.getId().equals(estoqueDestino.getId())) {
            throw new InvarianteVioladaException("O estoque de origem e destino não podem ser o mesmo.");
        }
        if (itens.stream().anyMatch(item -> item.getQuantidadeEntregaLiquida().signum() > 0)) {
            throw new InvarianteVioladaException(
                    "Já saiu material desta origem. A origem só pode mudar antes da primeira entrega.");
        }
        this.estoqueOrigem = novaOrigem;
    }

    public void reagendarSaqueComercial(LocalDate novaData) {
        if (contexto != ContextoSolicitacaoMaterial.COMERCIAL) {
            throw new InvarianteVioladaException("O reagendamento do saque exige solicitacao comercial.");
        }
        this.dataPrevistaRetirada = Objects.requireNonNull(novaData, "A data prevista de saque e obrigatoria");
    }

    /** Cria uma solicitacao tecnica independente, sem raiz de agrupamento. */
    public static SolicitacaoMaterial criarTecnica(
            ObraPublica obra,
            OcorrenciaPublica ocorrencia,
            TipoSolicitacaoTecnica tipoTecnico,
            Estoque estoqueObra,
            Estoque almoxarifadoAtendimento,
            UUID idEquipeSaque,
            LocalDate dataPrevistaRetirada,
            String observacao,
            UUID idRequisitante
    ) {
        return criarTecnica(
                obra, ocorrencia, tipoTecnico, estoqueObra, almoxarifadoAtendimento,
                idEquipeSaque, null, false, dataPrevistaRetirada, observacao, idRequisitante);
    }

    public static SolicitacaoMaterial criarTecnica(
            ObraPublica obra,
            OcorrenciaPublica ocorrencia,
            TipoSolicitacaoTecnica tipoTecnico,
            Estoque estoqueObra,
            Estoque almoxarifadoAtendimento,
            UUID idEquipeSaque,
            UUID idColaboradorSaque,
            boolean saqueForaEquipe,
            LocalDate dataPrevistaRetirada,
            String observacao,
            UUID idRequisitante
    ) {
        SolicitacaoMaterial solicitacao = new SolicitacaoMaterial();
        solicitacao.contexto = ContextoSolicitacaoMaterial.TECNICO;
        solicitacao.tipoTecnico = Objects.requireNonNull(tipoTecnico, "O tipo tecnico e obrigatorio");
        solicitacao.idObra = Objects.requireNonNull(
                Objects.requireNonNull(obra, "A obra e obrigatoria").id(), "O ID da obra e obrigatorio");
        solicitacao.tipoObraSnapshot = TipoObraSnapshot.valueOf(obra.tipoObra().name());
        solicitacao.idOcorrencia = ocorrencia == null ? null : ocorrencia.id();
        solicitacao.ocorrencia = ocorrencia == null ? null : ocorrencia.codigo();
        solicitacao.almoxarifadoAtendimento = Objects.requireNonNull(
                almoxarifadoAtendimento, "O almoxarifado de atendimento e obrigatorio");
        solicitacao.idEquipeSaque = idEquipeSaque;
        solicitacao.idColaboradorSaque = idColaboradorSaque;
        solicitacao.saqueForaEquipe = saqueForaEquipe;
        solicitacao.dataPrevistaRetirada = dataPrevistaRetirada;
        solicitacao.observacao = observacao == null || observacao.isBlank() ? null : observacao.trim();
        solicitacao.idRequisitante = Objects.requireNonNull(idRequisitante, "O requisitante e obrigatorio");
        solicitacao.status = StatusSolicitacao.APROVADO;
        solicitacao.dataSolicitacao = OffsetDateTime.now();
        if (tipoTecnico.isAdicao()) {
            solicitacao.estoqueDestino = Objects.requireNonNull(estoqueObra, "O estoque da obra e obrigatorio");
            solicitacao.statusSeparacao = dataPrevistaRetirada == null
                    ? StatusSeparacao.AGUARDANDO_AGENDAMENTO : StatusSeparacao.PRONTO_PARA_SEPARACAO;
        } else {
            solicitacao.estoqueOrigem = Objects.requireNonNull(estoqueObra, "O estoque da obra e obrigatorio");
            solicitacao.statusSeparacao = null;
        }
        return solicitacao;
    }

    public void adicionarItem(Material material, java.math.BigDecimal quantidade) {
        if (this.status != StatusSolicitacao.PENDENTE && this.status != StatusSolicitacao.APROVADO && this.status != StatusSolicitacao.ENTREGUE_PARCIAL) {
            throw new InvarianteVioladaException("Não é possível adicionar itens com status: " + this.status);
        }
        if (material == null) throw new IllegalArgumentException("Material é obrigatório");
        if (quantidade == null || quantidade.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantidade deve ser positiva");
        }
        this.itens.add(new ItemSolicitacao(this, material, quantidade));
    }

    public ItemSolicitacao adicionarItemTecnico(Material material, BigDecimal quantidade) {
        if (contexto != ContextoSolicitacaoMaterial.TECNICO) {
            throw new InvarianteVioladaException("Itens tecnicos exigem uma solicitacao tecnica.");
        }
        if (quantidade == null || quantidade.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantidade deve ser positiva");
        }
        ItemSolicitacao item = new ItemSolicitacao(this, material, quantidade);
        this.itens.add(item);
        return item;
    }

    public void substituirPlanejamentoTecnico(
            ObraPublica obra,
            OcorrenciaPublica ocorrencia,
            Estoque estoqueObra,
            Estoque almoxarifadoAtendimento,
            UUID idEquipeSaque,
            UUID idColaboradorSaque,
            boolean saqueForaEquipe,
            LocalDate dataPrevistaRetirada,
            String observacao
    ) {
        if (contexto != ContextoSolicitacaoMaterial.TECNICO) {
            throw new InvarianteVioladaException("O replanejamento exige uma solicitacao tecnica.");
        }
        this.idObra = Objects.requireNonNull(
                Objects.requireNonNull(obra, "A obra e obrigatoria").id(), "O ID da obra e obrigatorio");
        this.tipoObraSnapshot = TipoObraSnapshot.valueOf(obra.tipoObra().name());
        this.idOcorrencia = ocorrencia == null ? null : ocorrencia.id();
        this.ocorrencia = ocorrencia == null ? null : ocorrencia.codigo();
        this.almoxarifadoAtendimento = Objects.requireNonNull(
                almoxarifadoAtendimento, "O almoxarifado de atendimento e obrigatorio");
        this.idEquipeSaque = idEquipeSaque;
        this.idColaboradorSaque = idColaboradorSaque;
        this.saqueForaEquipe = saqueForaEquipe;
        this.dataPrevistaRetirada = dataPrevistaRetirada;
        this.observacao = observacao == null || observacao.isBlank() ? null : observacao.trim();
        if (tipoTecnico.isAdicao()) {
            this.estoqueOrigem = null;
            this.estoqueDestino = Objects.requireNonNull(estoqueObra, "O estoque da obra e obrigatorio");
            atualizarProntidaoPorData();
        } else {
            this.estoqueOrigem = Objects.requireNonNull(estoqueObra, "O estoque da obra e obrigatorio");
            this.estoqueDestino = null;
            this.statusSeparacao = null;
        }
    }

    public void substituirPlanejamentoTecnico(
            ObraPublica obra,
            OcorrenciaPublica ocorrencia,
            Estoque estoqueObra,
            Estoque almoxarifadoAtendimento,
            UUID idEquipeSaque,
            LocalDate dataPrevistaRetirada,
            String observacao
    ) {
        substituirPlanejamentoTecnico(
                obra, ocorrencia, estoqueObra, almoxarifadoAtendimento,
                idEquipeSaque, null, false, dataPrevistaRetirada, observacao);
    }

    public void invalidarSeparacao() {
        if (tipoTecnico != null && tipoTecnico.isAdicao()) {
            this.statusSeparacao = dataPrevistaRetirada == null
                    ? StatusSeparacao.AGUARDANDO_AGENDAMENTO
                    : StatusSeparacao.PRONTO_PARA_SEPARACAO;
        }
    }

    public void iniciarSeparacao(UUID idUsuario) {
        exigirSeparacaoAplicavel();
        if (statusSeparacao != StatusSeparacao.PRONTO_PARA_SEPARACAO) {
            throw new InvarianteVioladaException("A solicitacao nao esta pronta para separacao.");
        }
        this.statusSeparacao = StatusSeparacao.EM_SEPARACAO;
        this.idUsuarioInicioSeparacao = Objects.requireNonNull(idUsuario, "O usuario e obrigatorio");
        this.separacaoIniciadaEm = OffsetDateTime.now();
    }

    public void concluirSeparacao(UUID idUsuario) {
        exigirSeparacaoAplicavel();
        if (statusSeparacao != StatusSeparacao.EM_SEPARACAO) {
            throw new InvarianteVioladaException("A solicitacao nao possui separacao em andamento.");
        }
        this.statusSeparacao = StatusSeparacao.SEPARADO;
        this.idUsuarioConclusaoSeparacao = Objects.requireNonNull(idUsuario, "O usuario e obrigatorio");
        this.separacaoConcluidaEm = OffsetDateTime.now();
    }

    public void pularSeparacao(UUID idUsuario) {
        exigirSeparacaoAplicavel();
        if (statusSeparacao != StatusSeparacao.PRONTO_PARA_SEPARACAO && statusSeparacao != StatusSeparacao.EM_SEPARACAO) {
            throw new InvarianteVioladaException("A solicitacao nao esta pronta para separacao.");
        }
        if (this.idUsuarioInicioSeparacao == null) {
            this.idUsuarioInicioSeparacao = Objects.requireNonNull(idUsuario, "O usuario e obrigatorio");
            this.separacaoIniciadaEm = OffsetDateTime.now();
        }
        this.statusSeparacao = StatusSeparacao.SEPARADO;
        this.idUsuarioConclusaoSeparacao = Objects.requireNonNull(idUsuario, "O usuario e obrigatorio");
        this.separacaoConcluidaEm = OffsetDateTime.now();
    }

    public void registrarEquipeEfetivaSeAusente(UUID idEquipe, UUID idColaboradorSaque, boolean saqueForaEquipe) {
        if (this.idEquipeSaque == null) {
            this.idEquipeSaque = idEquipe;
        }
        if (this.idColaboradorSaque == null) {
            this.idColaboradorSaque = idColaboradorSaque;
            this.saqueForaEquipe = saqueForaEquipe;
        }
    }

    public void encerrarSaldoPendente(UUID idUsuario, String motivo) {
        if (contexto != ContextoSolicitacaoMaterial.TECNICO) {
            throw new InvarianteVioladaException("O encerramento de saldo se aplica somente a solicitacao tecnica.");
        }
        if (encerradaEm != null) {
            throw new InvarianteVioladaException("O saldo pendente ja foi encerrado.");
        }
        if (!possuiFatoOperacional() || itens.stream().noneMatch(item -> item.getQuantidadePendente().signum() > 0)) {
            throw new InvarianteVioladaException("O encerramento exige saldo pendente apos uma operacao parcial.");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new InvarianteVioladaException("Informe o motivo do encerramento do saldo pendente.");
        }
        this.idUsuarioEncerramento = Objects.requireNonNull(idUsuario, "O usuario e obrigatorio");
        this.motivoEncerramento = motivo.trim();
        this.encerradaEm = OffsetDateTime.now();
    }

    /** Séries só ficam reservadas enquanto a solicitação comercial ainda pode ser entregue. */
    public boolean aceitaReservaDeSerie() {
        return isAtivo()
                && contexto == ContextoSolicitacaoMaterial.COMERCIAL
                && !estaEncerrada()
                && (status == StatusSolicitacao.APROVADO || status == StatusSolicitacao.ENTREGUE_PARCIAL
                        || status == StatusSolicitacao.ESTORNADO);
    }

    /** Saldo encerrado (ex.: parcial expirada em 72h) não aceita nova entrega. */
    public void exigirSaldoAberto() {
        if (estaEncerrada()) {
            throw new InvarianteVioladaException(
                    "O saldo pendente desta solicitação foi encerrado; ela não aceita novas entregas.");
        }
    }

    public boolean estaEncerrada() {
        return encerradaEm != null;
    }

    public boolean isEncerrada() {
        return estaEncerrada();
    }

    public boolean isForaPrazoSaque() {
        return dataPrevistaRetirada != null
                && dataSolicitacao != null
                && dataPrevistaRetirada.isBefore(dataSolicitacao.toLocalDate().plusDays(2));
    }

    private void exigirSeparacaoAplicavel() {
        if (contexto != ContextoSolicitacaoMaterial.TECNICO) {
            throw new InvarianteVioladaException("Separacao se aplica somente a solicitacoes tecnicas.");
        }
        if (tipoTecnico == null || !tipoTecnico.isAdicao()) {
            throw new InvarianteVioladaException("Separacao se aplica somente a adicoes tecnicas.");
        }
        if (status != StatusSolicitacao.APROVADO && status != StatusSolicitacao.ENTREGUE_PARCIAL) {
            throw new InvarianteVioladaException("O status atual nao permite separacao.");
        }
        if (estaEncerrada()) {
            throw new InvarianteVioladaException("A solicitacao possui saldo pendente encerrado.");
        }
        if (dataPrevistaRetirada == null) {
            throw new InvarianteVioladaException("Defina a data prevista antes de iniciar a separacao.");
        }
    }

    /** Atualiza somente a fila ainda não iniciada; etapas físicas já registradas são fatos. */
    private void atualizarProntidaoPorData() {
        if (statusSeparacao == StatusSeparacao.EM_SEPARACAO || statusSeparacao == StatusSeparacao.SEPARADO) {
            return;
        }
        this.statusSeparacao = dataPrevistaRetirada == null
                ? StatusSeparacao.AGUARDANDO_AGENDAMENTO
                : StatusSeparacao.PRONTO_PARA_SEPARACAO;
    }

    public boolean possuiFatoOperacional() {
        return itens.stream().anyMatch(ItemSolicitacao::possuiFatoOperacional);
    }

    public ItemSolicitacao removerItemTecnico(UUID idItem) {
        ItemSolicitacao item = itens.stream()
                .filter(existente -> Objects.equals(existente.getId(), idItem))
                .findFirst()
                .orElseThrow(() -> new InvarianteVioladaException("Item tecnico nao encontrado: " + idItem));
        if (item.possuiFatoOperacional()) {
            throw new InvarianteVioladaException("Item com fato operacional nao pode ser removido.");
        }
        itens.remove(item);
        if (!possuiPendencia()) {
            removerPendencia();
        }
        return item;
    }

    public void associarReserva(ReservaSolicitacaoTecnica novaReserva) {
        Objects.requireNonNull(novaReserva, "Reserva e obrigatoria");
        if (this.reserva != null && this.reserva != novaReserva) {
            throw new InvarianteVioladaException("A solicitacao tecnica ja possui uma reserva associada.");
        }
        this.reserva = novaReserva;
    }

    public boolean isExcecaoReserva() {
        return (tipoObraSnapshot != null && (tipoObraSnapshot == TipoObraSnapshot.CORRETIVA || tipoObraSnapshot == TipoObraSnapshot.PREVENTIVA))
                || (ocorrencia != null && !ocorrencia.isBlank())
                || idOcorrencia != null;
    }

    /** A reserva externa é requisito de confirmação, não de separação ou saldo. */
    public ReservaSolicitacaoTecnica exigirReservaParaMovimentacaoTecnica() {
        if (contexto != ContextoSolicitacaoMaterial.TECNICO) {
            throw new InvarianteVioladaException("Reserva externa se aplica somente a movimentacao tecnica.");
        }
        if (reserva == null) {
            if (isExcecaoReserva()) {
                return null;
            }
            throw new InvarianteVioladaException(
                    "Registre uma reserva valida antes de confirmar a movimentacao tecnica.");
        }
        return reserva;
    }

    public void confirmarEntregaTecnica() {
        exigirOperacaoTecnica(TipoSolicitacaoTecnica::isAdicao, "Entrega se aplica somente a adicoes tecnicas.");
        this.status = itens.stream().allMatch(ItemSolicitacao::isTotalmenteEntregue)
                ? StatusSolicitacao.ENTREGUE
                : StatusSolicitacao.ENTREGUE_PARCIAL;
        this.dataEntrega = OffsetDateTime.now();
    }

    public void confirmarDevolucaoTecnica() {
        exigirOperacaoTecnica(TipoSolicitacaoTecnica::isDevolucao, "Devolucao se aplica somente a devolucoes tecnicas.");
        this.status = itens.stream().allMatch(ItemSolicitacao::isTotalmenteEntregue)
                ? StatusSolicitacao.DEVOLVIDO
                : StatusSolicitacao.DEVOLVIDO_PARCIAL;
    }

    /** Atualiza a projeção de status sem apagar a operação original ou seu estorno. */
    public void confirmarEstornoTecnico() {
        if (contexto != ContextoSolicitacaoMaterial.TECNICO || tipoTecnico == null) {
            throw new InvarianteVioladaException("O estorno exige solicitacao tecnica.");
        }
        boolean semQuantidadeLiquida = itens.stream()
                .allMatch(item -> item.getQuantidadeOperadaLiquida().signum() == 0);
        if (semQuantidadeLiquida) {
            this.status = StatusSolicitacao.ESTORNADO;
            return;
        }
        boolean concluida = itens.stream().allMatch(ItemSolicitacao::isTotalmenteEntregue);
        this.status = tipoTecnico.isAdicao()
                ? concluida ? StatusSolicitacao.ENTREGUE : StatusSolicitacao.ENTREGUE_PARCIAL
                : concluida ? StatusSolicitacao.DEVOLVIDO : StatusSolicitacao.DEVOLVIDO_PARCIAL;
    }

    private void exigirOperacaoTecnica(
            java.util.function.Predicate<TipoSolicitacaoTecnica> tipoPermitido,
            String mensagemTipo
    ) {
        if (contexto != ContextoSolicitacaoMaterial.TECNICO || tipoTecnico == null || !tipoPermitido.test(tipoTecnico)) {
            throw new InvarianteVioladaException(mensagemTipo);
        }
        if (estaEncerrada()) {
            throw new InvarianteVioladaException("A solicitacao possui saldo pendente encerrado.");
        }
        if (status != StatusSolicitacao.APROVADO && status != StatusSolicitacao.ENTREGUE_PARCIAL
                && status != StatusSolicitacao.DEVOLVIDO_PARCIAL
                && status != StatusSolicitacao.ESTORNADO) {
            throw new InvarianteVioladaException("O status atual nao permite confirmar a movimentacao tecnica.");
        }
    }

    public void removerItem(UUID itemId) {
        if (this.status != StatusSolicitacao.PENDENTE && this.status != StatusSolicitacao.APROVADO && this.status != StatusSolicitacao.ENTREGUE_PARCIAL) {
            throw new InvarianteVioladaException("A remoção de itens só é permitida antes do encerramento da solicitação.");
        }
        
        ItemSolicitacao item = this.itens.stream()
            .filter(i -> i.getId().equals(itemId))
            .findFirst()
            .orElseThrow(() -> new InvarianteVioladaException("Item não encontrado para remoção: " + itemId));

        if (item.possuiFatoOperacional()) {
            throw new InvarianteVioladaException("Não é possível remover um item que já possui operações registradas: " + item.getMaterial().getCodigo());
        }
        
        this.itens.remove(item);
        if (!possuiPendencia()) {
            removerPendencia();
        }
    }

    public void aprovar(UUID idAprovador) {
        if (this.status != StatusSolicitacao.PENDENTE && this.status != StatusSolicitacao.APROVADO) {
            throw new InvarianteVioladaException("Status inválido para aprovação: " + this.status);
        }
        if (this.itens.isEmpty()) {
            throw new InvarianteVioladaException("Não é possível aprovar uma solicitação sem itens.");
        }
        this.idAprovador = Objects.requireNonNull(idAprovador, "ID do aprovador é obrigatório");
        this.dataVerificacao = OffsetDateTime.now();
        this.status = StatusSolicitacao.APROVADO;
    }

    public void rejeitar(MotivoRejeicao motivo, String justificativa) {
        if (this.contexto == ContextoSolicitacaoMaterial.TECNICO) {
            if (this.status != StatusSolicitacao.APROVADO) {
                throw new InvarianteVioladaException("Apenas solicitações técnicas aprovadas podem ser rejeitadas.");
            }
            if (estaEncerrada()) {
                throw new InvarianteVioladaException("A solicitação técnica já está encerrada.");
            }
            if (possuiFatoOperacional()) {
                throw new InvarianteVioladaException("Não é possível rejeitar solicitação com entrega confirmada.");
            }
            if (this.statusSeparacao == null
                    || this.statusSeparacao == StatusSeparacao.AGUARDANDO_AGENDAMENTO) {
                throw new InvarianteVioladaException("Apenas solicitações técnicas já planejadas podem ser rejeitadas.");
            }
        } else {
            if (this.status != StatusSolicitacao.PENDENTE && this.status != StatusSolicitacao.APROVADO) {
                throw new InvarianteVioladaException("Apenas solicitações PENDENTES ou APROVADAS podem ser rejeitadas.");
            }
        }
        if (motivo == null) {
            throw new IllegalArgumentException("O motivo da rejeição é obrigatório.");
        }
        if (justificativa == null || justificativa.isBlank()) {
            throw new IllegalArgumentException("A justificativa da rejeição é obrigatória.");
        }
        if (!motivo.isAtivo()) {
            throw new InvarianteVioladaException("O motivo de rejeição informado está desativado.");
        }
        this.motivoRejeicao = motivo;
        this.justificativaRejeicao = justificativa.trim();
        this.dataVerificacao = OffsetDateTime.now();
        this.status = StatusSolicitacao.REJEITADO;
        this.statusSeparacao = null;
    }

    public void registrarEntrega(UUID idEntregador, String assinaturaAlmox, String assinaturaEquipe, 
                                 java.util.Map<UUID, ItemEntregaInfo> entregas,
                                 java.util.List<NovoItemEntregaInfo> novosItens) {
        if (this.status != StatusSolicitacao.APROVADO && this.status != StatusSolicitacao.ENTREGUE_PARCIAL
                && this.status != StatusSolicitacao.ESTORNADO) {
            throw new InvarianteVioladaException("Apenas solicitações aprovadas, parcialmente entregues ou estornadas podem ser entregues.");
        }
        exigirSaldoAberto();
        this.idEntregadorAlmox = Objects.requireNonNull(idEntregador, "ID do entregador é obrigatório");
        this.assinaturaAlmox = Objects.requireNonNull(assinaturaAlmox, "Assinatura do almoxarifado é obrigatória");
        this.assinaturaEquipe = Objects.requireNonNull(assinaturaEquipe, "Assinatura da equipe é obrigatória");
        
        // Processa itens novos (adicionados no balcão)
        if (novosItens != null) {
            for (NovoItemEntregaInfo novo : novosItens) {
                ItemSolicitacao novoItem = new ItemSolicitacao(this, novo.material(), novo.quantidadeSolicitada());
                this.itens.add(novoItem);
                if (novo.quantidadeEntregue() != null && novo.quantidadeEntregue().compareTo(BigDecimal.ZERO) > 0) {
                    novoItem.registrarEntrega(novo.quantidadeEntregue(), novo.numSerie());
                }
            }
        }

        // Processa itens existentes
        if (entregas != null) {
            entregas.forEach((idItem, info) -> {
                ItemSolicitacao item = this.itens.stream()
                    .filter(i -> i.getId().equals(idItem))
                    .findFirst()
                    .orElseThrow(() -> new InvarianteVioladaException("Item não encontrado nesta solicitação: " + idItem));
                
                if (info.novaQuantidadeSolicitada() != null) {
                    item.atualizarQuantidade(info.novaQuantidadeSolicitada());
                }

                if (info.quantidade() != null && info.quantidade().compareTo(BigDecimal.ZERO) > 0) {
                    item.registrarEntrega(info.quantidade(), info.numSerie());
                }
            });
        }

        if (this.itens.isEmpty()) {
            throw new InvarianteVioladaException("Não é possível registrar entrega sem itens.");
        }

        atualizarStatusEntrega();
        this.dataEntrega = OffsetDateTime.now();
    }

    public void registrarResponsavelSaqueComercial(UUID idColaboradorSaque, boolean saqueForaEquipe) {
        if (contexto != ContextoSolicitacaoMaterial.COMERCIAL) {
            throw new InvarianteVioladaException("O responsavel comercial pelo saque exige uma solicitacao comercial.");
        }
        this.idColaboradorSaque = Objects.requireNonNull(
                idColaboradorSaque, "O colaborador responsavel pelo saque e obrigatorio");
        this.saqueForaEquipe = saqueForaEquipe;
    }

    /**
     * Informação para novos itens adicionados durante a entrega física.
     */
    public record NovoItemEntregaInfo(
        Material material,
        BigDecimal quantidadeSolicitada,
        BigDecimal quantidadeEntregue,
        String numSerie
    ) {
        public NovoItemEntregaInfo {
            Objects.requireNonNull(material, "O material é obrigatório");
            if (quantidadeSolicitada == null || quantidadeSolicitada.signum() <= 0) {
                throw new IllegalArgumentException("A quantidade solicitada deve ser positiva");
            }
            if (quantidadeEntregue == null || quantidadeEntregue.signum() <= 0) {
                throw new IllegalArgumentException("A quantidade entregue deve ser positiva");
            }
            if (quantidadeEntregue.compareTo(quantidadeSolicitada) > 0) {
                throw new InvarianteVioladaException(
                        "A quantidade entregue não pode ultrapassar a quantidade solicitada do novo material.");
            }
        }
    }

    /**
     * Objeto de transferência interno para o domínio processar a entrega.
     */
    public record ItemEntregaInfo(
        java.math.BigDecimal quantidade,
        java.math.BigDecimal novaQuantidadeSolicitada,
        String numSerie
    ) {}

    private void atualizarStatusEntrega() {
        boolean todosEntregues = this.itens.stream().allMatch(ItemSolicitacao::isTotalmenteEntregue);
        this.status = todosEntregues ? StatusSolicitacao.ENTREGUE : StatusSolicitacao.ENTREGUE_PARCIAL;
    }

    public void encerrarEntregaParcialExpirada() {
        if (this.status != StatusSolicitacao.ENTREGUE_PARCIAL) {
            throw new InvarianteVioladaException("Apenas solicitações com entrega parcial podem ser encerradas por expiração.");
        }
        if (this.encerradaEm == null) {
            this.encerradaEm = OffsetDateTime.now();
            this.motivoEncerramento = "Entrega parcial encerrada automaticamente apos 72 horas.";
        }
    }

    public void estornar() {
        if (this.status != StatusSolicitacao.ENTREGUE && this.status != StatusSolicitacao.ENTREGUE_PARCIAL) {
            throw new InvarianteVioladaException("O estorno só é permitido para solicitações que já foram entregues.");
        }
        this.itens.forEach(item -> {
            BigDecimal quantidadeLiquida = item.getQuantidadeEntregaLiquida();
            if (quantidadeLiquida.signum() > 0) {
                item.registrarEstorno(quantidadeLiquida);
            }
        });
        this.status = StatusSolicitacao.ESTORNADO;
    }

    public void registrarEstornoParcial(java.util.Map<UUID, java.math.BigDecimal> estornos) {
        if (this.status != StatusSolicitacao.ENTREGUE && this.status != StatusSolicitacao.ENTREGUE_PARCIAL) {
            throw new InvarianteVioladaException("O estorno parcial só é permitido para solicitações com entregas registradas.");
        }
        if (estornos == null || estornos.isEmpty()) {
            throw new IllegalArgumentException("É necessário informar ao menos um item para estorno.");
        }

        estornos.forEach((idItem, qtd) -> {
            ItemSolicitacao item = this.itens.stream()
                .filter(i -> i.getId().equals(idItem))
                .findFirst()
                .orElseThrow(() -> new InvarianteVioladaException("Item não encontrado nesta solicitação para estorno: " + idItem));
            
            item.registrarEstorno(qtd);
        });

        atualizarStatusAposEstorno();
    }

    private void atualizarStatusAposEstorno() {
        BigDecimal totalEntregue = this.itens.stream()
            .map(ItemSolicitacao::getQuantidadeEntregaLiquida)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalEntregue.compareTo(BigDecimal.ZERO) == 0) {
            this.status = StatusSolicitacao.ESTORNADO;
        } else {
            boolean todosEntregues = this.itens.stream().allMatch(ItemSolicitacao::isTotalmenteEntregue);
            this.status = todosEntregues ? StatusSolicitacao.ENTREGUE : StatusSolicitacao.ENTREGUE_PARCIAL;
        }
    }

    private void validarEstoque(Estoque estoque) {
        if (estoque == null) throw new IllegalArgumentException("Estoque de destino é obrigatório");
        if (!estoque.isAtivo()) throw new InvarianteVioladaException("Estoque de destino está desativado.");
    }

    private void validarStatusParaAlteracao() {
        if (this.status != StatusSolicitacao.PENDENTE) {
            throw new InvarianteVioladaException("A solicitação não pode mais ser alterada (Status: " + this.status + ")");
        }
    }
}
