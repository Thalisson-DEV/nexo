package com.nexo.app.modules.execucao.domain;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.application.dto.OperacaoMaterial;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.infrastructure.persistence.CryptoConverterImpl;
import com.nexo.app.shared.infrastructure.security.HashUtils;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Representa uma execução de campo realizada por uma equipe.
 * Centraliza o registro de ações e materiais aplicados/retirados.
 */
@Entity
@Table(name = "execucoes_comercial")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExecucaoComercial extends BaseEntity {

    @Convert(converter = CryptoConverterImpl.class)
    @Column(name = "referencia_nota", length = 100)
    private String referenciaNota;

    @Column(name = "referencia_nota_hash", length = 32)
    private String referenciaNotaHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_nota", nullable = false, length = 20)
    private TipoNota tipoNota = TipoNota.COMUM;

    /**
     * Número gerado pelo app para a nota avulsa. Fica guardado depois que o número
     * oficial substitui a referência, para quem anotou o provisório em campo achar a nota.
     */
    @Column(name = "referencia_provisoria", length = 40)
    private String referenciaProvisoria;

    /** Quando o número oficial da avulsa foi informado; nulo enquanto ela espera. */
    @Column(name = "data_numero_oficial")
    private OffsetDateTime dataNumeroOficial;

    @Column(name = "id_usuario_numero_oficial")
    private UUID idUsuarioNumeroOficial;

    /**
     * Tipo de serviço da nota. Obrigatório no app de campo, mas nulo nas execuções
     * anteriores ao cadastro e nos lançamentos enviados por versões antigas do app.
     */
    @Column(name = "id_tipo_servico")
    private UUID idTipoServico;

    @NotNull(message = "O ID da equipe é obrigatório")
    @Column(name = "id_equipe", nullable = false)
    private UUID idEquipe;

    @NotNull(message = "O ID do turno é obrigatório")
    @Column(name = "id_turno", nullable = false)
    private UUID idTurno;

    /**
     * Município de atendimento. Opcional: o lançamento de campo identifica o local
     * apenas pela localização (GPS); execuções antigas mantêm o município informado.
     */
    @Column(name = "id_municipio")
    private UUID idMunicipio;


    /** Localização coletada pelo dispositivo no momento do lançamento. */
    @Column(name = "latitude", precision = 10, scale = 8)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 11, scale = 8)
    private BigDecimal longitude;

    /**
     * Momento em que a nota foi lançada. O lançamento de campo é feito de uma só vez,
     * então não existe início nem fim: esta é a única data da execução, e é ela que
     * define o período de faturamento e o preço vigente das ações.
     */
    @NotNull(message = "A data de lançamento é obrigatória")
    @Column(name = "data_lancamento", nullable = false)
    private OffsetDateTime dataLancamento;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_execucao", length = 20)
    private StatusExecucao statusExecucao;

    @Column(name = "valor_total_faturado", precision = 12, scale = 2)
    private BigDecimal valorTotalFaturado;

    @Column(name = "id_usuario_alteracao")
    private UUID idUsuarioAlteracao;

    @Column(name = "data_alteracao")
    private OffsetDateTime dataAlteracao;

    /** Quantas vezes a nota foi alterada depois de sincronizada; 0 = como foi lançada. */
    @Column(name = "total_alteracoes", nullable = false)
    private int totalAlteracoes = 0;

    @JsonManagedReference
    @OneToMany(mappedBy = "execucao", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ExecucaoAcao> acoes = new ArrayList<>();

    @JsonManagedReference
    @OneToMany(mappedBy = "execucao", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ExecucaoMaterial> materiais = new ArrayList<>();

    public ExecucaoComercial(String referenciaNota, UUID idEquipe, UUID idTurno, UUID idMunicipio,
                             OffsetDateTime dataLancamento, BigDecimal latitude, BigDecimal longitude,
                             UUID idTipoServico) {
        this(referenciaNota, idEquipe, idTurno, idMunicipio, dataLancamento, latitude, longitude,
                idTipoServico, TipoNota.COMUM);
    }

    public ExecucaoComercial(String referenciaNota, UUID idEquipe, UUID idTurno, UUID idMunicipio,
                             OffsetDateTime dataLancamento, BigDecimal latitude, BigDecimal longitude,
                             UUID idTipoServico, TipoNota tipoNota) {
        this.tipoNota = Objects.requireNonNull(tipoNota, "Tipo da nota é obrigatório");
        if (referenciaNota == null || referenciaNota.isBlank()) {
            throw new InvarianteVioladaException(tipoNota == TipoNota.OCORRENCIA
                    ? "Informe o número da ocorrência."
                    : "Informe o número da nota.");
        }
        referenciaNota = referenciaNota.trim();
        if (tipoNota == TipoNota.AVULSA) {
            this.referenciaProvisoria = referenciaNota;
        }
        this.referenciaNota = referenciaNota;
        this.idTipoServico = idTipoServico;
        this.referenciaNotaHash = HashUtils.gerarBlindIndex(referenciaNota);
        this.idEquipe = Objects.requireNonNull(idEquipe, "ID da equipe é obrigatório");
        this.idTurno = Objects.requireNonNull(idTurno, "ID do turno é obrigatório");
        this.idMunicipio = idMunicipio;
        this.dataLancamento = Objects.requireNonNull(dataLancamento, "Data de lançamento é obrigatória");
        this.statusExecucao = StatusExecucao.INICIADO;
        this.valorTotalFaturado = BigDecimal.ZERO;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    /**
     * Adiciona uma ação à execução.
     * Invariante: Uma execução não pode comportar a mesma ação mais de uma vez.
     */
    public void adicionarAcao(UUID idAcao) {
        Objects.requireNonNull(idAcao, "ID da ação é obrigatório");
        boolean jaExiste = acoes.stream().anyMatch(a -> a.getIdAcao().equals(idAcao));
        if (jaExiste) {
            throw new InvarianteVioladaException("A ação " + idAcao + " já foi adicionada a esta execução.");
        }
        this.acoes.add(new ExecucaoAcao(this, idAcao));
    }

    /**
     * Adiciona um material à execução (aplicado ou retirado).
     */
    public void adicionarMaterial(UUID idMaterial, BigDecimal quantidade, OperacaoMaterial operacao, String numSerie) {
        adicionarMaterial(idMaterial, quantidade, operacao, numSerie, OrigemMaterial.EQUIPE);
    }

    /**
     * Adiciona um material dizendo de onde ele veio. Material da concessionária só
     * existe na ocorrência comercial e nunca movimenta o saldo da equipe.
     */
    public void adicionarMaterial(UUID idMaterial, BigDecimal quantidade, OperacaoMaterial operacao, String numSerie,
                                  OrigemMaterial origem) {
        Objects.requireNonNull(idMaterial, "ID do material é obrigatório");
        Objects.requireNonNull(operacao, "Operação do material é obrigatória");
        Objects.requireNonNull(origem, "Origem do material é obrigatória");

        if (origem == OrigemMaterial.CONCESSIONARIA && tipoNota != TipoNota.OCORRENCIA) {
            throw new InvarianteVioladaException("Material da Concessionária só pode ser lançado em ocorrência comercial.");
        }

        if (quantidade == null || quantidade.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvarianteVioladaException("A quantidade de material deve ser maior que zero.");
        }

        // Validação de Unicidade de Série na Execução
        if (numSerie != null && !numSerie.isBlank()) {
            boolean jaExisteSerie = materiais.stream()
                    .anyMatch(m -> numSerie.equalsIgnoreCase(m.getNumSerie()));
            if (jaExisteSerie) {
                throw new InvarianteVioladaException("O número de série '" + numSerie + "' já foi registrado nesta execução.");
            }
        }

        this.materiais.add(new ExecucaoMaterial(this, idMaterial, quantidade, operacao, numSerie, origem));
    }

    /** Materiais que movimentam o saldo da equipe (os da concessionária ficam de fora). */
    public List<ExecucaoMaterial> getMateriaisDaEquipe() {
        return materiais.stream().filter(m -> m.getOrigem() == OrigemMaterial.EQUIPE).toList();
    }

    public boolean isAguardandoNumeroOficial() {
        return tipoNota == TipoNota.AVULSA && dataNumeroOficial == null;
    }

    /**
     * Troca o número provisório da nota avulsa pelo número oficial. Pode ser repetido
     * para corrigir um número digitado errado; o provisório continua guardado.
     */
    public void informarNumeroOficial(String numeroOficial, UUID idUsuario, OffsetDateTime quando) {
        if (tipoNota != TipoNota.AVULSA) {
            throw new InvarianteVioladaException("Só a nota avulsa recebe número oficial depois do lançamento.");
        }
        if (numeroOficial == null || numeroOficial.isBlank()) {
            throw new InvarianteVioladaException("Informe o número oficial da nota.");
        }
        String numero = numeroOficial.trim();
        if (numero.equalsIgnoreCase(referenciaProvisoria)) {
            throw new InvarianteVioladaException("O número oficial precisa ser diferente do número provisório.");
        }
        this.referenciaNota = numero;
        this.referenciaNotaHash = HashUtils.gerarBlindIndex(numero);
        this.dataNumeroOficial = Objects.requireNonNull(quando, "Data é obrigatória");
        this.idUsuarioNumeroOficial = idUsuario;
    }


    public boolean isAlterada() {
        return totalAlteracoes > 0;
    }

    /**
     * Corrige os serviços e os materiais de uma nota já lançada para ficarem como na
     * lista recebida. Só mexe no que mudou: linhas iguais continuam as mesmas, e
     * material com a quantidade trocada muda só a quantidade. Devolve o que entrou e o
     * que saiu, que é o que o estoque precisa estornar ou baixar.
     */
    public AlteracaoLancamentos alterarLancamentos(List<UUID> acoesNovas, List<MaterialLancado> materiaisNovos) {
        AlteracaoLancamentos alteracao = aplicarLancamentos(acoesNovas, materiaisNovos);
        if (alteracao.isVazia()) {
            throw new InvarianteVioladaException("Nada mudou na nota: os serviços e os materiais são os mesmos.");
        }
        return alteracao;
    }

    /**
     * Como {@link #alterarLancamentos}, mas aceita a lista igual à atual: a alteração
     * pode mexer só nos dados da nota ({@link #corrigirDados}). Devolve vazia, então.
     */
    public AlteracaoLancamentos aplicarLancamentos(List<UUID> acoesNovas, List<MaterialLancado> materiaisNovos) {
        exigirConcluida();
        Set<UUID> novas = new LinkedHashSet<>(Objects.requireNonNullElse(acoesNovas, List.of()));
        Set<UUID> atuais = new LinkedHashSet<>(acoes.stream().map(ExecucaoAcao::getIdAcao).toList());
        List<UUID> acoesRemovidas = atuais.stream().filter(id -> !novas.contains(id)).toList();
        List<UUID> acoesAdicionadas = novas.stream().filter(id -> !atuais.contains(id)).toList();

        Map<MaterialLancado.Chave, MaterialLancado> antes = agrupar(materiais.stream().map(ExecucaoMaterial::comoLancado).toList());
        Map<MaterialLancado.Chave, MaterialLancado> depois = agrupar(Objects.requireNonNullElse(materiaisNovos, List.of()));
        List<MaterialLancado> materiaisRemovidos = new ArrayList<>();
        List<MaterialLancado> materiaisAdicionados = new ArrayList<>();
        antes.forEach((chave, anterior) -> {
            BigDecimal nova = depois.containsKey(chave) ? depois.get(chave).quantidade() : BigDecimal.ZERO;
            if (nova.compareTo(anterior.quantidade()) < 0) {
                materiaisRemovidos.add(anterior.comQuantidade(anterior.quantidade().subtract(nova)));
            }
        });
        depois.forEach((chave, novo) -> {
            BigDecimal anterior = antes.containsKey(chave) ? antes.get(chave).quantidade() : BigDecimal.ZERO;
            if (novo.quantidade().compareTo(anterior) > 0) {
                materiaisAdicionados.add(novo.comQuantidade(novo.quantidade().subtract(anterior)));
            }
        });

        AlteracaoLancamentos alteracao = new AlteracaoLancamentos(
                acoesAdicionadas, acoesRemovidas, materiaisAdicionados, materiaisRemovidos);
        if (alteracao.isVazia()) {
            return alteracao;
        }

        // Primeiro sai o que foi removido, para uma série trocada de linha não colidir consigo mesma.
        acoes.removeIf(a -> acoesRemovidas.contains(a.getIdAcao()));
        materiaisRemovidos.forEach(this::reduzirMaterial);
        acoesAdicionadas.forEach(this::adicionarAcao);
        for (MaterialLancado adicionado : materiaisAdicionados) {
            materiais.stream()
                    .filter(m -> m.comoLancado().chave().equals(adicionado.chave()))
                    .findFirst()
                    .ifPresentOrElse(
                            m -> m.atualizarQuantidade(m.getQuantidade().add(adicionado.quantidade())),
                            () -> adicionarMaterial(adicionado.idMaterial(), adicionado.quantidade(), adicionado.operacao(),
                                    adicionado.numSerie(), adicionado.origem()));
        }
        return alteracao;
    }

    /** A identificação da nota como está agora. */
    public DadosNota dados() {
        return new DadosNota(referenciaNota, tipoNota, idTipoServico, idMunicipio, dataLancamento);
    }

    /**
     * Corrige a identificação de uma nota já lançada: número, tipo, tipo de serviço,
     * município e data do lançamento. O que vier nulo fica como está.
     *
     * <p>Virar avulsa faz do número atual (ou do novo) o provisório, à espera do
     * oficial. Numa avulsa, trocar o número por outro que não o provisório é informar
     * o oficial.</p>
     *
     * @return se algo mudou
     */
    public boolean corrigirDados(DadosNota novos, UUID idUsuario, OffsetDateTime quando) {
        exigirConcluida();
        DadosNota antes = dados();
        boolean viraAvulsa = false;
        if (novos.tipoNota() != null && novos.tipoNota() != tipoNota) {
            viraAvulsa = novos.tipoNota() == TipoNota.AVULSA;
            this.tipoNota = novos.tipoNota();
            if (viraAvulsa) {
                this.referenciaProvisoria = referenciaNota;
                this.dataNumeroOficial = null;
                this.idUsuarioNumeroOficial = null;
            }
        }
        if (novos.referenciaNota() != null) {
            String numero = novos.referenciaNota().trim();
            if (numero.isEmpty()) {
                throw new InvarianteVioladaException(tipoNota == TipoNota.OCORRENCIA
                        ? "Informe o número da ocorrência."
                        : "Informe o número da nota.");
            }
            if (!numero.equals(referenciaNota)) {
                this.referenciaNota = numero;
                this.referenciaNotaHash = HashUtils.gerarBlindIndex(numero);
                if (viraAvulsa) {
                    this.referenciaProvisoria = numero;
                } else if (tipoNota == TipoNota.AVULSA && !numero.equalsIgnoreCase(referenciaProvisoria)) {
                    this.dataNumeroOficial = Objects.requireNonNull(quando, "Data é obrigatória");
                    this.idUsuarioNumeroOficial = idUsuario;
                }
            }
        }
        if (novos.idTipoServico() != null) {
            this.idTipoServico = novos.idTipoServico();
        }
        if (novos.idMunicipio() != null) {
            this.idMunicipio = novos.idMunicipio();
        }
        if (novos.dataLancamento() != null && !novos.dataLancamento().isEqual(dataLancamento)) {
            if (quando != null && novos.dataLancamento().isAfter(quando)) {
                throw new InvarianteVioladaException("A data do lançamento não pode ficar no futuro.");
            }
            this.dataLancamento = novos.dataLancamento();
        }
        return !antes.equals(dados());
    }

    /** Material da Concessionária só existe na ocorrência comercial, também depois de uma alteração. */
    public void validarMateriaisDoTipo() {
        if (tipoNota != TipoNota.OCORRENCIA
                && materiais.stream().anyMatch(m -> m.getOrigem() == OrigemMaterial.CONCESSIONARIA)) {
            throw new InvarianteVioladaException(
                    "Só a ocorrência comercial aceita material da Concessionária. Remova esses materiais para mudar o tipo da nota.");
        }
    }

    private void exigirConcluida() {
        if (statusExecucao != StatusExecucao.CONCLUIDO) {
            throw new InvarianteVioladaException("Só uma nota concluída pode ser alterada.");
        }
    }

    /** Fecha a alteração: novo valor faturado, quem alterou e quando. */
    public void registrarAlteracao(BigDecimal novoValor, UUID idUsuario, OffsetDateTime quando) {
        this.valorTotalFaturado = Objects.requireNonNull(novoValor, "Valor total é obrigatório");
        this.idUsuarioAlteracao = idUsuario;
        this.dataAlteracao = Objects.requireNonNull(quando, "Data é obrigatória");
        this.totalAlteracoes++;
    }

    private static Map<MaterialLancado.Chave, MaterialLancado> agrupar(List<MaterialLancado> itens) {
        Map<MaterialLancado.Chave, MaterialLancado> grupos = new LinkedHashMap<>();
        for (MaterialLancado item : itens) {
            if (item.quantidade().compareTo(BigDecimal.ZERO) <= 0) {
                throw new InvarianteVioladaException("A quantidade de material deve ser maior que zero.");
            }
            grupos.merge(item.chave(), item, (a, b) -> a.comQuantidade(a.quantidade().add(b.quantidade())));
        }
        return grupos;
    }

    /** Tira a quantidade das linhas desse material, da mais recente para a mais antiga. */
    private void reduzirMaterial(MaterialLancado removido) {
        BigDecimal restante = removido.quantidade();
        List<ExecucaoMaterial> linhas = materiais.stream()
                .filter(m -> m.comoLancado().chave().equals(removido.chave()))
                .toList()
                .reversed();
        for (ExecucaoMaterial linha : linhas) {
            if (restante.signum() <= 0) {
                break;
            }
            if (linha.getQuantidade().compareTo(restante) <= 0) {
                restante = restante.subtract(linha.getQuantidade());
                materiais.remove(linha);
            } else {
                linha.atualizarQuantidade(linha.getQuantidade().subtract(restante));
                restante = BigDecimal.ZERO;
            }
        }
    }

    /**
     * Conclui a execução comercial com o valor faturado calculado. As ações são opcionais.
     */
    public void concluir(BigDecimal valorTotal) {
        if (this.statusExecucao == StatusExecucao.CONCLUIDO) {
            throw new InvarianteVioladaException("Esta execução já foi concluída.");
        }
        this.valorTotalFaturado = Objects.requireNonNull(valorTotal, "Valor total é obrigatório");
        this.statusExecucao = StatusExecucao.CONCLUIDO;
    }
}
