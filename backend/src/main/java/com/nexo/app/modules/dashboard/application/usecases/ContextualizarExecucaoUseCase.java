package com.nexo.app.modules.dashboard.application.usecases;

import com.nexo.app.modules.contrato.ContratoApi;
import com.nexo.app.modules.dashboard.application.dto.ContextoExecucaoResponse;
import com.nexo.app.modules.dashboard.application.dto.ContextoExecucaoResponse.DiaDaEquipe;
import com.nexo.app.modules.dashboard.application.dto.ContextoExecucaoResponse.ReferenciaEquipe;
import com.nexo.app.modules.dashboard.application.dto.ContextoExecucaoResponse.ValorAcao;
import com.nexo.app.modules.dashboard.domain.CalendarioOperacional;
import com.nexo.app.modules.execucao.ExecucaoComercialApi;
import com.nexo.app.modules.execucao.TurnoApi;
import com.nexo.app.modules.execucao.application.dto.ExecucaoComercialResponse;
import com.nexo.app.modules.execucao.application.dto.LancamentoDoTurno;
import com.nexo.app.modules.execucao.application.dto.LancamentoResumido;
import com.nexo.app.modules.execucao.application.dto.TurnoResponse;
import com.nexo.app.modules.rh.EquipeApi;
import com.nexo.app.modules.rh.EquipeResponse;
import com.nexo.app.modules.rh.MetaFaturamentoApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.nexo.app.modules.dashboard.application.usecases.GerarDashboardOperacionalUseCase.media;
import static com.nexo.app.modules.dashboard.application.usecases.GerarDashboardOperacionalUseCase.soma;
import static com.nexo.app.modules.dashboard.domain.CalendarioOperacional.percentual;

/** Monta o contexto do relatório de uma nota (ver {@link ContextoExecucaoResponse}). */
@Service
@RequiredArgsConstructor
public class ContextualizarExecucaoUseCase {

    /** Dias anteriores ao lançamento usados como referência de valor médio da equipe. */
    static final int DIAS_REFERENCIA = 30;

    private final ExecucaoComercialApi execucaoApi;
    private final TurnoApi turnoApi;
    private final EquipeApi equipeApi;
    private final ContratoApi contratoApi;
    private final MetaFaturamentoApi metaFaturamentoApi;

    @Transactional(readOnly = true)
    public ContextoExecucaoResponse executar(UUID idExecucao) {
        ExecucaoComercialResponse execucao = execucaoApi.buscarPorId(idExecucao);
        UUID idEquipe = execucao.equipe() != null ? execucao.equipe().id() : null;
        EquipeResponse equipe = idEquipe != null ? equipeApi.buscarPorIdResponse(idEquipe).orElse(null) : null;
        LocalDate dia = CalendarioOperacional.diaDe(execucao.dataLancamento());

        ContextoExecucaoResponse.Turno turno = null;
        List<LancamentoDoTurno> lancamentosDoTurno = List.of();
        if (execucao.idTurno() != null) {
            TurnoResponse t = turnoApi.buscarPorId(execucao.idTurno());
            turno = new ContextoExecucaoResponse.Turno(t.id(), t.status(), t.dataInicio(), t.dataFim());
            lancamentosDoTurno = execucaoApi.listarLancamentosDoTurno(t.id());
        }

        DiaDaEquipe diaDaEquipe = null;
        ReferenciaEquipe referencia = null;
        if (idEquipe != null) {
            List<LancamentoResumido> doDia = execucaoApi.listarLancamentosResumidos(
                    CalendarioOperacional.inicioDoDia(dia), CalendarioOperacional.inicioDoDia(dia.plusDays(1)),
                    Set.of(idEquipe));
            BigDecimal producaoDia = soma(doDia);
            BigDecimal metaDiaria = equipe != null
                    ? metaFaturamentoApi.cronograma(idEquipe).metaDiaria(dia)
                    : null;
            diaDaEquipe = new DiaDaEquipe(dia, doDia.size(), producaoDia, metaDiaria, percentual(producaoDia, metaDiaria));

            List<LancamentoResumido> anteriores = execucaoApi.listarLancamentosResumidos(
                    CalendarioOperacional.inicioDoDia(dia.minusDays(DIAS_REFERENCIA)),
                    CalendarioOperacional.inicioDoDia(dia), Set.of(idEquipe));
            referencia = new ReferenciaEquipe(DIAS_REFERENCIA, anteriores.size(),
                    anteriores.isEmpty() ? null : media(soma(anteriores), anteriores.size()));
        }

        return new ContextoExecucaoResponse(idExecucao, turno, lancamentosDoTurno, diaDaEquipe, referencia,
                valoresPorAcao(execucao, equipe, dia));
    }

    private List<ValorAcao> valoresPorAcao(ExecucaoComercialResponse execucao, EquipeResponse equipe, LocalDate dia) {
        if (equipe == null || equipe.idContrato() == null || execucao.acoes() == null || execucao.acoes().isEmpty()) {
            return List.of();
        }
        Set<UUID> idsAcoes = execucao.acoes().stream()
                .map(a -> a.acao() != null ? a.acao().id() : null)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return contratoApi.listarAcoesComValor(equipe.idContrato(), dia).stream()
                .filter(item -> idsAcoes.contains(item.idAcao()))
                .map(item -> new ValorAcao(item.idAcao(), item.valor()))
                .toList();
    }
}
