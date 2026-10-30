package com.nexo.app.modules.sst.domain;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Invariantes das atividades pré-cadastradas de SST.
 */
class AtividadeSstTest {

    private static final UUID GESTOR = UUID.randomUUID();
    private static final UUID PROCESSO = UUID.randomUUID();

    @Test
    @DisplayName("Deve normalizar o nome e guardar gestor e processo")
    void deveCriarAtividade() {
        AtividadeSst atividade = new AtividadeSst("  Detonação  ", "  Uso de explosivos  ", GESTOR, PROCESSO);

        assertEquals("Detonação", atividade.getNome());
        assertEquals("detonacao", atividade.getNomeNormalizado());
        assertEquals("Uso de explosivos", atividade.getDescricao());
        assertEquals(GESTOR, atividade.getIdGestor());
        assertEquals(PROCESSO, atividade.getIdProcesso());
        assertTrue(atividade.possuiGestor());
    }

    @Test
    @DisplayName("Deve aceitar atividade sem gestor — a exigência passa para o evento")
    void deveAceitarAtividadeSemGestor() {
        AtividadeSst atividade = new AtividadeSst("Trajeto", null, null, null);

        assertNull(atividade.getIdGestor());
        assertFalse(atividade.possuiGestor());
    }

    @Test
    @DisplayName("Não deve aceitar nome vazio ou curto demais")
    void deveExigirNomeValido() {
        assertThrows(InvarianteVioladaException.class,
                () -> new AtividadeSst("  ", null, GESTOR, null));
        assertThrows(InvarianteVioladaException.class,
                () -> new AtividadeSst("P", null, GESTOR, null));
    }

    @Test
    @DisplayName("Deve permitir trocar o gestor — quem responde muda daqui para a frente")
    void deveTrocarGestor() {
        AtividadeSst atividade = new AtividadeSst("Poda", null, GESTOR, null);
        UUID novoGestor = UUID.randomUUID();

        atividade.atualizar("Poda", null, novoGestor, PROCESSO);

        assertEquals(novoGestor, atividade.getIdGestor());
        assertEquals(PROCESSO, atividade.getIdProcesso());
    }

    @Test
    @DisplayName("Deve permitir remover o gestor da atividade")
    void devePermitirRemoverGestor() {
        AtividadeSst atividade = new AtividadeSst("Varredura", null, GESTOR, null);

        atividade.atualizar("Varredura", null, null, null);

        assertFalse(atividade.possuiGestor());
    }

    @Test
    @DisplayName("Não deve alterar uma atividade desativada")
    void naoDeveAlterarAtividadeDesativada() {
        AtividadeSst atividade = new AtividadeSst("Carga e Descarga", null, GESTOR, null);
        atividade.desativar();

        assertThrows(InvarianteVioladaException.class,
                () -> atividade.atualizar("Carga e Descarga", null, GESTOR, null));
    }
}
