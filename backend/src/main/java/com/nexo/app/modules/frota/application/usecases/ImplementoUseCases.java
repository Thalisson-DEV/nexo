package com.nexo.app.modules.frota.application.usecases;

import com.nexo.app.modules.frota.application.dto.AcoplamentoImplementoResponse;
import com.nexo.app.modules.frota.application.dto.AcoplarImplementoRequest;
import com.nexo.app.modules.frota.application.dto.ImplementoFilter;
import com.nexo.app.modules.frota.application.dto.ImplementoRequest;
import com.nexo.app.modules.frota.application.dto.ImplementoResponse;
import com.nexo.app.modules.frota.domain.AcoplamentoImplemento;
import com.nexo.app.modules.frota.domain.FichaVeiculo;
import com.nexo.app.modules.frota.domain.Implemento;
import com.nexo.app.modules.frota.domain.SituacaoOperacional;
import com.nexo.app.modules.frota.infrastructure.persistence.AcoplamentoImplementoRepository;
import com.nexo.app.modules.frota.infrastructure.persistence.FichaVeiculoRepository;
import com.nexo.app.modules.frota.infrastructure.persistence.ImplementoRepository;
import com.nexo.app.modules.frota.infrastructure.persistence.ImplementoSpecification;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Implementos e o histórico de em qual veículo cada um esteve acoplado. */
@Service
@RequiredArgsConstructor
public class ImplementoUseCases {

    private final ImplementoRepository repository;
    private final AcoplamentoImplementoRepository acoplamentoRepository;
    private final FichaVeiculoRepository fichaRepository;
    private final TipoImplementoUseCases tipos;
    private final ReferenciasFrota referencias;

    @Transactional(readOnly = true)
    public PageResponse<ImplementoResponse> listar(ImplementoFilter filtro, Pageable pageable) {
        Page<Implemento> pagina = repository.findAll(ImplementoSpecification.of(filtro), pageable);
        return PageResponse.fromPage(new PageImpl<>(montar(pagina.getContent()), pagina.getPageable(),
                pagina.getTotalElements()));
    }

    @Transactional(readOnly = true)
    public ImplementoResponse buscarPorId(UUID id) {
        return montar(List.of(buscarEntidade(id))).get(0);
    }

    @Transactional
    public ImplementoResponse criar(ImplementoRequest request) {
        if (repository.existsByIdentificacaoIgnoreCase(request.identificacao().trim())) {
            throw new InvarianteVioladaException("Já existe um implemento com esta identificação.");
        }
        exigirBase(request.idBaseOperacional());
        var implemento = new Implemento(request.identificacao(), tipos.buscarEntidade(request.idTipoImplemento()),
                request.fabricante(), request.modelo(), request.numeroSerie(), request.anoFabricacao(),
                request.idBaseOperacional(), request.observacao());
        return montar(List.of(repository.save(implemento))).get(0);
    }

    @Transactional
    public ImplementoResponse atualizar(UUID id, ImplementoRequest request) {
        var implemento = buscarEntidade(id);
        if (repository.existsByIdentificacaoIgnoreCaseAndIdNot(request.identificacao().trim(), id)) {
            throw new InvarianteVioladaException("Já existe um implemento com esta identificação.");
        }
        exigirBase(request.idBaseOperacional());
        if (request.situacao() == SituacaoOperacional.BAIXADO
                && acoplamentoRepository.findByImplementoIdAndFimIsNullAndAtivoTrue(id).isPresent()) {
            throw new InvarianteVioladaException("Desacople o implemento antes de baixá-lo.");
        }
        implemento.atualizar(request.identificacao(), tipos.buscarEntidade(request.idTipoImplemento()),
                request.fabricante(), request.modelo(), request.numeroSerie(), request.anoFabricacao(),
                request.idBaseOperacional(), request.situacao(), request.observacao());
        return montar(List.of(repository.save(implemento))).get(0);
    }

    @Transactional
    public void desativar(UUID id) {
        var implemento = buscarEntidade(id);
        if (acoplamentoRepository.findByImplementoIdAndFimIsNullAndAtivoTrue(id).isPresent()) {
            throw new InvarianteVioladaException("Desacople o implemento antes de desativá-lo.");
        }
        implemento.desativar();
        repository.save(implemento);
    }

    @Transactional
    public void ativar(UUID id) {
        var implemento = buscarEntidade(id);
        implemento.ativar();
        repository.save(implemento);
    }

    /**
     * Acopla o implemento ao veículo. Se estava em outro veículo, o acoplamento anterior
     * é encerrado no mesmo instante; se já está neste veículo, nada muda.
     */
    @Transactional
    public AcoplamentoImplementoResponse acoplar(UUID idImplemento, AcoplarImplementoRequest request) {
        var implemento = buscarEntidade(idImplemento);
        FichaVeiculo ficha = fichaRepository.findByIdVeiculo(request.idVeiculo())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Veículo não encontrado na frota."));
        if (!ficha.isAtivo() || ficha.getSituacao() == SituacaoOperacional.BAIXADO) {
            throw new InvarianteVioladaException("O veículo " + ficha.getPlaca() + " não aceita acoplamento.");
        }
        var historico = acoplamentoRepository.findAllByImplementoIdAndAtivoTrueOrderByInicioDesc(idImplemento);
        var aberto = historico.stream().filter(AcoplamentoImplemento::isAberto).findFirst();
        if (aberto.isPresent() && aberto.get().getIdVeiculo().equals(request.idVeiculo())) {
            return montarAcoplamentos(List.of(aberto.get())).get(0);
        }
        boolean sobrepoe = historico.stream()
                .anyMatch(a -> !a.isAberto() && a.getFim().isAfter(request.inicio()));
        if (sobrepoe) {
            throw new InvarianteVioladaException(
                    "O implemento tem um acoplamento encerrado depois deste início. Os períodos não podem se sobrepor.");
        }
        if (aberto.isPresent()) {
            encerrarAntesDe(aberto.get(), request.inicio());
        }
        var novo = acoplamentoRepository.save(
                new AcoplamentoImplemento(implemento, request.idVeiculo(), request.inicio(), request.observacao()));
        return montarAcoplamentos(List.of(novo)).get(0);
    }

