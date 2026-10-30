package com.nexo.app.modules.assistente.application.files;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.app.modules.assistente.application.AssistenteProperties;
import com.nexo.app.modules.assistente.application.agent.AgentRunContext;
import com.nexo.app.modules.assistente.application.dto.ArquivoAssistenteResponse;
import com.nexo.app.modules.assistente.application.results.TabularData;
import com.nexo.app.modules.assistente.domain.ArquivoAssistente;
import com.nexo.app.modules.assistente.domain.ArquivoConteudo;
import com.nexo.app.modules.assistente.infrastructure.persistence.ArquivoAssistenteRepository;
import com.nexo.app.modules.assistente.infrastructure.persistence.ArquivoConteudoRepository;
import com.nexo.app.shared.domain.exception.PreenchimentoInvalidoException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import com.nexo.app.shared.infrastructure.storage.ObjectStorageService;
import com.nexo.app.shared.infrastructure.storage.StorageObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Anexos e arquivos gerados do Copiloto. O conteudo fica no banco (padrao) ou no object storage do Nexo;
 * a extracao roda no upload para que o agente decida entre ler trechos, buscar ou processar no Python.
 * Todo acesso e restrito ao dono do arquivo.
 */
@Service
public class ArquivoService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArquivoService.class);
    private static final TypeReference<Map<String, Object>> MAPA = new TypeReference<>() { };
    private static final TypeReference<List<Map<String, Object>>> PLANILHAS = new TypeReference<>() { };
    private static final Set<String> EXTENSOES = Set.of("pdf", "xlsx", "xlsm", "xls", "csv", "tsv", "json", "txt", "md",
            "log", "xml", "yml", "yaml", "sql", "html", "htm", "docx", "png", "jpg", "jpeg", "gif", "webp");

    private final ArquivoAssistenteRepository repository;
    private final ArquivoConteudoRepository conteudoRepository;
    private final ObjectStorageService objectStorage;
    private final ArquivoParser parser;
    private final ObjectMapper objectMapper;
    private final AssistenteProperties properties;

    public ArquivoService(ArquivoAssistenteRepository repository, ArquivoConteudoRepository conteudoRepository,
            ObjectStorageService objectStorage, ArquivoParser parser, ObjectMapper objectMapper,
            AssistenteProperties properties) {
        this.repository = repository;
        this.conteudoRepository = conteudoRepository;
        this.objectStorage = objectStorage;
        this.parser = parser;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Transactional
    public ArquivoAssistenteResponse enviar(UUID usuarioId, String nomeOriginal, String tipoConteudo, byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw erro("arquivo", "O arquivo está vazio.");
        long maximo = properties.getArquivos().getMaxBytes();
        if (bytes.length > maximo) {
            throw erro("arquivo", "O arquivo excede o limite de " + (maximo / (1024 * 1024)) + " MB.");
        }
        String extensao = ArquivoParser.extensao(nomeOriginal);
        if (!EXTENSOES.contains(extensao)) {
            throw erro("arquivo", "Formato não suportado. Envie PDF, XLSX, XLS, CSV, JSON, TXT, DOCX ou imagens.");
        }
        ArquivoAssistente.Categoria categoria = ArquivoParser.categoria(nomeOriginal, tipoConteudo, bytes);
        ArquivoAssistente arquivo = repository.saveAndFlush(new ArquivoAssistente(usuarioId, null,
                ArquivoAssistente.Origem.UPLOAD, nomeOriginal, tipoConteudoSeguro(tipoConteudo, extensao), extensao,
                categoria, bytes.length, sha256(bytes), armazenamento()));
        armazenar(arquivo, bytes);
        extrair(arquivo, bytes);
        return toResponse(repository.save(arquivo));
    }

    /** Vincula anexos pendentes a conversa/pergunta e atribui codigos f1, f2... */
    @Transactional
    public List<ArquivoAssistente> vincular(UUID conversaId, UUID mensagemId, UUID usuarioId, Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        List<ArquivoAssistente> arquivos = repository.findByIdInAndUsuarioIdAndAtivoTrue(ids, usuarioId);
        if (arquivos.size() != new HashSet<>(ids).size()) {
            throw new RecursoNaoEncontradoException("Um ou mais anexos não foram encontrados");
        }
        long existentes = repository.contarCodificados(conversaId);
        List<ArquivoAssistente> ordenados = new ArrayList<>(arquivos);
        ordenados.sort(Comparator.comparing(ArquivoAssistente::getDataCriacao, Comparator.nullsLast(Comparator.naturalOrder())));
        for (ArquivoAssistente arquivo : ordenados) {
            if (arquivo.getCodigo() == null) existentes++;
            arquivo.vincular(conversaId, mensagemId, "f" + existentes);
        }
        return repository.saveAll(ordenados);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ArquivoAssistente salvarGerado(AgentRunContext context, String nome, String tipoConteudo, byte[] bytes) {
        String extensao = ArquivoParser.extensao(nome);
        ArquivoAssistente.Categoria categoria = ArquivoParser.categoria(nome, tipoConteudo, bytes);
        ArquivoAssistente arquivo = repository.saveAndFlush(new ArquivoAssistente(context.usuarioId(), context.conversaId(),
                ArquivoAssistente.Origem.GERADO, nome, tipoConteudoSeguro(tipoConteudo, extensao), extensao, categoria,
                bytes.length, sha256(bytes), armazenamento()));
        arquivo.vincular(context.conversaId(), context.mensagemAssistenteId(), context.novoCodigoArquivo());
        armazenar(arquivo, bytes);
        if (categoria != ArquivoAssistente.Categoria.IMAGEM && categoria != ArquivoAssistente.Categoria.OUTRO) {
            extrair(arquivo, bytes);
        }
        return repository.save(arquivo);
    }

    @Transactional(readOnly = true)
    public ArquivoAssistente exigirDoUsuario(UUID arquivoId, UUID usuarioId) {
        return repository.findByIdAndUsuarioIdAndAtivoTrue(arquivoId, usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Arquivo não encontrado"));
    }

    /** Resolve "f1" ou UUID dentro da conversa. */
    @Transactional(readOnly = true)
    public Optional<ArquivoAssistente> daConversa(UUID conversaId, String referencia) {
        if (referencia == null || referencia.isBlank()) return Optional.empty();
        String valor = referencia.trim();
        try {
            UUID id = UUID.fromString(valor);
            return repository.findById(id).filter(ArquivoAssistente::isAtivo)
                    .filter(arquivo -> conversaId.equals(arquivo.getConversaId()));
        } catch (IllegalArgumentException ignored) {
            return repository.findByConversaIdAndCodigoAndAtivoTrue(conversaId, valor.toLowerCase(Locale.ROOT));
        }
    }

    @Transactional(readOnly = true)
    public List<ArquivoAssistente> listarDaConversa(UUID conversaId) {
        return repository.findByConversaIdAndAtivoTrueOrderByDataCriacaoAsc(conversaId);
    }

    @Transactional(readOnly = true)
    public byte[] conteudo(ArquivoAssistente arquivo) {
        if (arquivo.getArmazenamento() == ArquivoAssistente.Armazenamento.R2) {
            StorageObject objeto = objectStorage.baixar(arquivo.getChaveArmazenamento());
            try (InputStream input = objeto.conteudo()) {
                return input.readAllBytes();
            } catch (IOException exception) {
                throw new IllegalStateException("Falha ao ler arquivo do armazenamento", exception);
            }
        }
        return conteudoRepository.findById(arquivo.getId()).map(ArquivoConteudo::getConteudo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Conteúdo do arquivo indisponível"));
    }

    @Transactional
    public void remover(UUID arquivoId, UUID usuarioId) {
        ArquivoAssistente arquivo = exigirDoUsuario(arquivoId, usuarioId);
        arquivo.desativar();
        repository.save(arquivo);
    }

    /** Planilhas extraidas (nome + dados), na ordem do arquivo. */
    public List<ArquivoExtraido.Planilha> planilhas(ArquivoAssistente arquivo) {
        if (arquivo.getDadosJson() == null || arquivo.getDadosJson().isBlank()) return List.of();
        try {
            List<Map<String, Object>> brutas = objectMapper.readValue(arquivo.getDadosJson(), PLANILHAS);
            List<ArquivoExtraido.Planilha> planilhas = new ArrayList<>();
            for (Map<String, Object> bruta : brutas) {
                TabularData dados = objectMapper.convertValue(bruta.get("dados"), TabularData.class);
                planilhas.add(new ArquivoExtraido.Planilha(String.valueOf(bruta.get("nome")), dados));
            }
            return planilhas;
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            LOGGER.warn("Dados tabulares invalidos no arquivo {}", arquivo.getId());
            return List.of();
        }
    }

    public Map<String, Object> resumo(ArquivoAssistente arquivo) {
        if (arquivo.getResumoJson() == null || arquivo.getResumoJson().isBlank()) return Map.of();
        try {
            return objectMapper.readValue(arquivo.getResumoJson(), MAPA);
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    public ArquivoAssistenteResponse toResponse(ArquivoAssistente arquivo) {
        return new ArquivoAssistenteResponse(arquivo.getId(), arquivo.getCodigo(), arquivo.getConversaId(),
                arquivo.getNome(), arquivo.getTipoConteudo(), arquivo.getCategoria().name(), arquivo.getTamanhoBytes(),
                arquivo.getOrigem().name(), arquivo.getStatus().name(), arquivo.getErro(), resumo(arquivo),
                arquivo.getDataCriacao());
    }

    // ------------------------------------------------------------------------------------ interno

    private void armazenar(ArquivoAssistente arquivo, byte[] bytes) {
        if (arquivo.getArmazenamento() == ArquivoAssistente.Armazenamento.R2) {
            String chave = "assistente/" + arquivo.getUsuarioId() + "/" + arquivo.getId() + "/" + arquivo.getNome();
            objectStorage.enviar(chave, new ByteArrayInputStream(bytes), bytes.length, arquivo.getTipoConteudo());
            arquivo.definirChaveArmazenamento(chave);
        } else {
            conteudoRepository.save(new ArquivoConteudo(arquivo.getId(), bytes));
        }
    }

    private void extrair(ArquivoAssistente arquivo, byte[] bytes) {
        try {
            ArquivoExtraido extraido = parser.extrair(arquivo.getNome(), bytes,
                    properties.getArquivos().getTextoMaxChars(), properties.getArquivos().getPlanilhaMaxLinhas());
            Map<String, Object> resumo = new LinkedHashMap<>(extraido.metadados());
            resumo.put("categoria", extraido.categoria().name());
            List<Map<String, Object>> planilhas = extraido.planilhas().stream()
                    .map(planilha -> Map.<String, Object>of("nome", planilha.nome(), "dados", planilha.dados()))
                    .toList();
            arquivo.registrarExtracao(objectMapper.writeValueAsString(resumo), extraido.texto(),
                    planilhas.isEmpty() ? null : objectMapper.writeValueAsString(planilhas));
        } catch (Exception exception) {
            LOGGER.info("Extracao do anexo {} falhou: {}", arquivo.getId(), exception.getClass().getSimpleName());
            arquivo.registrarFalhaExtracao("Não foi possível ler o conteúdo: " + Objects.toString(exception.getMessage(), "formato invalido"));
        }
    }

    private ArquivoAssistente.Armazenamento armazenamento() {
        return "r2".equalsIgnoreCase(properties.getArquivos().getArmazenamento())
                ? ArquivoAssistente.Armazenamento.R2 : ArquivoAssistente.Armazenamento.BANCO;
    }

    private static String tipoConteudoSeguro(String informado, String extensao) {
        return switch (extensao) {
            case "pdf" -> "application/pdf";
            case "xlsx", "xlsm" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "xls" -> "application/vnd.ms-excel";
            case "csv" -> "text/csv";
            case "tsv" -> "text/tab-separated-values";
            case "json" -> "application/json";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "html", "htm" -> "text/plain";
            case "txt", "md", "log", "sql", "yml", "yaml", "xml" -> "text/plain";
            default -> informado == null || informado.isBlank() ? "application/octet-stream" : informado;
        };
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static PreenchimentoInvalidoException erro(String campo, String mensagem) {
        return new PreenchimentoInvalidoException(Map.of(campo, mensagem));
    }
}
