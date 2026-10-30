package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.infrastructure.persistence.MaterialLookupProjection;
import com.nexo.app.modules.estoque.infrastructure.persistence.MaterialRepository;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Busca server-side, leve e paginada para seletores de materiais. */
@Service
@RequiredArgsConstructor
public class MaterialLookupUseCase {

    private static final String RESOURCE = "material";
    private static final String ENDPOINT = "/materiais/options";
    private static final String SUCCESS = "success";

    private final MaterialRepository repository;
    private final LookupMetrics metrics;

    @Transactional(readOnly = true)
    public LookupPageDto<LookupOptionDto> execute(String query, Integer requestedLimit, String encodedCursor) {
        String normalizedQuery = LookupNormalizer.normalize(query);
        if (!LookupConstants.queryLongEnough(normalizedQuery)) {
            metrics.recordRequest(RESOURCE, ENDPOINT, SUCCESS);
            metrics.recordResultCount(RESOURCE, ENDPOINT, SUCCESS, 0);
            return LookupPageDto.empty();
        }

        int limit = LookupConstants.effectiveLimit(requestedLimit);
        LookupCursor cursor = encodedCursor == null || encodedCursor.isBlank()
                ? null
                : LookupCursorCodec.decode(encodedCursor);

        Instant queryStarted = Instant.now();
        List<MaterialLookupProjection> rows;
        try {
            rows = repository.buscarOpcoes(
                    normalizedQuery,
                    cursor == null ? null : cursor.sortKey(),
                    cursor == null ? null : cursor.id(),
                    PageRequest.of(0, limit + 1));
        } finally {
            metrics.recordQueryDuration(RESOURCE, ENDPOINT, SUCCESS,
                    Duration.between(queryStarted, Instant.now()));
        }

        boolean hasMore = rows.size() > limit;
        List<MaterialLookupProjection> page = hasMore ? rows.subList(0, limit) : rows;
        List<LookupOptionDto> items = page.stream()
                .map(row -> new LookupOptionDto(row.getId().toString(),
                        row.getCodigo() + " — " + row.getDescricao()))
                .toList();

        String nextCursor = hasMore
                ? LookupCursorCodec.encode(page.getLast().getSortKey(), page.getLast().getId())
                : null;
        metrics.recordRequest(RESOURCE, ENDPOINT, SUCCESS);
        metrics.recordResultCount(RESOURCE, ENDPOINT, SUCCESS, items.size());
        return new LookupPageDto<>(items, nextCursor, hasMore);
    }
}
