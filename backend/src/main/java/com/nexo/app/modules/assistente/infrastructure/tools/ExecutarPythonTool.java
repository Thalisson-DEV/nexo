package com.nexo.app.modules.assistente.infrastructure.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexo.app.modules.assistente.application.AssistenteProperties;
import com.nexo.app.modules.assistente.application.agent.*;
import com.nexo.app.modules.assistente.application.files.ArquivoExtraido;
import com.nexo.app.modules.assistente.application.files.ArquivoService;
import com.nexo.app.modules.assistente.application.results.ResultFormatter;
import com.nexo.app.modules.assistente.application.results.ResultStore;
import com.nexo.app.modules.assistente.application.results.TabularExport;
import com.nexo.app.modules.assistente.domain.ArquivoAssistente;
import com.nexo.app.modules.assistente.domain.ResultadoAssistente;
import com.nexo.app.modules.assistente.infrastructure.sandbox.CodeInterpreterClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Execucao de Python em sandbox isolado, portada do {@code PythonTool} do Onyx: mesma selecao de arquivos
 * (os citados no codigo primeiro, depois os mais recentes, com limites de quantidade e bytes), cache de
 * upload por (nome, hash) durante a execucao, saida em streaming, arquivos gerados devolvidos ao usuario e
 * resultado estruturado para o modelo. Extensao do Nexo: resultados rN viram {@code rN.csv} no diretorio de
 * trabalho e planilhas geradas voltam como novos resultados, reutilizaveis em graficos e relatorios.
 */
