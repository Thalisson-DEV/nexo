package com.nexo.app.modules.frota.application.usecases;

import com.nexo.app.modules.frota.FrotaOdometroApi;
import com.nexo.app.modules.frota.application.dto.LeituraOdometroFilter;
import com.nexo.app.modules.frota.application.dto.LeituraOdometroResponse;
import com.nexo.app.modules.frota.application.dto.RegistrarLeituraRequest;
import com.nexo.app.modules.frota.domain.FichaVeiculo;
import com.nexo.app.modules.frota.domain.LeituraOdometro;
import com.nexo.app.modules.frota.domain.OrigemLeituraOdometro;
import com.nexo.app.modules.frota.domain.SituacaoLeituraOdometro;
import com.nexo.app.modules.frota.infrastructure.persistence.FichaVeiculoRepository;
import com.nexo.app.modules.frota.infrastructure.persistence.LeituraOdometroRepository;
import com.nexo.app.modules.frota.infrastructure.persistence.LeituraOdometroSpecification;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import com.nexo.app.shared.infrastructure.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Livro-razão do odômetro: registra leituras de todas as origens, calcula o KM atual e
 * expõe a fila de inconsistências para o gestor.
 */
@Service
@RequiredArgsConstructor
public class OdometroUseCases implements FrotaOdometroApi {

    private final LeituraOdometroRepository repository;
    private final FichaVeiculoRepository fichaRepository;
    private final ParametrosFrotaUseCases parametros;
    private final ReferenciasFrota referencias;

    /** Lançamento manual pela web: quem digita corrige na hora, então incoerência é recusada. */
    @Transactional
    public LeituraOdometroResponse registrarManual(UUID idVeiculo, RegistrarLeituraRequest request) {
        LeituraOdometro leitura = registrar(idVeiculo, request.km(), request.lidoEm(),
                OrigemLeituraOdometro.MANUAL, null, SecurityUtils.getUsuarioLogadoId());
        return montar(List.of(leitura)).get(0);
    }

    @Override
    @Transactional
    public ResultadoLeitura registrarLeitura(UUID idVeiculo, long km, OffsetDateTime lidoEm,
                                             OrigemLeitura origem, UUID idOrigem, UUID idUsuario) {
        LeituraOdometro leitura = registrar(idVeiculo, km, lidoEm,
                OrigemLeituraOdometro.valueOf(origem.name()), idOrigem, idUsuario);
        return new ResultadoLeitura(leitura.getId(), leitura.isValida(),
                leitura.isInconsistente() ? leitura.getMotivoSituacao() : null,
                kmAtual(idVeiculo).orElse(null));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> kmAtual(UUID idVeiculo) {
        return repository.findFirstByIdVeiculoAndSituacaoOrderByKmDescLidoEmDesc(idVeiculo, SituacaoLeituraOdometro.VALIDA)
                .map(LeituraOdometro::getKm);
    }

    @Transactional(readOnly = true)
    public PageResponse<LeituraOdometroResponse> listar(LeituraOdometroFilter filtro, Pageable pageable) {
        List<UUID> escopo = null;
        if (filtro != null && !filtro.idsBase().isEmpty()) {
            escopo = fichaRepository.findAllByIdBaseOperacionalIn(filtro.idsBase()).stream()
                    .map(FichaVeiculo::getIdVeiculo).toList();
        }
        Pageable ordenado = pageable.getSort().isSorted() ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        Sort.by(Sort.Order.desc("lidoEm"), Sort.Order.desc("km")));
        Page<LeituraOdometro> pagina = repository.findAll(LeituraOdometroSpecification.of(filtro, escopo), ordenado);
        var conteudo = montar(pagina.getContent());
        return PageResponse.fromPage(new org.springframework.data.domain.PageImpl<>(conteudo, ordenado, pagina.getTotalElements()));
    }

    @Transactional
    public LeituraOdometroResponse anular(UUID id, String justificativa) {
        LeituraOdometro leitura = buscarEntidade(id);
        leitura.anular(SecurityUtils.getUsuarioLogadoId(), justificativa);
        return montar(List.of(repository.save(leitura))).get(0);
    }

    @Transactional
    public LeituraOdometroResponse confirmar(UUID id, String justificativa) {
        LeituraOdometro leitura = buscarEntidade(id);
        leitura.confirmar(justificativa);
        return montar(List.of(repository.save(leitura))).get(0);
    }

