package com.nexo.app.modules.assistente.application;

import com.nexo.app.modules.assistente.application.agent.ContextoCes;
import com.nexo.app.modules.assistente.application.agent.ContextoPainel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConversaPersistenciaContextoTest {

    @Test
    void perguntaSemContextoNaoTrataPainelAntigoComoTelaAtual() {
        ContextoPainel painel = new ContextoPainel("chamados.visao-geral", "Chamados", null, null, null, List.of(), List.of(),
                List.of(), List.of(), List.of(), null, Map.of());
        ContextoCes salvo = new ContextoCes("Chamados", "/chamados/dashboard", "DASHBOARD", "chamados.visao-geral",
                "Chamados", Map.of("Status", "Aberto"), painel);

        ContextoCes usado = ConversaPersistencia.semPainel(salvo);

        assertThat(usado.painel()).isNull();
        assertThat(usado.pagina()).isEqualTo("Chamados");
        assertThat(usado.filtros()).containsEntry("Status", "Aberto");
        assertThat(ConversaPersistencia.semPainel(null)).isNull();
    }
}
