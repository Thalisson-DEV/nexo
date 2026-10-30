package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.obras.application.dto.ObraImportacaoDTO;
import com.nexo.app.modules.obras.domain.AgenteResponsavel;
import com.nexo.app.modules.obras.domain.Obra;
import com.nexo.app.modules.obras.domain.StatusObra;
import com.nexo.app.modules.obras.infrastructure.persistence.AgenteResponsavelRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.ObraRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.StatusObraRepository;
import com.nexo.app.modules.rh.BaseOperacionalApi;
import com.nexo.app.modules.rh.CentroFaturamentoApi;
import com.nexo.app.modules.rh.CoordenadorApi;
import com.nexo.app.modules.rh.EquipeApi;
import com.nexo.app.modules.rh.MunicipioApi;
import com.nexo.app.modules.rh.ProcessoNegocioApi;
import com.nexo.app.modules.rh.SupervisorApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImportarObrasAsyncService {

    private final ObraRepository obraRepository;
    private final StatusObraRepository statusObraRepository;
    private final AgenteResponsavelRepository agenteRepository;
    private final ProcessoNegocioApi processoNegocioApi;
    private final BaseOperacionalApi baseOperacionalApi;
    private final MunicipioApi municipioApi;
    private final CentroFaturamentoApi centroFaturamentoApi;
    private final SupervisorApi supervisorApi;
    private final CoordenadorApi coordenadorApi;
    private final EquipeApi equipeApi;
    private final NormalizadorObraService normalizador;

    @Async
    @Transactional
    public void processarPersistencia(List<ObraImportacaoDTO> registros) {
        log.info("Iniciando persistência assíncrona de {} obras", registros.size());
        int salvas = 0;

        for (ObraImportacaoDTO dto : registros) {
            try {
                salvarObra(dto);
                salvas++;
            } catch (Exception e) {
                log.error("Erro ao persistir obra da linha {} (Número: {}): {}",
                        dto.linha(), dto.numero(), e.getMessage(), e);
            }
        }

        log.info("Persistência assíncrona concluída: {} de {} obras salvas com sucesso", salvas, registros.size());
    }

    private void salvarObra(ObraImportacaoDTO dto) {
        String numeroNorm = normalizador.normalizarNumero(dto.numero());
        String tituloNorm = normalizador.normalizarTitulo(dto.titulo());

        if (obraRepository.existsByNumeroIgnoreCaseAndAtivoTrue(numeroNorm)) {
            log.warn("Obra número '{}' já existe ativa. Registro ignorado.", numeroNorm);
            return;
        }

        LocalDate dataInicio = normalizador.parseData(dto.dataInicio());
        LocalDate dataPrevisaoFim = normalizador.parseData(dto.dataPrevisaoFim());
        Double latitude = normalizador.parseCoordenada(dto.latitude());
        Double longitude = normalizador.parseCoordenada(dto.longitude());
        BigDecimal valorProjeto = normalizador.parseValor(dto.valorProjeto());

        StatusObra status = resolverStatus(dto.nomeStatus());
        AgenteResponsavel agente = resolverAgente(dto.nomeAgenteResponsavel());
        UUID idProcesso = resolverProcesso(dto.nomeProcesso());
        UUID idBase = resolverBase(dto.nomeBaseOperacional());
        UUID idMunicipio = resolverMunicipio(dto.nomeMunicipio());
        UUID idCentroCusto = resolverCentroCusto(dto.nomeCentroCusto());
        UUID idSupervisor = resolverSupervisor(dto.nomeSupervisor());
        UUID idCoordenador = resolverCoordenador(dto.nomeCoordenador());
        Set<UUID> equipes = resolverEquipes(dto.codigosEquipes());

        Obra obra = new Obra(
                numeroNorm,
                tituloNorm,
                dto.descricao(),
                dto.notaObra(),
                valorProjeto,
                dataInicio,
                dataPrevisaoFim,
                latitude,
                longitude,
                idProcesso,
                idBase,
                idMunicipio,
                idCentroCusto,
                idSupervisor,
                idCoordenador,
                status,
                agente,
                equipes,
                normalizador.parseTipoObra(dto.tipoObra())
        );

        obraRepository.save(obra);
    }

    private StatusObra resolverStatus(String nomeStatus) {
        if (nomeStatus != null && !nomeStatus.isBlank()) {
            Optional<StatusObra> opt = statusObraRepository.findByNomeIgnoreCase(nomeStatus.trim());
            if (opt.isPresent()) return opt.get();
            String norm = java.text.Normalizer.normalize(nomeStatus.trim(), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
            for (StatusObra s : statusObraRepository.findAll()) {
                String sNorm = java.text.Normalizer.normalize(s.getNome(), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
                if (sNorm.equalsIgnoreCase(norm)) {
                    return s;
                }
            }
        }
        return statusObraRepository.findByNomeIgnoreCase("PLANEJADA")
                .or(() -> statusObraRepository.findByNomeIgnoreCase("EM EXECUÇÃO"))
                .orElseGet(() -> statusObraRepository.findAll().stream().findFirst()
                        .orElseGet(() -> statusObraRepository.save(new StatusObra("PLANEJADA", 1, false))));
    }

    private AgenteResponsavel resolverAgente(String nome) {
        if (nome == null || nome.isBlank()) {
            return null;
        }
        String nomeNorm = nome.trim().toUpperCase(Locale.ROOT);
        return agenteRepository.findByNomeIgnoreCase(nomeNorm).orElse(null);
    }

    private UUID resolverProcesso(String nomeProcesso) {
        if (nomeProcesso == null || nomeProcesso.isBlank()) return null;
        return processoNegocioApi.buscarIdPorNome(nomeProcesso.trim()).orElse(null);
    }

    private UUID resolverBase(String nomeBase) {
        if (nomeBase == null || nomeBase.isBlank()) return null;
        return baseOperacionalApi.buscarIdPorNome(nomeBase.trim()).orElse(null);
    }

    private UUID resolverMunicipio(String nomeMunicipio) {
        if (nomeMunicipio == null || nomeMunicipio.isBlank()) return null;
        return municipioApi.buscarIdPorNome(nomeMunicipio.trim()).orElse(null);
    }

    private UUID resolverCentroCusto(String nomeCentro) {
        if (nomeCentro == null || nomeCentro.isBlank()) return null;
        return centroFaturamentoApi.buscarIdPorNome(nomeCentro.trim()).orElse(null);
    }

    private Set<UUID> resolverEquipes(String codigosEquipes) {
        if (codigosEquipes == null || codigosEquipes.isBlank()) {
            return Collections.emptySet();
        }
        Set<UUID> ids = new HashSet<>();
        String[] partes = codigosEquipes.split("[,;/]");
        for (String cod : partes) {
            String c = cod.trim();
            if (!c.isBlank()) {
                equipeApi.buscarIdPorCodigo(c).ifPresent(ids::add);
            }
        }
        return ids;
    }

    private UUID resolverSupervisor(String nomeSupervisor) {
        if (nomeSupervisor == null || nomeSupervisor.isBlank()) return null;
        return supervisorApi.buscarIdPorNomeColaborador(nomeSupervisor.trim()).orElse(null);
    }

    private UUID resolverCoordenador(String nomeCoordenador) {
        if (nomeCoordenador == null || nomeCoordenador.isBlank()) return null;
        return coordenadorApi.buscarIdPorNomeColaborador(nomeCoordenador.trim()).orElse(null);
    }
}
