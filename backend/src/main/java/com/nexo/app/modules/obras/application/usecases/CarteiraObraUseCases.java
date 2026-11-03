package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.contrato.ContratoApi;
import com.nexo.app.modules.obras.application.dto.CarteiraObraRequest;
import com.nexo.app.modules.obras.application.dto.CarteiraObraResponse;
import com.nexo.app.modules.obras.application.dto.HistoricoStatusObraResponse;
import com.nexo.app.modules.obras.application.dto.IndicadoresCarteiraResponse;
import com.nexo.app.modules.obras.application.dto.ObraFilter;
import com.nexo.app.modules.obras.application.dto.ObraProximaResponse;
import com.nexo.app.modules.obras.application.dto.OrcamentoObraResponse;
import com.nexo.app.modules.obras.application.dto.StatusOperacionalObraRequest;
import com.nexo.app.modules.obras.application.mappers.ImportacaoObraMapper;
import com.nexo.app.modules.obras.application.mappers.ObraMapper;
import com.nexo.app.modules.obras.application.port.RotaRodoviariaPort.Coordenada;
import com.nexo.app.modules.obras.domain.HistoricoStatusObra;
import com.nexo.app.modules.obras.domain.ItemOrcamentoObra;
import com.nexo.app.modules.obras.domain.Obra;
import com.nexo.app.modules.obras.domain.StatusObra;
import com.nexo.app.modules.obras.infrastructure.persistence.ItemOrcamentoObraRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.ObraRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.ObraSpecification;
import com.nexo.app.modules.rh.MunicipioApi;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.infrastructure.config.CacheNames;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Dados de carteira da obra: contrato, classe de tensão, prazos, prioridade, valores previstos, status
 * operacional com histórico, deslocamento da base, obras próximas e indicadores do recorte filtrado.
 *
 * <p>Fica separado de {@link ObraUseCases} para não alterar o cadastro atual de obras.</p>
 */
@Service
@RequiredArgsConstructor
public class CarteiraObraUseCases {

    static final double RAIO_PADRAO_KM = 10;
    static final double RAIO_MAXIMO_KM = 100;

    private final ObraRepository repository;
    private final ObraUseCases obraUseCases;
    private final StatusObraUseCases statusUseCases;
    private final DeslocamentoObraService deslocamentoService;
    private final ContratoApi contratoApi;
    private final MunicipioApi municipioApi;
    private final ObraMapper mapper;
    private final ItemOrcamentoObraRepository orcamentoRepository;
    private final ImportacaoObraMapper importacaoMapper;
    private final AuditorAware<String> auditor;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public CarteiraObraResponse buscarCarteira(UUID idObra) {
        return mapper.toCarteira(obraUseCases.buscarEntidade(idObra));
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.OBRAS, allEntries = true)
    public CarteiraObraResponse informarCarteira(UUID idObra, CarteiraObraRequest request) {
        Obra obra = obraUseCases.buscarEntidade(idObra);
        if (request.idContrato() != null && !contratoApi.existeContrato(request.idContrato())) {
            throw new InvarianteVioladaException("O contrato informado não existe ou está inativo.");
        }
        obra.informarCarteira(request.idContrato(), request.prazoRegulado(), request.prioridade(),
                request.lotePrevisto(), request.valorProjeto(),
                request.valorMaterialPrevisto(), request.valorServicoPrevisto());
        obra.informarClasseTensao(request.classeTensao(), usuarioAtual());
        return mapper.toCarteira(repository.save(obra));
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.OBRAS, allEntries = true)
    public CarteiraObraResponse informarStatusOperacional(UUID idObra, StatusOperacionalObraRequest request) {
        Obra obra = obraUseCases.buscarEntidade(idObra);
        StatusObra status = statusUseCases.buscarEntidade(request.idStatusObra());
        obra.informarStatusOperacional(status, request.observacao(), request.dataFimEfetiva());
        return mapper.toCarteira(repository.save(obra));
    }

