package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.ColaboradorResponse;
import com.nexo.app.modules.sst.application.dto.ApuracaoMetaPeriodo;
import com.nexo.app.modules.sst.domain.EscopoMeta;
import com.nexo.app.modules.sst.domain.MetaInspecao;
import com.nexo.app.modules.sst.infrastructure.persistence.MetaInspecaoRepository;
import com.nexo.app.shared.application.SelecaoMultipla;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Converte as metas cadastradas na meta esperada de um intervalo qualquer.
 *
 * <p>Como as janelas de apuração (mensal, trimestral…) quase nunca coincidem com o
 * período consultado no dashboard, a meta de cada colaborador é rateada por dia:
 * a quantidade exigida é diluída pelos dias da janela e somada apenas nos dias que
 * caem dentro do intervalo. Assim a meta pode ser comparada ao realizado em qualquer
 * recorte — dia, mês, ano ou inspetor.</p>
 *
 * <p>Em cada dia vale a meta individual do colaborador quando houver; caso contrário,
 * a meta da função que ele exerce.</p>
 */
@Service
@RequiredArgsConstructor
public class ApurarMetasPeriodoUseCase {

    private final MetaInspecaoRepository metaRepository;
    private final ColaboradorApi colaboradorApi;

    /**
     * Os quatro recortes aceitam múltipla seleção; vazios ou nulos, não restringem nada.
     *
     * @param idsInspetor        restringe a apuração aos usuários informados
     * @param idsBaseOperacional restringe a apuração aos colaboradores dessas bases
     * @param idsCentroCusto     restringe a apuração aos colaboradores cujas bases
     *                           pertencem a esses centros de custo
     * @param idsTipoInspecao    restringe a apuração aos tipos de inspeção informados
     */
    @Transactional(readOnly = true)
    public ApuracaoMetaPeriodo apurar(LocalDate inicio, LocalDate fim, Collection<UUID> idsInspetor,
                                      Collection<UUID> idsBaseOperacional, Collection<UUID> idsCentroCusto) {
        return apurar(inicio, fim, idsInspetor, idsBaseOperacional, idsCentroCusto, null);
    }

    @Transactional(readOnly = true)
    public ApuracaoMetaPeriodo apurar(LocalDate inicio, LocalDate fim, Collection<UUID> idsInspetor,
                                      Collection<UUID> idsBaseOperacional, Collection<UUID> idsCentroCusto,
                                      Collection<UUID> idsTipoInspecao) {
        List<MetaInspecao> vigentes = metaRepository.findByAtivoTrue().stream()
                .filter(meta -> SelecaoMultipla.contem(idsTipoInspecao, meta.getIdTipoInspecao()))
                .filter(meta -> meta.vigenteEmAlgumDiaEntre(inicio, fim))
                .toList();

        if (vigentes.isEmpty()) {
            return ApuracaoMetaPeriodo.vazia();
        }

        Map<UUID, List<MetaInspecao>> porFuncao = agrupar(vigentes, EscopoMeta.FUNCAO);
        Map<UUID, List<MetaInspecao>> porColaborador = agrupar(vigentes, EscopoMeta.COLABORADOR);
        Map<UUID, ColaboradorResponse> colaboradores = carregarColaboradores(porFuncao, porColaborador);

        BigDecimal metaTotal = BigDecimal.ZERO;
        Map<LocalDate, BigDecimal> metaPorDia = new LinkedHashMap<>();
        Map<UUID, BigDecimal> metaPorInspetor = new HashMap<>();
        long comMeta = 0;
        long semAcesso = 0;

        for (ColaboradorResponse colaborador : colaboradores.values()) {
            if (!aplicaFiltros(colaborador, idsInspetor, idsBaseOperacional, idsCentroCusto)) {
                continue;
            }

            BigDecimal metaDoColaborador = BigDecimal.ZERO;
            for (LocalDate cursor = inicio; !cursor.isAfter(fim); cursor = cursor.plusDays(1)) {
                LocalDate dia = cursor;
                BigDecimal metaDoDia = metaAplicavel(colaborador, porFuncao, porColaborador, dia)
                        .flatMap(meta -> meta.metaDiariaEm(dia))
                        .orElse(BigDecimal.ZERO);

                if (metaDoDia.signum() > 0) {
                    metaDoColaborador = metaDoColaborador.add(metaDoDia);
                    metaPorDia.merge(dia, metaDoDia, BigDecimal::add);
                }
            }

            if (metaDoColaborador.signum() == 0) {
                continue;
            }

            comMeta++;
            metaTotal = metaTotal.add(metaDoColaborador);
            if (colaborador.idUsuario() == null) {
                semAcesso++;
            } else {
                metaPorInspetor.merge(colaborador.idUsuario(), metaDoColaborador, BigDecimal::add);
            }
        }

        return new ApuracaoMetaPeriodo(metaTotal, metaPorDia, metaPorInspetor, comMeta, semAcesso);
    }

