package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.dto.EstoqueImportacaoDTO;
import com.nexo.app.modules.estoque.domain.Estoque;
import com.nexo.app.modules.estoque.domain.TipoEstoque;
import com.nexo.app.modules.estoque.infrastructure.persistence.EstoqueRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.TipoEstoqueRepository;
import com.nexo.app.modules.rh.EquipeApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@CacheConfig(cacheNames = "estoques")
public class ImportarEstoqueAsyncService {

    private final EstoqueRepository repository;
    private final TipoEstoqueRepository tipoEstoqueRepository;
    private final EquipeApi equipeApi;

    @Async
    @Transactional
    @CacheEvict(allEntries = true)
    public void processarPersistencia(List<EstoqueImportacaoDTO> estoques) {
        log.info("Iniciando persistência assíncrona de {} estoques", estoques.size());
        
        for (EstoqueImportacaoDTO dto : estoques) {
            try {
                if (repository.existsByNomeIdentificadorIgnoreCase(dto.nomeIdentificador())) {
                    log.warn("Estoque já existe: {}. Pulando.", dto.nomeIdentificador());
                    continue;
                }

                TipoEstoque tipo = tipoEstoqueRepository.findByNomeIgnoreCase(dto.nomeTipoEstoque())
                        .orElseThrow(() -> new IllegalArgumentException("Tipo de estoque não encontrado: " + dto.nomeTipoEstoque()));

                Estoque estoque = new Estoque(dto.nomeIdentificador(), tipo);

                if (dto.codigoEquipe() != null && !dto.codigoEquipe().isBlank()) {
                    UUID idEquipe = equipeApi.buscarIdPorCodigo(dto.codigoEquipe())
                            .orElseThrow(() -> new IllegalArgumentException("Equipe não encontrada: " + dto.codigoEquipe()));
                    estoque.vincularEquipe(idEquipe);
                }

                repository.save(estoque);
            } catch (Exception e) {
                log.error("Erro ao persistir estoque da linha {}: {}", dto.linha(), e.getMessage());
            }
        }
        log.info("Finalizada persistência assíncrona de estoques");
    }
}
