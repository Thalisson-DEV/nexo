package com.nexo.app.modules.estoque.application.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Preserva a diferenca entre chave ausente e chave presente com valor JSON null. */
public class PlanejarSolicitacaoTecnicaRequestDeserializer
        extends StdDeserializer<PlanejarSolicitacaoTecnicaRequest> {

    public PlanejarSolicitacaoTecnicaRequestDeserializer() {
        super(PlanejarSolicitacaoTecnicaRequest.class);
    }

    @Override
    public PlanejarSolicitacaoTecnicaRequest deserialize(
            JsonParser parser,
            DeserializationContext context
    ) throws IOException {
        ObjectMapper mapper = (ObjectMapper) parser.getCodec();
        JsonNode raiz = mapper.readTree(parser);
        return new PlanejarSolicitacaoTecnicaRequest(
                raiz.has("versaoEsperada")
                        ? valor(mapper, raiz, "versaoEsperada", Long.class)
                        : valor(mapper, raiz, "versao", Long.class),
                valor(mapper, raiz, "idObra", UUID.class),
                valor(mapper, raiz, "ocorrencia", String.class),
                valor(mapper, raiz, "idAlmoxarifadoAtendimento", UUID.class),
                valor(mapper, raiz, "idEquipe", UUID.class),
                valor(mapper, raiz, "idColaboradorSaque", UUID.class),
                valor(mapper, raiz, "saqueForaEquipe", Boolean.class),
                valor(mapper, raiz, "dataPrevista", LocalDate.class),
                valor(mapper, raiz, "observacao", String.class),
                lista(mapper, raiz.get("itens")),
                valor(mapper, raiz, "justificativa", String.class),
                raiz.has("ocorrencia"),
                raiz.has("idEquipe"),
                raiz.has("dataPrevista"),
                raiz.has("observacao"),
                raiz.has("idColaboradorSaque"),
                raiz.has("saqueForaEquipe"));
    }

    private <T> T valor(ObjectMapper mapper, JsonNode raiz, String campo, Class<T> tipo)
            throws IOException {
        JsonNode valor = raiz.get(campo);
        return valor == null || valor.isNull() ? null : mapper.treeToValue(valor, tipo);
    }

    private List<PlanejarSolicitacaoTecnicaRequest.Item> lista(ObjectMapper mapper, JsonNode valor) {
        return valor == null || valor.isNull() ? null : mapper.convertValue(
                valor, new TypeReference<List<PlanejarSolicitacaoTecnicaRequest.Item>>() {});
    }

}
