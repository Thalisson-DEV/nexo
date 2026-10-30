package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.dto.TipoEstoqueImportacaoDTO;
import com.nexo.app.modules.estoque.domain.TipoEstoque;
import com.nexo.app.modules.estoque.infrastructure.persistence.TipoEstoqueRepository;
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
@CacheConfig(cacheNames = "tipos-estoque")
public class ImportarTipoEstoqueAsyncService {

    private final TipoEstoqueRepository repository;

    @Async
    @Transactional
    @CacheEvict(allEntries = true)
    public void processarPersistencia(List<TipoEstoqueImportacaoDTO> tipos) {
        log.info("Iniciando persistência assíncrona de {} tipos de estoque", tipos.size());
        
        for (TipoEstoqueImportacaoDTO dto : tipos) {
            try {
                if (repository.existsByNomeIgnoreCase(dto.nome())) {
                    log.warn("Tipo de estoque já existe: {}. Pulando.", dto.nome());
                    continue;
                }

                TipoEstoque tipo = new TipoEstoque(dto.nome());
                repository.save(tipo);
            } catch (Exception e) {
                log.error("Erro ao persistir tipo de estoque da linha {}: {}", dto.linha(), e.getMessage());
            }
        }
        log.info("Finalizada persistência assíncrona de tipos de estoque");
    }
}