@Component
public class ExecutarPythonTool extends BaseTool {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExecutarPythonTool.class);
    private static final Pattern REFERENCIA_RESULTADO = Pattern.compile("\\b(r\\d{1,4})\\.csv\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern NOME_INSEGURO = Pattern.compile("[\\x00-\\x1f/\\\\:*?\"<>|]+");
    private static final int NOME_MAXIMO = 200;
    private static final int MAX_ARQUIVOS_GERADOS = 20;
    private static final long MAX_BYTES_ARQUIVO_GERADO = 25L * 1024 * 1024;
    private static final int MAX_RESULTADOS_DE_PLANILHAS = 4;
    private static final int MAX_SAIDA_TRANSMITIDA = 30_000;
    private static final Set<String> EXTENSOES_PLANILHA = Set.of("csv", "tsv", "xlsx", "xlsm", "xls");

    private final CodeInterpreterClient client;
    private final ArquivoService arquivos;
    private final ResultStore store;
    private final ResultFormatter formatter;
    private final AssistenteProperties properties;

    public ExecutarPythonTool(CodeInterpreterClient client, ArquivoService arquivos, ResultStore store,
            ResultFormatter formatter, AssistenteProperties properties) {
        super("executar_python", "Executar Python",
                "Executa codigo Python em um sandbox isolado, sem internet e sem acesso ao banco do Nexo. Use para "
                        + "estatistica, projecoes, regressoes, simulacoes, conciliacoes complexas, transformacao de "
                        + "planilhas e graficos personalizados. Bibliotecas: pandas, numpy, scipy, matplotlib, openpyxl, "
                        + "PIL. Arquivos anexados na conversa ficam no diretorio atual com o nome original. Resultados "
                        + "rN ficam disponiveis como 'rN.csv' (UTF-8, separador virgula, ponto decimal, datas ISO, "
                        + "cabecalho com os nomes das colunas) quando citados no codigo ou listados em 'resultados'. "
                        + "Tudo que o codigo gravar no diretorio atual (PNG, XLSX, CSV, PDF...) e entregue ao usuario; "
                        + "CSV/XLSX gerados tambem viram novos resultados rN para criar_artefato e relatorios. Cada "
                        + "chamada roda em ambiente novo e sem estado: faca todo o trabalho em um unico script. Use "
                        + "print() para mostrar os numeros que for citar. Textos de graficos em portugues. Limite de "
                        + "60 segundos por execucao. Para consultas e agregacoes simples, prefira as ferramentas de dados.",
                JsonSchemas.object()
                        .required("code", JsonSchemas.string("Codigo Python completo a executar"))
                        .prop("resultados", JsonSchemas.arrayOfStrings(
                                "Resultados a disponibilizar como arquivos CSV (ex.: [\"r1\", \"r3\"] vira r1.csv e r3.csv)"))
                        .prop("objetivo", JsonSchemas.string("Frase curta, em portugues, do que o codigo faz (exibida ao usuario)"))
                        .build(),
                ToolKind.PYTHON, Set.of(), Duration.ofMillis(properties.getCodeInterpreter().getTimeoutMs() + 90_000L));
        this.client = client;
        this.arquivos = arquivos;
        this.store = store;
        this.formatter = formatter;
        this.properties = properties;
    }

    @Override
    public boolean isAvailable(AgentRunContext context) {
        return client.configured() && client.health().healthy();
    }

    @Override
    public String progressLabel(JsonNode arguments) {
        String objetivo = ToolArgs.text(arguments, "objetivo");
        return objetivo == null ? "Executando Python" : limitar(objetivo, 120);
    }

    @Override
    public JsonNode displayArguments(JsonNode arguments) {
        ObjectNode display = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        String objetivo = ToolArgs.text(arguments, "objetivo");
        if (objetivo != null) display.put("objetivo", limitar(objetivo, 200));
        display.put("code", Objects.toString(ToolArgs.text(arguments, "code"), ""));
        List<String> resultados = ToolArgs.texts(arguments, "resultados");
        if (!resultados.isEmpty()) {
            var array = display.putArray("resultados");
            resultados.forEach(array::add);
        }
        return display;
    }

    @Override
    public ToolResult execute(JsonNode arguments, ToolContext context) {
        String code = ToolArgs.text(arguments, "code");
        if (code == null) {
            throw new ToolException("executar_python exige o parametro 'code' com o codigo Python, por exemplo: "
                    + "{\"code\": \"print('ola')\"}");
        }
        AssistenteProperties.CodeInterpreter config = properties.getCodeInterpreter();
        Staging staging = prepararArquivos(code, ToolArgs.texts(arguments, "resultados"), context, config);

        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        int[] transmitido = {0};
        CodeInterpreterClient.ExecuteResult execucao;
        try {
            List<CodeInterpreterClient.StagedFile> staged = enviarArquivos(staging, context);
            execucao = client.executeStreaming(code, config.getTimeoutMs(), staged, (stream, data) -> {
                ("stderr".equals(stream) ? stderr : stdout).append(data);
                if (transmitido[0] < MAX_SAIDA_TRANSMITIDA && !data.isEmpty()) {
                    String parte = data.length() > MAX_SAIDA_TRANSMITIDA - transmitido[0]
                            ? data.substring(0, MAX_SAIDA_TRANSMITIDA - transmitido[0]) : data;
                    transmitido[0] += parte.length();
                    context.emit(Packet.Types.TOOL_OUTPUT, "stream", "stderr".equals(stream) ? "stderr" : "stdout",
                            "data", parte);
                }
            }, context::cancelled);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ToolException("Execucao Python interrompida.");
        } catch (IOException exception) {
            client.invalidarSaude();
            LOGGER.warn("Falha no sandbox Python: {}", exception.getMessage());
            String mensagem = context.cancelled() ? "Execução cancelada pelo usuário."
                    : "O sandbox Python falhou: " + limitar(Objects.toString(exception.getMessage(), "erro de comunicação"), 400);
            context.emit(Packet.Types.TOOL_OUTPUT, "stream", "stderr", "data", mensagem);
            Map<String, Object> erro = new LinkedHashMap<>();
            erro.put("stdout", "");
            erro.put("stderr", mensagem);
            erro.put("exit_code", -1);
            erro.put("timed_out", false);
            erro.put("generated_files", List.of());
            erro.put("error", mensagem);
            if (staging.aviso() != null) erro.put("staging_notice", staging.aviso());
            return ToolResult.of(formatter.json(erro), "Falha no sandbox Python").asFailure();
        }

        List<Map<String, Object>> gerados = new ArrayList<>();
        List<Map<String, Object>> paraModelo = new ArrayList<>();
        List<String> falhas = new ArrayList<>();
        int resultadosCriados = 0;
        int processados = 0;
        for (CodeInterpreterClient.WorkspaceFile arquivo : execucao.files()) {
            if (!"file".equals(arquivo.kind()) || arquivo.fileId() == null) continue;
            String nome = nomeFinal(arquivo.path());
            if (processados >= MAX_ARQUIVOS_GERADOS) {
                falhas.add(nome + " (limite de " + MAX_ARQUIVOS_GERADOS + " arquivos por execucao)");
                client.deleteFile(arquivo.fileId());
                continue;
            }
            processados++;
            try {
                byte[] conteudo = client.downloadFile(arquivo.fileId());
                if (conteudo.length == 0) continue;
                if (conteudo.length > MAX_BYTES_ARQUIVO_GERADO) {
                    falhas.add(nome + " (maior que 25 MB)");
                    continue;
                }
                ArquivoAssistente salvo = arquivos.salvarGerado(context.run(), nome, tipoConteudo(nome), conteudo);
                Map<String, Object> descricao = ArtefatoTools.descreverArquivo(salvo);
                Map<String, Object> resumoModelo = new LinkedHashMap<>();
                resumoModelo.put("arquivo", salvo.getCodigo());
                resumoModelo.put("nome", salvo.getNome());
                resumoModelo.put("tipo", salvo.getCategoria().name());
                resumoModelo.put("bytes", salvo.getTamanhoBytes());
                if (EXTENSOES_PLANILHA.contains(Objects.toString(salvo.getExtensao(), ""))
                        && resultadosCriados < MAX_RESULTADOS_DE_PLANILHAS) {
                    List<String> codigos = new ArrayList<>();
                    List<ArquivoExtraido.Planilha> planilhas = arquivos.planilhas(salvo);
                    for (ArquivoExtraido.Planilha planilha : planilhas) {
                        if (resultadosCriados >= MAX_RESULTADOS_DE_PLANILHAS) break;
                        if (planilha.dados().columns().isEmpty()) continue;
                        ResultStore.StoredResult resultado = store.registrar(context.run(), name(),
                                ResultadoAssistente.Natureza.CALCULO, "Python: " + salvo.getNome()
                                        + (planilhas.size() > 1 ? " / " + planilha.nome() : ""),
                                planilha.dados(), Map.of("arquivo", salvo.getCodigo(), "aba", planilha.nome()));
                        codigos.add(resultado.codigo());
                        resultadosCriados++;
                    }
                    if (!codigos.isEmpty()) {
                        resumoModelo.put("resultados", codigos);
                        descricao.put("resultados", codigos);
                    }
                }
                gerados.add(descricao);
                paraModelo.add(resumoModelo);
                context.emit(Packet.Types.FILE, "arquivo", descricao);
            } catch (IOException exception) {
                falhas.add(nome);
                LOGGER.warn("Falha ao baixar arquivo gerado no sandbox: {}", exception.getMessage());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new ToolException("Execucao Python interrompida.");
            } finally {
                client.deleteFile(arquivo.fileId());
            }
        }

        int maximoSaida = Math.max(2_000, config.getMaxOutputChars());
        String stdoutModelo = truncar(stdout.toString(), maximoSaida);
        String stderrModelo = truncar(stderr.toString(), maximoSaida);
        boolean sucesso = !execucao.timedOut() && execucao.exitCode() != null && execucao.exitCode() == 0;

        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("stdout", stdoutModelo);
        resposta.put("stderr", stderrModelo);
        resposta.put("exit_code", execucao.exitCode());
        resposta.put("timed_out", execucao.timedOut());
        resposta.put("duration_ms", execucao.durationMs());
        resposta.put("generated_files", paraModelo);
        resposta.put("error", sucesso ? null : (execucao.timedOut()
                ? "Tempo limite de " + (config.getTimeoutMs() / 1000) + " s excedido. Simplifique ou divida o processamento."
                : stderrModelo));
        resposta.put("arquivos_disponiveis", staging.nomes());
        if (staging.aviso() != null) resposta.put("staging_notice", staging.aviso());
        if (!falhas.isEmpty()) resposta.put("arquivos_nao_salvos", falhas);
        if (!paraModelo.isEmpty()) {
            resposta.put("observacao", "Os arquivos gerados ja aparecem para o usuario na conversa. Cite-os pelo nome; "
                    + "nao escreva links. Use os resultados rN de planilhas geradas em criar_artefato ou relatorios.");
        }

        String resumo;
        if (execucao.timedOut()) resumo = "Tempo limite excedido";
        else if (!sucesso) resumo = "Erro na execução (código " + execucao.exitCode() + ")";
        else resumo = "Executado em " + formatarDuracao(execucao.durationMs())
                + (gerados.isEmpty() ? "" : " · " + gerados.size() + (gerados.size() == 1 ? " arquivo gerado" : " arquivos gerados"));
        ToolResult result = ToolResult.of(formatter.json(resposta), resumo).withFiles(gerados);
        return sucesso ? result : result.asFailure();
    }

    // ------------------------------------------------------------------------------------ staging

    private Staging prepararArquivos(String code, List<String> resultadosPedidos, ToolContext context,
            AssistenteProperties.CodeInterpreter config) {
        int maxArquivos = Math.max(1, config.getMaxStagedFiles());
        long maxBytes = Math.max(1, config.getMaxStagedBytes());
        List<Candidato> candidatos = new ArrayList<>();
        List<String> avisos = new ArrayList<>();

        // Resultados: pedidos explicitamente ou citados como rN.csv no codigo.
        LinkedHashSet<String> codigos = new LinkedHashSet<>();
        resultadosPedidos.forEach(codigo -> codigos.add(codigo.trim().toLowerCase(Locale.ROOT)));
        Matcher matcher = REFERENCIA_RESULTADO.matcher(code);
        while (matcher.find()) codigos.add(matcher.group(1).toLowerCase(Locale.ROOT));
        for (String codigo : codigos) {
            Optional<ResultStore.StoredResult> resultado = store.carregar(context.conversaId(), codigo);
            if (resultado.isEmpty()) {
                avisos.add("O resultado " + codigo + " nao existe nesta conversa.");
                continue;
            }
            ResultStore.StoredResult stored = resultado.get();
            byte[] csv = TabularExport.csvTecnico(stored.data());
            if (stored.data().rows().size() < stored.data().totalRows()) {
                avisos.add(codigo + ".csv contem " + stored.data().rows().size() + " de " + stored.data().totalRows()
                        + " linhas (limite de armazenamento do resultado).");
            }
            candidatos.add(new Candidato(codigo + ".csv", () -> csv, true));
        }

        // Arquivos da conversa: citados no codigo primeiro, depois os mais recentes.
        List<ArquivoAssistente> daConversa = new ArrayList<>(arquivos.listarDaConversa(context.conversaId()));
        Collections.reverse(daConversa);
        List<Candidato> citados = new ArrayList<>();
        List<Candidato> demais = new ArrayList<>();
        for (ArquivoAssistente arquivo : daConversa) {
            String nome = nomeSeguro(arquivo.getNome());
            Candidato candidato = new Candidato(nome, () -> arquivos.conteudo(arquivo),
                    code.contains(arquivo.getNome()) || code.contains(nome));
            (candidato.citado() ? citados : demais).add(candidato);
        }
        candidatos.addAll(citados);
        candidatos.addAll(demais);

        List<ArquivoPreparado> selecionados = new ArrayList<>();
        Set<String> nomes = new HashSet<>();
        long bytes = 0;
        int descartados = 0;
        List<String> falhasLeitura = new ArrayList<>();
        for (Candidato candidato : candidatos) {
            if (selecionados.size() >= maxArquivos) {
                descartados++;
                continue;
            }
            byte[] conteudo;
            try {
                conteudo = candidato.conteudo().get();
            } catch (RuntimeException exception) {
                falhasLeitura.add(candidato.nome());
                continue;
            }
            if (!selecionados.isEmpty() && bytes + conteudo.length > maxBytes) {
                descartados++;
                continue;
            }
            String nome = deduplicar(candidato.nome(), nomes, String.valueOf(selecionados.size()));
            selecionados.add(new ArquivoPreparado(nome, conteudo));
            bytes += conteudo.length;
        }
        if (descartados > 0) {
            avisos.add(descartados + " arquivo(s) da conversa nao foram disponibilizados pelos limites por execucao ("
                    + maxArquivos + " arquivos / " + (maxBytes / (1024 * 1024)) + " MB); os citados no codigo tiveram prioridade.");
        }
        if (!falhasLeitura.isEmpty()) avisos.add("Falha ao ler: " + String.join(", ", falhasLeitura) + ".");
        return new Staging(selecionados, avisos);
    }

    private List<CodeInterpreterClient.StagedFile> enviarArquivos(Staging staging, ToolContext context)
            throws IOException, InterruptedException {
        List<CodeInterpreterClient.StagedFile> staged = new ArrayList<>();
        Map<String, String> cache = context.run().cacheArquivosSandbox();
        for (ArquivoPreparado arquivo : staging.arquivos()) {
            String chave = arquivo.nome() + ":" + sha256(arquivo.conteudo());
            String fileId = cache.get(chave);
            if (fileId == null) {
                try {
                    fileId = client.uploadFile(arquivo.conteudo(), arquivo.nome());
                    cache.put(chave, fileId);
                } catch (IOException exception) {
                    staging.avisos().add("Falha ao enviar " + arquivo.nome() + " ao sandbox.");
                    continue;
                }
            }
            staged.add(new CodeInterpreterClient.StagedFile(arquivo.nome(), fileId));
        }
        return staged;
    }

    // ------------------------------------------------------------------------------------ utilitarios

    static String nomeSeguro(String nome) {
        String limpo = NOME_INSEGURO.matcher(nome == null ? "" : nome).replaceAll("_").strip();
        while (limpo.startsWith(".")) limpo = limpo.substring(1);
        while (limpo.endsWith(".")) limpo = limpo.substring(0, limpo.length() - 1);
        if (limpo.isBlank()) return "arquivo";
        int ponto = limpo.lastIndexOf('.');
        String base = ponto > 0 ? limpo.substring(0, ponto) : limpo;
        String extensao = ponto > 0 ? limpo.substring(ponto) : "";
        int maximo = Math.max(1, NOME_MAXIMO - extensao.length());
        return (base.length() > maximo ? base.substring(0, maximo) : base) + extensao;
    }

    private static String deduplicar(String nome, Set<String> usados, String sufixo) {
        String seguro = nomeSeguro(nome);
        if (usados.add(seguro)) return seguro;
        int ponto = seguro.lastIndexOf('.');
        String base = ponto > 0 ? seguro.substring(0, ponto) : seguro;
        String extensao = ponto > 0 ? seguro.substring(ponto) : "";
        String candidato = base + "_" + sufixo + extensao;
        usados.add(candidato);
        return candidato;
    }

    private static String nomeFinal(String caminho) {
        String nome = caminho == null ? "arquivo" : caminho.replace('\\', '/');
        nome = nome.substring(nome.lastIndexOf('/') + 1);
        return nomeSeguro(nome);
    }

    private static String tipoConteudo(String nome) {
        String tipo = URLConnection.guessContentTypeFromName(nome);
        if (tipo != null) return tipo;
        String lower = nome.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (lower.endsWith(".csv")) return "text/csv";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".webp")) return "image/webp";
        return "application/octet-stream";
    }

    /** Porta de {@code truncate_output} do Onyx. */
    static String truncar(String output, int maximo) {
        if (output.length() <= maximo) return output;
        return output.substring(0, maximo) + "\n... [saida truncada, " + (output.length() - maximo) + " caracteres omitidos]";
    }

    private static String formatarDuracao(long ms) {
        if (ms < 1000) return ms + " ms";
        return String.format(Locale.of("pt", "BR"), "%.1f s", ms / 1000.0);
    }

    private static String limitar(String texto, int maximo) {
        return texto.length() <= maximo ? texto : texto.substring(0, maximo - 3) + "...";
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record Candidato(String nome, java.util.function.Supplier<byte[]> conteudo, boolean citado) {
    }

    private record ArquivoPreparado(String nome, byte[] conteudo) {
    }

    private record Staging(List<ArquivoPreparado> arquivos, List<String> avisos) {
        Staging {
            avisos = new ArrayList<>(avisos);
        }

        List<String> nomes() {
            return arquivos.stream().map(ArquivoPreparado::nome).toList();
        }

        String aviso() {
            return avisos.isEmpty() ? null : String.join(" ", avisos);
        }
    }
}
