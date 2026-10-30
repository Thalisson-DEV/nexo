package com.nexo.app.modules.sst.application.usecases;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.app.modules.sst.application.dto.NormalizacaoTranscricaoDto;
import com.nexo.app.modules.sst.application.port.NormalizacaoTranscricaoGateway;
import com.nexo.app.modules.sst.domain.ConflitoGovernancaSstException;
import com.nexo.app.modules.sst.domain.NormalizacaoTranscricaoSst;
import com.nexo.app.modules.sst.domain.ReuniaoGovernancaSst;
import com.nexo.app.modules.sst.domain.RevisaoTranscricaoSst;
import com.nexo.app.modules.sst.infrastructure.persistence.NormalizacaoTranscricaoSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.ReuniaoGovernancaSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.RevisaoTranscricaoSstRepository;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

/** Mantém a chamada remota fora da transação e persiste tentativa/resultado nas bordas. */
@Service
@RequiredArgsConstructor
public class NormalizacaoTranscricaoSstUseCases {
    private final ReuniaoGovernancaSstRepository reuniaoRepository;
    private final RevisaoTranscricaoSstRepository transcricaoRepository;
    private final NormalizacaoTranscricaoSstRepository repository;
    private final NormalizacaoTranscricaoGateway gateway;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;

    public NormalizacaoTranscricaoDto.Response solicitar(
            UUID idReuniao, NormalizacaoTranscricaoDto.SolicitarRequest request, UUID idUsuario) {
        if (idUsuario == null) throw new InvarianteVioladaException("Não foi possível identificar o usuário autenticado.");
        Inicio inicio = transacao().execute(status -> iniciar(idReuniao, request, idUsuario));
        if (inicio == null) throw new IllegalStateException("A transação de normalização não retornou resultado.");
        if (inicio.existente()) return toResponse(inicio.normalizacao());

        NormalizacaoTranscricaoGateway.Resultado resultado = gateway.normalizar(
                new NormalizacaoTranscricaoGateway.Entrada(gateway.versaoModelo(), inicio.transcricao()));
        NormalizacaoTranscricaoSst encerrada = transacao().execute(status -> encerrar(inicio.normalizacao().getId(), resultado));
        if (encerrada == null) throw new IllegalStateException("A conclusão da normalização não foi persistida.");
        return toResponse(encerrada);
    }

    public NormalizacaoTranscricaoDto.Response confirmar(UUID idReuniao, UUID idNormalizacao, UUID idUsuario) {
        if (idUsuario == null) throw new InvarianteVioladaException("Não foi possível identificar o usuário autenticado.");
        NormalizacaoTranscricaoSst confirmada = transacao().execute(status -> {
            reuniaoRepository.findAtivaByIdForUpdate(idReuniao)
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Reunião de governança não encontrada."));
            NormalizacaoTranscricaoSst normalizacao = repository
                    .findByIdAndIdReuniaoAndAtivoTrue(idNormalizacao, idReuniao)
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Normalização não encontrada."));
            normalizacao.confirmar(idUsuario);
            return repository.save(normalizacao);
        });
        return toResponse(confirmada);
    }

    public List<NormalizacaoTranscricaoDto.Response> listar(UUID idReuniao) {
        reuniaoRepository.findByIdAndAtivoTrue(idReuniao)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Reunião de governança não encontrada."));
        return repository.findAllByIdReuniaoAndAtivoTrueOrderByNumeroDesc(idReuniao)
                .stream().map(this::toResponse).toList();
    }

    private Inicio iniciar(UUID idReuniao, NormalizacaoTranscricaoDto.SolicitarRequest request, UUID idUsuario) {
        ReuniaoGovernancaSst reuniao = reuniaoRepository.findAtivaByIdForUpdate(idReuniao)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Reunião de governança não encontrada."));
        if (!reuniao.getStatus().permiteConteudo()) {
            throw new InvarianteVioladaException("O estado da reunião não permite normalizar a transcrição.");
        }
        RevisaoTranscricaoSst revisao = transcricaoRepository
                .findByIdAndIdReuniaoAndAtivoTrue(request.idRevisaoTranscricao(), idReuniao)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Revisão de transcrição não encontrada."));
        if (!revisao.confirmada()) {
            throw new InvarianteVioladaException("Confirme a revisão original antes de solicitar a normalização.");
        }
        String hashInsumos = ArquivoPrivadoReuniao.sha256(
                revisao.getHashConteudo() + "|" + gateway.versaoModelo());
        var anterior = repository.findByIdReuniaoAndClientRequestIdAndAtivoTrue(
                idReuniao, request.clientRequestId());
        if (anterior.isPresent()) {
            if (!anterior.get().mesmosInsumos(hashInsumos)) {
                throw new ConflitoGovernancaSstException(
                        "clientRequestId já foi usado com outra revisão ou configuração de modelo.");
            }
            return new Inicio(anterior.get(), revisao.getConteudo(), true);
        }
        NormalizacaoTranscricaoSst normalizacao = new NormalizacaoTranscricaoSst(
                idReuniao, revisao.getId(), repository.proximoNumero(idReuniao),
                request.clientRequestId(), hashInsumos, gateway.versaoModelo(), idUsuario);
        return new Inicio(repository.saveAndFlush(normalizacao), revisao.getConteudo(), false);
    }

    private NormalizacaoTranscricaoSst encerrar(
            UUID id, NormalizacaoTranscricaoGateway.Resultado resultado) {
        NormalizacaoTranscricaoSst normalizacao = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Tentativa de normalização não encontrada."));
        if (resultado.sucesso()) {
            String json = serializar(resultado.conteudo());
            normalizacao.concluir(json, ArquivoPrivadoReuniao.sha256(json));
        } else {
            normalizacao.falhar(resultado.codigoFalha());
        }
        return repository.save(normalizacao);
    }

    private NormalizacaoTranscricaoDto.Response toResponse(NormalizacaoTranscricaoSst n) {
        return new NormalizacaoTranscricaoDto.Response(n.getId(), n.getIdReuniao(), n.getIdRevisaoTranscricao(),
                n.getNumero(), n.getClientRequestId(), n.getStatus(), desserializar(n.getConteudoEstruturado()),
                n.getSha256Conteudo(), n.getVersaoModelo(), n.getCodigoFalha(), n.getConcluidaEm(),
                n.getConfirmadaEm(), n.getDataCriacao());
    }

    private String serializar(NormalizacaoTranscricaoGateway.Conteudo conteudo) {
        try {
            return objectMapper.writeValueAsString(conteudo);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar a normalização validada.", e);
        }
    }

    private NormalizacaoTranscricaoGateway.Conteudo desserializar(String conteudo) {
        if (conteudo == null) return null;
        try {
            return objectMapper.readValue(conteudo, NormalizacaoTranscricaoGateway.Conteudo.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("A normalização persistida está inválida.", e);
        }
    }

    private TransactionTemplate transacao() {
        return new TransactionTemplate(transactionManager);
    }

    private record Inicio(NormalizacaoTranscricaoSst normalizacao, String transcricao, boolean existente) {}
}
