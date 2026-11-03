package com.nexo.app.modules.obras.domain;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentacaoTecnicaTest {

    private static final UUID OBRA = UUID.randomUUID();
    private static final UUID PONTO = UUID.randomUUID();

    private static final EstruturaChecklist ESTRUTURA = new EstruturaChecklist(List.of(new EstruturaChecklist.Grupo("Acesso", List.of(
            new EstruturaChecklist.Pergunta("p1", "Acesso livre?", EstruturaChecklist.TipoPergunta.ESCOLHA_UNICA, true, false, List.of("Sim", "Não")),
            new EstruturaChecklist.Pergunta("p2", "Altura do poste", EstruturaChecklist.TipoPergunta.NUMERO, true, true, null),
            new EstruturaChecklist.Pergunta("p3", "Observação", EstruturaChecklist.TipoPergunta.TEXTO, false, false, null)))));

    private static RespostasChecklist.Informada resposta(String id, String valor, String... selecionadas) {
        return new RespostasChecklist.Informada(id, valor, List.of(selecionadas), false);
    }

    private static ModeloChecklistObra modelo(ModeloChecklistObra.Finalidade finalidade) {
        return new ModeloChecklistObra(finalidade, "Modelo", null, ESTRUTURA);
    }

    @Test
    void modeloExigeGruposPerguntasEOpcoes() {
        assertThatThrownBy(() -> new ModeloChecklistObra(ModeloChecklistObra.Finalidade.AS_BUILT, "M", null, new EstruturaChecklist(List.of())))
                .isInstanceOf(InvarianteVioladaException.class).hasMessageContaining("grupo");
        assertThatThrownBy(() -> new ModeloChecklistObra(ModeloChecklistObra.Finalidade.AS_BUILT, "M", null, new EstruturaChecklist(List.of(
                new EstruturaChecklist.Grupo("G", List.of(new EstruturaChecklist.Pergunta("p", "Tipo?", EstruturaChecklist.TipoPergunta.ESCOLHA_UNICA, true, false, List.of("Único"))))))))
                .isInstanceOf(InvarianteVioladaException.class).hasMessageContaining("duas opções");
        assertThatThrownBy(() -> new ModeloChecklistObra(ModeloChecklistObra.Finalidade.AS_BUILT, "M", null, new EstruturaChecklist(List.of(
                new EstruturaChecklist.Grupo("G", List.of(
                        new EstruturaChecklist.Pergunta("p", "A", EstruturaChecklist.TipoPergunta.TEXTO, true, false, null),
                        new EstruturaChecklist.Pergunta("p", "B", EstruturaChecklist.TipoPergunta.TEXTO, true, false, null)))))))
                .isInstanceOf(InvarianteVioladaException.class).hasMessageContaining("identificador");
    }

    @Test
    void respostasGuardamOTextoDaPerguntaEListamPendencias() {
        RespostasChecklist respostas = RespostasChecklist.de(ESTRUTURA, List.of(resposta("p1", null, "Sim")));

        assertThat(respostas.itens()).hasSize(3);
        assertThat(respostas.itens().get(0).enunciado()).isEqualTo("Acesso livre?");
        assertThat(respostas.itens().get(0).grupo()).isEqualTo("Acesso");
        assertThat(respostas.pendencias()).containsExactly("Altura do poste");
    }

    @Test
    void naoSeAplicaContaComoRespostaSoOndeOModeloAceita() {
        RespostasChecklist respostas = RespostasChecklist.de(ESTRUTURA, List.of(resposta("p1", null, "Não"),
                new RespostasChecklist.Informada("p2", null, List.of(), true)));

        assertThat(respostas.pendencias()).isEmpty();
        assertThat(respostas.itens().get(1).resposta()).isEqualTo("Não se aplica");
        assertThatThrownBy(() -> RespostasChecklist.de(ESTRUTURA, List.of(new RespostasChecklist.Informada("p1", null, List.of(), true))))
                .isInstanceOf(InvarianteVioladaException.class).hasMessageContaining("Não se aplica");
    }

    @Test
    void recusaRespostaInvalida() {
        assertThatThrownBy(() -> RespostasChecklist.de(ESTRUTURA, List.of(resposta("p1", null, "Talvez"))))
                .isInstanceOf(InvarianteVioladaException.class);
        assertThatThrownBy(() -> RespostasChecklist.de(ESTRUTURA, List.of(resposta("p2", "alto"))))
                .isInstanceOf(InvarianteVioladaException.class);
    }

    @Test
    void visitaSoSalvaCompletaEComModeloDeVisita() {
        ModeloChecklistObra modelo = modelo(ModeloChecklistObra.Finalidade.VISITA_PREVIA);
        RespostasChecklist incompleta = RespostasChecklist.de(ESTRUTURA, List.of());
        RespostasChecklist completa = RespostasChecklist.de(ESTRUTURA, List.of(resposta("p1", null, "Sim"), resposta("p2", "11")));

        assertThatThrownBy(() -> new VisitaPreviaObra(OBRA, modelo, LocalDate.now(), "Ana", null, incompleta))
                .isInstanceOf(InvarianteVioladaException.class).hasMessageContaining("Acesso livre?");
        assertThatThrownBy(() -> new VisitaPreviaObra(OBRA, modelo(ModeloChecklistObra.Finalidade.AS_BUILT), LocalDate.now(), "Ana", null, completa))
                .isInstanceOf(InvarianteVioladaException.class);
        assertThat(new VisitaPreviaObra(OBRA, modelo, LocalDate.now(), "Ana", null, completa).getTituloModelo()).isEqualTo("Modelo");
    }

    @Test
    void fichaPodeFicarPelaMetadeESabeSeEstaCompleta() {
        ModeloChecklistObra modelo = modelo(ModeloChecklistObra.Finalidade.AS_BUILT);
        FichaAsBuilt ficha = new FichaAsBuilt(OBRA, PONTO, modelo, RespostasChecklist.de(ESTRUTURA, List.of(resposta("p1", null, "Sim"))));
        assertThat(ficha.isCompleta()).isFalse();

        ficha.preencher(modelo, RespostasChecklist.de(ESTRUTURA, List.of(resposta("p1", null, "Sim"), resposta("p2", "11"))));
        assertThat(ficha.isCompleta()).isTrue();
    }

    @Test
    void soReligadorTemTrafoAuxiliar() {
        assertThat(new EquipamentoObra(OBRA, PONTO, EquipamentoObra.Tipo.RELIGADOR, true, null).isTrafoAuxiliar()).isTrue();
        assertThatThrownBy(() -> new EquipamentoObra(OBRA, PONTO, EquipamentoObra.Tipo.REGULADOR, true, null))
                .isInstanceOf(InvarianteVioladaException.class);
    }
}
