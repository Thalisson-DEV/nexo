package com.nexo.app.modules.assistente.application.agent;

import com.nexo.app.modules.assistente.application.llm.LlmResponse;
import com.nexo.app.modules.assistente.application.navigation.NavigationActionCollector;
import com.nexo.app.shared.application.analytics.SemanticQueryContext;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Estado de uma execucao do agente, compartilhado pelo loop e pelas ferramentas. O contexto de seguranca
 * e capturado na thread da requisicao: as ferramentas nunca dependem do ThreadLocal do Spring Security.
 */
public final class AgentRunContext {

    private final AgentRun run;
    private final SemanticQueryContext security;
    private final String nomeUsuario;
    private final ContextoCes contexto;
    private final List<UUID> arquivosDaMensagem;
    private final Instant inicio = Instant.now();
    private final Duration duracaoMaxima;

    private final AtomicInteger proximoResultado;
    private final AtomicInteger proximoArtefato;
    private final AtomicInteger proximoArquivo;
    private final AtomicInteger proximaCitacao = new AtomicInteger(1);
    private final AtomicInteger chamadasFerramenta = new AtomicInteger();
    private final NavigationActionCollector navegacao = new NavigationActionCollector();
    private final Map<String, Integer> citacoesPorUrl = new ConcurrentHashMap<>();
    private final Map<String, String> cacheArquivosSandbox = new ConcurrentHashMap<>();
    private final List<String> resultadosDoTurno = Collections.synchronizedList(new ArrayList<>());
    private final List<String> artefatosDoTurno = Collections.synchronizedList(new ArrayList<>());
    private LlmResponse.LlmUsage uso = LlmResponse.LlmUsage.EMPTY;
    private volatile String modelo;
    /** Limites desta resposta; nulos usam os da configuração (o chat). */
    private Integer maxCiclos;
    private Integer maxChamadas;

    public AgentRunContext(AgentRun run, SemanticQueryContext security, String nomeUsuario, ContextoCes contexto,
            List<UUID> arquivosDaMensagem, Duration duracaoMaxima, int resultadosExistentes,
            int artefatosExistentes, int arquivosExistentes) {
        this.run = Objects.requireNonNull(run);
        this.security = Objects.requireNonNull(security);
        this.nomeUsuario = nomeUsuario;
        this.contexto = contexto;
        this.arquivosDaMensagem = arquivosDaMensagem == null ? List.of() : List.copyOf(arquivosDaMensagem);
        this.duracaoMaxima = duracaoMaxima;
        this.proximoResultado = new AtomicInteger(resultadosExistentes + 1);
        this.proximoArtefato = new AtomicInteger(artefatosExistentes + 1);
        this.proximoArquivo = new AtomicInteger(arquivosExistentes + 1);
    }

    public void emit(Packet packet) {
        run.emit(packet);
    }

    /** Orçamento maior que o do chat: análises disparadas por automações (relatórios). */
    public void definirOrcamento(Integer maxCiclos, Integer maxChamadas) {
        this.maxCiclos = maxCiclos;
        this.maxChamadas = maxChamadas;
    }

    public int maxCiclos(int padrao) {
        return maxCiclos == null ? padrao : maxCiclos;
    }

    public int maxChamadas(int padrao) {
        return maxChamadas == null ? padrao : maxChamadas;
    }

    public boolean cancelled() {
        return run.isCancelRequested();
    }

    public boolean tempoEsgotado(double fracao) {
        if (duracaoMaxima == null) return false;
        long limite = (long) (duracaoMaxima.toMillis() * fracao);
        return Duration.between(inicio, Instant.now()).toMillis() > limite;
    }

    public String novoCodigoResultado() {
        return "r" + proximoResultado.getAndIncrement();
    }

    public String novoCodigoArtefato() {
        return "a" + proximoArtefato.getAndIncrement();
    }

    public String novoCodigoArquivo() {
        return "f" + proximoArquivo.getAndIncrement();
    }

    /** Numero de citacao estavel por URL durante a execucao (fontes externas numeradas [1], [2]...). */
    public int citacaoPara(String url) {
        return citacoesPorUrl.computeIfAbsent(url, ignored -> proximaCitacao.getAndIncrement());
    }

    public int registrarChamadaFerramenta() {
        return chamadasFerramenta.incrementAndGet();
    }

    public int chamadasFerramenta() {
        return chamadasFerramenta.get();
    }

    public synchronized void somarUso(LlmResponse.LlmUsage adicional, String modeloUsado) {
        this.uso = this.uso.plus(adicional);
        if (modeloUsado != null && !modeloUsado.isBlank()) this.modelo = modeloUsado;
    }

    public synchronized LlmResponse.LlmUsage uso() {
        return uso;
    }

    public String modelo() {
        return modelo;
    }

    public void registrarResultadoDoTurno(String codigo) {
        resultadosDoTurno.add(codigo);
    }

    public void registrarArtefatoDoTurno(String codigo) {
        artefatosDoTurno.add(codigo);
    }

    public List<String> resultadosDoTurno() {
        synchronized (resultadosDoTurno) {
            return List.copyOf(resultadosDoTurno);
        }
    }

    public List<String> artefatosDoTurno() {
        synchronized (artefatosDoTurno) {
            return List.copyOf(artefatosDoTurno);
        }
    }

    public Map<String, String> cacheArquivosSandbox() {
        return cacheArquivosSandbox;
    }

    public NavigationActionCollector navegacao() {
        return navegacao;
    }

    public AgentRun run() {
        return run;
    }

    public SemanticQueryContext security() {
        return security;
    }

    public UUID usuarioId() {
        return security.usuarioId();
    }

    public UUID conversaId() {
        return run.conversaId();
    }

    public UUID mensagemAssistenteId() {
        return run.mensagemAssistenteId();
    }

    public UUID mensagemUsuarioId() {
        return run.mensagemUsuarioId();
    }

    public String nomeUsuario() {
        return nomeUsuario;
    }

    public ContextoCes contexto() {
        return contexto;
    }

    public List<UUID> arquivosDaMensagem() {
        return arquivosDaMensagem;
    }

    public Instant inicio() {
        return inicio;
    }

    public boolean possuiAutoridade(String autoridade) {
        return security.possuiAutoridade(autoridade);
    }
}
