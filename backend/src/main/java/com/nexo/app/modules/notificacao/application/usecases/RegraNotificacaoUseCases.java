package com.nexo.app.modules.notificacao.application.usecases;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexo.app.modules.notificacao.application.dto.GatilhoResponse;
import com.nexo.app.modules.notificacao.application.dto.RegraNotificacaoFilter;
import com.nexo.app.modules.notificacao.application.dto.RegraNotificacaoRequest;
import com.nexo.app.modules.notificacao.application.dto.RegraNotificacaoResponse;
import com.nexo.app.modules.notificacao.application.mappers.RegraNotificacaoMapper;
import com.nexo.app.modules.notificacao.domain.GatilhoNotificacao;
import com.nexo.app.modules.notificacao.domain.RegraNotificacao;
import com.nexo.app.modules.notificacao.domain.TipoDestinatarioRegra;
import com.nexo.app.modules.notificacao.infrastructure.persistence.PoliticaRoteamentoNotificacaoRepository;
import com.nexo.app.modules.notificacao.infrastructure.persistence.RegraNotificacaoRepository;
import com.nexo.app.modules.notificacao.infrastructure.persistence.RegraNotificacaoSpecification;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;

import lombok.RequiredArgsConstructor;

/**
 * Administração das regras.
 *
 * <p>Não usa cache: o motor lê as regras por gatilho a cada evento, e uma regra
 * desligada às pressas — porque está notificando demais — precisa parar na hora, não
 * quando a entrada expirar.</p>
 */
@Service
@RequiredArgsConstructor
public class RegraNotificacaoUseCases {

    private final RegraNotificacaoRepository repository;
    private final PoliticaRoteamentoNotificacaoRepository politicaRepository;
    private final RegraNotificacaoMapper mapper;

