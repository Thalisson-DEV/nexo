package com.nexo.app.modules.sst.domain;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Desvio de segurança (não-conformidade) identificado em uma {@link InspecaoSeguranca},
 * tipicamente originado pela escolha de uma opção marcada como {@code geraDesvio}.
 * É atribuído a um ou mais colaboradores inspecionados e possui ciclo de tratamento.
 */
@Entity
@Table(name = "desvios_seguranca")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DesvioSeguranca extends BaseEntity {

    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_inspecao", nullable = false)
    private InspecaoSeguranca inspecao;

    @NotNull
    @Column(name = "id_pergunta", nullable = false)
    private UUID idPergunta;

    @Column(name = "id_opcao_resposta")
    private UUID idOpcaoResposta;

    @Enumerated(EnumType.STRING)
    @Column(name = "gravidade", nullable = false, length = 20)
    private GravidadeDesvio gravidade;

    @Column(name = "descricao", length = 1000)
    private String descricao;

    @Column(name = "acao_corretiva", length = 1000)
    private String acaoCorretiva;

    @Column(name = "prazo")
    private LocalDate prazo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusDesvio status;

    @Column(name = "data_resolucao")
    private OffsetDateTime dataResolucao;

    @JsonManagedReference
    @OneToMany(mappedBy = "desvio", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DesvioColaborador> colaboradores = new ArrayList<>();

    @JsonManagedReference
    @OneToMany(mappedBy = "desvio", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AnexoDesvio> anexos = new ArrayList<>();

    DesvioSeguranca(InspecaoSeguranca inspecao, UUID idPergunta, UUID idOpcaoResposta,
                    GravidadeDesvio gravidade, String descricao, String acaoCorretiva, LocalDate prazo) {
        this(inspecao, idPergunta, idOpcaoResposta, gravidade, descricao, acaoCorretiva, prazo, StatusDesvio.ABERTO, null);
    }

    DesvioSeguranca(InspecaoSeguranca inspecao, UUID idPergunta, UUID idOpcaoResposta,
                    GravidadeDesvio gravidade, String descricao, String acaoCorretiva, LocalDate prazo,
                    StatusDesvio status, OffsetDateTime dataResolucao) {
        this.inspecao = Objects.requireNonNull(inspecao, "Inspeção é obrigatória");
        this.idPergunta = Objects.requireNonNull(idPergunta, "ID da pergunta é obrigatório");
        this.gravidade = Objects.requireNonNull(gravidade, "A gravidade do desvio é obrigatória");
        this.idOpcaoResposta = idOpcaoResposta;
        this.descricao = descricao;
        this.acaoCorretiva = acaoCorretiva;
        this.prazo = prazo;
        this.status = status != null ? status : StatusDesvio.ABERTO;
        if (this.status == StatusDesvio.RESOLVIDO) {
            this.dataResolucao = dataResolucao != null ? dataResolucao : OffsetDateTime.now();
        } else {
            this.dataResolucao = null;
        }
    }

    void adicionarColaborador(UUID idColaborador) {
        this.colaboradores.add(new DesvioColaborador(this, idColaborador));
    }

    /**
     * Atualiza os dados completos do desvio e sincroniza colaboradores.
     */
    public void atualizar(String descricao, String acaoCorretiva, LocalDate prazo, StatusDesvio status, List<UUID> novosColaboradores) {
        this.descricao = descricao;
        this.acaoCorretiva = acaoCorretiva;
        this.prazo = prazo;
        StatusDesvio novoStatus = status != null ? status : StatusDesvio.ABERTO;
        if (this.status != StatusDesvio.RESOLVIDO && novoStatus == StatusDesvio.RESOLVIDO) {
            this.dataResolucao = (this.inspecao != null && this.inspecao.getDataRealizacao() != null)
                    ? this.inspecao.getDataRealizacao()
                    : OffsetDateTime.now();
        } else if (this.status == StatusDesvio.RESOLVIDO && novoStatus != StatusDesvio.RESOLVIDO) {
            this.dataResolucao = null;
        }
        this.status = novoStatus;
        if (this.status == StatusDesvio.RESOLVIDO && this.prazo == null && this.inspecao != null && this.inspecao.getDataRealizacao() != null) {
            this.prazo = this.inspecao.getDataRealizacao().toLocalDate();
        }
        this.colaboradores.clear();
        if (novosColaboradores != null) {
            novosColaboradores.forEach(this::adicionarColaborador);
        }
    }

    /**
     * Atualiza o tratamento do desvio (status, ação corretiva e prazo).
     */
    public void atualizarTratamento(StatusDesvio status, String acaoCorretiva, LocalDate prazo) {
        StatusDesvio novoStatus = Objects.requireNonNull(status, "O status do desvio é obrigatório");
        if (this.status == StatusDesvio.RESOLVIDO && novoStatus != StatusDesvio.RESOLVIDO) {
            this.dataResolucao = null;
        } else if (this.status != StatusDesvio.RESOLVIDO && novoStatus == StatusDesvio.RESOLVIDO) {
            this.dataResolucao = OffsetDateTime.now();
        }
        this.status = novoStatus;
        if (acaoCorretiva != null) {
            this.acaoCorretiva = acaoCorretiva;
        }
        if (prazo != null) {
            this.prazo = prazo;
        }
    }

    /**
     * Anexa uma evidência (foto/documento) ao desvio, no contexto informado
     * (a própria não-conformidade ou a ação corretiva). Anexos são opcionais.
     */
    public AnexoDesvio adicionarAnexo(ContextoAnexo contexto, String nomeArquivo, String contentType,
                                      long tamanhoBytes, String objectKey) {
        AnexoDesvio anexo = new AnexoDesvio(this, contexto, nomeArquivo, contentType, tamanhoBytes, objectKey);
        this.anexos.add(anexo);
        return anexo;
    }

    /**
     * Remove uma evidência do desvio. A exclusão do objeto no armazenamento é
     * responsabilidade do caso de uso.
     */
    public void removerAnexo(AnexoDesvio anexo) {
        this.anexos.remove(anexo);
    }
}
