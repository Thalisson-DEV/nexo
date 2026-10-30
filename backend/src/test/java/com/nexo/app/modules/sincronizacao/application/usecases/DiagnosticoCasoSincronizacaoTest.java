package com.nexo.app.modules.sincronizacao.application.usecases;

import com.nexo.app.modules.execucao.TurnoApi;
import com.nexo.app.modules.execucao.application.dto.TurnoResponse;
import com.nexo.app.modules.execucao.domain.StatusTurno;
import com.nexo.app.modules.rh.EquipeApi;
import com.nexo.app.modules.sincronizacao.application.dto.SincronizacaoDtos.AcaoRecomendada;
import com.nexo.app.modules.sincronizacao.domain.CasoSincronizacao;
import com.nexo.app.modules.sincronizacao.domain.OperacaoSincronizacao;
import com.nexo.app.modules.sincronizacao.domain.TipoOperacaoSincronizacao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DiagnosticoCasoSincronizacaoTest {
    private static final String SEM_TURNO = "Erro informado pelo aplicativo: Falha ao lançar execução: Não é possível lançar uma execução sem um turno aberto para a equipe.";
    private static final ZoneOffset BRASILIA = ZoneOffset.ofHours(-3);

    private final TurnoApi turnoApi = mock(TurnoApi.class);
    private final EquipeApi equipeApi = mock(EquipeApi.class);
    private final DiagnosticoCasoSincronizacao diagnostico = new DiagnosticoCasoSincronizacao(turnoApi, equipeApi);
    private final UUID idEquipe = UUID.randomUUID();
    private final UUID idTurnoOntem = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(equipeApi.buscarPublicaPorId(idEquipe)).thenReturn(Optional.of(
                new EquipeApi.EquipePublica(idEquipe, "4FK50E", true, null, null, null, null)));
    }

    @Test
    void notaDeAntesDaAberturaDoTurnoDoMesmoDiaPedeSoReenvio() {
        var hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        var lancamento = hoje.atTime(9, 48).atOffset(BRASILIA);
        when(turnoApi.buscarPorId(idTurnoOntem)).thenReturn(turno(idTurnoOntem, hoje.minusDays(1).atTime(9, 10).atOffset(BRASILIA), true));
        when(turnoApi.buscarAtivoPorEquipe(idEquipe)).thenReturn(Optional.of(turno(UUID.randomUUID(), hoje.atTime(10, 4).atOffset(BRASILIA), false)));

        var d = diagnostico.completo(caso(TipoOperacaoSincronizacao.EXECUCAO, SEM_TURNO), payload(lancamento));

        assertThat(d.acao()).isEqualTo(AcaoRecomendada.REENVIAR);
        assertThat(d.titulo()).isEqualTo("Nota lançada antes de abrir o turno");
        assertThat(d.explicacao()).contains("às 09:48", "equipe 4FK50E", "sistema fechou no corte");
        assertThat(d.orientacao()).isEqualTo("A equipe 4FK50E abriu o turno às 10:04. Reenvie sem alterar: a nota entra nesse turno.");
    }

    @Test
    void semTurnoAbertoHojeAguardaOReenvioAutomatico() {
        var lancamento = LocalDate.now(ZoneId.of("America/Sao_Paulo")).atTime(9, 48).atOffset(BRASILIA);
        when(turnoApi.buscarAtivoPorEquipe(idEquipe)).thenReturn(Optional.empty());

        var d = diagnostico.completo(caso(TipoOperacaoSincronizacao.EXECUCAO, SEM_TURNO), payload(lancamento));

        assertThat(d.acao()).isEqualTo(AcaoRecomendada.AGUARDAR);
    }

    @Test
    void turnoAbertoEmOutroDiaPedeConferenciaAntesDeReenviar() {
        var hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        when(turnoApi.buscarAtivoPorEquipe(idEquipe)).thenReturn(Optional.of(turno(UUID.randomUUID(), hoje.atTime(7, 0).atOffset(BRASILIA), false)));

        var d = diagnostico.completo(caso(TipoOperacaoSincronizacao.EXECUCAO, SEM_TURNO), payload(hoje.minusDays(2).atTime(9, 48).atOffset(BRASILIA)));

        assertThat(d.acao()).isEqualTo(AcaoRecomendada.CONFERIR);
        assertThat(d.orientacao()).contains("outro dia");
    }

    @Test
    void resumoDaListagemNaoConsultaOutrosModulos() {
        var d = diagnostico.resumo(caso(TipoOperacaoSincronizacao.EXECUCAO, SEM_TURNO), payload(OffsetDateTime.now()));

        assertThat(d.codigo()).isEqualTo("SEM_TURNO_ABERTO");
        verifyNoInteractions(turnoApi, equipeApi);
    }

    @Test
    void serieRepetidaApontaAsDuasLinhasEPedeCorrecao() {
        var p = Map.<String, Object>of("idEquipe", idEquipe.toString(), "materiais", List.of(
                Map.of("idMaterial", UUID.randomUUID().toString(), "numSerie", "1010098751", "operacao", "APLICADO"),
                Map.of("idMaterial", UUID.randomUUID().toString(), "numSerie", "1010098751", "operacao", "RETIRADO")));

        var d = diagnostico.completo(caso(TipoOperacaoSincronizacao.EXECUCAO,
                "Erro informado pelo aplicativo: Falha ao lançar execução: O número de série '1010098751' já foi registrado nesta execução."), p);

        assertThat(d.acao()).isEqualTo(AcaoRecomendada.CORRIGIR);
        assertThat(d.explicacao()).isEqualTo("A série 1010098751 foi informada mais de uma vez na mesma nota como aplicada e como retirada. Cada série entra uma vez só.");
    }

    @Test
    void erroDesconhecidoMostraAMensagemSemPrefixosTecnicos() {
        var d = diagnostico.completo(caso(TipoOperacaoSincronizacao.SOLICITACAO,
                "Erro informado pelo aplicativo: Falha ao enviar solicitação: InvarianteVioladaException: Quantidade acima do limite."), Map.of());

        assertThat(d.acao()).isEqualTo(AcaoRecomendada.CONFERIR);
        assertThat(d.explicacao()).isEqualTo("Quantidade acima do limite.");
    }

    @Test
    void ultimaFalhaDeReprocessamentoValeMaisQueOMotivoOriginal() {
        var c = caso(TipoOperacaoSincronizacao.EXECUCAO, SEM_TURNO);
        c.corrigir("Falha no reprocessamento: O número de série 'X1' já foi registrado nesta execução.");

        assertThat(diagnostico.resumo(c, Map.of()).codigo()).isEqualTo("SERIE_REPETIDA");
    }

    private Map<String, Object> payload(OffsetDateTime lancamento) {
        return Map.of("idEquipe", idEquipe.toString(), "idTurno", idTurnoOntem.toString(), "dataLancamento", lancamento.toString());
    }

    private CasoSincronizacao caso(TipoOperacaoSincronizacao tipo, String motivo) {
        var op = new OperacaoSincronizacao(UUID.randomUUID(), null, tipo, UUID.randomUUID(), OffsetDateTime.now(), Map.of(), "hash");
        return new CasoSincronizacao(op, motivo);
    }

    private TurnoResponse turno(UUID id, OffsetDateTime inicio, boolean encerradoPeloSistema) {
        return new TurnoResponse(id, idEquipe, UUID.randomUUID(), inicio, encerradoPeloSistema ? inicio.plusHours(8) : null,
                encerradoPeloSistema ? StatusTurno.FECHADO : StatusTurno.ABERTO, null, Set.of(), encerradoPeloSistema, null);
    }
}
