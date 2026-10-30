package com.nexo.app.modules.sst.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModeloAtaSstTest {

    @Test
    @DisplayName("atualizacao do rascunho altera propriedades com sucesso")
    void deveAtualizarRascunho() {
        ModeloAtaSst modelo = new ModeloAtaSst("Ata Antiga", TipoCorpoGovernancaSst.BRIGADA, null, null);
        
        modelo.atualizarRascunho("Ata Nova", "Novas orientacoes");
        
        assertEquals("Ata Nova", modelo.getNome());
        assertEquals("Novas orientacoes", modelo.getOrientacoesGerais());
    }
}
