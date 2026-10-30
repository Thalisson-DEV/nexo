package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.dto.CentroInvestimentoImportacaoDTO;
import com.nexo.app.modules.estoque.domain.CentroInvestimento;
import com.nexo.app.modules.estoque.infrastructure.persistence.CentroInvestimentoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@CacheConfig(cacheNames = "centros-investimento")
public class ImportarCentroInvestimentoAsyncService {

    private final CentroInvestimentoRepository repository;

    @Async
    @Transactional
    @CacheEvict(allEntries = true)
    public void processarPersistencia(List<CentroInvestimentoImportacaoDTO> centros) {
        log.info("Iniciando persistência assíncrona de {} centros de investimento", centros.size());
        
        for (CentroInvestimentoImportacaoDTO dto : centros) {
            try {
                if (repository.existsByCodigoIgnoreCase(dto.codigo())) {
                    log.warn("Centro de investimento já existe: {}. Pulando.", dto.codigo());
                    continue;
                }

                CentroInvestimento centro = new CentroInvestimento(dto.codigo());
                repository.save(centro);
            } catch (Exception e) {
                log.error("Erro ao persistir centro de investimento da linha {}: {}", dto.linha(), e.getMessage());
            }
        }
        log.info("Finalizada persistência assíncrona de centros de investimento");
    }
}
