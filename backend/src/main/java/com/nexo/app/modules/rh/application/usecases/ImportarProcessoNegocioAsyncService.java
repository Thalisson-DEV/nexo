package com.nexo.app.modules.rh.application.usecases;

import com.nexo.app.modules.rh.application.dto.ProcessoNegocioImportacaoDTO;
import com.nexo.app.modules.rh.domain.ProcessoNegocio;
import com.nexo.app.modules.rh.infrastructure.persistence.ProcessoNegocioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.nexo.app.shared.infrastructure.config.CacheNames.LOOKUP_BOOTSTRAP;
import static com.nexo.app.shared.infrastructure.config.CacheNames.PROCESSOS_NEGOCIO;

/**
 * Serviço assíncrono para persistência de Processos de Negócio importados.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImportarProcessoNegocioAsyncService {

    private final ProcessoNegocioRepository repository;

    @Async
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = PROCESSOS_NEGOCIO, allEntries = true),
            @CacheEvict(cacheNames = LOOKUP_BOOTSTRAP, allEntries = true)
    })
    public void processarPersistencia(List<ProcessoNegocioImportacaoDTO> processos) {
        log.info("Iniciando persistência assíncrona de {} processos de negócio", processos.size());
        
        for (ProcessoNegocioImportacaoDTO dto : processos) {
            try {
                if (!repository.existsByNomeIgnoreCase(dto.nome())) {
                    ProcessoNegocio processo = new ProcessoNegocio(dto.nome());
                    repository.save(processo);
                }
            } catch (Exception e) {
                log.error("Erro ao persistir processo de negócio da linha {}: {}", dto.linha(), e.getMessage());
            }
        }
        log.info("Finalizada persistência assíncrona de processos de negócio");
    }
}
