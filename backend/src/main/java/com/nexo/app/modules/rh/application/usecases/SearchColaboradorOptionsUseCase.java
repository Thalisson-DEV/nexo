package com.nexo.app.modules.rh.application.usecases;

import com.nexo.app.modules.rh.infrastructure.persistence.ColaboradorLookupProjection;
import com.nexo.app.modules.rh.infrastructure.persistence.ColaboradorRepository;
import com.nexo.app.shared.application.dto.LookupOptionDto;
import com.nexo.app.shared.application.dto.LookupPageDto;
import com.nexo.app.shared.application.lookup.LookupConstants;
import com.nexo.app.shared.application.lookup.LookupCursor;
import com.nexo.app.shared.application.lookup.LookupCursorCodec;
import com.nexo.app.shared.application.lookup.LookupNormalizer;
import com.nexo.app.shared.infrastructure.lookup.LookupMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Caso de uso leve e específico para os seletores de colaboradores. */
@Service
@RequiredArgsConstructor
public class SearchColaboradorOptionsUseCase {

    private static final String RESOURCE = "collaborator";
    private static final String ENDPOINT = "/rh/colaboradores/options";
    private static final String SUCCESS = "success";

    private final ColaboradorRepository repository;
    private final LookupMetrics metrics;

    @Transactional(readOnly = true)
    public LookupPageDto<LookupOptionDto> execute(String query, Integer requestedLimit, String encodedCursor) {
        String normalizedQuery = LookupNormalizer.normalize(query == null ? "" : query);
        if (!LookupConstants.queryLongEnough(normalizedQuery)) {
            metrics.recordRequest(RESOURCE, ENDPOINT, SUCCESS);
            metrics.recordResultCount(RESOURCE, ENDPOINT, SUCCESS, 0);
            return LookupPageDto.empty();
        }

        int limit = LookupConstants.effectiveLimit(requestedLimit);
        var pageable = PageRequest.of(0, limit + 1);
        List<ColaboradorLookupProjection> rows;
        if (encodedCursor == null || encodedCursor.isBlank()) {
            rows = metrics.observeQuery(RESOURCE, ENDPOINT, SUCCESS,
                    () -> repository.buscarOpcoes(normalizedQuery, pageable));
        } else {
            LookupCursor cursor = LookupCursorCodec.decode(encodedCursor);
            rows = metrics.observeQuery(RESOURCE, ENDPOINT, SUCCESS,
                    () -> repository.buscarOpcoesAposCursor(
                            normalizedQuery, cursor.sortKey(), cursor.id(), pageable));
        }
        if (rows == null) {
            rows = List.of();
        }

        boolean hasMore = rows.size() > limit;
        List<ColaboradorLookupProjection> pageRows = hasMore ? rows.subList(0, limit) : rows;
        List<LookupOptionDto> items = pageRows.stream()
                .map(row -> new LookupOptionDto(row.getId().toString(), row.getNomeCompleto()))
                .toList();

        String nextCursor = null;
        if (hasMore) {
            ColaboradorLookupProjection last = pageRows.getLast();
            nextCursor = LookupCursorCodec.encode(last.getNomeBusca(), last.getId());
        }
        metrics.recordRequest(RESOURCE, ENDPOINT, SUCCESS);
        metrics.recordResultCount(RESOURCE, ENDPOINT, SUCCESS, items.size());
        return new LookupPageDto<>(items, nextCursor, hasMore);
    }
}
