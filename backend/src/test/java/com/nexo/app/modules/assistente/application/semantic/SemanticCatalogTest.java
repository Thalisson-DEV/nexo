package com.nexo.app.modules.assistente.application.semantic;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SemanticCatalogTest {

    @Test
    void encontraSaidaDeMaterialPeloNomeEPorSinonimos() {
        SemanticCatalog catalogo = new SemanticCatalog(mock(JdbcTemplate.class));

        for (String busca : new String[] { "saída de material", "saidas", "consumo", "material usado" }) {
            assertThat(catalogo.buscarGlossario(busca))
                    .anySatisfy(termo -> {
                        assertThat(termo.termo()).isEqualTo("Saída de material");
                        assertThat(termo.fontes()).contains("movimentacoes");
                        assertThat(termo.definicao()).contains("ENTREGA", "APLICACAO", "RETIRADA", "BAIXA_AVULSA")
                                .contains("DEVOLUCAO", "ESTORNO", "TRANSFERENCIA", "ENTRADA_AVULSA");
                    });
        }
    }
}