    @Transactional(readOnly = true)
    public List<HistoricoStatusObraResponse> listarHistoricoStatus(UUID idObra) {
        return obraUseCases.buscarEntidade(idObra).getHistoricoStatus().stream()
                .sorted(Comparator.comparing(HistoricoStatusObra::getDataCriacao,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(h -> new HistoricoStatusObraResponse(
                        h.getId(),
                        h.getStatusAnterior() != null ? h.getStatusAnterior().getId() : null,
                        h.getStatusAnterior() != null ? h.getStatusAnterior().getNome() : null,
                        h.getStatusNovo().getId(),
                        h.getStatusNovo().getNome(),
                        h.getObservacao(),
                        h.getUsuarioCriacao(),
                        h.getDataCriacao()))
                .toList();
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.OBRAS, allEntries = true)
    public CarteiraObraResponse recalcularDeslocamento(UUID idObra) {
        Obra obra = obraUseCases.buscarEntidade(idObra);
        deslocamentoService.recalcular(obra);
        return mapper.toCarteira(repository.save(obra));
    }

    /**
     * Orçamento previsto da obra: totais de material/serviço e os serviços da última importação.
     */
    @Transactional(readOnly = true)
    public OrcamentoObraResponse buscarOrcamento(UUID idObra) {
        Obra obra = obraUseCases.buscarEntidade(idObra);
        List<ItemOrcamentoObra> itens = orcamentoRepository.findByIdObraAndAtivoTrueOrderByCodigoAsc(idObra);
        BigDecimal total = itens.stream().map(ItemOrcamentoObra::getValorTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        return new OrcamentoObraResponse(
                obra.getId(),
                !itens.isEmpty(),
                itens.stream().map(ItemOrcamentoObra::getDataCriacao).filter(Objects::nonNull)
                        .max(Comparator.naturalOrder()).orElse(null),
                obra.getValorMaterialPrevisto(),
                obra.getValorServicoPrevisto(),
                total,
                itens.stream().map(importacaoMapper::toItemOrcamento).toList());
    }

    /**
     * Obras ativas num raio (km, em linha reta) das coordenadas da obra, da mais próxima para a mais distante.
     * Obra sem coordenadas devolve lista vazia.
     */
    @Transactional(readOnly = true)
    public List<ObraProximaResponse> listarObrasProximas(UUID idObra, Double raioKm) {
        Obra obra = obraUseCases.buscarEntidade(idObra);
        if (!obra.possuiCoordenadas()) {
            return List.of();
        }
        double raio = raioKm == null || raioKm <= 0 ? RAIO_PADRAO_KM : Math.min(raioKm, RAIO_MAXIMO_KM);
        double deltaLat = raio / 111.32;
        double deltaLng = raio / (111.32 * Math.max(0.01, Math.cos(Math.toRadians(obra.getLatitude()))));
        Coordenada origem = new Coordenada(obra.getLatitude(), obra.getLongitude());

        Map<UUID, String> municipios = new HashMap<>();
        return repository.buscarNoRetangulo(obra.getId(),
                        obra.getLatitude() - deltaLat, obra.getLatitude() + deltaLat,
                        obra.getLongitude() - deltaLng, obra.getLongitude() + deltaLng)
                .stream()
                .map(o -> Map.entry(o, DeslocamentoObraService.distanciaKm(origem,
                        new Coordenada(o.getLatitude(), o.getLongitude()))))
                .filter(e -> e.getValue() <= raio)
                .sorted(Map.Entry.comparingByValue())
                .map(e -> {
                    Obra o = e.getKey();
                    String municipio = municipios.computeIfAbsent(o.getIdMunicipio(),
                            id -> municipioApi.buscarPorIdResponse(id).map(m -> m.nome()).orElse(null));
                    return new ObraProximaResponse(o.getId(), o.getNumero(), o.getTitulo(),
                            o.getStatusObra() != null ? o.getStatusObra().getNome() : null, municipio,
                            BigDecimal.valueOf(e.getValue()).setScale(2, RoundingMode.HALF_UP),
                            o.getLatitude(), o.getLongitude());
                })
                .toList();
    }

    /**
     * Totais do mesmo recorte da listagem de obras (mesmos filtros).
     */
    @Transactional(readOnly = true)
    public IndicadoresCarteiraResponse calcularIndicadores(ObraFilter filter) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<Obra> root = query.from(Obra.class);
        var predicado = ObraSpecification.filterBy(filter, Sort.unsorted()).toPredicate(root, query, cb);
        query.multiselect(
                cb.count(root),
                cb.sum(root.<BigDecimal>get("valorProjeto")),
                cb.sum(root.<BigDecimal>get("valorMaterialPrevisto")),
                cb.sum(root.<BigDecimal>get("valorServicoPrevisto")),
                cb.countDistinct(root.get("idMunicipio")));
        if (predicado != null) {
            query.where(predicado);
        }
        query.orderBy(List.of());
        query.distinct(false);
        Tuple t = entityManager.createQuery(query).getSingleResult();
        return new IndicadoresCarteiraResponse(
                valorLong(t.get(0)),
                valorDecimal(t.get(1)),
                valorDecimal(t.get(2)),
                valorDecimal(t.get(3)),
                valorLong(t.get(4)));
    }

    private String usuarioAtual() {
        return auditor.getCurrentAuditor().orElse("SYSTEM");
    }

    private static long valorLong(Object valor) {
        return valor instanceof Number n ? n.longValue() : 0L;
    }

    private static BigDecimal valorDecimal(Object valor) {
        return valor instanceof BigDecimal b ? b.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
    }
}
