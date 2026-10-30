package com.nexo.app.modules.rh.application.usecases;

import com.nexo.app.modules.rh.application.dto.TipoEquipeImportacaoDTO;
import com.nexo.app.modules.rh.domain.TipoEquipe;
import com.nexo.app.modules.rh.infrastructure.persistence.TipoEquipeRepository;
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
@CacheConfig(cacheNames = "tipos-equipes")
public class ImportarTipoEquipeAsyncService {

    private final TipoEquipeRepository repository;

    @Async
    @Transactional
    @CacheEvict(allEntries = true)
    public void processarPersistencia(List<TipoEquipeImportacaoDTO> tiposEquipe) {
        log.info("Iniciando persistência assíncrona de {} tipos de equipe", tiposEquipe.size());
        
        for (TipoEquipeImportacaoDTO dto : tiposEquipe) {
            try {
                if (!repository.existsByNomeIgnoreCase(dto.nome())) {
                    TipoEquipe tipoEquipe = new TipoEquipe(dto.nome());
                    repository.save(tipoEquipe);
                }
            } catch (Exception e) {
                log.error("Erro ao persistir tipo de equipe da linha {}: {}", dto.linha(), e.getMessage());
            }
        }
        log.info("Finalizada persistência assíncrona de tipos de equipe");
    }
}
