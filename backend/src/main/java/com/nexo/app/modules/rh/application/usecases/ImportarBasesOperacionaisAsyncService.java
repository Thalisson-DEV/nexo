package com.nexo.app.modules.rh.application.usecases;

import com.nexo.app.modules.rh.application.dto.BaseOperacionalImportacaoDTO;
import com.nexo.app.modules.rh.domain.BaseOperacional;
import com.nexo.app.modules.rh.domain.CentroFaturamento;
import com.nexo.app.modules.rh.infrastructure.persistence.BaseOperacionalRepository;
import com.nexo.app.modules.rh.infrastructure.persistence.CentroFaturamentoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.nexo.app.shared.infrastructure.config.CacheNames.BASES_OPERACIONAIS;
import static com.nexo.app.shared.infrastructure.config.CacheNames.LOOKUP_BOOTSTRAP;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImportarBasesOperacionaisAsyncService {

    private final BaseOperacionalRepository repository;
    private final CentroFaturamentoRepository centroFaturamentoRepository;

    @Async
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = BASES_OPERACIONAIS, allEntries = true),
            @CacheEvict(cacheNames = LOOKUP_BOOTSTRAP, allEntries = true)
    })
    public void processarPersistencia(List<BaseOperacionalImportacaoDTO> bases) {
        log.info("Iniciando persistência assíncrona de {} bases operacionais", bases.size());
        
        for (BaseOperacionalImportacaoDTO dto : bases) {
            try {
                CentroFaturamento centroFaturamento = centroFaturamentoRepository.findByNomeIgnoreCase(dto.nomeCentroFaturamento())
                        .orElseThrow();

                BaseOperacional baseOperacional = new BaseOperacional(
                        dto.nome(),
                        dto.uf(),
                        dto.endereco(),
                        centroFaturamento
                );

                repository.save(baseOperacional);
            } catch (Exception e) {
                log.error("Erro ao persistir base operacional da linha {}: {}", dto.linha(), e.getMessage());
            }
        }
        log.info("Finalizada persistência assíncrona de bases operacionais");
    }
}
