package com.nexo.app.modules.frota.application.usecases;

import com.nexo.app.modules.frota.application.dto.CondutorFilter;
import com.nexo.app.modules.frota.application.dto.CondutorRequest;
import com.nexo.app.modules.frota.application.dto.CondutorResponse;
import com.nexo.app.modules.frota.domain.Condutor;
import com.nexo.app.modules.frota.domain.FichaVeiculo;
import com.nexo.app.modules.frota.domain.SituacaoVencimento;
import com.nexo.app.modules.frota.domain.VinculoCondutor;
import com.nexo.app.modules.frota.infrastructure.persistence.CondutorRepository;
import com.nexo.app.modules.frota.infrastructure.persistence.CondutorSpecification;
import com.nexo.app.modules.frota.infrastructure.persistence.FichaVeiculoRepository;
import com.nexo.app.modules.rh.ResumoColaborador;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import com.nexo.app.shared.infrastructure.security.HashUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Ficha de condutor (CNH) dos colaboradores que dirigem veículos da frota. */
@Service
@RequiredArgsConstructor
public class CondutorUseCases {

    private final CondutorRepository repository;
    private final FichaVeiculoRepository fichaRepository;
    private final VinculoCondutorUseCases vinculos;
    private final ParametrosFrotaUseCases parametros;
    private final ReferenciasFrota referencias;

    @Transactional(readOnly = true)
    public PageResponse<CondutorResponse> listar(CondutorFilter filtro, Pageable pageable) {
        LocalDate hoje = LocalDate.now();
        int diasAviso = parametros.diasAviso();
        List<UUID> porNome = filtro != null && filtro.busca() != null && !filtro.busca().isBlank()
                ? referencias.idsColaboradoresPorNome(filtro.busca()) : null;
        List<UUID> porBase = filtro != null && !filtro.idsBase().isEmpty()
                ? referencias.idsColaboradoresPorBases(filtro.idsBase()) : null;
        Page<Condutor> pagina = repository.findAll(
                CondutorSpecification.of(filtro, porNome, porBase, hoje, diasAviso), pageable);
        return PageResponse.fromPage(new PageImpl<>(montar(pagina.getContent(), hoje, diasAviso),
                pagina.getPageable(), pagina.getTotalElements()));
    }

    @Transactional(readOnly = true)
    public CondutorResponse buscarPorColaborador(UUID idColaborador) {
        Condutor condutor = repository.findByIdColaborador(idColaborador)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Este colaborador ainda não tem ficha de condutor."));
        return montar(List.of(condutor), LocalDate.now(), parametros.diasAviso()).get(0);
    }

    /** Cria ou atualiza a CNH do colaborador. */
    @Transactional
    public CondutorResponse registrarCnh(UUID idColaborador, CondutorRequest request) {
        if (!referencias.colaboradorAtivo(idColaborador)) {
            throw new InvarianteVioladaException("Colaborador não encontrado ou desligado.");
        }
        String numero = request.cnhNumero() == null ? null : request.cnhNumero().replaceAll("[^0-9]", "");
        if (numero != null && repository.existsByCnhNumeroHashAndIdColaboradorNot(
                HashUtils.gerarBlindIndex(numero), idColaborador)) {
            throw new InvarianteVioladaException("Esta CNH já está registrada para outro colaborador.");
        }
        Condutor condutor = repository.findByIdColaborador(idColaborador).orElseGet(() -> new Condutor(idColaborador));
        condutor.registrarCnh(numero, request.cnhCategoria(), request.cnhValidade(),
                request.cnhPrimeiraHabilitacao(), request.observacao());
        Condutor salvo = repository.save(condutor);
        return montar(List.of(salvo), LocalDate.now(), parametros.diasAviso()).get(0);
    }

    /** Retira o colaborador da lista de condutores. Não apaga o histórico de vínculos. */
    @Transactional
    public void desativar(UUID idColaborador) {
        Condutor condutor = repository.findByIdColaborador(idColaborador)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ficha de condutor não encontrada."));
        if (vinculos.abertosPorCondutor(List.of(idColaborador)).containsKey(idColaborador)) {
            throw new InvarianteVioladaException("Encerre o vínculo do condutor com o veículo antes de desativá-lo.");
        }
        condutor.desativar();
        repository.save(condutor);
    }

    private List<CondutorResponse> montar(List<Condutor> condutores, LocalDate hoje, int diasAviso) {
        Collection<UUID> idsColaborador = condutores.stream().map(Condutor::getIdColaborador).toList();
        Map<UUID, ResumoColaborador> pessoas = referencias.colaboradores(idsColaborador);
        Map<UUID, VinculoCondutor> abertos = vinculos.abertosPorCondutor(idsColaborador);
        Map<UUID, String> placas = abertos.isEmpty() ? Map.of()
                : fichaRepository.findAllByIdVeiculoIn(abertos.values().stream().map(VinculoCondutor::getIdVeiculo).toList())
                        .stream().collect(Collectors.toMap(FichaVeiculo::getIdVeiculo, FichaVeiculo::getPlaca));
        return condutores.stream().map(c -> {
            var pessoa = pessoas.get(c.getIdColaborador());
            var aberto = abertos.get(c.getIdColaborador());
            return new CondutorResponse(
                    c.getId(),
                    c.getIdColaborador(),
                    pessoa == null ? null : pessoa.nomeCompleto(),
                    pessoa == null ? null : pessoa.funcao(),
                    pessoa == null ? null : pessoa.baseOperacional(),
                    c.cnhNumeroMascarado(),
                    c.getCnhCategoria(),
                    c.getCnhValidade(),
                    c.getCnhPrimeiraHabilitacao(),
                    SituacaoVencimento.calcular(c.getCnhValidade(), hoje, diasAviso),
                    c.getCnhValidade() == null ? null : ChronoUnit.DAYS.between(hoje, c.getCnhValidade()),
                    aberto == null ? null : aberto.getIdVeiculo(),
                    aberto == null ? null : placas.get(aberto.getIdVeiculo()),
                    aberto == null ? null : aberto.getInicio(),
                    c.getObservacao(),
                    c.isAtivo());
        }).toList();
    }
}
