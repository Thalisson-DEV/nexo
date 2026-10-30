package com.nexo.app.modules.sst.domain;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes de invariantes do agregado de template {@link FormularioInspecao}
 * (Formulário -> Grupo -> Pergunta -> Opção de Resposta).
 */
class FormularioInspecaoTest {

    private FormularioInspecao novoFormulario() {
        return new FormularioInspecao("Inspeção de Campo", "Checklist diário de campo");
    }

    @Test
    @DisplayName("Deve adicionar grupos preservando a ordem de inserção")
    void deveAdicionarGruposComOrdem() {
        FormularioInspecao form = novoFormulario();

        GrupoPergunta kitSmc = form.adicionarGrupo("Kit SMC");
        GrupoPergunta epi = form.adicionarGrupo("EPIs");

        assertEquals(2, form.getGrupos().size());
        assertEquals(0, kitSmc.getOrdem());
        assertEquals(1, epi.getOrdem());
        assertEquals("Kit SMC", form.getGrupos().get(0).getTitulo());
    }

    @Test
    @DisplayName("Deve adicionar perguntas e opções em uma pergunta objetiva")
    void deveMontarArvoreCompleta() {
        FormularioInspecao form = novoFormulario();
        GrupoPergunta grupo = form.adicionarGrupo("Kit SMC");

        Pergunta pergunta = grupo.adicionarPergunta(
                "A câmera estava em perfeito estado de conservação?",
                TipoResposta.MULTIPLA_ESCOLHA,
                true);

        OpcaoResposta sim = pergunta.adicionarOpcao("Sim", false, null);
        OpcaoResposta nao = pergunta.adicionarOpcao("Não", true, GravidadeDesvio.ALTA);

        assertEquals(1, grupo.getPerguntas().size());
        assertEquals(2, pergunta.getOpcoes().size());
        assertTrue(pergunta.isObrigatoria());
        assertFalse(sim.isGeraDesvio());
        assertTrue(nao.isGeraDesvio(), "A opção 'Não' deve marcar geração de desvio");
        assertEquals(0, sim.getOrdem());
        assertEquals(1, nao.getOrdem());
    }

    @Test
    @DisplayName("Não deve permitir opções em perguntas de resposta livre (texto/número/data)")
    void naoDevePermitirOpcaoEmPerguntaSubjetiva() {
        FormularioInspecao form = novoFormulario();
        GrupoPergunta grupo = form.adicionarGrupo("Observações");

        Pergunta observacao = grupo.adicionarPergunta("Observações gerais", TipoResposta.TEXTO, false);

        assertFalse(observacao.getTipoResposta().permiteOpcoes());
        assertThrows(InvarianteVioladaException.class, () -> observacao.adicionarOpcao("Qualquer", false, null));
    }

    @Test
    @DisplayName("validarEstrutura deve falhar quando uma pergunta objetiva não possui opções")
    void validarEstruturaFalhaSemOpcoes() {
        FormularioInspecao form = novoFormulario();
        GrupoPergunta grupo = form.adicionarGrupo("Kit SMC");
        grupo.adicionarPergunta("A câmera estava conservada?", TipoResposta.MULTIPLA_ESCOLHA, true);

        assertThrows(InvarianteVioladaException.class, form::validarEstrutura);
    }

    @Test
    @DisplayName("validarEstrutura deve falhar quando o formulário não possui nenhuma pergunta")
    void validarEstruturaFalhaSemPerguntas() {
        FormularioInspecao form = novoFormulario();
        form.adicionarGrupo("Grupo vazio");

        assertThrows(InvarianteVioladaException.class, form::validarEstrutura);
    }

    @Test
    @DisplayName("validarEstrutura deve passar para um formulário consistente")
    void validarEstruturaConsistente() {
        FormularioInspecao form = novoFormulario();
        GrupoPergunta grupo = form.adicionarGrupo("Kit SMC");
        Pergunta objetiva = grupo.adicionarPergunta("A câmera estava conservada?", TipoResposta.MULTIPLA_ESCOLHA, true);
        objetiva.adicionarOpcao("Sim", false, null);
        objetiva.adicionarOpcao("Não", true, GravidadeDesvio.ALTA);
        grupo.adicionarPergunta("Observações", TipoResposta.TEXTO, false);

        assertDoesNotThrow(form::validarEstrutura);
    }

    @Test
    @DisplayName("Deve permitir marcar uma opção como padrão e rejeitar opção padrão que gere desvio")
    void deveValidarOpcaoPadrao() {
        FormularioInspecao form = novoFormulario();
        GrupoPergunta grupo = form.adicionarGrupo("Segurança");
        Pergunta p = grupo.adicionarPergunta("Uso de EPI", TipoResposta.MULTIPLA_ESCOLHA, true);

        OpcaoResposta conforme = p.adicionarOpcao("CONFORME", false, null, true);
        assertTrue(conforme.isPadrao());

        assertThrows(InvarianteVioladaException.class, () ->
                p.adicionarOpcao("NÃO CONFORME", true, GravidadeDesvio.ALTA, true));
    }

    @Test
    @DisplayName("Não deve permitir mais de uma opção padrão em pergunta de múltipla escolha")
    void naoDevePermitirMultiplasOpcoesPadraoEmMultiplaEscolha() {
        FormularioInspecao form = novoFormulario();
        GrupoPergunta grupo = form.adicionarGrupo("Segurança");
        Pergunta p = grupo.adicionarPergunta("Uso de EPI", TipoResposta.MULTIPLA_ESCOLHA, true);

        p.adicionarOpcao("CONFORME", false, null, true);

        assertThrows(InvarianteVioladaException.class, () ->
                p.adicionarOpcao("N/A", false, null, true));
    }
}
