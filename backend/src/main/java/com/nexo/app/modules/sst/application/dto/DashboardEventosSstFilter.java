package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.GranularidadeDashboard;
import com.nexo.app.modules.sst.domain.StatusEventoSst;
import com.nexo.app.modules.sst.domain.TipoEventoSst;
import com.nexo.app.shared.application.SelecaoMultipla;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Recortes do dashboard de acidentalidade. Sem {@code inicio}/{@code fim}, o período
 * padrão é o mês corrente.
 *
 * <p>Os recortes são traduzidos para o mesmo {@link EventoSstFilter} da listagem por
 * {@link #paraListagem()}: dashboard, tabela e exportação passam pela mesma
 * {@code Specification}, e por isso não têm como discordar sobre o que entra na
 * conta.</p>
 */
public record DashboardEventosSstFilter(

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        OffsetDateTime inicio,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        OffsetDateTime fim,

        @Parameter(description = "Recorte da série (DIA, MES ou ANO). Omitido, é escolhido pelo tamanho do período.")
        GranularidadeDashboard granularidade,

        List<TipoEventoSst> tipo,
        List<UUID> idClassificacao,
        List<UUID> idAtividade,
        List<UUID> idGestor,
        List<UUID> idProcesso,
        List<UUID> idBaseOperacional,

        @Parameter(description = "Centro de custo (centro de faturamento) das bases operacionais")
        List<UUID> idCentroCusto,

        List<UUID> idEquipe
) {

    public DashboardEventosSstFilter {
        tipo = SelecaoMultipla.normalizar(tipo);
        idClassificacao = SelecaoMultipla.normalizar(idClassificacao);
        idAtividade = SelecaoMultipla.normalizar(idAtividade);
        idGestor = SelecaoMultipla.normalizar(idGestor);
        idProcesso = SelecaoMultipla.normalizar(idProcesso);
        idBaseOperacional = SelecaoMultipla.normalizar(idBaseOperacional);
        idCentroCusto = SelecaoMultipla.normalizar(idCentroCusto);
        idEquipe = SelecaoMultipla.normalizar(idEquipe);
    }

    public static DashboardEventosSstFilter vazio() {
        return new DashboardEventosSstFilter(null, null, null, null, null, null, null, null, null, null, null);
    }

    /** Mesmos recortes, na forma que a listagem e a exportação entendem. */
    public EventoSstFilter paraListagem() {
        return recorte(tipo, null);
    }

    /**
     * Recorte com os tipos trocados — usado pelos painéis que são sobre uma classe de
     * evento por definição, como "dias sem acidente", que é sobre lesão. Respeitar ali um
     * filtro de "quase-acidente" esvaziaria a tela sem explicação.
     */
    public EventoSstFilter paraListagemComTipos(Collection<TipoEventoSst> tipos) {
        return recorte(List.copyOf(tipos), null);
    }

    /** Recorte restrito ao que ainda está em aberto, em qualquer data. */
    public EventoSstFilter paraListagemAbertos() {
        return recorte(tipo, List.of(StatusEventoSst.REGISTRADO, StatusEventoSst.EM_TRATATIVA));
    }

    /**
     * Os mesmos recortes organizacionais, na forma que a listagem de <b>desvios</b>
     * entende — é o que permite à base da pirâmide respeitar o filtro do dashboard.
     * O período é o da inspeção de origem, que é quando o desvio foi apontado.
     */
    public DesvioSegurancaFilter paraDesvios(OffsetDateTime inicio, OffsetDateTime fim) {
        return new DesvioSegurancaFilter(
                null,   // status
                null,   // apenasPendentes
                null,   // gravidade
                null,   // apenasVencidos
                null,   // idInspetor
                null,   // idColaborador
                null,   // idFormulario
                null,   // idPergunta
                idProcesso,
                null,   // idProcessoInspetor
                idBaseOperacional,
                idCentroCusto,
                idEquipe,
                null,   // idVeiculo
                inicio, // dataInspecaoDe
                fim,    // dataInspecaoAte
                null,   // prazoDe
                null,   // prazoAte
                null,   // semPrazo
                null,   // dataResolucaoDe
                null,   // dataResolucaoAte
                null);  // buscaGlobal
    }

    private EventoSstFilter recorte(List<TipoEventoSst> tipos, List<StatusEventoSst> status) {
        return new EventoSstFilter(
                tipos, status, idClassificacao, idAtividade, idGestor, idProcesso, idBaseOperacional,
                idCentroCusto, idEquipe, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null);
    }
}
