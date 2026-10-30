package com.nexo.app.modules.contrato.application.usecases;

import com.nexo.app.modules.contrato.AcaoResponse;
import com.nexo.app.modules.contrato.ContratoApi;
import com.nexo.app.modules.contrato.application.dto.ContratoFilter;
import com.nexo.app.modules.contrato.application.dto.ContratoRequest;
import com.nexo.app.modules.contrato.application.dto.ContratoResponse;
import com.nexo.app.modules.contrato.application.mappers.AcaoMapper;
import com.nexo.app.modules.contrato.application.mappers.ContratoMapper;
import com.nexo.app.modules.contrato.domain.AcaoValor;
import com.nexo.app.modules.contrato.domain.Contrato;
import com.nexo.app.modules.contrato.infrastructure.persistence.*;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContratoUseCases implements ContratoApi {

    private final ContratoRepository repository;
    private final AcaoValorRepository acaoValorRepository;
    private final AcaoRepository acaoRepository;
    private final ContratoMapper mapper;
    private final AcaoMapper acaoMapper;

    @Transactional
    public ContratoResponse criar(ContratoRequest request) {
        if (repository.existsByNumeroContratoIgnoreCase(request.numeroContrato())) {
            throw new InvarianteVioladaException("Já existe um contrato com este número: " + request.numeroContrato());
        }

        Contrato contrato = new Contrato(
                request.numeroContrato(),
                request.cliente(),
                request.dataVigencia(),
                request.dataEncerramento(),
                request.coeficienteFaturamento()
        );

        return mapper.toResponse(repository.save(contrato));
    }

    @Transactional
    public ContratoResponse atualizar(UUID id, ContratoRequest request) {
        Contrato contrato = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Contrato não encontrado"));

        repository.findByNumeroContratoIgnoreCase(request.numeroContrato())
                .ifPresent(existente -> {
                    if (!existente.getId().equals(id)) {
                        throw new InvarianteVioladaException("Já existe outro contrato com este número: " + request.numeroContrato());
                    }
                });

        contrato.atualizarDados(request.numeroContrato(), request.cliente());
        contrato.atualizarVigencia(request.dataVigencia(), request.dataEncerramento());
        contrato.atualizarCoeficiente(request.coeficienteFaturamento());

        return mapper.toResponse(repository.save(contrato));
    }

    @Transactional
    public void ativar(UUID id) {
        Contrato contrato = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Contrato não encontrado"));

        contrato.ativar();
        repository.save(contrato);
    }

    @Transactional
    public void desativar(UUID id) {
        Contrato contrato = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Contrato não encontrado"));
        contrato.desativar();
        repository.save(contrato);
    }

    @Transactional(readOnly = true)
    public ContratoResponse buscarPorId(UUID id) {
        Contrato contrato = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Contrato não encontrado"));
        return mapper.toResponse(contrato);
    }

    @Transactional(readOnly = true)
    public PageResponse<ContratoResponse> listar(ContratoFilter filter, Pageable pageable) {
        Specification<Contrato> spec = ContratoSpecification.filterBy(filter);
        return PageResponse.fromPage(
                repository.findAll(spec, pageable).map(mapper::toResponse)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal calcularValorFaturado(List<UUID> idAcoes, UUID idContrato, LocalDate dataExecucao) {
        if (idAcoes == null || idAcoes.isEmpty()) {
            return BigDecimal.ZERO;
        }
        if (idContrato == null) {
            throw new InvarianteVioladaException("Equipe não possui contrato vinculado para faturamento");
        }

        Contrato contrato = repository
                .findByIdAndAtivoTrueAndDataVigenciaLessThanEqualAndDataEncerramentoGreaterThanEqual(
                        idContrato, dataExecucao, dataExecucao)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Contrato inativo ou fora da vigência: " + idContrato));

        BigDecimal coeficiente = contrato.getCoeficienteFaturamento();
        BigDecimal total = BigDecimal.ZERO;

        for (UUID idAcao : idAcoes) {
            BigDecimal valorBase = acaoValorRepository
                    .findFirstByAcaoIdAndContratoIdAndDataVigenciaLessThanEqualOrderByDataVigenciaDesc(
                            idAcao, contrato.getId(), dataExecucao)
                    .map(AcaoValor::getValorBase)
                    .orElseThrow(() -> new RecursoNaoEncontradoException(
                            "Valor não definido para a ação " + idAcao + " no contrato " + contrato.getNumeroContrato()));

            total = total.add(valorBase.multiply(coeficiente));
        }

        return total;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AcaoValorItem> listarAcoesComValor(UUID idContrato, LocalDate dataReferencia) {
        if (idContrato == null) {
            return List.of();
        }
        LocalDate dataRef = dataReferencia != null ? dataReferencia : LocalDate.now();
        Contrato contrato = repository.findById(idContrato)
                .filter(Contrato::isAtivo)
                .orElse(null);
        if (contrato == null) {
            return List.of();
        }

        if (contrato.getDataVigencia() != null && dataRef.isBefore(contrato.getDataVigencia())) {
            return List.of();
        }
        if (contrato.getDataEncerramento() != null && dataRef.isAfter(contrato.getDataEncerramento())) {
            return List.of();
        }

        BigDecimal coeficiente = contrato.getCoeficienteFaturamento() != null
                ? contrato.getCoeficienteFaturamento()
                : BigDecimal.ONE;

        List<AcaoValor> valores = acaoValorRepository.findValoresVigentesPorContrato(idContrato, dataRef);

        Map<UUID, AcaoValorItem> acoesUnicas = new LinkedHashMap<>();
        for (AcaoValor av : valores) {
            if (av.getAcao() != null && av.getAcao().isAtivo() && !acoesUnicas.containsKey(av.getAcao().getId())) {
                BigDecimal valorFinal = av.getValorBase().multiply(coeficiente).setScale(2, RoundingMode.HALF_UP);
                acoesUnicas.put(av.getAcao().getId(), new AcaoValorItem(
                        av.getAcao().getId(),
                        av.getAcao().getCodigo(),
                        av.getAcao().getNome(),
                        valorFinal
                ));
            }
        }
        return new ArrayList<>(acoesUnicas.values());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existeContratoAtivoNaData(UUID idContrato, LocalDate data) {
        if (idContrato == null || data == null) {
            return false;
        }
        return repository
                .findByIdAndAtivoTrueAndDataVigenciaLessThanEqualAndDataEncerramentoGreaterThanEqual(
                        idContrato, data, data)
                .isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AcaoResponse> buscarAcaoPorIdResponse(UUID id) {
        return acaoRepository.findById(id).map(acaoMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AcaoResponse> buscarAcoesPorIdsResponse(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return acaoMapper.toResponseList(acaoRepository.findAllById(ids));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existeAcao(UUID id) {
        return acaoRepository.existsByIdAndAtivoTrue(id);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existeContrato(UUID id) {
        return id != null && repository.findById(id).filter(Contrato::isAtivo).isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ContratoResumo> buscarContratoResumo(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return repository.findById(id)
                .map(c -> new ContratoResumo(c.getId(), c.getNumeroContrato(), c.getCliente(), c.isAtivo()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> buscarIdsPorTrecho(String trecho) {
        String termo = SpecificationUtils.normalizarTermo(trecho);
        return termo == null || termo.isBlank() ? List.of() : repository.buscarIdsPorTrecho(termo);
    }
}
