package com.nexo.app.modules.dashboard.application.usecases;

import com.nexo.app.modules.dashboard.application.dto.NotaDoRecorteResponse;
import com.nexo.app.modules.dashboard.application.dto.NotasDoRecorteFilter;
import com.nexo.app.modules.dashboard.domain.CalendarioOperacional;
import com.nexo.app.modules.execucao.ExecucaoComercialApi;
import com.nexo.app.modules.execucao.application.dto.ExecucaoComercialResponse;
import com.nexo.app.modules.rh.EquipeResponse;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Notas que compõem os números dos dashboards operacional e de turnos — a camada de
 * origem do drill-down. Usa o mesmo escopo dos dashboards (equipes ativas do recorte,
 * notas concluídas, dia civil de São Paulo), então a soma das notas fecha com o cartão
 * ou o ponto do gráfico que o usuário abriu.
 */
@Service
public class ConsultarNotasDoRecorteUseCase {

    /** Colunas que o banco ordena; padrão: lançamento mais recente primeiro. */
    static final Set<String> ORDENAVEIS = Set.of("dataLancamento", "valorTotalFaturado");
    static final int TAMANHO_MAXIMO = 500;

    private static final Sort ORDEM_PADRAO = Sort.by(Sort.Direction.DESC, "dataLancamento");

    private final EquipesDoRecorte equipesDoRecorte;
    private final ExecucaoComercialApi execucaoApi;
    private final Clock clock;

    @Autowired
    public ConsultarNotasDoRecorteUseCase(EquipesDoRecorte equipesDoRecorte, ExecucaoComercialApi execucaoApi) {
        this(equipesDoRecorte, execucaoApi, Clock.system(CalendarioOperacional.FUSO));
    }

    ConsultarNotasDoRecorteUseCase(EquipesDoRecorte equipesDoRecorte, ExecucaoComercialApi execucaoApi, Clock clock) {
        this.equipesDoRecorte = equipesDoRecorte;
        this.execucaoApi = execucaoApi;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<NotaDoRecorteResponse> executar(NotasDoRecorteFilter filter, Pageable pageable) {
        LocalDate hoje = LocalDate.now(clock);
        LocalDate inicio = filter.inicio() != null ? filter.inicio() : (filter.fim() != null ? filter.fim() : hoje);
        LocalDate fim = filter.fim() != null ? filter.fim() : inicio;
        if (fim.isBefore(inicio)) {
            throw new InvarianteVioladaException("A data final do período não pode ser anterior à inicial.");
        }
        if (ChronoUnit.DAYS.between(inicio, fim) >= GerarDashboardOperacionalUseCase.PERIODO_MAXIMO_DIAS) {
            throw new InvarianteVioladaException("O período das notas não pode passar de um ano.");
        }

        List<UUID> equipes = equipesDoRecorte.listar(filter.idBaseOperacional(), filter.idProcesso()).stream()
                .map(EquipeResponse::id)
                .filter(id -> filter.idEquipe() == null || filter.idEquipe().equals(id))
                .toList();

        return PageResponse.fromPage(execucaoApi.listarConcluidasDasEquipes(
                        equipes,
                        CalendarioOperacional.inicioDoDia(inicio),
                        CalendarioOperacional.inicioDoDia(fim.plusDays(1)),
                        filter.idTipoServico(),
                        Boolean.TRUE.equals(filter.semTipoServico()),
                        paginacaoSegura(pageable))
                .map(ConsultarNotasDoRecorteUseCase::linha));
    }

    static NotaDoRecorteResponse linha(ExecucaoComercialResponse nota) {
        var equipe = nota.equipe();
        var tipo = nota.tipoServico();
        List<String> acoes = nota.acoes() == null ? List.of() : nota.acoes().stream()
                .map(a -> a.acao() == null ? null : a.acao().codigo() + " · " + a.acao().nome())
                .filter(Objects::nonNull)
                .toList();
        return new NotaDoRecorteResponse(
                nota.id(),
                nota.referenciaNota(),
                nota.idTurno(),
                equipe != null ? equipe.id() : null,
                equipe != null ? equipe.codigo() : null,
                equipe != null && equipe.baseOperacional() != null ? equipe.baseOperacional().nome() : null,
                equipe != null && equipe.processo() != null ? equipe.processo().nome() : null,
                tipo != null ? tipo.id() : null,
                tipo != null ? tipo.codigo() : null,
                tipo != null ? tipo.descricao() : null,
                nota.dataLancamento(),
                nota.valorTotalFaturado(),
                acoes,
                nota.materiais() == null ? 0 : nota.materiais().size(),
                nota.latitude(),
                nota.longitude());
    }

    /** Ordena só por colunas do banco e limita o tamanho da página. */
    static Pageable paginacaoSegura(Pageable pageable) {
        Sort sort = Sort.by(pageable.getSort().stream()
                .filter(ordem -> ORDENAVEIS.contains(ordem.getProperty()))
                .toList());
        Sort efetiva = sort.isSorted() ? sort : ORDEM_PADRAO;
        if (pageable.isUnpaged()) {
            return PageRequest.of(0, TAMANHO_MAXIMO, efetiva);
        }
        return PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), TAMANHO_MAXIMO), efetiva);
    }
}
