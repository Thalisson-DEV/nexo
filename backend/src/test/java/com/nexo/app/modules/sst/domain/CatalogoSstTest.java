package com.nexo.app.modules.sst.domain;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Invariantes das entradas dos vocabulários de SST.
 */
class CatalogoSstTest {

    private CatalogoSst classificacao() {
        return new CatalogoSst(TipoCatalogoSst.CLASSIFICACAO_EVENTO,
                "  Queda de mesmo nível  ", " Perda de equilíbrio no piso ", " 31 ", 10);
    }

    @Test
    @DisplayName("Deve normalizar o nome e limpar os textos ao criar")
    void deveNormalizarDadosNaCriacao() {
        CatalogoSst entrada = classificacao();

        assertEquals("Queda de mesmo nível", entrada.getNome());
        assertEquals("queda de mesmo nivel", entrada.getNomeNormalizado());
        assertEquals("Perda de equilíbrio no piso", entrada.getDescricao());
        assertEquals("31", entrada.getCodigo());
        assertEquals(10, entrada.getOrdem());
        assertTrue(entrada.isDoTipo(TipoCatalogoSst.CLASSIFICACAO_EVENTO));
    }

    @Test
    @DisplayName("Deve tratar descrição e código em branco como ausentes")
    void deveTratarTextosEmBrancoComoNulos() {
        CatalogoSst entrada = new CatalogoSst(TipoCatalogoSst.LOCAL_LESAO, "Mão direita", "   ", "", null);

        assertNull(entrada.getDescricao());
        assertNull(entrada.getCodigo());
        assertEquals(0, entrada.getOrdem(), "Ordem omitida deve valer zero");
    }

    @Test
    @DisplayName("Não deve aceitar entrada sem tipo")
    void deveExigirTipo() {
        assertThrows(InvarianteVioladaException.class,
                () -> new CatalogoSst(null, "Corte", null, null, null));
    }

    @Test
    @DisplayName("Não deve aceitar nome vazio ou curto demais")
    void deveExigirNomeValido() {
        assertThrows(InvarianteVioladaException.class,
                () -> new CatalogoSst(TipoCatalogoSst.NATUREZA_LESAO, "   ", null, null, null));
        assertThrows(InvarianteVioladaException.class,
                () -> new CatalogoSst(TipoCatalogoSst.NATUREZA_LESAO, "A", null, null, null));
    }

    @Test
    @DisplayName("Não deve aceitar ordem negativa")
    void deveExigirOrdemNaoNegativa() {
        assertThrows(InvarianteVioladaException.class,
                () -> new CatalogoSst(TipoCatalogoSst.AGENTE_CAUSADOR, "Ferramenta manual", null, null, -1));
    }

    @Test
    @DisplayName("Deve reaplicar a normalização ao atualizar")
    void deveRenormalizarNaAtualizacao() {
        CatalogoSst entrada = classificacao();

        entrada.atualizar("Choque Elétrico", null, null, 5);

        assertEquals("Choque Elétrico", entrada.getNome());
        assertEquals("choque eletrico", entrada.getNomeNormalizado());
        assertNull(entrada.getDescricao());
        assertEquals(5, entrada.getOrdem());
    }

    @Test
    @DisplayName("Não deve alterar uma entrada desativada")
    void naoDeveAlterarEntradaDesativada() {
        CatalogoSst entrada = classificacao();
        entrada.desativar();

        assertThrows(InvarianteVioladaException.class, () -> entrada.atualizar("Outro nome", null, null, null));
    }

    @Test
    @DisplayName("isDoTipo distingue os vocabulários — é a garantia que a FK não dá")
    void deveDistinguirTipos() {
        CatalogoSst local = new CatalogoSst(TipoCatalogoSst.LOCAL_LESAO, "Joelho esquerdo", null, null, null);

        assertTrue(local.isDoTipo(TipoCatalogoSst.LOCAL_LESAO));
        assertFalse(local.isDoTipo(TipoCatalogoSst.CLASSIFICACAO_EVENTO));
    }
}
