package com.nexo.app.modules.rh.application.usecases;

import com.nexo.app.modules.rh.application.dto.SupervisorImportacaoDTO;
import com.nexo.app.modules.rh.domain.Colaborador;
import com.nexo.app.modules.rh.domain.Coordenador;
import com.nexo.app.modules.rh.domain.Supervisor;
import com.nexo.app.modules.rh.infrastructure.persistence.ColaboradorRepository;
import com.nexo.app.modules.rh.infrastructure.persistence.CoordenadorRepository;
import com.nexo.app.modules.rh.infrastructure.persistence.SupervisorRepository;
import com.nexo.app.shared.infrastructure.config.CacheNames;
import com.nexo.app.shared.infrastructure.security.HashUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@CacheConfig(cacheNames = CacheNames.SUPERVISORES)
public class ImportarSupervisoresAsyncService {

    private final SupervisorRepository repository;
    private final CoordenadorRepository coordenadorRepository;
    private final ColaboradorRepository colaboradorRepository;

    private static final DateTimeFormatter FORMATTER_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FORMATTER_ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Async
    @Transactional
    @CacheEvict(allEntries = true)
    public void processarPersistencia(List<SupervisorImportacaoDTO> supervisores) {
        log.info("Iniciando persistência assíncrona de {} supervisores", supervisores.size());

        for (SupervisorImportacaoDTO dto : supervisores) {
            try {
                Optional<Colaborador> colaboradorOpt = buscarColaborador(dto.cpfColaborador(), dto.nomeColaborador());
                if (colaboradorOpt.isEmpty()) {
                    log.warn("Colaborador supervisor não encontrado na linha {}: CPF={}, Nome={}", dto.linha(), dto.cpfColaborador(), dto.nomeColaborador());
                    continue;
                }

                Colaborador colaborador = colaboradorOpt.get();
                if (!colaborador.isAtivo()) {
                    log.warn("Colaborador supervisor inativo na linha {}: {}", dto.linha(), colaborador.getNomeCompleto());
                    continue;
                }

                if (repository.existsByColaboradorIdAndAtivoTrue(colaborador.getId())) {
                    log.warn("Colaborador já é supervisor ativo na linha {}: {}", dto.linha(), colaborador.getNomeCompleto());
                    continue;
                }

                Optional<Colaborador> coordenadorColaboradorOpt = buscarColaborador(dto.cpfCoordenador(), dto.nomeCoordenador());
                if (coordenadorColaboradorOpt.isEmpty()) {
                    log.warn("Colaborador coordenador não encontrado na linha {}: CPF={}, Nome={}", dto.linha(), dto.cpfCoordenador(), dto.nomeCoordenador());
                    continue;
                }

                Colaborador coordenadorColaborador = coordenadorColaboradorOpt.get();
                Optional<Coordenador> coordenadorOpt = coordenadorRepository.listarAtivos().stream()
                        .filter(c -> c.getColaborador().getId().equals(coordenadorColaborador.getId()))
                        .findFirst();

                if (coordenadorOpt.isEmpty()) {
                    log.warn("Registro ativo de coordenador não encontrado para o colaborador na linha {}: {}", dto.linha(), coordenadorColaborador.getNomeCompleto());
                    continue;
                }

                Coordenador coordenador = coordenadorOpt.get();

                if (colaborador.getId().equals(coordenador.getColaborador().getId())) {
                    log.warn("Supervisor e coordenador são a mesma pessoa na linha {}: {}", dto.linha(), colaborador.getNomeCompleto());
                    continue;
                }

                LocalDate dataInicio = parseData(dto.dataInicio());
                LocalDate dataFim = parseData(dto.dataFim());

                Supervisor supervisor = new Supervisor(colaborador, coordenador, dataInicio, dataFim);
                repository.save(supervisor);
            } catch (Exception e) {
                log.error("Erro ao persistir supervisor da linha {}: {}", dto.linha(), e.getMessage());
            }
        }
        log.info("Finalizada persistência assíncrona de supervisores");
    }

    private Optional<Colaborador> buscarColaborador(String cpf, String nome) {
        if (cpf != null && !cpf.isBlank()) {
            String cpfFormatado = cpf.replace("-", "").replace(".", "").trim();
            String hash = HashUtils.gerarBlindIndex(cpfFormatado);
            Optional<Colaborador> porCpf = colaboradorRepository.findByCpfHash(hash);
            if (porCpf.isPresent()) {
                return porCpf;
            }
        }

        if (nome != null && !nome.isBlank()) {
            String hash = HashUtils.gerarBlindIndex(nome.trim().toUpperCase());
            return colaboradorRepository.findByNomeCompletoHash(hash);
        }

        return Optional.empty();
    }

    private LocalDate parseData(String dataStr) {
        if (dataStr == null || dataStr.isBlank()) {
            return null;
        }
        String limpo = dataStr.trim();
        if (limpo.contains("/")) {
            return LocalDate.parse(limpo, FORMATTER_BR);
        }
        return LocalDate.parse(limpo, FORMATTER_ISO);
    }
}
