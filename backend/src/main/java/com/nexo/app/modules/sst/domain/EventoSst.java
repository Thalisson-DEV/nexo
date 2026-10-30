package com.nexo.app.modules.sst.domain;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.infrastructure.persistence.CryptoConverterImpl;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Raiz do agregado da governança de acidentalidade: uma ocorrência de segurança, do
 * quase-acidente ao óbito.
 *
 * <p><b>Um agregado para tudo.</b> O tipo ({@link TipoEventoSst}) discrimina a natureza
 * da ocorrência em vez de existirem seis tabelas. Assim a base da pirâmide e o topo
 * dela são contados na mesma série, e os campos de lesão e de CAT ficam condicionais ao
 * tipo — validados aqui, não por {@code NOT NULL}.</p>
 *
 * <p><b>Uma regra de cópia, aplicada sem exceção.</b> Pessoas e unidades organizacionais
 * (atividade, gestor, técnicos, envolvidos, equipe, veículo, base) são <b>copiadas</b>
 * no registro; catálogos (classificação, local e natureza da lesão, agente causador) são
 * <b>referenciados</b>. O primeiro grupo rotaciona e o histórico não pode se mexer; o
 * segundo é vocabulário estável cujo conserto deve propagar.</p>
 *
 * <p><b>Nome copiado continua sendo dado pessoal.</b> Todo nome snapshotado aqui é
 * cifrado com {@link CryptoConverterImpl}, como no RH — copiar o nome é o que faz o
 * registro sobreviver à rotatividade, cifrá-lo é o que impede que isso vire um segundo
 * diretório de pessoas em texto claro.</p>
 *
 * <p><b>O atraso de comunicação não é gravado.</b> Ocorrência e comunicação são fatos;
 * o atraso é uma subtração, e o prazo legal mora em {@link TipoEventoSst}. Um sinalizador
 * gravado apodreceria no dia em que a regra mudasse.</p>
 */
