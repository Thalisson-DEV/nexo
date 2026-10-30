package com.nexo.app.modules.lookup.application.usecases;

import com.nexo.app.modules.estoque.EstoqueLookupApi;
import com.nexo.app.modules.lookup.application.dto.LookupBootstrapDto;
import com.nexo.app.modules.rh.BaseOperacionalApi;
import com.nexo.app.modules.rh.ProcessoNegocioApi;
import com.nexo.app.shared.infrastructure.lookup.LookupMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import static com.nexo.app.shared.infrastructure.config.CacheNames.LOOKUP_BOOTSTRAP;

@Service
@RequiredArgsConstructor
@Slf4j
public class LookupBootstrapUseCase {

    private static final String CACHE_KEY = "bootstrap-v1";
    private static final String RESOURCE = "bootstrap";
    private static final String ENDPOINT = "/lookups/bootstrap";

    private final BaseOperacionalApi baseOperacionalApi;
    private final ProcessoNegocioApi processoNegocioApi;
    private final EstoqueLookupApi estoqueLookupApi;
    private final CacheManager cacheManager;
    private final LookupMetrics metrics;

    public LookupBootstrapDto execute() {
        Cache cache = cache();
        LookupBootstrapDto cached = readCache(cache);
        if (cached != null) {
            metrics.recordCacheHit(RESOURCE, ENDPOINT);
            metrics.recordRequest(RESOURCE, ENDPOINT, "success");
            metrics.recordResultCount(RESOURCE, ENDPOINT, "success", totalItems(cached));
            return cached;
        }

        metrics.recordCacheMiss(RESOURCE, ENDPOINT);
        LookupBootstrapDto result = metrics.observeQuery(RESOURCE, ENDPOINT, "success", () ->
                new LookupBootstrapDto(
                        baseOperacionalApi.listarOpcoesAtivas(),
                        estoqueLookupApi.listarTiposMovimentacao(),
                        estoqueLookupApi.listarTiposSolicitacao(),
                        estoqueLookupApi.listarStatusSolicitacao(),
                        processoNegocioApi.listarOpcoesAtivas()));
        writeCache(cache, result);
        metrics.recordRequest(RESOURCE, ENDPOINT, "success");
        metrics.recordResultCount(RESOURCE, ENDPOINT, "success", totalItems(result));
        return result;
    }

    private Cache cache() {
        try {
            return cacheManager.getCache(LOOKUP_BOOTSTRAP);
        } catch (RuntimeException exception) {
            log.warn("Falha ao obter o cache do bootstrap de lookups; consulta direta sera usada.");
            return null;
        }
    }

    private LookupBootstrapDto readCache(Cache cache) {
        if (cache == null) {
            return null;
        }
        try {
            return cache.get(CACHE_KEY, LookupBootstrapDto.class);
        } catch (RuntimeException exception) {
            log.warn("Falha ao ler o cache do bootstrap de lookups; consulta direta sera usada.");
            return null;
        }
    }

    private void writeCache(Cache cache, LookupBootstrapDto result) {
        if (cache == null) {
            return;
        }
        try {
            cache.put(CACHE_KEY, result);
        } catch (RuntimeException exception) {
            log.warn("Falha ao atualizar o cache do bootstrap de lookups.");
        }
    }

    private static int totalItems(LookupBootstrapDto result) {
        return result.operationalBases().size()
                + result.movementTypes().size()
                + result.requestTypes().size()
                + result.statuses().size()
                + result.processes().size();
    }
}
