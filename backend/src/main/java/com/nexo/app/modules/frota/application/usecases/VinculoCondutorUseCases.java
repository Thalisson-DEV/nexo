package com.nexo.app.modules.frota.application.usecases;

import com.nexo.app.modules.frota.FrotaCondutorApi;
import com.nexo.app.modules.frota.application.dto.VincularCondutorRequest;
import com.nexo.app.modules.frota.application.dto.VinculoCondutorResponse;
import com.nexo.app.modules.frota.domain.Condutor;
import com.nexo.app.modules.frota.domain.FichaVeiculo;
import com.nexo.app.modules.frota.domain.OrigemVinculoCondutor;
import com.nexo.app.modules.frota.domain.SituacaoOperacional;
import com.nexo.app.modules.frota.domain.VinculoCondutor;
import com.nexo.app.modules.frota.infrastructure.persistence.CondutorRepository;
import com.nexo.app.modules.frota.infrastructure.persistence.FichaVeiculoRepository;
import com.nexo.app.modules.frota.infrastructure.persistence.VinculoCondutorRepository;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Histórico de quem conduziu cada veículo. Um condutor aberto por veículo e um veículo
 * aberto por condutor; trocar encerra o anterior no instante da troca.
 */
@Service
@RequiredArgsConstructor
public class VinculoCondutorUseCases implements FrotaCondutorApi {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private final VinculoCondutorRepository repository;
    private final FichaVeiculoRepository fichaRepository;
    private final CondutorRepository condutorRepository;
    private final ReferenciasFrota referencias;

    @Transactional
    public VinculoCondutorResponse vincular(UUID idVeiculo, VincularCondutorRequest request) {
        VinculoCondutor vinculo = vincular(idVeiculo, request.idColaborador(), request.inicio(),
                OrigemVinculoCondutor.MANUAL, null, request.observacao());
        return montar(List.of(vinculo)).get(0);
    }

