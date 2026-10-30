package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.rh.BaseOperacionalApi;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.ColaboradorResponse;
import com.nexo.app.modules.rh.EquipeApi;
import com.nexo.app.modules.rh.ProcessoNegocioApi;
import com.nexo.app.modules.sst.application.dto.*;
import com.nexo.app.modules.sst.domain.DesvioSeguranca;
import com.nexo.app.modules.sst.domain.FormularioInspecao;
import com.nexo.app.modules.sst.domain.StatusDesvio;
import com.nexo.app.modules.sst.infrastructure.persistence.DesvioSegurancaRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.DesvioSegurancaSpecification;
import com.nexo.app.modules.sst.infrastructure.persistence.FormularioInspecaoRepository;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Caixa de pendências do inspetor. Toda leitura e alteração é restringida às
 * inspeções lançadas pelo usuário autenticado.
 */
@Service
@RequiredArgsConstructor
public class MeuDesvioUseCases {

    private final DesvioSegurancaRepository repository;
    private final FormularioInspecaoRepository formularioRepository;
    private final ProcessoNegocioApi processoApi;
    private final BaseOperacionalApi baseOperacionalApi;
    private final EquipeApi equipeApi;
    private final ColaboradorApi colaboradorApi;

    @Transactional(readOnly = true)
    public PageResponse<MeuDesvioResponse> listar(
            UUID idUsuario, MeuDesvioFilter filter, Pageable pageable) {
        return PageResponse.fromPage(repository.findAll(
                DesvioSegurancaSpecification.doInspetor(idUsuario, filter), pageable
        ).map(this::toResponse));
    }

    @Transactional
    public MeuDesvioResponse tratar(
            UUID idUsuario, UUID idDesvio, TratarDesvioRequest request) {
        DesvioSeguranca desvio = buscarDesviosDoInspetor(Set.of(idDesvio), idUsuario).getFirst();
        desvio.atualizarTratamento(request.status(), request.acaoCorretiva(), request.prazo());
        return toResponse(repository.save(desvio));
    }

    @Transactional
    public TratamentoDesviosEmMassaResponse tratarEmMassa(
            UUID idUsuario, TratarDesviosEmMassaRequest request) {
        Set<UUID> idsUnicos = new LinkedHashSet<>(request.idsDesvios());
        List<DesvioSeguranca> desvios = buscarDesviosDoInspetor(idsUnicos, idUsuario);

        desvios.forEach(desvio -> desvio.atualizarTratamento(
                request.status(), request.acaoCorretiva(), request.prazo()));
        repository.saveAll(desvios);

        return new TratamentoDesviosEmMassaResponse(
                desvios.size(), desvios.stream().map(DesvioSeguranca::getId).toList());
    }

    private List<DesvioSeguranca> buscarDesviosDoInspetor(
            Set<UUID> idsDesvios, UUID idInspetor) {
        List<DesvioSeguranca> desvios = repository.findAll(
                DesvioSegurancaSpecification.idsDoInspetor(idsDesvios, idInspetor));
        if (desvios.size() != idsDesvios.size()) {
            throw new RecursoNaoEncontradoException(
                    "Um ou mais desvios não existem ou não pertencem a uma inspeção do usuário autenticado.");
        }
        return desvios;
    }

    private MeuDesvioResponse toResponse(DesvioSeguranca desvio) {
        var inspecao = desvio.getInspecao();
        var processo = inspecao.getIdProcesso() == null ? null
                : processoApi.buscarPorIdResponse(inspecao.getIdProcesso()).orElse(null);
        var processoInspetor = colaboradorApi.buscarPorUsuarioResponse(inspecao.getIdInspetor())
                .or(() -> colaboradorApi.buscarPorIdResponse(inspecao.getIdInspetor()))
                .map(ColaboradorResponse::processo)
                .orElse(null);
        var base = inspecao.getIdBaseOperacional() == null ? null
                : baseOperacionalApi.buscarPorIdResponse(inspecao.getIdBaseOperacional()).orElse(null);
        var equipe = inspecao.getIdEquipe() == null ? null
                : equipeApi.buscarPorIdResponse(inspecao.getIdEquipe()).orElse(null);
        boolean vencido = desvio.getPrazo() != null
                && desvio.getPrazo().isBefore(LocalDate.now())
                && desvio.getStatus() != StatusDesvio.RESOLVIDO;

        return new MeuDesvioResponse(
                desvio.getId(), inspecao.getId(), inspecao.getDataRealizacao(),
                processo, processoInspetor, base, equipe, desvio.getIdPergunta(),
                buscarEnunciado(inspecao.getIdFormulario(), desvio.getIdPergunta()),
                desvio.getGravidade(), desvio.getDescricao(), desvio.getAcaoCorretiva(),
                desvio.getPrazo(), desvio.getStatus(), desvio.getDataResolucao(), vencido,
                desvio.getAnexos().stream()
                        .map(a -> new AnexoDesvioResponse(
                                a.getId(), a.getContexto(), a.getTipo(), a.getNomeArquivo(),
                                a.getContentType(), a.getTamanhoBytes(), a.getDataCriacao()))
                        .toList()
        );
    }

    private String buscarEnunciado(UUID idFormulario, UUID idPergunta) {
        return formularioRepository.findById(idFormulario)
                .stream()
                .map(FormularioInspecao::getGrupos)
                .flatMap(List::stream)
                .flatMap(grupo -> grupo.getPerguntas().stream())
                .filter(pergunta -> pergunta.getId().equals(idPergunta))
                .map(pergunta -> pergunta.getEnunciado())
                .findFirst()
                .orElse(null);
    }
}
