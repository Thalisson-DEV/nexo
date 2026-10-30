package com.nexo.app.modules.frota.application.usecases;

import com.nexo.app.modules.frota.application.dto.AcoplamentoImplementoResponse;
import com.nexo.app.modules.frota.application.dto.FichaVeiculoFilter;
import com.nexo.app.modules.frota.application.dto.VeiculoFrotaDetalheResponse;
import com.nexo.app.modules.frota.application.dto.VeiculoFrotaRequest;
import com.nexo.app.modules.frota.application.dto.VeiculoFrotaResumoResponse;
import com.nexo.app.modules.frota.application.mappers.CatalogoFrotaMapper;
import com.nexo.app.modules.frota.domain.AcoplamentoImplemento;
import com.nexo.app.modules.frota.domain.DadosFichaVeiculo;
import com.nexo.app.modules.frota.domain.FichaVeiculo;
import com.nexo.app.modules.frota.domain.VinculoCondutor;
import com.nexo.app.modules.frota.infrastructure.persistence.DocumentoFrotaRepository;
import com.nexo.app.modules.frota.infrastructure.persistence.FichaVeiculoRepository;
import com.nexo.app.modules.frota.infrastructure.persistence.FichaVeiculoSpecification;
import com.nexo.app.modules.frota.infrastructure.persistence.LeituraOdometroRepository;
import com.nexo.app.modules.rh.VeiculoApi;
import com.nexo.app.modules.rh.VeiculoResponse;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Cadastro de veículos pela visão da frota.
 *
 * <p>Placa, modelo, fabricante e base são gravados no RH pela {@link VeiculoApi}; a ficha
 * é gravada aqui, na mesma transação. A listagem filtra e ordena pela ficha (que guarda
 * a projeção de placa e base) e completa cada página com consultas em lote.</p>
 */
@Service
@RequiredArgsConstructor
public class VeiculoFrotaUseCases {

    private final FichaVeiculoRepository fichaRepository;
    private final DocumentoFrotaRepository documentoRepository;
    private final VeiculoApi veiculoApi;
    private final TipoVeiculoUseCases tipos;
    private final OdometroUseCases odometro;
    private final VinculoCondutorUseCases vinculos;
    private final ImplementoUseCases implementos;
    private final ParametrosFrotaUseCases parametros;
    private final ReferenciasFrota referencias;
    private final CatalogoFrotaMapper catalogoMapper;

    @Transactional(readOnly = true)
    public PageResponse<VeiculoFrotaResumoResponse> listar(FichaVeiculoFilter filtro, Pageable pageable) {
        Page<FichaVeiculo> pagina = fichaRepository.findAll(FichaVeiculoSpecification.of(filtro), pageable);
        return PageResponse.fromPage(new PageImpl<>(montarResumos(pagina.getContent()),
                pagina.getPageable(), pagina.getTotalElements()));
    }

    @Transactional(readOnly = true)
    public VeiculoFrotaDetalheResponse detalhar(UUID idVeiculo) {
        return montarDetalhe(buscarFicha(idVeiculo));
    }

    @Transactional
    public VeiculoFrotaDetalheResponse criar(VeiculoFrotaRequest request) {
        VeiculoResponse veiculo = veiculoApi.cadastrar(dadosRh(request));
        // O RH publicou VeiculoCadastradoEvent e o listener desta mesma transação criou a ficha.
        FichaVeiculo ficha = garantirFicha(veiculo);
        ficha.atualizar(dadosFicha(request));
        return montarDetalhe(fichaRepository.save(ficha));
    }

    @Transactional
    public VeiculoFrotaDetalheResponse atualizar(UUID idVeiculo, VeiculoFrotaRequest request) {
        buscarFicha(idVeiculo);
        VeiculoResponse veiculo = veiculoApi.alterarCadastro(idVeiculo, dadosRh(request));
        FichaVeiculo ficha = garantirFicha(veiculo);
        ficha.atualizar(dadosFicha(request));
        return montarDetalhe(fichaRepository.save(ficha));
    }