    /**
     * Abre o vínculo, encerrando no mesmo instante o vínculo aberto do veículo e o do
     * condutor em outro veículo. Repetir o vínculo já aberto devolve o existente.
     */
    VinculoCondutor vincular(UUID idVeiculo, UUID idColaborador, OffsetDateTime inicio,
                             OrigemVinculoCondutor origem, UUID idOrigem, String observacao) {
        FichaVeiculo ficha = fichaRepository.findByIdVeiculo(idVeiculo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Veículo não encontrado na frota."));
        if (!ficha.isAtivo() || ficha.getSituacao() == SituacaoOperacional.BAIXADO) {
            throw new InvarianteVioladaException("O veículo " + ficha.getPlaca() + " não está disponível para condução.");
        }
        if (!referencias.colaboradorAtivo(idColaborador)) {
            throw new InvarianteVioladaException("Colaborador não encontrado ou desligado.");
        }
        if (inicio == null) {
            throw new InvarianteVioladaException("Informe a data e hora de início.");
        }
        if (origem != OrigemVinculoCondutor.IMPORTACAO) {
            Condutor condutor = condutorRepository.findByIdColaborador(idColaborador)
                    .filter(Condutor::isAtivo)
                    .orElseThrow(() -> new InvarianteVioladaException(
                            "Cadastre a CNH do colaborador na tela de condutores antes de vinculá-lo a um veículo."));
            var exigida = ficha.getTipoVeiculo() == null ? null : ficha.getTipoVeiculo().getCategoriaCnhMinima();
            condutor.exigirHabilitadoPara(exigida, inicio.atZoneSameInstant(FUSO).toLocalDate());
        }

        Optional<VinculoCondutor> abertoVeiculo = repository.findByIdVeiculoAndFimIsNullAndAtivoTrue(idVeiculo);
        if (abertoVeiculo.isPresent() && abertoVeiculo.get().getIdColaborador().equals(idColaborador)) {
            return abertoVeiculo.get();
        }
        if (repository.existeEncerradoDepois(idVeiculo, idColaborador, inicio)) {
            throw new InvarianteVioladaException(
                    "Já existe um período encerrado depois deste início para o veículo ou para o condutor. "
                            + "Os períodos não podem se sobrepor.");
        }
        abertoVeiculo.ifPresent(v -> encerrarAntesDe(v, inicio));
        repository.findByIdColaboradorAndFimIsNullAndAtivoTrue(idColaborador)
                .ifPresent(v -> encerrarAntesDe(v, inicio));

        return repository.save(new VinculoCondutor(idVeiculo, idColaborador, inicio, origem, idOrigem, observacao));
    }

    @Transactional
    public VinculoCondutorResponse encerrar(UUID idVinculo, OffsetDateTime fim) {
        VinculoCondutor vinculo = repository.findById(idVinculo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Vínculo não encontrado."));
        vinculo.encerrar(fim);
        return montar(List.of(repository.save(vinculo))).get(0);
    }

    @Transactional(readOnly = true)
    public PageResponse<VinculoCondutorResponse> historicoDoVeiculo(UUID idVeiculo, Pageable pageable) {
        return pagina(repository.findAllByIdVeiculoAndAtivoTrueOrderByInicioDesc(idVeiculo, pageable));
    }

    @Transactional(readOnly = true)
    public PageResponse<VinculoCondutorResponse> historicoDoCondutor(UUID idColaborador, Pageable pageable) {
        return pagina(repository.findAllByIdColaboradorAndAtivoTrueOrderByInicioDesc(idColaborador, pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> condutorEm(UUID idVeiculo, OffsetDateTime instante) {
        return repository.buscarVigente(idVeiculo, instante).map(VinculoCondutor::getIdColaborador);
    }

    @Transactional(readOnly = true)
    public Optional<VinculoCondutorResponse> vinculoEm(UUID idVeiculo, OffsetDateTime instante) {
        return repository.buscarVigente(idVeiculo, instante).map(v -> montar(List.of(v)).get(0));
    }

    Map<UUID, VinculoCondutor> abertosPorVeiculo(Collection<UUID> idsVeiculo) {
        if (idsVeiculo.isEmpty()) return Map.of();
        return repository.findAllByIdVeiculoInAndFimIsNullAndAtivoTrue(idsVeiculo).stream()
                .collect(Collectors.toMap(VinculoCondutor::getIdVeiculo, v -> v, (a, b) -> a));
    }

    Map<UUID, VinculoCondutor> abertosPorCondutor(Collection<UUID> idsColaborador) {
        if (idsColaborador.isEmpty()) return Map.of();
        return repository.findAllByIdColaboradorInAndFimIsNullAndAtivoTrue(idsColaborador).stream()
                .collect(Collectors.toMap(VinculoCondutor::getIdColaborador, v -> v, (a, b) -> a));
    }

    List<VinculoCondutorResponse> montar(List<VinculoCondutor> vinculos) {
        var idsVeiculo = vinculos.stream().map(VinculoCondutor::getIdVeiculo).collect(Collectors.toSet());
        Map<UUID, String> placas = idsVeiculo.isEmpty() ? Map.of()
                : fichaRepository.findAllByIdVeiculoIn(idsVeiculo).stream()
                        .collect(Collectors.toMap(FichaVeiculo::getIdVeiculo, FichaVeiculo::getPlaca));
        Map<UUID, String> nomes = referencias.nomesColaboradores(
                vinculos.stream().map(VinculoCondutor::getIdColaborador).toList());
        return vinculos.stream().map(v -> new VinculoCondutorResponse(
                v.getId(), v.getIdVeiculo(), placas.get(v.getIdVeiculo()), v.getIdColaborador(),
                nomes.get(v.getIdColaborador()), v.getInicio(), v.getFim(), v.getOrigem(), v.getObservacao()
        )).toList();
    }

    /**
     * Encerra e grava já: o índice único parcial de vínculo aberto seria violado se o
     * novo vínculo fosse inserido antes deste UPDATE chegar ao banco.
     */
    private void encerrarAntesDe(VinculoCondutor aberto, OffsetDateTime inicioNovo) {
        if (inicioNovo.isBefore(aberto.getInicio())) {
            throw new InvarianteVioladaException(
                    "O novo vínculo não pode começar antes do vínculo atual (iniciado em "
                            + aberto.getInicio().atZoneSameInstant(FUSO).toLocalDateTime() + ").");
        }
        aberto.encerrar(inicioNovo);
        repository.saveAndFlush(aberto);
    }

    private PageResponse<VinculoCondutorResponse> pagina(Page<VinculoCondutor> pagina) {
        return PageResponse.fromPage(new org.springframework.data.domain.PageImpl<>(
                montar(pagina.getContent()), pagina.getPageable(), pagina.getTotalElements()));
    }
}
