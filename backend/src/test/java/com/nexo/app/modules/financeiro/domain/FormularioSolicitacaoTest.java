package com.nexo.app.modules.financeiro.domain;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Invariantes do agregado de template: um formulário é uma seleção ordenada de
 * campos do sistema, e nada além disso.
 *
 * <p>Como o agregado não é persistido nestes testes, os IDs são atribuídos à mão —
 * a API pública trabalha por ID justamente para que a raiz mantenha o controle.</p>
 */
class FormularioSolicitacaoTest {

    private FormularioSolicitacao formularioDeCompra() {
        return new FormularioSolicitacao(TipoSolicitacao.COMPRA, "PADRAO", "Solicitação de Compra", null, true);
    }

    /** Simula a atribuição de ID que aconteceria no flush. */
    private static <T extends BaseEntity> T comId(T entidade) {
        ReflectionTestUtils.setField(entidade, "id", UUID.randomUUID());
        return entidade;
    }

    private UUID adicionar(FormularioSolicitacao formulario, CampoCanonico canonico, String rotulo,
                           boolean obrigatorio) {
        return comId(formulario.adicionarCampo(canonico, rotulo, null, obrigatorio)).getId();
    }

    @Test
    @DisplayName("O campo herda chave, tipo e fonte do catálogo do sistema")
    void campoHerdaDefinicaoDoCatalogo() {
        var formulario = formularioDeCompra();

        var campo = formulario.adicionarCampo(CampoCanonico.BASE, "BASE", null, true);

        assertEquals("baseId", campo.getChave());
        assertEquals(TipoCampo.REFERENCIA, campo.getTipoCampo());
        assertEquals(FonteReferencia.BASE_OPERACIONAL, campo.getFonteReferencia());
    }

    @Test
    @DisplayName("Rótulo em branco devolve o campo ao rótulo padrão do sistema")
    void rotuloEmBrancoVoltaAoPadrao() {
        var formulario = formularioDeCompra();

        var campo = formulario.adicionarCampo(CampoCanonico.VALOR, "  ", null, true);

        assertEquals(CampoCanonico.VALOR.getRotuloPadrao(), campo.getRotulo());
    }

    @Test
    @DisplayName("Recusa campo que não se aplica ao tipo de solicitação")
    void recusaCampoIncompativelComOTipo() {
        var formulario = new FormularioSolicitacao(TipoSolicitacao.PAGAMENTO, "PADRAO", "Pagamento", null, true);

        // "Placa" só faz sentido numa compra de frota.
        var erro = assertThrows(InvarianteVioladaException.class,
                () -> formulario.adicionarCampo(CampoCanonico.VEICULO, "Placa", null, true));
        assertTrue(erro.getMessage().contains("não se aplica"));
    }

    @Test
    @DisplayName("Recusa duas ocorrências do mesmo campo — disputariam a mesma coluna")
    void recusaCampoDuplicado() {
        var formulario = formularioDeCompra();
        formulario.adicionarCampo(CampoCanonico.VALOR, "Valor", null, true);

        var erro = assertThrows(InvarianteVioladaException.class,
                () -> formulario.adicionarCampo(CampoCanonico.VALOR, "Valor total", null, true));
        assertTrue(erro.getMessage().contains("já faz parte"));
    }

    @Test
    @DisplayName("A condição de visibilidade vem do catálogo, não da configuração")
    void condicaoVemDoCatalogo() {
        var formulario = formularioDeCompra();
        UUID justificativa = adicionar(formulario, CampoCanonico.JUSTIFICATIVA_SEM_NF, "Justificativa", true);

        var condicao = formulario.buscarCampo(justificativa).getCondicao().orElseThrow();

        assertEquals(CampoCanonico.TEM_NF, condicao.governante());
        assertTrue(condicao.satisfeitaPor(Boolean.FALSE));
        assertTrue(!condicao.satisfeitaPor(Boolean.TRUE), "Com NF, a justificativa não deve aparecer");
    }

    @Test
    @DisplayName("Não remove campo do qual outro depende para se exibir")
    void naoRemoveCampoComDependente() {
        var formulario = formularioDeCompra();
        UUID temNf = adicionar(formulario, CampoCanonico.TEM_NF, "Tem NF", true);
        adicionar(formulario, CampoCanonico.JUSTIFICATIVA_SEM_NF, "Justificativa", true);

        var erro = assertThrows(InvarianteVioladaException.class, () -> formulario.removerCampo(temNf));
        assertTrue(erro.getMessage().contains("governa a exibição"));
    }

