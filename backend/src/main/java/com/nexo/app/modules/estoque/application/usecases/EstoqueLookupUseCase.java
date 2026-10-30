package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.infrastructure.persistence.EstoqueLookupProjection;
import com.nexo.app.modules.estoque.infrastructure.persistence.EstoqueRepository;
import com.nexo.app.shared.application.dto.LookupOptionDto;
import com.nexo.app.shared.application.dto.LookupPageDto;
import com.nexo.app.shared.application.lookup.LookupConstants;
import com.nexo.app.shared.application.lookup.LookupCursor;
import com.nexo.app.shared.application.lookup.LookupCursorCodec;
import com.nexo.app.shared.application.lookup.LookupNormalizer;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.infrastructure.lookup.LookupMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Busca server-side, leve e paginada para seletores de estoques. */
@Service
@RequiredArgsConstructor
public class EstoqueLookupUseCase {

    private static final String RESOURCE = "estoque";
    private static final String ENDPOINT = "/estoques/options";
    private static final String SUCCESS = "success";
    private static final Set<String> TIPOS_SUPORTADOS = Set.of("OBRA", "FISICO", "CAMPO");

    private final EstoqueRepository repository;
    private final LookupMetrics metrics;

    @Transactional(readOnly = true)
    public LookupPageDto<LookupOptionDto> execute(
            String query,
            Integer requestedLimit,
            String encodedCursor,
            List<String> requestedTypes
    ) {
        String normalizedQuery = LookupNormalizer.normalize(query);
        if (!LookupConstants.queryLongEnough(normalizedQuery)) {
            metrics.recordRequest(RESOURCE, ENDPOINT, SUCCESS);
            metrics.recordResultCount(RESOURCE, ENDPOINT, SUCCESS, 0);
            return LookupPageDto.empty();
        }

        List<String> types = normalizeTypes(requestedTypes);
        int limit = LookupConstants.effectiveLimit(requestedLimit);
        LookupCursor cursor = encodedCursor == null || encodedCursor.isBlank()
                ? null
                : LookupCursorCodec.decode(encodedCursor);

        Instant queryStarted = Instant.now();
        List<EstoqueLookupProjection> rows;
        try {
            rows = repository.buscarOpcoes(
                    normalizedQuery,
                    types,
                    cursor == null ? null : cursor.sortKey(),
                    cursor == null ? null : cursor.id(),
                    PageRequest.of(0, limit + 1));
        } finally {
            metrics.recordQueryDuration(
                    RESOURCE, ENDPOINT, SUCCESS, Duration.between(queryStarted, Instant.now()));
        }

        boolean hasMore = rows.size() > limit;
        List<EstoqueLookupProjection> page = hasMore ? rows.subList(0, limit) : rows;
        List<LookupOptionDto> items = page.stream()
                .map(row -> new LookupOptionDto(
                        row.getId().toString(), row.getNomeIdentificador()))
                .toList();
        String nextCursor = hasMore
                ? LookupCursorCodec.encode(page.getLast().getSortKey(), page.getLast().getId())
                : null;

        metrics.recordRequest(RESOURCE, ENDPOINT, SUCCESS);
        metrics.recordResultCount(RESOURCE, ENDPOINT, SUCCESS, items.size());
        return new LookupPageDto<>(items, nextCursor, hasMore);
    }

    private List<String> normalizeTypes(List<String> requestedTypes) {
        if (requestedTypes == null || requestedTypes.isEmpty()) {
            return TIPOS_SUPORTADOS.stream().sorted().toList();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : requestedTypes) {
            if (value == null) continue;
            for (String part : value.split(",")) {
                String type = part.trim().toUpperCase(Locale.ROOT);
                if (type.isEmpty()) continue;
                if (!TIPOS_SUPORTADOS.contains(type)) {
                    throw new InvarianteVioladaException("Tipo de estoque nao suportado no lookup: " + type);
                }
                normalized.add(type);
            }
        }
        if (normalized.isEmpty()) {
            throw new InvarianteVioladaException("Informe ao menos um tipo de estoque no lookup.");
        }
        return normalized.stream().sorted().toList();
    }
}
