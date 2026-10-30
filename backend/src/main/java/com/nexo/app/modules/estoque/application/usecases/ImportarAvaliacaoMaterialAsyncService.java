package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.dto.AvaliacaoMaterialImportacaoDTO;
import com.nexo.app.modules.estoque.domain.AvaliacaoMaterial;
import com.nexo.app.modules.estoque.infrastructure.persistence.AvaliacaoMaterialRepository;
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
@CacheConfig(cacheNames = "avaliacoes-material")
public class ImportarAvaliacaoMaterialAsyncService {

    private final AvaliacaoMaterialRepository repository;

    @Async
    @Transactional
    @CacheEvict(allEntries = true)
    public void processarPersistencia(List<AvaliacaoMaterialImportacaoDTO> avaliacoes) {
        log.info("Iniciando persistência assíncrona de {} avaliações de material", avaliacoes.size());
        
        for (AvaliacaoMaterialImportacaoDTO dto : avaliacoes) {
            try {
                if (repository.existsByNomeIgnoreCase(dto.nome())) {
                    log.warn("Avaliação já existe: {}. Pulando.", dto.nome());
                    continue;
                }

                AvaliacaoMaterial avaliacao = new AvaliacaoMaterial(dto.nome());
                repository.save(avaliacao);
            } catch (Exception e) {
                log.error("Erro ao persistir avaliação da linha {}: {}", dto.linha(), e.getMessage());
            }
        }
        log.info("Finalizada persistência assíncrona de avaliações de material");
    }
}
