package com.nexo.app.modules.obras.domain;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LiberacaoExecucaoTest {

    private static final UUID TURNO = UUID.randomUUID();
    private static final UUID OBRA = UUID.randomUUID();
    private static final UUID SERVICO = UUID.randomUUID();
    private static final UUID RESPONSAVEL = UUID.randomUUID();
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate HOJE = LocalDate.now(FUSO);

    private static FormularioApr formulario(FormularioApr.Resposta resposta) {
        return new FormularioApr("P1", List.of("Choque elétrico"), null, List.of("Área sinalizada"), null,
                List.of(new FormularioApr.Verificacao("Área isolada?", resposta)), List.of(RESPONSAVEL));
    }

    private static AprTurno apr(FormularioApr formulario) {
        return new AprTurno(TURNO, OBRA, SERVICO, "Instalar poste", RESPONSAVEL, formulario);
    }

    private static SituacaoLiberacao avaliar(UUID obra, List<AprTurno> aprs, List<OcorrenciaTurno> ocorrencias) {
        return SituacaoLiberacao.avaliar(obra, SERVICO, HOJE, FUSO, aprs, ocorrencias);
    }

    @Test
    void semAprAObraFicaPendenteEComRascunhoContinuaBloqueada() {
        assertThat(avaliar(OBRA, List.of(), List.of())).isEqualTo(SituacaoLiberacao.PENDENTE);
        assertThat(avaliar(OBRA, List.of(apr(formulario(FormularioApr.Resposta.SIM))), List.of()))
                .isEqualTo(SituacaoLiberacao.EM_PREENCHIMENTO);
    }

    @Test
    void aprConcluidaLiberaSoASuaObra() {
        AprTurno concluida = apr(formulario(FormularioApr.Resposta.SIM));
        concluida.concluir();

        assertThat(avaliar(OBRA, List.of(concluida), List.of())).isEqualTo(SituacaoLiberacao.LIBERADA);
        assertThat(avaliar(UUID.randomUUID(), List.of(concluida), List.of())).isEqualTo(SituacaoLiberacao.PENDENTE);
    }

    @Test
    void semCondicaoDeExecutarDispensaAAprEPrevaleceSobreAAprAnterior() {
        AprTurno concluida = apr(formulario(FormularioApr.Resposta.SIM));
        concluida.concluir();
        OcorrenciaTurno impedimento = new OcorrenciaTurno(TURNO, OBRA, TipoOcorrenciaTurno.CLIMA, "Chuva forte", true);
        ReflectionTestUtils.setField(impedimento, "dataCriacao", OffsetDateTime.now().plusSeconds(5));

        assertThat(avaliar(OBRA, List.of(), List.of(impedimento))).isEqualTo(SituacaoLiberacao.IMPEDIDA);
        assertThat(avaliar(OBRA, List.of(concluida), List.of(impedimento))).isEqualTo(SituacaoLiberacao.IMPEDIDA);
    }

    @Test
    void cadaServicoDoDiaELiberadoPelaSuaApr() {
        UUID servicoDaTarde = UUID.randomUUID();
        AprTurno daManha = apr(formulario(FormularioApr.Resposta.SIM));
        daManha.concluir();

        assertThat(SituacaoLiberacao.avaliar(OBRA, SERVICO, HOJE, FUSO, List.of(daManha), List.of())).isEqualTo(SituacaoLiberacao.LIBERADA);
        assertThat(SituacaoLiberacao.avaliar(OBRA, servicoDaTarde, HOJE, FUSO, List.of(daManha), List.of())).isEqualTo(SituacaoLiberacao.PENDENTE);

        AprTurno daTarde = new AprTurno(TURNO, OBRA, servicoDaTarde, "Lançar cabo", RESPONSAVEL, formulario(FormularioApr.Resposta.SIM));
        daTarde.concluir();
        assertThat(SituacaoLiberacao.avaliar(OBRA, servicoDaTarde, HOJE, FUSO, List.of(daManha, daTarde), List.of())).isEqualTo(SituacaoLiberacao.LIBERADA);
    }

    @Test
    void aprDeOutroDiaNaoLibera() {
        AprTurno concluida = apr(formulario(FormularioApr.Resposta.SIM));
        concluida.concluir();

        assertThat(SituacaoLiberacao.avaliar(OBRA, SERVICO, HOJE.plusDays(1), FUSO, List.of(concluida), List.of())).isEqualTo(SituacaoLiberacao.PENDENTE);
    }

    @Test
    void aprConcluidaDepoisDoImpedimentoLiberaATarde() {
        OcorrenciaTurno daManha = new OcorrenciaTurno(TURNO, OBRA, TipoOcorrenciaTurno.CLIMA, "Chuva forte", true);
        ReflectionTestUtils.setField(daManha, "dataCriacao", OffsetDateTime.now().minusSeconds(5));
        AprTurno daTarde = apr(formulario(FormularioApr.Resposta.SIM));
        daTarde.concluir();

        assertThat(avaliar(OBRA, List.of(daTarde), List.of(daManha))).isEqualTo(SituacaoLiberacao.LIBERADA);
    }

    @Test
    void ocorrenciaComumNaoBloqueiaEImpedimentoExigeObra() {
        OcorrenciaTurno comum = new OcorrenciaTurno(TURNO, OBRA, TipoOcorrenciaTurno.CLIMA, "Garoa rápida", false);

        assertThat(avaliar(OBRA, List.of(), List.of(comum))).isEqualTo(SituacaoLiberacao.PENDENTE);
        assertThatThrownBy(() -> new OcorrenciaTurno(TURNO, null, TipoOcorrenciaTurno.CLIMA, "Chuva", true))
                .isInstanceOf(InvarianteVioladaException.class);
    }

    @Test
    void naoConcluiComVerificacaoNegativaOuFormularioIncompleto() {
        assertThatThrownBy(() -> apr(formulario(FormularioApr.Resposta.NAO)).concluir())
                .isInstanceOf(InvarianteVioladaException.class).hasMessageContaining("Não");
        assertThatThrownBy(() -> apr(formulario(null)).concluir())
                .isInstanceOf(InvarianteVioladaException.class).hasMessageContaining("verificações");
        assertThatThrownBy(() -> apr(null).concluir())
                .isInstanceOf(InvarianteVioladaException.class).hasMessageContaining("formulário");
        assertThatThrownBy(() -> apr(new FormularioApr(null, List.of(), null, List.of("Área sinalizada"), null,
                List.of(new FormularioApr.Verificacao("Área isolada?", FormularioApr.Resposta.SIM)), List.of(RESPONSAVEL))).concluir())
                .isInstanceOf(InvarianteVioladaException.class).hasMessageContaining("risco");
    }

    @Test
    void aprConcluidaNaoMudaMaisEObraEObrigatoria() {
        AprTurno concluida = apr(formulario(FormularioApr.Resposta.NAO_SE_APLICA));
        concluida.concluir();

        assertThatThrownBy(() -> concluida.informar(OBRA, SERVICO, "Outra", RESPONSAVEL, formulario(FormularioApr.Resposta.SIM)))
                .isInstanceOf(InvarianteVioladaException.class);
        assertThatThrownBy(() -> new AprTurno(TURNO, null, SERVICO, "Instalar poste", RESPONSAVEL, null))
                .isInstanceOf(InvarianteVioladaException.class);
    }
}
