package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.dto.MaterialImportacaoDTO;
import com.nexo.app.modules.estoque.domain.AvaliacaoMaterial;
import com.nexo.app.modules.estoque.domain.CentroInvestimento;
import com.nexo.app.modules.estoque.domain.Material;
import com.nexo.app.modules.estoque.infrastructure.persistence.AvaliacaoMaterialRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.CentroInvestimentoRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.MaterialRepository;
import com.nexo.app.modules.rh.ProcessoNegocioApi;
import com.nexo.app.modules.rh.ProcessoNegocioResponse;
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
@CacheConfig(cacheNames = "materiais")
public class ImportarMaterialAsyncService {

    private final MaterialRepository repository;
    private final AvaliacaoMaterialRepository avaliacaoRepository;
    private final CentroInvestimentoRepository centroRepository;
    private final ProcessoNegocioApi processoApi;

    @Async
    @Transactional
    @CacheEvict(allEntries = true)
    public void processarPersistencia(List<MaterialImportacaoDTO> materiais) {
        log.info("Iniciando persistência assíncrona de {} materiais", materiais.size());
        
        for (MaterialImportacaoDTO dto : materiais) {
            try {
                if (repository.existsByCodigoIgnoreCase(dto.codigo())) {
                    log.warn("Material já existe: {}. Pulando.", dto.codigo());
                    continue;
                }

                AvaliacaoMaterial avaliacao = null;
                if (dto.nomeAvaliacao() != null && !dto.nomeAvaliacao().isBlank()) {
                    avaliacao = avaliacaoRepository.findByNomeIgnoreCase(dto.nomeAvaliacao())
                            .orElse(null);
                }

                CentroInvestimento centro = null;
                if (dto.codigoCentroInvestimento() != null && !dto.codigoCentroInvestimento().isBlank()) {
                    centro = centroRepository.findByCodigoIgnoreCase(dto.codigoCentroInvestimento())
                            .orElse(null);
                }

                UUID idProcesso = null;
                if (dto.nomeProcesso() != null && !dto.nomeProcesso().isBlank()) {
                    idProcesso = processoApi.buscarIdPorNome(dto.nomeProcesso()).orElse(null);
                }
                if (idProcesso == null) {
                    idProcesso = processoApi.buscarIdPorNome("COMERCIAL").orElse(null);
                }
                ProcessoNegocioResponse processo = processoApi.buscarPorIdResponse(idProcesso).orElse(null);
                Material material = new Material(
                        dto.codigo(),
                        dto.descricao(),
                        dto.unidadeMedida(),
                        dto.suprMatrFlag(),
                        avaliacao,
                        centro,
                        dto.detalhaSerie(),
                        idProcesso
                );

                repository.save(material);
            } catch (Exception e) {
                log.error("Erro ao persistir material da linha {}: {}", dto.linha(), e.getMessage());
            }
        }
        log.info("Finalizada persistência assíncrona de materiais");
    }
}
