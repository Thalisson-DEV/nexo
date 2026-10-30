package com.nexo.app.modules.assistente.application.artifacts;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.app.modules.assistente.application.agent.AgentRunContext;
import com.nexo.app.modules.assistente.domain.ArtefatoAssistente;
import com.nexo.app.modules.assistente.infrastructure.persistence.ArtefatoAssistenteRepository;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Persistencia e leitura de artefatos (a1, a2...). A especificacao gravada ja esta resolvida (dados
 * calculados a partir dos resultados), entao reabrir uma conversa antiga mostra exatamente o mesmo
 * dashboard, grafico ou relatorio, mesmo que os dados do Nexo tenham mudado depois.
 */
@Service
public class ArtifactService {

    private static final TypeReference<Map<String, Object>> MAPA = new TypeReference<>() { };

    private final ArtefatoAssistenteRepository repository;
    private final ObjectMapper objectMapper;

    public ArtifactService(ArtefatoAssistenteRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ArtefatoView criar(AgentRunContext context, ArtefatoAssistente.Tipo tipo, String titulo, String descricao,
            Map<String, Object> especificacao, UUID arquivoId) {
        String codigo = context.novoCodigoArtefato();
        Map<String, Object> spec = new LinkedHashMap<>(especificacao);
        spec.putIfAbsent("versao", 1);
        ArtefatoAssistente entity = repository.save(new ArtefatoAssistente(context.conversaId(),
                context.mensagemAssistenteId(), codigo, tipo, titulo, descricao, write(spec), arquivoId));
        context.registrarArtefatoDoTurno(codigo);
        return toView(entity, spec);
    }

    @Transactional(readOnly = true)
    public List<ArtefatoView> listar(UUID conversaId) {
        return repository.findByConversaIdAndAtivoTrueOrderByDataCriacaoAsc(conversaId).stream()
                .map(entity -> toView(entity, read(entity.getEspecificacaoJson())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ArtefatoView buscar(UUID conversaId, String codigo) {
        return repository.findByConversaIdAndCodigoAndAtivoTrue(conversaId, codigo.trim().toLowerCase(Locale.ROOT))
                .map(entity -> toView(entity, read(entity.getEspecificacaoJson())))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Artefato não encontrado"));
    }

    @Transactional(readOnly = true)
    public long contar(UUID conversaId) {
        return repository.countByConversaId(conversaId);
    }

    public ArtefatoView toView(ArtefatoAssistente entity, Map<String, Object> spec) {
        return new ArtefatoView(entity.getId(), entity.getCodigo(), entity.getTipo().name(), entity.getTitulo(),
                entity.getDescricao(), spec, entity.getArquivoId(), entity.getMensagemId(), entity.getDataCriacao());
    }

    private String write(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Falha ao serializar artefato", exception);
        }
    }

    private Map<String, Object> read(String json) {
        try {
            return objectMapper.readValue(json, MAPA);
        } catch (JsonProcessingException exception) {
            return Map.of("erro", "Especificacao invalida");
        }
    }

    /** Representacao publica do artefato (API e pacote {@code artifact} do stream). */
    public record ArtefatoView(UUID id, String codigo, String tipo, String titulo, String descricao,
            Map<String, Object> especificacao, UUID arquivoId, UUID mensagemId, OffsetDateTime dataCriacao) {
    }
}
