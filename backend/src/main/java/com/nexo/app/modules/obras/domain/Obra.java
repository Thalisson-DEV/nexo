package com.nexo.app.modules.obras.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
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
        this.statusObra = statusObra;
        this.agenteResponsavel = agenteResponsavel;
        atualizarEquipes(novasEquipes);
    }

    public void alterarStatus(StatusObra novoStatus, LocalDate dataFimEfetiva) {
        validarAtivo();
        this.statusObra = Objects.requireNonNull(novoStatus, "O novo status é obrigatório.");
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