    @Transactional
    public AcoplamentoImplementoResponse desacoplar(UUID idImplemento, OffsetDateTime fim) {
        var aberto = acoplamentoRepository.findByImplementoIdAndFimIsNullAndAtivoTrue(idImplemento)
                .orElseThrow(() -> new InvarianteVioladaException("O implemento não está acoplado a nenhum veículo."));
        aberto.encerrar(fim);
        return montarAcoplamentos(List.of(acoplamentoRepository.save(aberto))).get(0);
    }

    @Transactional(readOnly = true)
    public List<AcoplamentoImplementoResponse> historicoDoImplemento(UUID idImplemento) {
        buscarEntidade(idImplemento);
        return montarAcoplamentos(acoplamentoRepository.findAllByImplementoIdAndAtivoTrueOrderByInicioDesc(idImplemento));
    }

    @Transactional(readOnly = true)
    public List<AcoplamentoImplementoResponse> historicoDoVeiculo(UUID idVeiculo) {
        return montarAcoplamentos(acoplamentoRepository.findAllByIdVeiculoAndAtivoTrueOrderByInicioDesc(idVeiculo));
    }

    Map<UUID, List<AcoplamentoImplemento>> abertosPorVeiculo(Collection<UUID> idsVeiculo) {
        if (idsVeiculo.isEmpty()) return Map.of();
        return acoplamentoRepository.findAllByIdVeiculoInAndFimIsNullAndAtivoTrue(idsVeiculo).stream()
                .collect(Collectors.groupingBy(AcoplamentoImplemento::getIdVeiculo));
    }

    List<AcoplamentoImplementoResponse> montarAcoplamentos(List<AcoplamentoImplemento> acoplamentos) {
        var idsVeiculo = acoplamentos.stream().map(AcoplamentoImplemento::getIdVeiculo).collect(Collectors.toSet());
        Map<UUID, String> placas = idsVeiculo.isEmpty() ? Map.of()
                : fichaRepository.findAllByIdVeiculoIn(idsVeiculo).stream()
                        .collect(Collectors.toMap(FichaVeiculo::getIdVeiculo, FichaVeiculo::getPlaca));
        return acoplamentos.stream().map(a -> new AcoplamentoImplementoResponse(
                a.getId(),
                a.getImplemento().getId(),
                a.getImplemento().getIdentificacao(),
                a.getImplemento().getTipoImplemento().getNome(),
                a.getIdVeiculo(),
                placas.get(a.getIdVeiculo()),
                a.getInicio(),
                a.getFim(),
                a.getObservacao()
        )).toList();
    }

    Implemento buscarEntidade(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Implemento não encontrado."));
    }

    private List<ImplementoResponse> montar(List<Implemento> implementos) {
        var ids = implementos.stream().map(Implemento::getId).toList();
        Map<UUID, AcoplamentoImplemento> abertos = ids.isEmpty() ? Map.of()
                : acoplamentoRepository.findAllByImplementoIdInAndFimIsNullAndAtivoTrue(ids).stream()
                        .collect(Collectors.toMap(a -> a.getImplemento().getId(), a -> a, (a, b) -> a));
        Map<UUID, AcoplamentoImplementoResponse> acoplamentos = montarAcoplamentos(List.copyOf(abertos.values()))
                .stream().collect(Collectors.toMap(AcoplamentoImplementoResponse::idImplemento, a -> a));
        Map<UUID, String> bases = referencias.nomesBases(implementos.stream().map(Implemento::getIdBaseOperacional).toList());
        return implementos.stream().map(i -> new ImplementoResponse(
                i.getId(),
                i.getIdentificacao(),
                i.getTipoImplemento().getId(),
                i.getTipoImplemento().getNome(),
                i.getFabricante(),
                i.getModelo(),
                i.getNumeroSerie(),
                i.getAnoFabricacao(),
                i.getIdBaseOperacional(),
                bases.get(i.getIdBaseOperacional()),
                i.getSituacao(),
                i.getObservacao(),
                acoplamentos.get(i.getId()),
                i.isAtivo()
        )).toList();
    }

    private void exigirBase(UUID idBase) {
        if (!referencias.baseExiste(idBase)) {
            throw new InvarianteVioladaException("Base operacional não encontrada ou desativada.");
        }
    }

    /** Grava o encerramento antes do novo acoplamento por causa do índice único parcial. */
    private void encerrarAntesDe(AcoplamentoImplemento aberto, OffsetDateTime inicioNovo) {
        if (inicioNovo.isBefore(aberto.getInicio())) {
            throw new InvarianteVioladaException(
                    "O novo acoplamento não pode começar antes do acoplamento atual.");
        }
        aberto.encerrar(inicioNovo);
        acoplamentoRepository.saveAndFlush(aberto);
    }
}