    /** O catálogo que a tela de criação de regras consome para se montar. */
    @Transactional(readOnly = true)
    public List<GatilhoResponse> listarGatilhos() {
        return Arrays.stream(GatilhoNotificacao.values())
                .map(gatilho -> new GatilhoResponse(
                        gatilho.getChave(), gatilho.getNome(), gatilho.getDescricao(),
                        gatilho.getModo(), gatilho.getModo().getRotulo(),
                        gatilho.getCategoriaPadrao(), gatilho.getVariaveis(), gatilho.getPapeis()))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<RegraNotificacaoResponse> listar(RegraNotificacaoFilter filtro, Pageable pageable) {
        return PageResponse.fromPage(repository
                .findAll(RegraNotificacaoSpecification.comFiltros(filtro), pageable)
                .map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    public RegraNotificacaoResponse buscar(UUID id) {
        return mapper.toResponse(buscarEntity(id));
    }

    @Transactional
    public RegraNotificacaoResponse criar(RegraNotificacaoRequest request) {
        GatilhoNotificacao gatilho = exigirGatilho(request.chaveGatilho());
        exigirNomeUnico(request.nome(), null);

        RegraNotificacao regra = new RegraNotificacao(
                gatilho, request.nome(), request.descricao(), request.categoria(),
                request.prioridade(), request.tituloTemplate(), request.corpoTemplate(),
                request.deeplinkTemplate(), request.limiar(), request.cooldownMinutos(),
                request.enviarEmail(), false);

        aplicarCondicoes(regra, request);
        aplicarDestinatarios(regra, request);
        validarPoliticas(regra);
        regra.exigirUtilizavel();

        return mapper.toResponse(repository.save(regra));
    }

    /**
     * O gatilho não é alterável: trocá-lo invalidaria de uma vez as condições, os
     * papéis e os textos, que foram validados contra ele. Quem precisa de outro
     * gatilho cria outra regra.
     */
    @Transactional
    public RegraNotificacaoResponse atualizar(UUID id, RegraNotificacaoRequest request) {
        RegraNotificacao regra = buscarEntity(id);
        if (!regra.getChaveGatilho().equals(request.chaveGatilho())) {
            throw new InvarianteVioladaException(
                    "O gatilho de uma regra não pode ser trocado. Crie uma regra nova para outro gatilho.");
        }
        exigirNomeUnico(request.nome(), id);

        regra.atualizar(request.nome(), request.descricao(), request.categoria(),
                request.prioridade(), request.tituloTemplate(), request.corpoTemplate(),
                request.deeplinkTemplate(), request.limiar(), request.cooldownMinutos(),
                request.enviarEmail());

        regra.limparCondicoes();
        regra.limparDestinatarios();
        aplicarCondicoes(regra, request);
        aplicarDestinatarios(regra, request);
        validarPoliticas(regra);
        regra.exigirUtilizavel();

        return mapper.toResponse(repository.save(regra));
    }

    @Transactional
    public RegraNotificacaoResponse ativar(UUID id) {
        RegraNotificacao regra = buscarEntity(id);
        validarPoliticas(regra);
        regra.exigirUtilizavel();
        regra.ativar();
        return mapper.toResponse(repository.save(regra));
    }

    /**
     * Desligar vale para qualquer regra, inclusive as de fábrica — é a válvula de
     * escape quando uma delas está incomodando.
     */
    @Transactional
    public RegraNotificacaoResponse desativar(UUID id) {
        RegraNotificacao regra = buscarEntity(id);
        regra.desativar();
        return mapper.toResponse(repository.save(regra));
    }

    /**
     * Remover é diferente de desligar, e regra de fábrica não se remove: apagar a
     * notificação de aprovação pendente passaria despercebido até a fila estourar.
     */
    @Transactional
    public void remover(UUID id) {
        RegraNotificacao regra = buscarEntity(id);
        if (regra.isSistema()) {
            throw new InvarianteVioladaException(
                    "Regras de fábrica não podem ser removidas. Desative-a se não quiser mais recebê-la.");
        }
        repository.delete(regra);
    }

    public RegraNotificacao buscarEntity(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Regra de notificação não encontrada."));
    }

    private void aplicarCondicoes(RegraNotificacao regra, RegraNotificacaoRequest request) {
        if (request.condicoes() == null) {
            return;
        }
        request.condicoes().forEach(condicao ->
                regra.adicionarCondicao(condicao.campo(), condicao.operador(), condicao.valor()));
    }

    private void aplicarDestinatarios(RegraNotificacao regra, RegraNotificacaoRequest request) {
        if (request.destinatarios() == null) {
            return;
        }
        request.destinatarios().forEach(destinatario ->
                regra.adicionarDestinatario(destinatario.tipo(), destinatario.idAlvo(), destinatario.valorAlvo()));
    }

    private void validarPoliticas(RegraNotificacao regra) {
        regra.destinatariosAtivos().stream()
                .filter(destinatario -> destinatario.getTipo() == TipoDestinatarioRegra.POLITICA)
                .forEach(destinatario -> {
                    var politica = politicaRepository.findById(destinatario.getIdAlvo())
                            .orElseThrow(() -> new InvarianteVioladaException(
                                    "A política de roteamento informada não existe."));
                    if (!politica.isAtivo()) {
                        throw new InvarianteVioladaException(
                                "A política de roteamento informada está inativa.");
                    }
                    if (!regra.getChaveGatilho().equals(politica.getChaveGatilho())) {
                        throw new InvarianteVioladaException(
                                "A política de roteamento pertence a outro gatilho.");
                    }
                    politica.exigirUtilizavel();
                });
    }

    private GatilhoNotificacao exigirGatilho(String chave) {
        return GatilhoNotificacao.porChave(chave).orElseThrow(() -> new InvarianteVioladaException(
                "Gatilho desconhecido: '" + chave + "'. Consulte os gatilhos disponíveis."));
    }

    private void exigirNomeUnico(String nome, UUID idIgnorado) {
        boolean duplicado = idIgnorado == null
                ? repository.existsByNomeIgnoreCase(nome)
                : repository.existsByNomeIgnoreCaseAndIdNot(nome, idIgnorado);
        if (duplicado) {
            throw new InvarianteVioladaException("Já existe uma regra com este nome.");
        }
    }
}