    /**
     * Grava a leitura classificada contra as vizinhas válidas. Idempotente por
     * {@code (origem, idOrigem)}: o mesmo registro reenviado devolve a leitura existente.
     */
    LeituraOdometro registrar(UUID idVeiculo, long km, OffsetDateTime lidoEm,
                              OrigemLeituraOdometro origem, UUID idOrigem, UUID idUsuario) {
        if (idOrigem != null) {
            var existente = repository.findByOrigemAndIdOrigem(origem, idOrigem);
            if (existente.isPresent()) {
                if (!existente.get().getIdVeiculo().equals(idVeiculo)) {
                    throw new InvarianteVioladaException("O registro de origem já gerou leitura para outro veículo.");
                }
                return existente.get();
            }
        }
        FichaVeiculo ficha = fichaRepository.findByIdVeiculo(idVeiculo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Veículo não encontrado na frota."));
        if (!ficha.isAtivo()) {
            throw new InvarianteVioladaException("O veículo " + ficha.getPlaca() + " está desativado.");
        }
        if (lidoEm == null) {
            throw new InvarianteVioladaException("Informe a data e hora da leitura.");
        }
        var anterior = repository.findFirstByIdVeiculoAndSituacaoAndLidoEmLessThanEqualOrderByLidoEmDescKmDesc(
                idVeiculo, SituacaoLeituraOdometro.VALIDA, lidoEm).orElse(null);
        var posterior = repository.findFirstByIdVeiculoAndSituacaoAndLidoEmAfterOrderByLidoEmAscKmAsc(
                idVeiculo, SituacaoLeituraOdometro.VALIDA, lidoEm).orElse(null);
        int limiteKmDia = parametros.obter().getLimiteKmDia();

        LeituraOdometro leitura = LeituraOdometro.registrar(idVeiculo, km, lidoEm, origem, idOrigem, idUsuario,
                anterior, posterior, limiteKmDia);
        if (origem == OrigemLeituraOdometro.MANUAL && leitura.isInconsistente()) {
            throw new InvarianteVioladaException(leitura.getMotivoSituacao()
                    + " Confira o valor digitado ou anule a leitura errada antes.");
        }
        return repository.save(leitura);
    }

    private LeituraOdometro buscarEntidade(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Leitura de odômetro não encontrada."));
    }

    private List<LeituraOdometroResponse> montar(List<LeituraOdometro> leituras) {
        var idsVeiculo = leituras.stream().map(LeituraOdometro::getIdVeiculo).collect(Collectors.toSet());
        Map<UUID, String> placas = fichaRepository.findAllByIdVeiculoIn(idsVeiculo).stream()
                .collect(Collectors.toMap(FichaVeiculo::getIdVeiculo, FichaVeiculo::getPlaca));
        Map<UUID, String> logins = referencias.loginsUsuarios(
                leituras.stream().map(LeituraOdometro::getIdUsuarioRegistro).toList());
        return leituras.stream().map(l -> new LeituraOdometroResponse(
                l.getId(),
                l.getIdVeiculo(),
                placas.get(l.getIdVeiculo()),
                l.getKm(),
                l.getLidoEm(),
                l.getOrigem(),
                l.getIdOrigem(),
                l.getSituacao(),
                l.getMotivoSituacao(),
                l.getKmAnterior(),
                l.getIdUsuarioRegistro() == null ? null : logins.get(l.getIdUsuarioRegistro()),
                l.getDataAnulacao(),
                l.getJustificativaAnulacao()
        )).toList();
    }

    /** KM atual e data da última leitura válida dos veículos informados. */
    Map<UUID, LeituraOdometroRepository.KmAtualProjection> kmAtualPorVeiculo(java.util.Collection<UUID> idsVeiculo) {
        if (idsVeiculo.isEmpty()) return Map.of();
        return repository.buscarKmAtual(idsVeiculo).stream()
                .collect(Collectors.toMap(LeituraOdometroRepository.KmAtualProjection::getIdVeiculo, Function.identity()));
    }

    Map<UUID, Long> inconsistentesPorVeiculo(java.util.Collection<UUID> idsVeiculo) {
        if (idsVeiculo.isEmpty()) return Map.of();
        return repository.contarInconsistentes(idsVeiculo).stream()
                .collect(Collectors.toMap(LeituraOdometroRepository.ContagemPorVeiculoProjection::getIdVeiculo,
                        LeituraOdometroRepository.ContagemPorVeiculoProjection::getTotal));
    }
}
