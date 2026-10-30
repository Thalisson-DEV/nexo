package com.nexo.app.modules.iam.application.usecases;

import com.nexo.app.modules.iam.application.dto.PerfilAcessoImportacaoDTO;
import com.nexo.app.modules.iam.domain.PerfilAcesso;
import com.nexo.app.modules.iam.infrastructure.persistence.PerfilAcessoRepository;
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
@CacheConfig(cacheNames = "perfis-acessos")
public class ImportarPerfilAcessoAsyncService {

    private final PerfilAcessoRepository repository;

    @Async
    @Transactional
    @CacheEvict(allEntries = true)
    public void processarPersistencia(List<PerfilAcessoImportacaoDTO> perfis) {
        log.info("Iniciando persistência assíncrona de {} perfis de acesso", perfis.size());
        
        for (PerfilAcessoImportacaoDTO dto : perfis) {
            try {
                if (!repository.existsByNomeIgnoreCase(dto.nome())) {
                    PerfilAcesso perfil = new PerfilAcesso(dto.nome());
                    repository.save(perfil);
                }
            } catch (Exception e) {
                log.error("Erro ao persistir perfil de acesso da linha {}: {}", dto.linha(), e.getMessage());
            }
        }
        log.info("Finalizada persistência assíncrona de perfis de acesso");
    }
}
