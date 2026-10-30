package com.nexo.app.modules.sst.domain;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes da reordenação dinâmica de grupos, perguntas e opções do formulário,
 * preservando os IDs (operação puramente de apresentação).
 */
class ReordenacaoFormularioTest {

    private void setId(BaseEntity entity, UUID id) {
        ReflectionTestUtils.setField(entity, "id", id);
    }

    @Test
    @DisplayName("Deve reordenar grupos por ID, atualizando a ordem e a lista")
    void deveReordenarGrupos() {
        FormularioInspecao form = new FormularioInspecao("F", "d");
        GrupoPergunta g1 = form.adicionarGrupo("Kit SMC");
        GrupoPergunta g2 = form.adicionarGrupo("EPIs");
        GrupoPergunta g3 = form.adicionarGrupo("Veículo");
        UUID id1 = UUID.randomUUID(), id2 = UUID.randomUUID(), id3 = UUID.randomUUID();
        setId(g1, id1);
        setId(g2, id2);
        setId(g3, id3);

        // Move o terceiro para o topo: [g3, g1, g2]
        form.reordenarGrupos(List.of(id3, id1, id2));

        assertEquals(0, g3.getOrdem());
        assertEquals(1, g1.getOrdem());
        assertEquals(2, g2.getOrdem());
        assertEquals(id3, form.getGrupos().get(0).getId());
        assertEquals(id1, form.getGrupos().get(1).getId());
    }

    @Test
    @DisplayName("Reordenar deve rejeitar lista que não seja exatamente os mesmos itens")
    void deveRejeitarListaInconsistente() {
        FormularioInspecao form = new FormularioInspecao("F", "d");
        GrupoPergunta g1 = form.adicionarGrupo("A");
        GrupoPergunta g2 = form.adicionarGrupo("B");
        setId(g1, UUID.randomUUID());
        setId(g2, UUID.randomUUID());

        // Falta um id / contém id estranho
        assertThrows(InvarianteVioladaException.class,
                () -> form.reordenarGrupos(List.of(g1.getId())));
        assertThrows(InvarianteVioladaException.class,
                () -> form.reordenarGrupos(List.of(g1.getId(), UUID.randomUUID())));
        assertThrows(InvarianteVioladaException.class,
                () -> form.reordenarGrupos(List.of(g1.getId(), g1.getId())));
    }

    @Test
    @DisplayName("Deve reordenar perguntas dentro de um grupo por ID")
    void deveReordenarPerguntas() {
        FormularioInspecao form = new FormularioInspecao("F", "d");
        GrupoPergunta grupo = form.adicionarGrupo("Kit SMC");
        Pergunta p1 = grupo.adicionarPergunta("P1", TipoResposta.TEXTO, false);
        Pergunta p2 = grupo.adicionarPergunta("P2", TipoResposta.TEXTO, false);
        UUID idGrupo = UUID.randomUUID(), idP1 = UUID.randomUUID(), idP2 = UUID.randomUUID();
        setId(grupo, idGrupo);
        setId(p1, idP1);
        setId(p2, idP2);

        form.reordenarPerguntas(idGrupo, List.of(idP2, idP1));

        assertEquals(0, p2.getOrdem());
        assertEquals(1, p1.getOrdem());
        assertEquals(idP2, grupo.getPerguntas().get(0).getId());
    }

    @Test
    @DisplayName("Deve reordenar opções dentro de uma pergunta por ID")
    void deveReordenarOpcoes() {
        FormularioInspecao form = new FormularioInspecao("F", "d");
        GrupoPergunta grupo = form.adicionarGrupo("Kit SMC");
        Pergunta pergunta = grupo.adicionarPergunta("P", TipoResposta.MULTIPLA_ESCOLHA, true);
        OpcaoResposta o1 = pergunta.adicionarOpcao("Sim", false, null);
        OpcaoResposta o2 = pergunta.adicionarOpcao("Não", true, GravidadeDesvio.ALTA);
        UUID idGrupo = UUID.randomUUID(), idPergunta = UUID.randomUUID();
        UUID idO1 = UUID.randomUUID(), idO2 = UUID.randomUUID();
        setId(grupo, idGrupo);
        setId(pergunta, idPergunta);
        setId(o1, idO1);
        setId(o2, idO2);

        form.reordenarOpcoes(idGrupo, idPergunta, List.of(idO2, idO1));

        assertEquals(0, o2.getOrdem());
        assertEquals(1, o1.getOrdem());
        assertEquals(idO2, pergunta.getOpcoes().get(0).getId());
    }
}
