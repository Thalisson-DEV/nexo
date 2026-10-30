package com.nexo.app.modules.estoque.application.usecases;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexo.app.modules.estoque.application.dto.HistoricoSolicitacaoFilter;
import com.nexo.app.modules.estoque.application.dto.HistoricoSolicitacaoResponse;
import com.nexo.app.modules.estoque.application.services.IntegridadeHistoricoSolicitacaoService;
import com.nexo.app.modules.estoque.application.services.ResolvedorAutorEventoSolicitacao;
import com.nexo.app.modules.estoque.application.services.SanitizadorPayloadEventoSolicitacao;
import com.nexo.app.modules.estoque.domain.EscopoEventoSolicitacao;
import com.nexo.app.modules.estoque.domain.EventoSolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.NormalizadorOcorrenciaTecnica;
import com.nexo.app.modules.estoque.infrastructure.persistence.EventoSolicitacaoMaterialRepository;
import com.nexo.app.shared.application.dto.Paged;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HistoricoSolicitacaoUseCases {

    private static final Sort ORDENACAO_ESTAVEL = Sort.by(Sort.Order.desc("ocorridoEm"), Sort.Order.desc("id"));

    private final EventoSolicitacaoMaterialRepository repository;
    private final IntegridadeHistoricoSolicitacaoService integridade;
    private final ResolvedorAutorEventoSolicitacao resolvedorAutor;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public HistoricoSolicitacaoResponse porSolicitacao(
            UUID idSolicitacao,
            HistoricoSolicitacaoFilter filter,
            Pageable pageable
    ) {
        Specification<EventoSolicitacaoMaterial> specification = Specification
                .<EventoSolicitacaoMaterial>where(
                        (root, query, builder) -> builder.equal(
                                root.get("escopo"), EscopoEventoSolicitacao.SOLICITACAO))
                .and((root, query, builder) -> builder.equal(root.get("idSolicitacao"), idSolicitacao))
                .and(filtrar(filter));
        Page<EventoSolicitacaoMaterial> pagina = repository.findAll(specification, paginaEstavel(pageable));
        List<EventoSolicitacaoMaterial> cadeia = repository
                .findAllByEscopoAndIdSolicitacaoOrderBySequenciaAsc(EscopoEventoSolicitacao.SOLICITACAO, idSolicitacao);
        return resposta(pagina, Map.of(chave(EscopoEventoSolicitacao.SOLICITACAO, idSolicitacao), cadeia));
    }

    private HistoricoSolicitacaoResponse resposta(
            Page<EventoSolicitacaoMaterial> pagina,
            Map<ChaveCadeia, List<EventoSolicitacaoMaterial>> cadeias
    ) {
        List<HistoricoSolicitacaoResponse.IntegridadeCadeia> integridades = cadeias.entrySet().stream()
                .map(entry -> {
                    var resultado = integridade.verificar(entry.getValue());
                    return new HistoricoSolicitacaoResponse.IntegridadeCadeia(
                            entry.getKey().escopo(), entry.getKey().id(), resultado.status(),
                            resultado.sequenciaInicial(), resultado.sequenciaFinal());
                })
                .sorted(Comparator.comparing(item -> item.escopo().name() + item.idCadeia()))
                .toList();
        return new HistoricoSolicitacaoResponse(
                pagina.getContent().stream().map(this::mapear).toList(),
                new Paged(pagina.getSize(), pagina.getNumber(), pagina.getTotalElements(), pagina.getTotalPages()),
                integridades);
    }

    private HistoricoSolicitacaoResponse.Evento mapear(EventoSolicitacaoMaterial evento) {
        return new HistoricoSolicitacaoResponse.Evento(
                evento.getId(), evento.getEscopo(), evento.getIdSolicitacao(),
                evento.getSequencia(), evento.getTipoEvento(),
                evento.getTipoTecnico() == null ? null : com.nexo.app.modules.estoque.domain.TipoSolicitacaoTecnica
                        .porCodigo(evento.getTipoTecnico()),
                evento.getTipoObraSnapshot(), evento.getOcorrencia(),
                new HistoricoSolicitacaoResponse.Autor(
                        evento.getIdAutor(), resolvedorAutor.resolver(evento.getIdAutor(), evento.getAutorSnapshot())),
                evento.getOrigem(), evento.getJustificativa(), evento.getVersaoSolicitacao(), evento.getOcorridoEm(),
                payloadSeguro(evento.getPayloadCanonico()));
    }

    private Object payloadSeguro(String payload) {
        try {
            JsonNode resultado = objectMapper.readTree(payload);
            removerCamposSensiveis(resultado);
            return objectMapper.treeToValue(resultado, Object.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("O payload de histórico persistido é inválido.", ex);
        }
    }

    private void removerCamposSensiveis(JsonNode no) {
        if (no instanceof ObjectNode objeto) {
            List<String> campos = new java.util.ArrayList<>();
            objeto.fieldNames().forEachRemaining(campos::add);
            campos.forEach(campo -> {
                if (SanitizadorPayloadEventoSolicitacao.nomeSensivel(campo)) {
                    objeto.remove(campo);
                } else {
                    removerCamposSensiveis(objeto.get(campo));
                }
            });
        } else if (no.isArray()) {
            no.forEach(this::removerCamposSensiveis);
        }
    }

    private Specification<EventoSolicitacaoMaterial> filtrar(HistoricoSolicitacaoFilter filter) {
        if (filter == null) {
            return Specification.unrestricted();
        }
        return (root, query, builder) -> {
            List<jakarta.persistence.criteria.Predicate> predicados = new java.util.ArrayList<>();
            if (filter.inicio() != null) predicados.add(builder.greaterThanOrEqualTo(root.get("ocorridoEm"), filter.inicio()));
            if (filter.fim() != null) predicados.add(builder.lessThanOrEqualTo(root.get("ocorridoEm"), filter.fim()));
            if (filter.tipoTecnico() != null) predicados.add(builder.equal(root.get("tipoTecnico"), filter.tipoTecnico().getCodigo()));
            if (filter.tipoObra() != null) predicados.add(builder.equal(root.get("tipoObraSnapshot"), filter.tipoObra()));
            if (filter.ocorrencia() != null && !filter.ocorrencia().isBlank()) {
                predicados.add(builder.equal(
                        root.get("ocorrencia"), NormalizadorOcorrenciaTecnica.normalizar(filter.ocorrencia())));
            }
            if (filter.tipoEvento() != null) predicados.add(builder.equal(root.get("tipoEvento"), filter.tipoEvento()));
            if (filter.idAutor() != null) predicados.add(builder.equal(root.get("idAutor"), filter.idAutor()));
            if (filter.origem() != null) predicados.add(builder.equal(root.get("origem"), filter.origem()));
            if (filter.idItem() != null) {
                predicados.add(builder.like(
                        root.get("payloadCanonico").as(String.class), "%" + filter.idItem() + "%"));
            }
            return builder.and(predicados.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private Pageable paginaEstavel(Pageable pageable) {
        int pagina = pageable == null ? 0 : pageable.getPageNumber();
        int tamanho = pageable == null ? 20 : pageable.getPageSize();
        return PageRequest.of(Math.max(0, pagina), Math.min(Math.max(1, tamanho), 100), ORDENACAO_ESTAVEL);
    }

    private ChaveCadeia chave(EscopoEventoSolicitacao escopo, UUID id) {
        return new ChaveCadeia(escopo, id);
    }

    private record ChaveCadeia(EscopoEventoSolicitacao escopo, UUID id) {
    }
}