    @Test
    @DisplayName("Remover renumera a ordem dos que sobraram, sem buracos")
    void remocaoRenumeraOsRestantes() {
        var formulario = formularioDeCompra();
        adicionar(formulario, CampoCanonico.VALOR, "Valor", true);
        UUID descricao = adicionar(formulario, CampoCanonico.DESCRICAO, "Descrição", false);
        adicionar(formulario, CampoCanonico.OBSERVACOES, "Observações", false);

        formulario.removerCampo(descricao);

        var restantes = formulario.camposOrdenados();
        assertEquals(List.of(0, 1), restantes.stream().map(CampoFormulario::getOrdem).toList());
        assertEquals(List.of(CampoCanonico.VALOR, CampoCanonico.OBSERVACOES),
                restantes.stream().map(CampoFormulario::getCampoCanonico).toList());
    }

    @Test
    @DisplayName("Estrutura válida exige ao menos um campo e o campo de valor, que é o que se autoriza")
    void validarEstruturaExigeCampoDeValor() {
        var formulario = formularioDeCompra();
        assertThrows(InvarianteVioladaException.class, formulario::validarEstrutura);

        formulario.adicionarCampo(CampoCanonico.DESCRICAO, "Descrição", null, true);
        var semValor = assertThrows(InvarianteVioladaException.class, formulario::validarEstrutura);
        assertTrue(semValor.getMessage().contains("valor"));

        formulario.adicionarCampo(CampoCanonico.VALOR, "Valor", null, true);
        formulario.validarEstrutura();
    }

    @Test
    @DisplayName("Campo condicionado sem o governante no formulário invalida a estrutura")
    void campoCondicionadoExigeOGovernante() {
        var formulario = formularioDeCompra();
        formulario.adicionarCampo(CampoCanonico.VALOR, "Valor", null, true);
        formulario.adicionarCampo(CampoCanonico.NUMERO_NF, "nº NF", null, false);

        // Sem "Tem NF" no formulário, o número da nota nunca teria como aparecer.
        var erro = assertThrows(InvarianteVioladaException.class, formulario::validarEstrutura);
        assertTrue(erro.getMessage().contains("precisa estar no formulário"));

        formulario.adicionarCampo(CampoCanonico.TEM_NF, "Tem NF", null, true);
        formulario.validarEstrutura();
    }

    @Test
    @DisplayName("Campo apoiado em enumeração publica as opções do sistema")
    void campoEnumeradoPublicaValoresDoSistema() {
        var formulario = formularioDeCompra();
        var prioridade = formulario.adicionarCampo(CampoCanonico.PRIORIDADE, "Prioridade", null, true);

        assertEquals(List.of("BAIXA", "NORMAL", "ALTA", "URGENTE"), List.copyOf(prioridade.valoresAceitos()));
    }

    @Test
    @DisplayName("Reordenação exige a permutação exata dos campos existentes")
    void reordenacaoExigePermutacaoExata() {
        var formulario = formularioDeCompra();
        UUID valor = adicionar(formulario, CampoCanonico.VALOR, "Valor", true);
        UUID descricao = adicionar(formulario, CampoCanonico.DESCRICAO, "Descrição", false);

        formulario.reordenarCampos(List.of(descricao, valor));
        assertEquals(descricao, formulario.camposOrdenados().get(0).getId());

        assertThrows(InvarianteVioladaException.class, () -> formulario.reordenarCampos(List.of(valor)));
        assertThrows(InvarianteVioladaException.class, () -> formulario.reordenarCampos(List.of(valor, valor)));
    }

    @Test
    @DisplayName("Atualizar campo mexe só na apresentação")
    void atualizarCampoMexeSoNaApresentacao() {
        var formulario = formularioDeCompra();
        UUID valor = adicionar(formulario, CampoCanonico.VALOR, "Valor", true);

        formulario.atualizarCampo(valor, "Valor da compra", "Some os itens de uma vez", false);

        var campo = formulario.buscarCampo(valor);
        assertEquals("Valor da compra", campo.getRotulo());
        assertEquals("Some os itens de uma vez", campo.getAjuda());
        assertTrue(!campo.isObrigatorio());
        assertEquals(CampoCanonico.VALOR, campo.getCampoCanonico(), "O campo do sistema é imutável");
        assertEquals("valor", campo.getChave());
        // O exemplo do campo vazio vem do catálogo e não é afetado pela configuração.
        assertEquals(CampoCanonico.VALOR.getPlaceholderPadrao(), campo.getPlaceholder());
    }

    @Test
    @DisplayName("O código da variante é normalizado e recusa formato inválido")
    void codigoNormalizadoEValidado() {
        var formulario = new FormularioSolicitacao(TipoSolicitacao.COMPRA, " frota ", "Frota", null, false);
        assertEquals("FROTA", formulario.getCodigo());

        assertThrows(InvarianteVioladaException.class,
                () -> new FormularioSolicitacao(TipoSolicitacao.COMPRA, "1FROTA", "Frota", null, false));
    }
}