    /** Cria a ficha vazia do veículo recém-cadastrado no RH. Idempotente. */
    @Transactional
    public void provisionarFicha(UUID idVeiculo, String placa, UUID idBase) {
        if (!fichaRepository.existsByIdVeiculo(idVeiculo)) {
            fichaRepository.save(FichaVeiculo.provisionar(idVeiculo, placa, idBase));
        }
    }

    /** Mantém a projeção de placa, base e situação da ficha igual ao RH. */
    @Transactional
    public void sincronizarCadastro(UUID idVeiculo, String placa, UUID idBase, boolean ativo) {
        fichaRepository.findByIdVeiculo(idVeiculo).ifPresentOrElse(
                ficha -> {
                    ficha.sincronizarCadastro(placa, idBase, ativo);
                    fichaRepository.save(ficha);
                },
                () -> {
                    var ficha = FichaVeiculo.provisionar(idVeiculo, placa, idBase);
                    ficha.sincronizarCadastro(placa, idBase, ativo);
                    fichaRepository.save(ficha);
                });
    }

    FichaVeiculo buscarFicha(UUID idVeiculo) {
        return fichaRepository.findByIdVeiculo(idVeiculo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Veículo não encontrado na frota."));
    }

    private FichaVeiculo garantirFicha(VeiculoResponse veiculo) {
        UUID idBase = veiculo.baseOperacional() == null ? null : veiculo.baseOperacional().id();
        provisionarFicha(veiculo.id(), veiculo.placa(), idBase);
        return buscarFicha(veiculo.id());
    }

    private static VeiculoApi.DadosCadastroVeiculo dadosRh(VeiculoFrotaRequest request) {
        return new VeiculoApi.DadosCadastroVeiculo(request.placa(), request.modelo(), request.fabricante(),
                request.idBaseOperacional());
    }

    private DadosFichaVeiculo dadosFicha(VeiculoFrotaRequest request) {
        return new DadosFichaVeiculo(
                request.idTipoVeiculo() == null ? null : tipos.buscarEntidade(request.idTipoVeiculo()),
                request.anoFabricacao(), request.anoModelo(), request.chassi(), request.renavam(), request.cor(),
                request.combustivel(), request.capacidadeTanqueLitros(), request.consumoReferenciaKmL(),
                request.situacao(), request.regime(), request.locadora(), request.custoMensalLocacao(),
                request.inicioLocacao(), request.fimLocacao(), request.provedorTelemetria(), request.idRastreador(),
                request.observacao());
    }

    private List<VeiculoFrotaResumoResponse> montarResumos(List<FichaVeiculo> fichas) {
        var contexto = carregarContexto(fichas);
        return fichas.stream().map(f -> {
            UUID id = f.getIdVeiculo();
            var veiculo = contexto.veiculos().get(id);
            var km = contexto.km().get(id);
            var vinculo = contexto.condutores().get(id);
            var docs = contexto.documentos().get(id);
            return new VeiculoFrotaResumoResponse(
                    id,
                    f.getPlaca(),
                    veiculo == null ? null : veiculo.modelo(),
                    veiculo == null ? null : veiculo.fabricante(),
                    f.getIdBaseOperacional(),
                    contexto.bases().get(f.getIdBaseOperacional()),
                    f.getTipoVeiculo() == null ? null : f.getTipoVeiculo().getId(),
                    f.getTipoVeiculo() == null ? null : f.getTipoVeiculo().getNome(),
                    f.getSituacao(),
                    f.getRegime(),
                    f.getCombustivel(),
                    f.getAnoModelo(),
                    km == null ? null : km.getKm(),
                    km == null ? null : km.getLidoEm(),
                    vinculo == null ? null : vinculo.getIdColaborador(),
                    vinculo == null ? null : contexto.nomesCondutores().get(vinculo.getIdColaborador()),
                    contexto.implementos().getOrDefault(id, List.of()).stream()
                            .map(a -> a.getImplemento().getIdentificacao()).toList(),
                    docs == null || docs.getVencidos() == null ? 0 : docs.getVencidos(),
                    docs == null || docs.getAVencer() == null ? 0 : docs.getAVencer(),
                    contexto.inconsistentes().getOrDefault(id, 0L),
                    f.isAtivo());
        }).toList();
    }

    private VeiculoFrotaDetalheResponse montarDetalhe(FichaVeiculo f) {
        var contexto = carregarContexto(List.of(f));
        UUID id = f.getIdVeiculo();
        var veiculo = contexto.veiculos().get(id);
        var km = contexto.km().get(id);
        var vinculo = contexto.condutores().get(id);
        var docs = contexto.documentos().get(id);
        List<AcoplamentoImplementoResponse> acoplados = implementos.montarAcoplamentos(
                contexto.implementos().getOrDefault(id, List.of()));
        return new VeiculoFrotaDetalheResponse(
                id,
                f.getPlaca(),
                veiculo == null ? null : veiculo.modelo(),
                veiculo == null ? null : veiculo.fabricante(),
                f.getIdBaseOperacional(),
                contexto.bases().get(f.getIdBaseOperacional()),
                f.getTipoVeiculo() == null ? null : catalogoMapper.toResponse(f.getTipoVeiculo()),
                f.getAnoFabricacao(),
                f.getAnoModelo(),
                f.getChassi(),
                f.getRenavam(),
                f.getCor(),
                f.getCombustivel(),
                f.getCapacidadeTanqueLitros(),
                f.getConsumoReferenciaKmL(),
                f.getSituacao(),
                f.getRegime(),
                f.getLocadora(),
                f.getCustoMensalLocacao(),
                f.getInicioLocacao(),
                f.getFimLocacao(),
                f.getProvedorTelemetria(),
                f.getIdRastreador(),
                f.getObservacao(),
                km == null ? null : km.getKm(),
                km == null ? null : km.getLidoEm(),
                vinculo == null ? null : vinculos.montar(List.of(vinculo)).get(0),
                acoplados,
                docs == null || docs.getVencidos() == null ? 0 : docs.getVencidos(),
                docs == null || docs.getAVencer() == null ? 0 : docs.getAVencer(),
                contexto.inconsistentes().getOrDefault(id, 0L),
                f.isAtivo());
    }

    private ContextoPagina carregarContexto(List<FichaVeiculo> fichas) {
        Collection<UUID> ids = fichas.stream().map(FichaVeiculo::getIdVeiculo).toList();
        LocalDate hoje = LocalDate.now();
        Map<UUID, VinculoCondutor> condutores = vinculos.abertosPorVeiculo(ids);
        Map<UUID, DocumentoFrotaRepository.ResumoVencimentoProjection> documentos = ids.isEmpty() ? Map.of()
                : documentoRepository.resumirPorVeiculo(ids, hoje, hoje.plusDays(parametros.diasAviso())).stream()
                        .collect(Collectors.toMap(DocumentoFrotaRepository.ResumoVencimentoProjection::getIdVeiculo, d -> d));
        return new ContextoPagina(
                referencias.veiculos(ids),
                referencias.nomesBases(fichas.stream().map(FichaVeiculo::getIdBaseOperacional).toList()),
                odometro.kmAtualPorVeiculo(ids),
                condutores,
                referencias.nomesColaboradores(condutores.values().stream().map(VinculoCondutor::getIdColaborador).toList()),
                implementos.abertosPorVeiculo(ids),
                documentos,
                odometro.inconsistentesPorVeiculo(ids));
    }

    private record ContextoPagina(
            Map<UUID, VeiculoResponse> veiculos,
            Map<UUID, String> bases,
            Map<UUID, LeituraOdometroRepository.KmAtualProjection> km,
            Map<UUID, VinculoCondutor> condutores,
            Map<UUID, String> nomesCondutores,
            Map<UUID, List<AcoplamentoImplemento>> implementos,
            Map<UUID, DocumentoFrotaRepository.ResumoVencimentoProjection> documentos,
            Map<UUID, Long> inconsistentes
    ) {
    }
}
