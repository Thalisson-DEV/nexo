package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.rh.BaseOperacionalApi;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.ResumoColaborador;
import com.nexo.app.modules.sst.application.dto.CriarExecucaoChecklistGovernancaRequest;
import com.nexo.app.modules.sst.application.dto.ExecucaoChecklistGovernancaFilter;
import com.nexo.app.modules.sst.application.dto.ExecucaoChecklistGovernancaResponse;
import com.nexo.app.modules.sst.application.dto.RespostaChecklistGovernancaRequest;
import com.nexo.app.modules.sst.application.dto.SalvarRespostasChecklistGovernancaRequest;
import com.nexo.app.modules.sst.domain.ConflitoGovernancaSstException;
import com.nexo.app.modules.sst.domain.ExecucaoChecklistGovernancaSst;
import com.nexo.app.modules.sst.domain.FormularioInspecao;
import com.nexo.app.modules.sst.domain.ModeloChecklistGovernancaSst;
import com.nexo.app.modules.sst.domain.OpcaoResposta;
import com.nexo.app.modules.sst.domain.Pergunta;
import com.nexo.app.modules.sst.domain.RespostaChecklistGovernancaSst;
import com.nexo.app.modules.sst.infrastructure.persistence.ExecucaoChecklistGovernancaSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.ExecucaoChecklistGovernancaSstSpecification;
import com.nexo.app.modules.sst.infrastructure.persistence.FormularioInspecaoRepository;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExecucaoChecklistGovernancaSstUseCases {

    private final ExecucaoChecklistGovernancaSstRepository repository;
    private final FormularioInspecaoRepository formularioRepository;
    private final ModeloChecklistGovernancaSstUseCases modeloUseCases;
    private final ColaboradorApi colaboradorApi;
    private final BaseOperacionalApi baseOperacionalApi;

    @Transactional
    public ExecucaoChecklistGovernancaResponse criar(
            CriarExecucaoChecklistGovernancaRequest request, UUID executorId) {
        if (request.clientRequestId() != null) {
            var existente = repository.findByIdCorpoAndClientRequestId(
                    request.idCorpo(), request.clientRequestId());
            if (existente.isPresent()) return montar(carregarDetalhada(existente.get().getId()));
        }
        ModeloChecklistGovernancaSst modelo = modeloUseCases.buscar(request.idModelo());
        if (!modelo.isAtivo()) {
            throw new InvarianteVioladaException("O modelo de checklist está inativo.");
        }
        if (!baseOperacionalApi.existeBase(request.idBaseOperacional())) {
            throw new RecursoNaoEncontradoException("Base operacional não encontrada ou inativa.");
        }
        if (!colaboradorApi.existeColaborador(executorId)) {
            throw new InvarianteVioladaException("O executor não está vinculado a colaborador ativo.");
        }
        FormularioInspecao formulario = buscarFormulario(modelo.getIdFormulario());
        formulario.validarEstrutura();
        ExecucaoChecklistGovernancaSst execucao = new ExecucaoChecklistGovernancaSst(
                modelo.getId(), formulario.getId(), request.idCorpo(), request.idBaseOperacional(),
                executorId, request.clientRequestId(), request.dataExecucao(), request.observacoes());
        return montar(repository.save(execucao));
    }

    @Transactional
    public ExecucaoChecklistGovernancaResponse salvarRespostas(
            UUID id, SalvarRespostasChecklistGovernancaRequest request) {
        ExecucaoChecklistGovernancaSst execucao = carregarDetalhada(id);
        conferirVersao(execucao, request.versaoEsperada());
        FormularioInspecao formulario = buscarFormulario(execucao.getIdFormulario());
        List<RespostaChecklistGovernancaSst.NovaResposta> respostas =
                montarRespostasConfiaveis(formulario, request.respostas());

        execucao.limparRespostas();
        repository.saveAndFlush(execucao);
        execucao.substituirRespostas(respostas);
        repository.saveAndFlush(execucao);
        return montar(carregarDetalhada(execucao.getId()));
    }

    @Transactional
    public ExecucaoChecklistGovernancaResponse concluir(UUID id, Long versaoEsperada) {
        ExecucaoChecklistGovernancaSst execucao = carregarDetalhada(id);
        conferirVersao(execucao, versaoEsperada);
        execucao.concluir(OffsetDateTime.now());
        repository.saveAndFlush(execucao);
        return montar(carregarDetalhada(id));
    }

    @Transactional(readOnly = true)
    public ExecucaoChecklistGovernancaResponse buscarPorId(UUID id) {
        return montar(carregarDetalhada(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<ExecucaoChecklistGovernancaResponse> listar(
            ExecucaoChecklistGovernancaFilter filtro, Pageable pageable) {
        return PageResponse.fromPage(repository.findAll(
                ExecucaoChecklistGovernancaSstSpecification.comFiltros(filtro), pageable)
                .map(this::montar));
    }

    ExecucaoChecklistGovernancaSst carregarDetalhada(UUID id) {
        return repository.findDetalhadaById(id)
                .filter(ExecucaoChecklistGovernancaSst::isAtivo)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Execução de checklist de governança não encontrada."));
    }

    private List<RespostaChecklistGovernancaSst.NovaResposta> montarRespostasConfiaveis(
            FormularioInspecao formulario, List<RespostaChecklistGovernancaRequest> recebidas) {
        Map<UUID, RespostaChecklistGovernancaRequest> porPergunta = new HashMap<>();
        if (recebidas != null) {
            for (RespostaChecklistGovernancaRequest recebida : recebidas) {
                if (porPergunta.put(recebida.idPergunta(), recebida) != null) {
                    throw new InvarianteVioladaException("Uma pergunta foi respondida mais de uma vez.");
                }
            }
        }

        List<Pergunta> perguntas = formulario.getGrupos().stream()
                .flatMap(grupo -> grupo.getPerguntas().stream()).toList();
        Set<UUID> idsValidos = perguntas.stream().map(Pergunta::getId).collect(Collectors.toSet());
        if (!idsValidos.containsAll(porPergunta.keySet())) {
            throw new InvarianteVioladaException("Há respostas para perguntas que não pertencem ao formulário.");
        }

        List<RespostaChecklistGovernancaSst.NovaResposta> resultado = new ArrayList<>();
        for (Pergunta pergunta : perguntas) {
            RespostaChecklistGovernancaRequest recebida = porPergunta.get(pergunta.getId());
            List<RespostaChecklistGovernancaSst.NovaOpcao> opcoes = recebida == null
                    ? List.of() : resolverOpcoes(pergunta, recebida.idsOpcoes());
            resultado.add(new RespostaChecklistGovernancaSst.NovaResposta(
                    pergunta.getId(), pergunta.getEnunciado(), pergunta.getTipoResposta(),
                    pergunta.isObrigatoria(), recebida == null ? null : recebida.valorTexto(),
                    recebida == null ? null : recebida.valorNumero(),
                    recebida == null ? null : recebida.valorData(), opcoes));
        }
        return resultado;
    }

    private List<RespostaChecklistGovernancaSst.NovaOpcao> resolverOpcoes(
            Pergunta pergunta, List<UUID> idsSelecionados) {
        if (idsSelecionados == null || idsSelecionados.isEmpty()) return List.of();
        if (!pergunta.getTipoResposta().permiteOpcoes()) {
            throw new InvarianteVioladaException("A pergunta informada não aceita opções.");
        }
        Set<UUID> unicos = new HashSet<>(idsSelecionados);
        if (unicos.size() != idsSelecionados.size()) {
            throw new InvarianteVioladaException("Uma opção foi selecionada mais de uma vez.");
        }
        if (pergunta.getTipoResposta() == com.nexo.app.modules.sst.domain.TipoResposta.MULTIPLA_ESCOLHA
                && idsSelecionados.size() > 1) {
            throw new InvarianteVioladaException("A pergunta permite selecionar somente uma opção.");
        }
        Map<UUID, OpcaoResposta> cadastradas = pergunta.getOpcoes().stream()
                .collect(Collectors.toMap(OpcaoResposta::getId, Function.identity()));
        List<RespostaChecklistGovernancaSst.NovaOpcao> resultado = new ArrayList<>();
        for (UUID idOpcao : idsSelecionados) {
            OpcaoResposta opcao = cadastradas.get(idOpcao);
            if (opcao == null) {
                throw new InvarianteVioladaException("Uma opção não pertence à pergunta informada.");
            }
            resultado.add(new RespostaChecklistGovernancaSst.NovaOpcao(
                    opcao.getId(), opcao.getTexto(), opcao.isGeraDesvio(), opcao.getGravidade()));
        }
        return resultado;
    }

    private FormularioInspecao buscarFormulario(UUID id) {
        return formularioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Formulário do checklist não encontrado."));
    }

    private static void conferirVersao(ExecucaoChecklistGovernancaSst execucao, Long esperada) {
        if (esperada == null || !Objects.equals(execucao.getVersao(), esperada)) {
            throw new ConflitoGovernancaSstException(
                    "O checklist foi alterado por outra pessoa. Atualize a tela antes de continuar.");
        }
    }

    private ExecucaoChecklistGovernancaResponse montar(ExecucaoChecklistGovernancaSst execucao) {
        ModeloChecklistGovernancaSst modelo = modeloUseCases.buscar(execucao.getIdModelo());
        ResumoColaborador executor = colaboradorApi.buscarResumosPorIds(List.of(execucao.getIdExecutor()))
                .get(execucao.getIdExecutor());
        var respostas = execucao.getRespostas().stream().map(resposta ->
                new ExecucaoChecklistGovernancaResponse.RespostaItem(
                        resposta.getId(), resposta.getIdPergunta(), resposta.getPerguntaSnapshot(),
                        resposta.getTipoResposta(), resposta.isObrigatoria(), resposta.getValorTexto(),
                        resposta.getValorNumero(), resposta.getValorData(), resposta.getOpcoes().stream()
                        .map(opcao -> new ExecucaoChecklistGovernancaResponse.OpcaoItem(
                                opcao.getIdOpcao(), opcao.getOpcaoSnapshot(), opcao.isNaoConforme(),
                                opcao.getGravidade())).toList())).toList();
        var constatacoes = execucao.getConstatacoes().stream().map(item ->
                new ExecucaoChecklistGovernancaResponse.ConstatacaoItem(
                        item.getId(), item.getPerguntaSnapshot(), item.getOpcaoSnapshot(),
                        item.getGravidade(), item.getDescricao(), item.getIdAcao())).toList();
        return new ExecucaoChecklistGovernancaResponse(
                execucao.getId(), execucao.getIdModelo(), modelo.getNome(), execucao.getIdFormulario(),
                execucao.getIdCorpo(), execucao.getIdBaseOperacional(), execucao.getIdExecutor(),
                executor == null ? null : executor.nomeCompleto(), execucao.getDataExecucao(),
                execucao.getStatus(), execucao.getObservacoes(), execucao.getDataConclusao(),
                respostas, constatacoes, execucao.getVersao(), execucao.getDataCriacao());
    }
}