@Entity
@Table(name = "eventos_sst")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventoSst extends BaseEntity {

    private static final int DESCRICAO_MAXIMA = 4000;
    private static final int TRATATIVA_MAXIMA = 4000;
    private static final int MOTIVO_MAXIMO = 500;

    /** Identificador legível {@code EVT-AAAA-NNNNNN}: acidente se cita em CAT e em e-mail. */
    @Column(name = "numero_registro", nullable = false, updatable = false, length = 20)
    private String numeroRegistro;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 30)
    private TipoEventoSst tipo;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusEventoSst status;

    @NotNull
    @Column(name = "data_ocorrencia", nullable = false)
    private OffsetDateTime dataOcorrencia;

    @NotNull
    @Column(name = "data_comunicacao", nullable = false)
    private OffsetDateTime dataComunicacao;

    // ------------------------------------------------------------------ //
    //  Snapshot: atividade e gestor
    // ------------------------------------------------------------------ //

    @Column(name = "id_atividade", nullable = false)
    private UUID idAtividade;

    @Column(name = "atividade_nome", nullable = false, length = 120)
    private String atividadeNome;

    @Column(name = "id_gestor", nullable = false)
    private UUID idGestor;

    /** 🔒 Cifrado. */
    @Convert(converter = CryptoConverterImpl.class)
    @Column(name = "gestor_nome", nullable = false)
    private String gestorNome;

    @Column(name = "gestor_funcao", length = 120)
    private String gestorFuncao;

    /** Marca o gestor informado no evento por divergir do gestor da atividade. */
    @Column(name = "gestor_substituto", nullable = false)
    private boolean gestorSubstituto;

    // ------------------------------------------------------------------ //
    //  Snapshot: onde
    // ------------------------------------------------------------------ //

    @Column(name = "id_base_operacional", nullable = false)
    private UUID idBaseOperacional;

    @Column(name = "base_nome", nullable = false, length = 120)
    private String baseNome;

    @Column(name = "id_equipe")
    private UUID idEquipe;

    @Column(name = "equipe_codigo", length = 60)
    private String equipeCodigo;

    @Column(name = "id_veiculo")
    private UUID idVeiculo;

    @Column(name = "veiculo_placa", length = 20)
    private String veiculoPlaca;

    // ------------------------------------------------------------------ //
    //  Referências vivas (catálogos e processo)
    // ------------------------------------------------------------------ //

    @Column(name = "id_classificacao", nullable = false)
    private UUID idClassificacao;

    @Column(name = "id_processo")
    private UUID idProcesso;

    @Column(name = "id_local_lesao")
    private UUID idLocalLesao;

    @Column(name = "id_natureza_lesao")
    private UUID idNaturezaLesao;

    @Column(name = "id_agente_causador")
    private UUID idAgenteCausador;

    // ------------------------------------------------------------------ //
    //  Circunstância
    // ------------------------------------------------------------------ //

    @Enumerated(EnumType.STRING)
    @Column(name = "telemetria", nullable = false, length = 20)
    private RegistroEvidencia telemetria;

    @Enumerated(EnumType.STRING)
    @Column(name = "filmagem", nullable = false, length = 20)
    private RegistroEvidencia filmagem;

    @Column(name = "local_ocorrencia", length = 255)
    private String localOcorrencia;

    @Column(name = "latitude", precision = 10, scale = 8)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 11, scale = 8)
    private BigDecimal longitude;

    @Column(name = "descricao", nullable = false, length = DESCRICAO_MAXIMA)
    private String descricao;

    @Column(name = "dias_perdidos")
    private Integer diasPerdidos;

    // ------------------------------------------------------------------ //
    //  CAT
    // ------------------------------------------------------------------ //

    @Column(name = "possui_cat", nullable = false)
    private boolean possuiCat;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_cat", length = 30)
    private TipoCat tipoCat;

    @Column(name = "numero_cat", length = 40)
    private String numeroCat;

    @Column(name = "data_emissao_cat")
    private LocalDate dataEmissaoCat;

    // ------------------------------------------------------------------ //
    //  Tratativa e desfecho
    // ------------------------------------------------------------------ //

    @Column(name = "tratativa", length = TRATATIVA_MAXIMA)
    private String tratativa;

    @Column(name = "data_conclusao")
    private OffsetDateTime dataConclusao;

    @Column(name = "data_cancelamento")
    private OffsetDateTime dataCancelamento;

    @Column(name = "motivo_cancelamento", length = MOTIVO_MAXIMO)
    private String motivoCancelamento;

    @Column(name = "motivo_reabertura", length = MOTIVO_MAXIMO)
    private String motivoReabertura;

    // ------------------------------------------------------------------ //
    //  Filhos
    // ------------------------------------------------------------------ //

    @JsonManagedReference
    @OneToMany(mappedBy = "evento", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TecnicoSegurancaEvento> tecnicos = new ArrayList<>();

    @JsonManagedReference
    @OneToMany(mappedBy = "evento", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EnvolvidoEvento> envolvidos = new ArrayList<>();

    @JsonManagedReference
    @OneToMany(mappedBy = "evento", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AnexoEventoSst> anexos = new ArrayList<>();

    /**
     * Registra a ocorrência inteira de uma vez.
     *
     * <p>Não existe construtor parcial de propósito: "pelo menos um técnico" e
     * "acidentado obrigatório quando o tipo implica lesão" são invariantes da
     * composição, e um agregado que nasce inválido para ser corrigido depois não é
     * um invariante — é uma intenção.</p>
     */
    public static EventoSst registrar(String numeroRegistro, DadosEventoSst dados,
                                      SnapshotAtividade atividade, SnapshotOperacional operacional,
                                      Collection<SnapshotTecnico> tecnicos,
                                      Collection<SnapshotEnvolvido> envolvidos) {
        EventoSst evento = new EventoSst();
        evento.numeroRegistro = exigirTexto(numeroRegistro, "O número de registro do evento é obrigatório.");
        evento.status = StatusEventoSst.REGISTRADO;
        evento.aplicar(dados, atividade, operacional, tecnicos, envolvidos);
        return evento;
    }

    /**
     * Corrige os dados do evento. Vale enquanto ele estiver aberto: um evento concluído
     * é imutável até ser reaberto com motivo, e um cancelado não volta por edição.
     */
    public void atualizar(DadosEventoSst dados, SnapshotAtividade atividade, SnapshotOperacional operacional,
                          Collection<SnapshotTecnico> tecnicos, Collection<SnapshotEnvolvido> envolvidos) {
        exigirEditavel("alterar");
        aplicar(dados, atividade, operacional, tecnicos, envolvidos);
    }

    // ------------------------------------------------------------------ //
    //  Ciclo de vida
    // ------------------------------------------------------------------ //

    /** Assume a análise do evento, opcionalmente já registrando o que está sendo feito. */
    public void iniciarTratativa(String tratativa) {
        exigirEditavel("tratar");
        if (tratativa != null && !tratativa.isBlank()) {
            this.tratativa = limitar(tratativa.trim(), TRATATIVA_MAXIMA, "A tratativa");
        }
        this.status = StatusEventoSst.EM_TRATATIVA;
    }

    /**
     * Encerra a tratativa. Exige o relato do que foi feito: um evento concluído sem
     * tratativa é um evento arquivado, e arquivar não é tratar.
     */
    public void concluir(String tratativa) {
        exigirEditavel("concluir");
        if (tratativa != null && !tratativa.isBlank()) {
            this.tratativa = limitar(tratativa.trim(), TRATATIVA_MAXIMA, "A tratativa");
        }
        if (this.tratativa == null || this.tratativa.isBlank()) {
            throw new InvarianteVioladaException(
                    "Descreva a tratativa antes de concluir o evento.");
        }
        this.status = StatusEventoSst.CONCLUIDO;
        this.dataConclusao = OffsetDateTime.now();
    }

    /**
     * Cancela um registro indevido ou duplicado. O evento continua legível e sai de
     * todos os indicadores — apagá-lo seria perder a trilha de que ele existiu.
     */
    public void cancelar(String motivo) {
        if (this.status == StatusEventoSst.CANCELADO) {
            throw new InvarianteVioladaException("Este evento já está cancelado.");
        }
        if (this.status == StatusEventoSst.CONCLUIDO) {
            throw new InvarianteVioladaException(
                    "Um evento concluído não pode ser cancelado. Reabra-o antes, informando o motivo.");
        }
        this.motivoCancelamento = exigirMotivo(motivo, "cancelamento");
        this.status = StatusEventoSst.CANCELADO;
        this.dataCancelamento = OffsetDateTime.now();
    }

    /** Reabre um evento concluído ou cancelado, devolvendo-o à tratativa. */
    public void reabrir(String motivo) {
        if (this.status.permiteEdicao()) {
            throw new InvarianteVioladaException("Só é possível reabrir um evento concluído ou cancelado.");
        }
        this.motivoReabertura = exigirMotivo(motivo, "reabertura");
        this.status = StatusEventoSst.EM_TRATATIVA;
        this.dataConclusao = null;
        this.dataCancelamento = null;
    }

    // ------------------------------------------------------------------ //
    //  Anexos
    // ------------------------------------------------------------------ //

    /** Vincula uma evidência já enviada ao armazenamento de objetos. */
    public AnexoEventoSst adicionarAnexo(ContextoAnexoEvento contexto, String nomeArquivo, String contentType,
                                         long tamanhoBytes, String objectKey) {
        exigirEditavel("anexar evidências a");
        AnexoEventoSst anexo = new AnexoEventoSst(this, contexto, nomeArquivo, contentType, tamanhoBytes, objectKey);
        this.anexos.add(anexo);
        return anexo;
    }

    /** Desvincula uma evidência. Apagar o objeto no armazenamento é do caso de uso. */
    public void removerAnexo(AnexoEventoSst anexo) {
        exigirEditavel("remover evidências de");
        this.anexos.remove(anexo);
    }

    // ------------------------------------------------------------------ //
    //  Derivados — calculados na leitura, nunca gravados
    // ------------------------------------------------------------------ //

    /** Horas entre a ocorrência e a comunicação ao SESMT. */
    public long getHorasAteComunicacao() {
        return Duration.between(dataOcorrencia, dataComunicacao).toHours();
    }

    /** Comunicação fora do prazo legal do tipo do evento. */
    public boolean isComunicacaoAtrasada() {
        return getHorasAteComunicacao() > tipo.getPrazoComunicacaoHoras();
    }

    /** Houve alguém lesionado neste evento. */
    public boolean isComLesao() {
        return tipo.implicaLesao();
    }

    /** Quantos envolvidos entraram como acidentados. */
    public int getQuantidadeAcidentados() {
        return (int) envolvidos.stream().filter(EnvolvidoEvento::isAcidentado).count();
    }

    // ------------------------------------------------------------------ //
    //  Aplicação e invariantes
    // ------------------------------------------------------------------ //

    private void aplicar(DadosEventoSst dados, SnapshotAtividade atividade, SnapshotOperacional operacional,
                         Collection<SnapshotTecnico> tecnicos, Collection<SnapshotEnvolvido> envolvidos) {
        Objects.requireNonNull(dados, "Os dados do evento são obrigatórios");

        if (dados.tipo() == null) {
            throw new InvarianteVioladaException("O tipo do evento é obrigatório.");
        }
        this.tipo = dados.tipo();

        aplicarDatas(dados);
        aplicarAtividade(atividade);
        aplicarOperacional(operacional);
        aplicarCircunstancia(dados);
        aplicarLesao(dados);
        aplicarCat(dados);
        aplicarTecnicos(tecnicos);
        aplicarEnvolvidos(envolvidos);
    }

    private void aplicarDatas(DadosEventoSst dados) {
        if (dados.dataOcorrencia() == null) {
            throw new InvarianteVioladaException("A data e hora da ocorrência são obrigatórias.");
        }
        if (dados.dataComunicacao() == null) {
            throw new InvarianteVioladaException("A data e hora da comunicação são obrigatórias.");
        }

        OffsetDateTime agora = OffsetDateTime.now();
        if (dados.dataOcorrencia().isAfter(agora)) {
            throw new InvarianteVioladaException("A data da ocorrência não pode estar no futuro.");
        }
        if (dados.dataComunicacao().isAfter(agora)) {
            throw new InvarianteVioladaException("A data da comunicação não pode estar no futuro.");
        }
        if (dados.dataComunicacao().isBefore(dados.dataOcorrencia())) {
            throw new InvarianteVioladaException(
                    "A comunicação não pode ser anterior à ocorrência.");
        }

        this.dataOcorrencia = dados.dataOcorrencia();
        this.dataComunicacao = dados.dataComunicacao();
    }

    private void aplicarAtividade(SnapshotAtividade atividade) {
        Objects.requireNonNull(atividade, "A atividade do evento é obrigatória");
        if (atividade.idAtividade() == null || atividade.nome() == null || atividade.nome().isBlank()) {
            throw new InvarianteVioladaException("A atividade em execução é obrigatória.");
        }
        // O gestor é opcional no cadastro da atividade e obrigatório aqui: no momento do
        // evento já se sabe quem responde por ele, e é este o momento que o histórico guarda.
        if (atividade.idGestor() == null || atividade.gestorNome() == null || atividade.gestorNome().isBlank()) {
            throw new InvarianteVioladaException(
                    "O gestor responsável pela atividade é obrigatório no registro do evento. "
                            + "Informe-o no evento ou defina-o no cadastro da atividade.");
        }

        this.idAtividade = atividade.idAtividade();
        this.atividadeNome = atividade.nome().trim();
        this.idGestor = atividade.idGestor();
        this.gestorNome = atividade.gestorNome().trim();
        this.gestorFuncao = textoOuNulo(atividade.gestorFuncao());
        this.gestorSubstituto = atividade.gestorSubstituto();
    }

    private void aplicarOperacional(SnapshotOperacional operacional) {
        Objects.requireNonNull(operacional, "O contexto operacional do evento é obrigatório");
        if (operacional.idBaseOperacional() == null
                || operacional.baseNome() == null || operacional.baseNome().isBlank()) {
            throw new InvarianteVioladaException("A base operacional do evento é obrigatória.");
        }

        this.idBaseOperacional = operacional.idBaseOperacional();
        this.baseNome = operacional.baseNome().trim();
        this.idEquipe = operacional.idEquipe();
        this.equipeCodigo = textoOuNulo(operacional.equipeCodigo());
        this.idVeiculo = operacional.idVeiculo();
        this.veiculoPlaca = textoOuNulo(operacional.veiculoPlaca());
    }

    private void aplicarCircunstancia(DadosEventoSst dados) {
        if (dados.idClassificacao() == null) {
            throw new InvarianteVioladaException(
                    "A classificação do evento é obrigatória: é ela que liga o registro à tabela do contrato.");
        }
        String descricao = dados.descricao() == null ? null : dados.descricao().trim();
        if (descricao == null || descricao.isBlank()) {
            throw new InvarianteVioladaException("A descrição do que aconteceu é obrigatória.");
        }

        this.idClassificacao = dados.idClassificacao();
        this.idProcesso = dados.idProcesso();
        this.descricao = limitar(descricao, DESCRICAO_MAXIMA, "A descrição");
        this.localOcorrencia = textoOuNulo(dados.localOcorrencia());
        this.latitude = dados.latitude();
        this.longitude = dados.longitude();
        // Sem declaração, "não aplicável": é a leitura honesta de um campo em branco.
        this.telemetria = dados.telemetria() == null ? RegistroEvidencia.NAO_APLICAVEL : dados.telemetria();
        this.filmagem = dados.filmagem() == null ? RegistroEvidencia.NAO_APLICAVEL : dados.filmagem();
    }

    /**
     * Dados de lesão só existem em tipo que implica lesão. Recusar o contrário é o que
     * impede um dano material de entrar na estatística de acidentados por engano de
     * preenchimento — e a mensagem nomeia o campo incompatível, porque quem preencheu
     * precisa saber qual apagar.
     */
    private void aplicarLesao(DadosEventoSst dados) {
        if (dados.diasPerdidos() != null && dados.diasPerdidos() < 0) {
            throw new InvarianteVioladaException("Os dias perdidos não podem ser negativos.");
        }

        if (tipo.implicaLesao()) {
            if (dados.idLocalLesao() == null) {
                throw new InvarianteVioladaException(
                        "O local da lesão é obrigatório para eventos do tipo " + tipo.getRotulo() + ".");
            }
            this.idLocalLesao = dados.idLocalLesao();
            this.idNaturezaLesao = dados.idNaturezaLesao();
            this.diasPerdidos = dados.diasPerdidos();
        } else {
            recusarCampoDeLesao(dados.idLocalLesao() != null, "local da lesão");
            recusarCampoDeLesao(dados.idNaturezaLesao() != null, "natureza da lesão");
            recusarCampoDeLesao(dados.diasPerdidos() != null, "dias perdidos");
            this.idLocalLesao = null;
            this.idNaturezaLesao = null;
            this.diasPerdidos = null;
        }

        // O agente causador vale para os dois lados: é o que causou a lesão ou o dano.
        this.idAgenteCausador = dados.idAgenteCausador();
    }

    private void aplicarCat(DadosEventoSst dados) {
        if (dados.possuiCat()) {
            if (dados.tipoCat() == null) {
                throw new InvarianteVioladaException(
                        "Informe o tipo da CAT (inicial, reabertura ou comunicação de óbito).");
            }
            this.possuiCat = true;
            this.tipoCat = dados.tipoCat();
            this.numeroCat = textoOuNulo(dados.numeroCat());
            this.dataEmissaoCat = dados.dataEmissaoCat();
            return;
        }

        recusarCampoDeCat(dados.tipoCat() != null, "tipo da CAT");
        recusarCampoDeCat(dados.numeroCat() != null && !dados.numeroCat().isBlank(), "número da CAT");
        recusarCampoDeCat(dados.dataEmissaoCat() != null, "data de emissão da CAT");
        this.possuiCat = false;
        this.tipoCat = null;
        this.numeroCat = null;
        this.dataEmissaoCat = null;
    }

    private void aplicarTecnicos(Collection<SnapshotTecnico> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            throw new InvarianteVioladaException(
                    "Informe ao menos um técnico de segurança responsável pelo evento.");
        }

        List<TecnicoSegurancaEvento> novos = new ArrayList<>();
        Set<UUID> vistos = new HashSet<>();
        for (SnapshotTecnico snapshot : snapshots) {
            TecnicoSegurancaEvento tecnico = new TecnicoSegurancaEvento(this, snapshot);
            if (!vistos.add(tecnico.getIdColaborador())) {
                throw new InvarianteVioladaException(
                        "O mesmo técnico de segurança foi informado mais de uma vez.");
            }
            novos.add(tecnico);
        }

        this.tecnicos.clear();
        this.tecnicos.addAll(novos);
    }

    private void aplicarEnvolvidos(Collection<SnapshotEnvolvido> snapshots) {
        List<EnvolvidoEvento> novos = new ArrayList<>();
        Set<UUID> colaboradores = new HashSet<>();

        if (snapshots != null) {
            for (SnapshotEnvolvido snapshot : snapshots) {
                EnvolvidoEvento envolvido = new EnvolvidoEvento(this, snapshot);
                if (envolvido.getIdColaborador() != null && !colaboradores.add(envolvido.getIdColaborador())) {
                    throw new InvarianteVioladaException(
                            "O mesmo colaborador foi informado mais de uma vez entre os envolvidos.");
                }
                novos.add(envolvido);
            }
        }

        if (tipo.implicaLesao() && novos.stream().noneMatch(EnvolvidoEvento::isAcidentado)) {
            throw new InvarianteVioladaException(
                    "Eventos do tipo " + tipo.getRotulo() + " exigem ao menos um envolvido no papel de acidentado.");
        }

        this.envolvidos.clear();
        this.envolvidos.addAll(novos);
    }

    private void exigirEditavel(String acao) {
        if (!status.permiteEdicao()) {
            throw new InvarianteVioladaException(
                    "Não é possível " + acao + " um evento " + status.getRotulo().toLowerCase()
                            + ". Reabra-o informando o motivo.");
        }
    }

    private void recusarCampoDeLesao(boolean informado, String campo) {
        if (informado) {
            throw new InvarianteVioladaException(
                    "Eventos do tipo " + tipo.getRotulo() + " não admitem " + campo + ".");
        }
    }

    private void recusarCampoDeCat(boolean informado, String campo) {
        if (informado) {
            throw new InvarianteVioladaException(
                    "O evento não declara CAT, portanto não pode informar " + campo + ".");
        }
    }

    private static String exigirMotivo(String motivo, String operacao) {
        if (motivo == null || motivo.isBlank()) {
            throw new InvarianteVioladaException("Informe o motivo do " + operacao + " do evento.");
        }
        return limitar(motivo.trim(), MOTIVO_MAXIMO, "O motivo");
    }

    private static String exigirTexto(String valor, String mensagem) {
        if (valor == null || valor.isBlank()) {
            throw new InvarianteVioladaException(mensagem);
        }
        return valor.trim();
    }

    private static String limitar(String valor, int maximo, String rotulo) {
        if (valor.length() > maximo) {
            throw new InvarianteVioladaException(rotulo + " deve ter no máximo " + maximo + " caracteres.");
        }
        return valor;
    }

    private static String textoOuNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
