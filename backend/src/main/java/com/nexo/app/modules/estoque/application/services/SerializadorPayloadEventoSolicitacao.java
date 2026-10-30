package com.nexo.app.modules.estoque.application.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexo.app.modules.estoque.domain.AlteracaoEventoSolicitacao;
import com.nexo.app.modules.estoque.domain.PayloadEventoSolicitacao;
import com.nexo.app.modules.estoque.domain.ReferenciaEventoSolicitacao;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Component
public class SerializadorPayloadEventoSolicitacao {

    private static final ObjectMapper CANONICAL_JSON_MAPPER = new ObjectMapper()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .setNodeFactory(JsonNodeFactory.withExactBigDecimals(true))
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final ObjectMapper objectMapper;

    public SerializadorPayloadEventoSolicitacao(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public String serializar(PayloadEventoSolicitacao payload) {
        PayloadEventoSolicitacao seguro = SanitizadorPayloadEventoSolicitacao.sanitizar(payload);
        Map<String, Object> canonico = new TreeMap<>();
        canonico.put("comando", seguro.comando());
        List<Map<String, Object>> alteracoes = seguro.alteracoes().stream()
                .sorted(Comparator.comparing(AlteracaoEventoSolicitacao::caminho))
                .map(alteracao -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("caminho", alteracao.caminho());
                    item.put("valorAnterior", normalizar(alteracao.valorAnterior()));
                    item.put("valorNovo", normalizar(alteracao.valorNovo()));
                    return item;
                }).toList();
        canonico.put("alteracoes", alteracoes);
        canonico.put("snapshots", normalizar(seguro.snapshots()));
        try {
            return objectMapper.writeValueAsString(canonico);
        } catch (JsonProcessingException ex) {
            throw new InvarianteVioladaException("Nao foi possivel serializar o payload canonico do evento.");
        }
    }

    /**
     * Reescreve um JSON usando a mesma representacao compacta e ordenada em
     * todas as camadas. O PostgreSQL pode devolver um JSONB com espacos e uma
     * ordem de chaves diferente da recebida, portanto o texto da coluna nao
     * pode ser usado diretamente como entrada da assinatura HMAC.
     */
    public static String canonicalizarJson(String payload) {
        if (payload == null || payload.isBlank()) {
            throw new InvarianteVioladaException("O payload canonico e obrigatorio.");
        }
        try {
            JsonNode arvore = CANONICAL_JSON_MAPPER.readTree(payload);
            if (arvore == null) {
                throw new InvarianteVioladaException("O payload canonico e invalido.");
            }
            return CANONICAL_JSON_MAPPER.writeValueAsString(ordenar(arvore));
        } catch (JsonProcessingException ex) {
            throw new InvarianteVioladaException("O payload canonico e invalido.");
        }
    }

    private static JsonNode ordenar(JsonNode no) {
        if (no instanceof ObjectNode objeto) {
            ObjectNode ordenado = CANONICAL_JSON_MAPPER.createObjectNode();
            List<String> campos = new ArrayList<>();
            objeto.fieldNames().forEachRemaining(campos::add);
            campos.sort(String::compareTo);
            campos.forEach(campo -> ordenado.set(campo, ordenar(objeto.get(campo))));
            return ordenado;
        }
        if (no instanceof ArrayNode array) {
            ArrayNode ordenado = CANONICAL_JSON_MAPPER.createArrayNode();
            array.forEach(item -> ordenado.add(ordenar(item)));
            return ordenado;
        }
        return no;
    }

    private Object normalizar(Object valor) {
        if (valor == null || valor instanceof String || valor instanceof Boolean
                || valor instanceof Integer || valor instanceof Long) {
            return valor;
        }
        if (valor instanceof UUID uuid) {
            return uuid.toString();
        }
        if (valor instanceof BigDecimal decimal) {
            int escala = Math.max(3, decimal.scale());
            return decimal.setScale(escala).toPlainString();
        }
        if (valor instanceof BigInteger inteiro) {
            return inteiro.toString();
        }
        if (valor instanceof TemporalAccessor temporal) {
            return temporal.toString();
        }
        if (valor instanceof Enum<?> enumeracao) {
            return enumeracao.name();
        }
        if (valor instanceof ReferenciaEventoSolicitacao referencia) {
            Map<String, Object> resultado = new TreeMap<>();
            resultado.put("id", normalizar(referencia.id()));
            resultado.put("snapshot", normalizar(referencia.snapshot()));
            return resultado;
        }
        if (valor instanceof Record record) {
            Map<String, Object> resultado = new TreeMap<>();
            for (java.lang.reflect.RecordComponent component : record.getClass().getRecordComponents()) {
                try {
                    resultado.put(component.getName(), normalizar(component.getAccessor().invoke(record)));
                } catch (ReflectiveOperationException ex) {
                    throw new InvarianteVioladaException(
                            "Nao foi possivel normalizar o record no payload canonico: " + record.getClass().getSimpleName());
                }
            }
            return resultado;
        }
        if (valor instanceof Map<?, ?> mapa) {
            Map<String, Object> resultado = new TreeMap<>();
            mapa.forEach((chave, item) -> resultado.put(String.valueOf(chave), normalizar(item)));
            return resultado;
        }
        if (valor instanceof Collection<?> colecao) {
            List<Object> resultado = new ArrayList<>(colecao.size());
            colecao.forEach(item -> resultado.add(normalizar(item)));
            return resultado;
        }
        throw new InvarianteVioladaException(
                "Tipo nao suportado no payload canonico: " + valor.getClass().getSimpleName());
    }
}
