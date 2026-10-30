package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.obras.application.dto.ProjetoRelacionadoResponse;
import com.nexo.app.modules.obras.application.dto.VinculoProjetoRequest;
import com.nexo.app.modules.obras.domain.Obra;
import com.nexo.app.modules.obras.domain.RelacionamentoProjeto;
import com.nexo.app.modules.obras.infrastructure.persistence.ObraRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.RelacionamentoProjetoRepository;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RelacionamentoProjetoUseCases {

    private final RelacionamentoProjetoRepository repository;
    private final ObraRepository obraRepository;

    @Transactional(readOnly = true)
    public List<ProjetoRelacionadoResponse> listar(UUID idProjeto) {
        exigirProjetoExistente(idProjeto);
        return listarSemValidarExistencia(idProjeto);
    }

    @Transactional(readOnly = true)
    public List<ProjetoRelacionadoResponse> listarSemValidarExistencia(UUID idProjeto) {
        if (idProjeto == null) {
            return List.of();
        }
        return repository.findAtivosPorProjeto(idProjeto).stream()
                .map(relacionamento -> mapear(relacionamento, idProjeto))
                .toList();
    }

    @Transactional(readOnly = true)
    public long contarAtivos(UUID idProjeto) {
        return idProjeto == null ? 0 : repository.countAtivosPorProjeto(idProjeto);
    }

    @Transactional
    public ProjetoRelacionadoResponse vincular(UUID idProjeto, VinculoProjetoRequest request) {
        Obra projeto = buscarProjetoAtivo(idProjeto);
        Obra relacionado = buscarProjetoAtivo(request.idProjetoRelacionado());
        if (idProjeto.equals(request.idProjetoRelacionado())) {
            throw new InvarianteVioladaException("Um projeto não pode ser relacionado a si mesmo.");
        }

        UUID idA = menorId(idProjeto, request.idProjetoRelacionado());
        UUID idB = idA.equals(idProjeto) ? request.idProjetoRelacionado() : idProjeto;
        RelacionamentoProjeto relacionamento = repository.findByProjetoAIdAndProjetoBId(idA, idB)
                .map(existente -> reativarOuRejeitar(existente, request))
                .orElseGet(() -> new RelacionamentoProjeto(projeto, relacionado, request.tipoVinculo()));

        return mapear(repository.save(relacionamento), idProjeto);
    }

    @Transactional
    public ProjetoRelacionadoResponse garantirVinculoLegado(UUID idProjeto, VinculoProjetoRequest request) {
        Obra projeto = buscarProjetoAtivo(idProjeto);
        Obra relacionado = buscarProjetoAtivo(request.idProjetoRelacionado());
        if (idProjeto.equals(request.idProjetoRelacionado())) {
            throw new InvarianteVioladaException("Um projeto não pode ser relacionado a si mesmo.");
        }

        UUID idA = menorId(idProjeto, request.idProjetoRelacionado());
        UUID idB = idA.equals(idProjeto) ? request.idProjetoRelacionado() : idProjeto;
        RelacionamentoProjeto relacionamento = repository.findByProjetoAIdAndProjetoBId(idA, idB)
                .map(existente -> {
                    existente.alterarTipo(request.tipoVinculo());
                    existente.ativar();
                    return existente;
                })
                .orElseGet(() -> new RelacionamentoProjeto(projeto, relacionado, request.tipoVinculo()));
        return mapear(repository.save(relacionamento), idProjeto);
    }

    @Transactional
    public void desvincular(UUID idProjeto, UUID idProjetoRelacionado) {
        exigirProjetoExistente(idProjeto);
        UUID idA = menorId(idProjeto, idProjetoRelacionado);
        UUID idB = idA.equals(idProjeto) ? idProjetoRelacionado : idProjeto;
        RelacionamentoProjeto relacionamento = repository.findByProjetoAIdAndProjetoBId(idA, idB)
                .filter(RelacionamentoProjeto::isAtivo)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Relacionamento ativo não encontrado entre os projetos informados."));
        relacionamento.desativar();
        repository.save(relacionamento);
    }

    private RelacionamentoProjeto reativarOuRejeitar(RelacionamentoProjeto relacionamento,
                                                      VinculoProjetoRequest request) {
        if (relacionamento.isAtivo()) {
            throw new InvarianteVioladaException("Os projetos informados já estão relacionados.");
        }
        relacionamento.alterarTipo(request.tipoVinculo());
        relacionamento.ativar();
        return relacionamento;
    }

    private ProjetoRelacionadoResponse mapear(RelacionamentoProjeto relacionamento, UUID idProjetoOrigem) {
        Obra relacionado = relacionamento.projetoRelacionadoA(idProjetoOrigem);
        return new ProjetoRelacionadoResponse(
                relacionamento.getId(),
                relacionado.getId(),
                relacionado.getNumero(),
                relacionado.getTitulo(),
                relacionado.getTipoObra(),
                relacionamento.getTipo(),
                relacionado.getNotaObra(),
                relacionado.getValorProjeto(),
                relacionado.getDataInicio(),
                relacionado.getDataPrevisaoFim(),
                relacionado.getDataFimEfetiva(),
                relacionado.getStatusObra() != null ? relacionado.getStatusObra().getId() : null,
                relacionado.getStatusObra() != null ? relacionado.getStatusObra().getNome() : null,
                relacionado.isAtivo()
        );
    }

    private Obra buscarProjetoAtivo(UUID idProjeto) {
        Obra obra = obraRepository.findById(idProjeto)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Projeto não encontrado com o ID: " + idProjeto));
        if (!obra.isAtivo()) {
            throw new InvarianteVioladaException("O projeto informado está desativado.");
        }
        return obra;
    }

    private void exigirProjetoExistente(UUID idProjeto) {
        if (idProjeto == null || !obraRepository.existsById(idProjeto)) {
            throw new RecursoNaoEncontradoException("Projeto não encontrado com o ID: " + idProjeto);
        }
    }

    private UUID menorId(UUID primeiro, UUID segundo) {
        if (primeiro == null || segundo == null) {
            throw new InvarianteVioladaException("Os dois projetos do relacionamento são obrigatórios.");
        }
        return primeiro.toString().compareTo(segundo.toString()) < 0 ? primeiro : segundo;
    }
}
