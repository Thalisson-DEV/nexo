package com.nexo.app.modules.rh.application.usecases;

import com.nexo.app.modules.rh.application.dto.EquipeImportacaoDTO;
import com.nexo.app.modules.rh.domain.*;
import com.nexo.app.modules.rh.infrastructure.persistence.*;
import com.nexo.app.shared.infrastructure.security.HashUtils;
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
@CacheConfig(cacheNames = "equipes")
public class ImportarEquipesAsyncService {

    private final EquipeRepository repository;
    private final TipoEquipeRepository tipoEquipeRepository;
    private final BaseOperacionalRepository baseRepository;
    private final VeiculoRepository veiculoRepository;
    private final ColaboradorRepository colaboradorRepository;
    private final CoordenadorRepository coordenadorRepository;
    private final SupervisorRepository supervisorRepository;
    private final ProcessoNegocioRepository processoRepository;
    private final MembroEquipeUseCase membroEquipeUseCase;

    @Async
    @Transactional
    @CacheEvict(allEntries = true)
    public void processarPersistencia(List<EquipeImportacaoDTO> equipes) {
        log.info("Iniciando persistência assíncrona de {} equipes", equipes.size());
        
        for (EquipeImportacaoDTO dto : equipes) {
            try {
                TipoEquipe tipoEquipe = tipoEquipeRepository.findByNomeIgnoreCase(dto.nomeTipoEquipe())
                        .orElseThrow(() -> new IllegalArgumentException("Tipo de equipe não encontrado: " + dto.nomeTipoEquipe()));
                
                BaseOperacional base = baseRepository.findByNomeIgnoreCase(dto.nomeBase())
                        .orElseThrow(() -> new IllegalArgumentException("Base não encontrada: " + dto.nomeBase()));
                
                Veiculo veiculo = veiculoRepository.findByPlacaIgnoreCase(dto.placaVeiculo())
                        .orElseThrow(() -> new IllegalArgumentException("Veículo não encontrado: " + dto.placaVeiculo()));
                
                String coordenadorHash = HashUtils.gerarBlindIndex(dto.nomeCoordenador().toUpperCase());
                Coordenador coordenador = coordenadorRepository.findByColaboradorNomeCompletoHashAndAtivoTrue(coordenadorHash)
                        .orElseThrow(() -> new IllegalArgumentException("Coordenador não encontrado: " + dto.nomeCoordenador()));
                
                String supervisorHash = HashUtils.gerarBlindIndex(dto.nomeSupervisor().toUpperCase());
                Supervisor supervisor = supervisorRepository.findByColaboradorNomeCompletoHashAndAtivoTrue(supervisorHash)
                        .orElseThrow(() -> new IllegalArgumentException("Supervisor não encontrado: " + dto.nomeSupervisor()));
                
                String responsavelHash = HashUtils.gerarBlindIndex(dto.nomeResponsavel().toUpperCase());
                Colaborador responsavel = colaboradorRepository.findByNomeCompletoHash(responsavelHash)
                        .orElseThrow(() -> new IllegalArgumentException("Responsável não encontrado: " + dto.nomeResponsavel()));

                ProcessoNegocio processo = processoRepository.findByNomeIgnoreCase(dto.nomeProcesso())
                        .orElseThrow(() -> new IllegalArgumentException("Processo não encontrado: " + dto.nomeProcesso()));

                Equipe equipe = new Equipe(
                        dto.codigo(),
                        tipoEquipe,
                        base,
                        veiculo,
                        coordenador,
                        supervisor,
                        processo
                );

                // Aloca o responsável inicial
                membroEquipeUseCase.processarAlocacaoUnica(equipe, responsavel, com.nexo.app.modules.rh.domain.EquipeMembro.FuncaoMembro.RESPONSAVEL);

                repository.save(equipe);
            } catch (Exception e) {
                log.error("Erro ao persistir equipe da linha {}: {}", dto.linha(), e.getMessage());
            }
        }
        log.info("Finalizada persistência assíncrona de equipes");
    }
}
