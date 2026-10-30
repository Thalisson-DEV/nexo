package com.nexo.app.modules.financeiro.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guarda-corpo da tabela de condições declarada no bloco estático de
 * {@link CampoCanonico}.
 *
 * <p>Como as condições são código e não cadastro, elas não passam por nenhuma
 * validação em tempo de execução — quem percorre a cadeia de visibilidade
 * (o validador do payload e o gerador de schema) confia que ela é finita e que os
 * valores declarados são alcançáveis. Estes testes são o que sustenta essa
 * confiança.</p>
 */
class CampoCanonicoTest {

    @Test
    @DisplayName("A tabela de condições é acíclica — a recursão de visibilidade sempre termina")
    void tabelaDeCondicoesEhAciclica() {
        for (CampoCanonico partida : CampoCanonico.values()) {
            Set<CampoCanonico> percorridos = new LinkedHashSet<>();
            CampoCanonico atual = partida;

            // Cada campo tem no máximo um governante, então seguir a cadeia até o
            // fim basta: reencontrar um campo já visitado é, por definição, um ciclo.
            while (atual != null) {
                assertTrue(percorridos.add(atual),
                        "Ciclo de visibilidade a partir de " + partida + ": " + percorridos + " → " + atual);
                atual = atual.getCondicao().map(CampoCanonico.Condicao::governante).orElse(null);
            }
        }
    }

    @Test
    @DisplayName("Nenhum campo governa a própria exibição")
    void nenhumCampoDependeDeSiMesmo() {
        for (CampoCanonico campo : CampoCanonico.values()) {
            campo.getCondicao().ifPresent(condicao ->
                    assertFalse(condicao.governante() == campo, campo + " governa a si mesmo."));
        }
    }

    @Test
    @DisplayName("Toda condição declara valores que o governante é capaz de assumir")
    void condicoesUsamValoresAlcancaveisPeloGovernante() {
        for (CampoCanonico campo : CampoCanonico.values()) {
            campo.getCondicao().ifPresent(condicao -> {
                CampoCanonico governante = condicao.governante();
                Set<String> possiveis = switch (governante.getTipoCampo()) {
                    case BOOLEANO -> Set.of("true", "false");
                    case SELECAO_UNICA -> governante.valoresAceitos();
                    // Condicionar a um campo de texto ou número livre tornaria a regra
                    // inverificável: qualquer valor digitado poderia satisfazê-la.
                    default -> Set.of();
                };
                assertTrue(possiveis.containsAll(condicao.valores()),
                        campo + " depende de " + governante + " assumir " + condicao.valores()
                                + ", mas ele só admite " + possiveis);
            });
        }
    }

    @Test
    @DisplayName("O governante se aplica a todo domínio em que o campo dependente aparece")
    void governanteAcompanhaODominioDoDependente() {
        for (CampoCanonico campo : CampoCanonico.values()) {
            campo.getCondicao().ifPresent(condicao -> {
                for (TipoSolicitacao tipo : TipoSolicitacao.values()) {
                    if (campo.aplicaSeA(tipo)) {
                        assertTrue(condicao.governante().aplicaSeA(tipo),
                                campo + " aparece em " + tipo + ", mas seu governante "
                                        + condicao.governante() + " não — o formulário seria inconfigurável.");
                    }
                }
            });
        }
    }

    @Test
    @DisplayName("Todo campo declara o bloco em que aparece — não existe campo órfão na tela")
    void todoCampoTemGrupo() {
        for (CampoCanonico campo : CampoCanonico.values()) {
            assertNotNull(campo.getGrupo(), campo + " não declara grupo.");
        }
    }

    @Test
    @DisplayName("O bloco de um campo condicionado acompanha o do seu governante")
    void condicionadoFicaNoMesmoBlocoDoGovernante() {
        // Um campo que só aparece conforme a resposta de outro precisa surgir ao lado
        // dele; noutro bloco, a relação entre a pergunta e o desdobramento se perde.
        for (CampoCanonico campo : CampoCanonico.values()) {
            campo.getCondicao().ifPresent(condicao ->
                    assertEquals(condicao.governante().getGrupo(), campo.getGrupo(),
                            campo + " depende de " + condicao.governante() + ", mas está noutro bloco."));
        }
    }

    @Test
    @DisplayName("Chaves publicadas são únicas — duas colunas não disputam o mesmo nome no payload")
    void chavesSaoUnicas() {
        Set<String> chaves = new LinkedHashSet<>();
        for (CampoCanonico campo : CampoCanonico.values()) {
            assertTrue(chaves.add(campo.getChave()), "Chave duplicada: " + campo.getChave());
        }
        assertEquals(CampoCanonico.values().length, chaves.size());
    }

    @Test
    @DisplayName("Resposta ausente não satisfaz condição alguma — é o bug que motivou o Sim/Não explícito")
    void respostaAusenteNaoSatisfazCondicao() {
        var condicao = CampoCanonico.JUSTIFICATIVA_SEM_NF.getCondicao().orElseThrow();

        assertFalse(condicao.satisfeitaPor(null));
        assertFalse(condicao.satisfeitaPor("   "));
        assertTrue(condicao.satisfeitaPor(Boolean.FALSE));
        assertTrue(condicao.satisfeitaPor("false"));
        assertFalse(condicao.satisfeitaPor(Boolean.TRUE));
    }

    @Test
    @DisplayName("Campo de referência declara a fonte; campo de escolha declara a enumeração")
    void definicaoDoCampoEhCoerenteComOTipo() {
        for (CampoCanonico campo : CampoCanonico.values()) {
            switch (campo.getTipoCampo()) {
                case REFERENCIA -> {
                    assertNull(campo.getEnumeracao(), campo + " é referência e não deve ter enumeração.");
                    assertTrue(campo.getFonteReferencia() != null,
                            campo + " é referência e precisa declarar a fonte.");
                    assertTrue(campo.valoresAceitos().isEmpty());
                }
                case SELECAO_UNICA -> {
                    assertNull(campo.getFonteReferencia(), campo + " é escolha fechada, não referência.");
                    assertFalse(campo.valoresAceitos().isEmpty(),
                            campo + " é escolha fechada e precisa de uma enumeração que a alimente.");
                }
                default -> {
                    assertNull(campo.getFonteReferencia());
                    assertNull(campo.getEnumeracao());
                }
            }
        }
    }
}
