package com.nexo.app.modules.estoque.application.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.nexo.app.modules.estoque.domain.ComandoIdempotenteSolicitacao;
import com.nexo.app.modules.estoque.infrastructure.persistence.ComandoIdempotenteSolicitacaoRepository;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * Persistencia e replay dos resultados de comandos criticos de solicitacao.
 * O payload e usado somente para o hash; apenas a resposta e correlacoes seguras
 * sao armazenadas no comando.
 */
@Service
public class IdempotenciaComandoSolicitacaoService {

    private static final String CAMPO_RESPOSTA = "respostaSerializada";

    private final ComandoIdempotenteSolicitacaoRepository comandoRepository;
    private final ObjectMapper objectMapper;
    private final ObservabilidadeSolicitacaoTecnica observabilidade;

    public IdempotenciaComandoSolicitacaoService(
            ComandoIdempotenteSolicitacaoRepository comandoRepository,
            ObjectMapper objectMapper,
            ObservabilidadeSolicitacaoTecnica observabilidade
    ) {
        this.comandoRepository = comandoRepository;
        this.objectMapper = objectMapper.copy()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.observabilidade = observabilidade;
    }

    public <T> Consulta<T> consultar(
            String escopo,
            UUID idUsuario,
            String chave,
            Object payload,
            Class<T> tipoResposta,
            ObservabilidadeSolicitacaoTecnica.Operacao operacao
    ) {
        String chaveNormalizada = validarChave(chave);
        String hashPayload = calcularHash(escopo, payload);
        ComandoIdempotenteSolicitacao comando = comandoRepository
                .findByEscopoAndIdUsuarioAndChave(escopo, idUsuario, chaveNormalizada)
                .orElse(null);
        if (comando == null) {
            return new Consulta<>(escopo, idUsuario, chaveNormalizada, hashPayload, null, false);
        }
        if (!comando.possuiMesmoPayload(hashPayload)) {
            observabilidade.registrarConflito(
                    operacao, ObservabilidadeSolicitacaoTecnica.TipoConflito.IDEMPOTENCIA);
            throw new InvarianteVioladaException(
                    "A chave de idempotencia ja foi usada com outro payload.");
        }
        T resposta = tipoResposta == Void.class ? null : deserializar(comando, tipoResposta);
        return new Consulta<>(escopo, idUsuario, chaveNormalizada, hashPayload, resposta, true);
    }

    public void registrar(
            Consulta<?> consulta,
            UUID idSolicitacao,
            UUID idOperacao,
            Object resposta
    ) {
        if (consulta.repetida()) {
            throw new IllegalStateException("Um comando repetido nao pode ser persistido novamente.");
        }
        comandoRepository.save(new ComandoIdempotenteSolicitacao(
                consulta.escopo(), consulta.idUsuario(), consulta.chave(), consulta.hashPayload(),
                idSolicitacao, idOperacao, serializarResposta(resposta)));
    }

    private String validarChave(String chave) {
        if (chave == null || chave.isBlank() || chave.length() > 200) {
            throw new InvarianteVioladaException(
                    "A chave de idempotencia e obrigatoria e deve ter ate 200 caracteres.");
        }
        return chave.trim();
    }

    private String calcularHash(String escopo, Object payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(escopo.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            return HexFormat.of().formatHex(digest.digest(objectMapper.writeValueAsBytes(payload)));
        } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Nao foi possivel calcular a idempotencia do comando.", ex);
        }
    }

    private Map<String, String> serializarResposta(Object resposta) {
        if (resposta == null) {
            return Map.of();
        }
        try {
            return Map.of(CAMPO_RESPOSTA, objectMapper.writeValueAsString(resposta));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Nao foi possivel persistir o resultado idempotente.", ex);
        }
    }

    private <T> T deserializar(ComandoIdempotenteSolicitacao comando, Class<T> tipoResposta) {
        String resposta = comando.getResposta() == null
                ? null : comando.getResposta().get(CAMPO_RESPOSTA);
        if (resposta == null) {
            throw new InvarianteVioladaException("O resultado idempotente do comando esta invalido.");
        }
        try {
            return objectMapper.readValue(resposta, tipoResposta);
        } catch (JsonProcessingException ex) {
            throw new InvarianteVioladaException("O resultado idempotente do comando esta invalido.");
        }
    }

    public record Consulta<T>(
            String escopo,
            UUID idUsuario,
            String chave,
            String hashPayload,
            T resposta,
            boolean repetida
    ) {
    }
}
