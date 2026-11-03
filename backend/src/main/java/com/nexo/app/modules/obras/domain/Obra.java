package com.nexo.app.modules.obras.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Raiz do agregado de Obra e Gestão de Projetos.
 *
 * <p>Centraliza os dados da obra, referências operacionais (base, processo, município, centro de custo),
 * status, agente responsável da concessionária/cliente, equipes alocadas, subprojetos vinculados (filhos)
 * e repositório de anexos técnicos (As-Built, CAD, memoriais, etc.).</p>
 */
@Entity
@Table(name = "obras")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Obra extends BaseEntity {

    @NotBlank(message = "O número da obra é obrigatório")
    @Size(min = 1, max = 7, message = "O número da obra deve ter até 7 caracteres")
    @Column(name = "numero", nullable = false, length = 7)
    private String numero;

    @NotBlank(message = "O título da obra é obrigatório")
    @Size(min = 2, max = 300, message = "O título da obra deve ter entre 2 e 300 caracteres")
    @Column(name = "titulo", nullable = false, length = 300)
    private String titulo;

    @NotNull(message = "O tipo da obra é obrigatório")
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_obra", nullable = false, length = 20)
    private TipoObra tipoObra = TipoObra.OBRA;

    @Column(name = "descricao", length = 1000)
    private String descricao;

    @Column(name = "nota_obra", length = 50)
    private String notaObra;

    @Column(name = "valor_projeto", precision = 15, scale = 2)
    private BigDecimal valorProjeto;

    @NotNull(message = "A data de início é obrigatória")
    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;

    @NotNull(message = "A data prevista de conclusão é obrigatória")
    @Column(name = "data_previsao_fim", nullable = false)
    private LocalDate dataPrevisaoFim;

    @Column(name = "data_fim_efetiva")
    private LocalDate dataFimEfetiva;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @NotNull(message = "O processo de negócio da obra é obrigatório")
    @Column(name = "id_processo", nullable = false)
    private UUID idProcesso;

    @NotNull(message = "A base operacional da obra é obrigatória")
    @Column(name = "id_base_operacional", nullable = false)
    private UUID idBaseOperacional;

    @NotNull(message = "O município da obra é obrigatório")
    @Column(name = "id_municipio", nullable = false)
    private UUID idMunicipio;

    @NotNull(message = "O centro de custo da obra é obrigatório")
    @Column(name = "id_centro_custo", nullable = false)
    private UUID idCentroCusto;

    @NotNull(message = "O supervisor da obra é obrigatório")
    @Column(name = "id_supervisor", nullable = false)
    private UUID idSupervisor;

    @NotNull(message = "O coordenador da obra é obrigatório")
    @Column(name = "id_coordenador", nullable = false)
    private UUID idCoordenador;

    @NotNull(message = "O status da obra é obrigatório")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_status_obra", nullable = false)
    private StatusObra statusObra;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_agente_responsavel")
    private AgenteResponsavel agenteResponsavel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_obra_pai")
    @Deprecated(forRemoval = true)
    private Obra obraPai;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_vinculo_pai", length = 50)
    @Deprecated(forRemoval = true)
    private TipoVinculoSubprojeto tipoVinculoPai;

    @OneToMany(mappedBy = "obraPai")
    @Deprecated(forRemoval = true)
    private List<Obra> subprojetos = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "projetoA")
    private List<RelacionamentoProjeto> relacionamentosComoProjetoA = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "projetoB")
    private List<RelacionamentoProjeto> relacionamentosComoProjetoB = new ArrayList<>();

    public List<RelacionamentoProjeto> getRelacionamentosProjeto() {
        List<RelacionamentoProjeto> relacionamentos = new ArrayList<>(relacionamentosComoProjetoA);
        relacionamentos.addAll(relacionamentosComoProjetoB);
        return List.copyOf(relacionamentos);
    }

    @ElementCollection
    @CollectionTable(name = "obras_equipes", joinColumns = @JoinColumn(name = "id_obra"))
    @Column(name = "id_equipe")
    private Set<UUID> equipes = new HashSet<>();

    @JsonManagedReference
    @OneToMany(mappedBy = "obra", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AnexoObra> anexos = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "obra", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private List<HistoricoStatusObra> historicoStatus = new ArrayList<>();

    // ---- Dados de carteira (todos opcionais; o "prazo" da carteira é a dataPrevisaoFim) ----

    @Column(name = "id_contrato")
    private UUID idContrato;

    @Enumerated(EnumType.STRING)
    @Column(name = "classe_tensao", length = 20)
    private ClasseTensao classeTensao;

    @Column(name = "classe_tensao_informada_por")
    private String classeTensaoInformadaPor;

    @Column(name = "classe_tensao_informada_em")
    private OffsetDateTime classeTensaoInformadaEm;

    @Column(name = "prazo_regulado")
    private LocalDate prazoRegulado;

    @Enumerated(EnumType.STRING)
    @Column(name = "prioridade", length = 20)
    private PrioridadeObra prioridade;

    @Column(name = "lote_previsto", length = 40)
    private String lotePrevisto;

    @Column(name = "valor_material_previsto", precision = 15, scale = 2)
    private BigDecimal valorMaterialPrevisto;

    @Column(name = "valor_servico_previsto", precision = 15, scale = 2)
    private BigDecimal valorServicoPrevisto;

    @Column(name = "observacao_status", length = 1000)
    private String observacaoStatus;

    // ---- Deslocamento base -> obra (calculado; guarda a base e as coordenadas usadas) ----

    @Column(name = "distancia_base_km", precision = 10, scale = 2)
    private BigDecimal distanciaBaseKm;

    @Column(name = "tempo_deslocamento_min")
    private Integer tempoDeslocamentoMin;

    @Column(name = "deslocamento_estimado")
    private Boolean deslocamentoEstimado;

    @Column(name = "deslocamento_calculado_em")
    private OffsetDateTime deslocamentoCalculadoEm;

    @Column(name = "id_base_deslocamento")
    private UUID idBaseDeslocamento;

    @Column(name = "latitude_deslocamento")
    private Double latitudeDeslocamento;

    @Column(name = "longitude_deslocamento")
    private Double longitudeDeslocamento;

    public Obra(String numero, String titulo, String descricao, String notaObra,
                BigDecimal valorProjeto, LocalDate dataInicio, LocalDate dataPrevisaoFim,
                Double latitude, Double longitude, UUID idProcesso, UUID idBaseOperacional,
                UUID idMunicipio, UUID idCentroCusto, UUID idSupervisor, UUID idCoordenador,
                StatusObra statusObra, AgenteResponsavel agenteResponsavel, Set<UUID> equipes) {
        this(numero, titulo, descricao, notaObra, valorProjeto, dataInicio, dataPrevisaoFim,
                latitude, longitude, idProcesso, idBaseOperacional, idMunicipio, idCentroCusto,
                idSupervisor, idCoordenador, statusObra, agenteResponsavel, equipes, TipoObra.OBRA);
    }

    public Obra(String numero, String titulo, String descricao, String notaObra,
                BigDecimal valorProjeto, LocalDate dataInicio, LocalDate dataPrevisaoFim,
                Double latitude, Double longitude, UUID idProcesso, UUID idBaseOperacional,
                UUID idMunicipio, UUID idCentroCusto, UUID idSupervisor, UUID idCoordenador,
                StatusObra statusObra, AgenteResponsavel agenteResponsavel, Set<UUID> equipes,
                TipoObra tipoObra) {
        validarCamposObrigatorios(numero, titulo, dataInicio, dataPrevisaoFim, idProcesso, idBaseOperacional, idMunicipio, idCentroCusto, idSupervisor, idCoordenador, statusObra);
        this.numero = numero.trim();
        this.titulo = titulo.trim().toUpperCase(Locale.ROOT);
        this.tipoObra = TipoObra.ouPadrao(tipoObra);
        this.descricao = descricao != null && !descricao.isBlank() ? descricao.trim() : null;
        this.notaObra = notaObra != null && !notaObra.isBlank() ? notaObra.trim() : null;
        this.valorProjeto = valorProjeto;
        this.dataInicio = dataInicio;
        this.dataPrevisaoFim = dataPrevisaoFim;
        this.latitude = latitude;
        this.longitude = longitude;
        this.idProcesso = idProcesso;
        this.idBaseOperacional = idBaseOperacional;
        this.idMunicipio = idMunicipio;
        this.idCentroCusto = idCentroCusto;
        this.idSupervisor = idSupervisor;
        this.idCoordenador = idCoordenador;
        this.statusObra = statusObra;
        this.agenteResponsavel = agenteResponsavel;
        if (equipes != null) {
            this.equipes.addAll(equipes);
        }
        this.historicoStatus.add(new HistoricoStatusObra(this, null, statusObra, null));
    }

    public void atualizarDados(String numero, String titulo, String descricao, String notaObra,
                               BigDecimal valorProjeto, LocalDate dataInicio, LocalDate dataPrevisaoFim,
                               Double latitude, Double longitude, UUID idProcesso, UUID idBaseOperacional,
                               UUID idMunicipio, UUID idCentroCusto, UUID idSupervisor, UUID idCoordenador,
                               StatusObra statusObra, AgenteResponsavel agenteResponsavel, Set<UUID> novasEquipes) {
        atualizarDados(numero, titulo, descricao, notaObra, valorProjeto, dataInicio, dataPrevisaoFim,
                latitude, longitude, idProcesso, idBaseOperacional, idMunicipio, idCentroCusto,
                idSupervisor, idCoordenador, statusObra, agenteResponsavel, novasEquipes, this.tipoObra);
    }

    public void atualizarDados(String numero, String titulo, String descricao, String notaObra,
                               BigDecimal valorProjeto, LocalDate dataInicio, LocalDate dataPrevisaoFim,
                               Double latitude, Double longitude, UUID idProcesso, UUID idBaseOperacional,
                               UUID idMunicipio, UUID idCentroCusto, UUID idSupervisor, UUID idCoordenador,
                               StatusObra statusObra, AgenteResponsavel agenteResponsavel, Set<UUID> novasEquipes,
                               TipoObra tipoObra) {
        validarAtivo();
        validarCamposObrigatorios(numero, titulo, dataInicio, dataPrevisaoFim, idProcesso, idBaseOperacional, idMunicipio, idCentroCusto, idSupervisor, idCoordenador, statusObra);
        this.numero = numero.trim();
        this.titulo = titulo.trim().toUpperCase(Locale.ROOT);
        this.tipoObra = TipoObra.ouPadrao(tipoObra);
        this.descricao = descricao != null && !descricao.isBlank() ? descricao.trim() : null;
        this.notaObra = notaObra != null && !notaObra.isBlank() ? notaObra.trim() : null;
        this.valorProjeto = valorProjeto;
        this.dataInicio = dataInicio;
        this.dataPrevisaoFim = dataPrevisaoFim;
        this.latitude = latitude;
        this.longitude = longitude;
        this.idProcesso = idProcesso;
        this.idBaseOperacional = idBaseOperacional;
        this.idMunicipio = idMunicipio;
        this.idCentroCusto = idCentroCusto;
        this.idSupervisor = idSupervisor;
        this.idCoordenador = idCoordenador;
        registrarTrocaStatus(statusObra, null);
        this.agenteResponsavel = agenteResponsavel;
        atualizarEquipes(novasEquipes);
    }

    public void alterarStatus(StatusObra novoStatus, LocalDate dataFimEfetiva) {
        validarAtivo();
        registrarTrocaStatus(Objects.requireNonNull(novoStatus, "O novo status é obrigatório."), null);
        if (novoStatus.isConcluida()) {
            this.dataFimEfetiva = dataFimEfetiva != null ? dataFimEfetiva : LocalDate.now();
        } else {
            this.dataFimEfetiva = null;
        }
    }

    @Deprecated(forRemoval = true)
    public void vincularObraPai(Obra pai, TipoVinculoSubprojeto tipo) {
        validarAtivo();
        if (pai != null && pai.getId() != null && pai.getId().equals(this.getId())) {
            throw new InvarianteVioladaException("Uma obra não pode ser vinculada como filha de si mesma.");
        }
        this.obraPai = pai;
        this.tipoVinculoPai = tipo != null ? tipo : TipoVinculoSubprojeto.KIT;
    }

    @Deprecated(forRemoval = true)
    public void desvincularObraPai() {
        validarAtivo();
        this.obraPai = null;
        this.tipoVinculoPai = null;
    }

    public void atualizarEquipes(Set<UUID> novasEquipes) {
        validarAtivo();
        this.equipes.clear();
        if (novasEquipes != null) {
            this.equipes.addAll(novasEquipes);
        }
    }

    public void vincularEquipeSeAusente(UUID idEquipe) {
        validarAtivo();
        if (idEquipe != null) {
            this.equipes.add(idEquipe);
        }
    }

    public AnexoObra adicionarAnexo(TipoAnexoObra tipo, String nomeArquivo, String contentType,
                                   long tamanhoBytes, String chaveStorage, String observacoes) {
        validarAtivo();
        AnexoObra anexo = new AnexoObra(this, tipo, nomeArquivo, contentType, tamanhoBytes, chaveStorage, observacoes);
        this.anexos.add(anexo);
        return anexo;
    }

    public void removerAnexo(UUID idAnexo) {
        validarAtivo();
        if (idAnexo == null) return;
        this.anexos.removeIf(a -> idAnexo.equals(a.getId()));
    }

    /**
     * Status operacional informado pela carteira, com observação que fica no histórico e na listagem.
     */
    public void informarStatusOperacional(StatusObra novoStatus, String observacao, LocalDate dataFimEfetiva) {
        validarAtivo();
        Objects.requireNonNull(novoStatus, "O novo status é obrigatório.");
        String obs = textoOpcional(observacao, 1000, "A observação do status");
        if (novoStatus.equals(this.statusObra)) {
            // Mesmo status com nova observação também vai para o histórico.
            this.historicoStatus.add(new HistoricoStatusObra(this, this.statusObra, novoStatus, obs));
        } else {
            registrarTrocaStatus(novoStatus, obs);
        }
        this.observacaoStatus = obs;
        if (novoStatus.isConcluida()) {
            this.dataFimEfetiva = dataFimEfetiva != null ? dataFimEfetiva : LocalDate.now();
        } else {
            this.dataFimEfetiva = null;
        }
    }

    public void informarCarteira(UUID idContrato, LocalDate prazoRegulado, PrioridadeObra prioridade,
                                 String lotePrevisto, BigDecimal valorProjeto,
                                 BigDecimal valorMaterialPrevisto, BigDecimal valorServicoPrevisto) {
        validarAtivo();
        validarNaoNegativo(valorProjeto, "O valor de projeto");
        validarNaoNegativo(valorMaterialPrevisto, "O valor de material previsto");
        validarNaoNegativo(valorServicoPrevisto, "O valor de serviço previsto");
        this.idContrato = idContrato;
        this.prazoRegulado = prazoRegulado;
        this.prioridade = prioridade;
        this.lotePrevisto = textoOpcional(lotePrevisto, 40, "O lote previsto");
        if (valorProjeto != null) {
            this.valorProjeto = valorProjeto;
        }
        this.valorMaterialPrevisto = valorMaterialPrevisto;
        this.valorServicoPrevisto = valorServicoPrevisto;
    }

    /**
     * Registra a classe de tensão com quem informou e quando. Informar a mesma classe não altera o registro.
     */
    public void informarClasseTensao(ClasseTensao classe, String usuario) {
        validarAtivo();
        if (Objects.equals(this.classeTensao, classe)) {
            return;
        }
        this.classeTensao = classe;
        this.classeTensaoInformadaPor = classe != null ? usuario : null;
        this.classeTensaoInformadaEm = classe != null ? OffsetDateTime.now() : null;
    }

    /**
     * Atualiza só as coordenadas (ex.: importação da carteira). O deslocamento fica desatualizado e é recalculado.
     */
    public void atualizarLocalizacao(Double latitude, Double longitude) {
        validarAtivo();
        if ((latitude == null) != (longitude == null)) {
            throw new InvarianteVioladaException("Informe latitude e longitude juntas.");
        }
        if (latitude != null && (Math.abs(latitude) > 90 || Math.abs(longitude) > 180)) {
            throw new InvarianteVioladaException("Coordenadas fora do intervalo válido.");
        }
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public boolean possuiCoordenadas() {
        return latitude != null && longitude != null;
    }

    /**
     * Deslocamento ausente ou calculado com outra base/coordenadas precisa ser recalculado.
     */
    public boolean deslocamentoDesatualizado() {
        if (!possuiCoordenadas()) {
            return distanciaBaseKm != null;
        }
        return distanciaBaseKm == null
                || !Objects.equals(idBaseDeslocamento, idBaseOperacional)
                || !Objects.equals(latitudeDeslocamento, latitude)
                || !Objects.equals(longitudeDeslocamento, longitude);
    }

    public void registrarDeslocamento(BigDecimal distanciaKm, Integer tempoMinutos, boolean estimado) {
        if (!possuiCoordenadas()) {
            throw new InvarianteVioladaException("A obra não tem coordenadas para calcular o deslocamento.");
        }
        this.distanciaBaseKm = distanciaKm != null ? distanciaKm.setScale(2, RoundingMode.HALF_UP) : null;
        this.tempoDeslocamentoMin = tempoMinutos;
        this.deslocamentoEstimado = estimado;
        this.deslocamentoCalculadoEm = OffsetDateTime.now();
        this.idBaseDeslocamento = idBaseOperacional;
        this.latitudeDeslocamento = latitude;
        this.longitudeDeslocamento = longitude;
    }

    public void limparDeslocamento() {
        this.distanciaBaseKm = null;
        this.tempoDeslocamentoMin = null;
        this.deslocamentoEstimado = null;
        this.deslocamentoCalculadoEm = null;
        this.idBaseDeslocamento = null;
        this.latitudeDeslocamento = null;
        this.longitudeDeslocamento = null;
    }

    public List<HistoricoStatusObra> getHistoricoStatus() {
        return List.copyOf(historicoStatus);
    }

    private void registrarTrocaStatus(StatusObra novoStatus, String observacao) {
        if (this.statusObra != null && this.statusObra.equals(novoStatus)) {
            return;
        }
        this.historicoStatus.add(new HistoricoStatusObra(this, this.statusObra, novoStatus, observacao));
        this.statusObra = novoStatus;
    }

    private static void validarNaoNegativo(BigDecimal valor, String campo) {
        if (valor != null && valor.signum() < 0) {
            throw new InvarianteVioladaException(campo + " não pode ser negativo.");
        }
    }

    private static String textoOpcional(String valor, int limite, String campo) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String limpo = valor.trim();
        if (limpo.length() > limite) {
            throw new InvarianteVioladaException(campo + " deve ter até " + limite + " caracteres.");
        }
        return limpo;
    }

    private void validarCamposObrigatorios(String numero, String titulo, LocalDate dataInicio,
                                          LocalDate dataPrevisaoFim, UUID idProcesso, UUID idBaseOperacional,
                                          UUID idMunicipio, UUID idCentroCusto, UUID idSupervisor,
                                          UUID idCoordenador, StatusObra statusObra) {
        if (numero == null || numero.isBlank()) {
            throw new InvarianteVioladaException("O número da obra é obrigatório.");
        }
        if (titulo == null || titulo.isBlank()) {
            throw new InvarianteVioladaException("O título da obra é obrigatório.");
        }
        if (dataInicio == null) {
            throw new InvarianteVioladaException("A data de início da obra é obrigatória.");
        }
        if (dataPrevisaoFim == null) {
            throw new InvarianteVioladaException("A data prevista de conclusão é obrigatória.");
        }
        if (idProcesso == null) {
            throw new InvarianteVioladaException("O processo de negócio da obra é obrigatório.");
        }
        if (idBaseOperacional == null) {
            throw new InvarianteVioladaException("A base operacional da obra é obrigatória.");
        }
        if (idMunicipio == null) {
            throw new InvarianteVioladaException("O município da obra é obrigatório.");
        }
        if (idCentroCusto == null) {
            throw new InvarianteVioladaException("O centro de custo da obra é obrigatório.");
        }
        if (idSupervisor == null) {
            throw new InvarianteVioladaException("O supervisor da obra é obrigatório.");
        }
        if (idCoordenador == null) {
            throw new InvarianteVioladaException("O coordenador da obra é obrigatório.");
        }
        if (statusObra == null) {
            throw new InvarianteVioladaException("O status da obra é obrigatório.");
        }
    }

    private void validarAtivo() {
        if (!isAtivo()) {
            throw new InvarianteVioladaException("Não é possível alterar uma obra desativada.");
        }
    }
}
