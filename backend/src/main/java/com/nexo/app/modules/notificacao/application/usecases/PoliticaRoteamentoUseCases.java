package com.nexo.app.modules.notificacao.application.usecases;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.notificacao.application.dto.PoliticaRoteamentoRequest;
import com.nexo.app.modules.notificacao.application.dto.PoliticaRoteamentoResponse;
import com.nexo.app.modules.notificacao.domain.GatilhoNotificacao;
import com.nexo.app.modules.notificacao.domain.PoliticaRoteamentoNotificacao;
import com.nexo.app.modules.notificacao.domain.RotaRoteamentoNotificacao;
import com.nexo.app.modules.notificacao.infrastructure.persistence.PoliticaRoteamentoNotificacaoRepository;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PoliticaRoteamentoUseCases {

    private final PoliticaRoteamentoNotificacaoRepository repository;
    private final UsuarioApi usuarioApi;

    @Transactional(readOnly = true)
    public List<PoliticaRoteamentoResponse> listar() {
        return repository.findAllByOrderByNomeAsc().stream()
                .map(PoliticaRoteamentoResponse::de)
                .toList();
    }

    @Transactional(readOnly = true)
    public PoliticaRoteamentoResponse buscar(UUID id) {
        return PoliticaRoteamentoResponse.de(buscarEntity(id));
    }

    @Transactional
    public PoliticaRoteamentoResponse criar(PoliticaRoteamentoRequest request) {
        GatilhoNotificacao gatilho = exigirGatilho(request.chaveGatilho());
        exigirNomeUnico(request.nome(), null);
        PoliticaRoteamentoNotificacao politica =
                new PoliticaRoteamentoNotificacao(gatilho, request.nome(), request.descricao());
        aplicarRotas(politica, request);
        validar(politica);
        return PoliticaRoteamentoResponse.de(repository.save(politica));
    }

    @Transactional
    public PoliticaRoteamentoResponse atualizar(UUID id, PoliticaRoteamentoRequest request) {
        PoliticaRoteamentoNotificacao politica = buscarEntity(id);
        if (!politica.getChaveGatilho().equals(request.chaveGatilho())) {
            throw new InvarianteVioladaException(
                    "O gatilho de uma política não pode ser trocado.");
        }
        exigirNomeUnico(request.nome(), id);
        politica.atualizar(request.nome(), request.descricao());
        politica.limparRotas();
        aplicarRotas(politica, request);
        validar(politica);
        return PoliticaRoteamentoResponse.de(repository.save(politica));
    }

    @Transactional
    public PoliticaRoteamentoResponse ativar(UUID id) {
        PoliticaRoteamentoNotificacao politica = buscarEntity(id);
        validar(politica);
        politica.ativar();
        return PoliticaRoteamentoResponse.de(repository.save(politica));
    }

    @Transactional
    public PoliticaRoteamentoResponse desativar(UUID id) {
        PoliticaRoteamentoNotificacao politica = buscarEntity(id);
        politica.desativar();
        return PoliticaRoteamentoResponse.de(repository.save(politica));
    }

    public PoliticaRoteamentoNotificacao buscarEntity(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Política de roteamento não encontrada."));
    }

    private void aplicarRotas(PoliticaRoteamentoNotificacao politica,
                              PoliticaRoteamentoRequest request) {
        if (request.rotas() == null) {
            return;
        }
        request.rotas().forEach(rotaRequest -> {
            RotaRoteamentoNotificacao rota = politica.adicionarRota(
                    rotaRequest.ordem(), rotaRequest.padrao(), rotaRequest.usuarioId());
            if (rotaRequest.condicoes() != null) {
                rotaRequest.condicoes().forEach(condicao -> rota.adicionarCondicao(
                        condicao.campo(), condicao.operador(), condicao.valor()));
            }
        });
    }

    private void validar(PoliticaRoteamentoNotificacao politica) {
        politica.exigirUtilizavel();
        politica.rotasAtivasOrdenadas().stream()
                .map(RotaRoteamentoNotificacao::getUsuarioId)
                .distinct()
                .forEach(this::exigirUsuarioAtivo);
    }

    private void exigirUsuarioAtivo(UUID usuarioId) {
        if (usuarioApi.buscarContextoAudiencia(usuarioId).isEmpty()) {
            throw new InvarianteVioladaException(
                    "O usuário destinatário " + usuarioId + " não existe ou está inativo.");
        }
    }

    private static GatilhoNotificacao exigirGatilho(String chave) {
        return GatilhoNotificacao.porChave(chave)
                .orElseThrow(() -> new InvarianteVioladaException(
                        "Gatilho desconhecido: '" + chave + "'."));
    }

    private void exigirNomeUnico(String nome, UUID idIgnorado) {
        boolean duplicado = idIgnorado == null
                ? repository.existsByNomeIgnoreCase(nome)
                : repository.existsByNomeIgnoreCaseAndIdNot(nome, idIgnorado);
        if (duplicado) {
            throw new InvarianteVioladaException("Já existe uma política com este nome.");
        }
    }
}