    /** A meta individual do colaborador prevalece sobre a da função dele. */
    private Optional<MetaInspecao> metaAplicavel(ColaboradorResponse colaborador,
                                                 Map<UUID, List<MetaInspecao>> porFuncao,
                                                 Map<UUID, List<MetaInspecao>> porColaborador,
                                                 LocalDate dia) {
        Optional<MetaInspecao> individual = vigenteEm(porColaborador.get(colaborador.id()), dia);
        if (individual.isPresent()) {
            return individual;
        }
        return colaborador.funcao() == null
                ? Optional.empty()
                : vigenteEm(porFuncao.get(colaborador.funcao().id()), dia);
    }

    private Optional<MetaInspecao> vigenteEm(List<MetaInspecao> metas, LocalDate dia) {
        return metas == null ? Optional.empty()
                : metas.stream().filter(meta -> meta.estaVigenteEm(dia)).findFirst();
    }

    private Map<UUID, List<MetaInspecao>> agrupar(List<MetaInspecao> metas, EscopoMeta escopo) {
        Map<UUID, List<MetaInspecao>> agrupado = new HashMap<>();
        metas.stream()
                .filter(meta -> meta.getEscopo() == escopo)
                .forEach(meta -> agrupado
                        .computeIfAbsent(escopo == EscopoMeta.FUNCAO ? meta.getIdFuncao() : meta.getIdColaborador(),
                                id -> new ArrayList<>())
                        .add(meta));
        return agrupado;
    }

    private Map<UUID, ColaboradorResponse> carregarColaboradores(Map<UUID, List<MetaInspecao>> porFuncao,
                                                                 Map<UUID, List<MetaInspecao>> porColaborador) {
        Map<UUID, ColaboradorResponse> colaboradores = new LinkedHashMap<>();
        porFuncao.keySet().forEach(idFuncao -> colaboradorApi.listarAtivosPorFuncao(idFuncao)
                .forEach(colaborador -> colaboradores.put(colaborador.id(), colaborador)));
        porColaborador.keySet().forEach(idColaborador -> colaboradorApi.buscarPorIdResponse(idColaborador)
                .filter(ColaboradorResponse::ativo)
                .ifPresent(colaborador -> colaboradores.put(colaborador.id(), colaborador)));
        return colaboradores;
    }

    private boolean aplicaFiltros(ColaboradorResponse colaborador, Collection<UUID> idsInspetor,
                                  Collection<UUID> idsBaseOperacional, Collection<UUID> idsCentroCusto) {
        return SelecaoMultipla.contem(idsInspetor, colaborador.idUsuario())
                && SelecaoMultipla.contem(idsBaseOperacional, idBaseOperacional(colaborador))
                && SelecaoMultipla.contem(idsCentroCusto, idCentroCusto(colaborador));
    }

    private UUID idBaseOperacional(ColaboradorResponse colaborador) {
        return colaborador.baseOperacional() == null ? null : colaborador.baseOperacional().id();
    }

    private UUID idCentroCusto(ColaboradorResponse colaborador) {
        if (colaborador.baseOperacional() == null || colaborador.baseOperacional().centroFaturamento() == null) {
            return null;
        }
        return colaborador.baseOperacional().centroFaturamento().id();
    }
}
