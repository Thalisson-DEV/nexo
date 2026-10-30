package com.nexo.app.modules.sst.infrastructure.web;

import com.nexo.app.modules.sst.application.dto.AtualizarCabecalhoInspecaoRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class InspecaoSegurancaControllerContractTest {

    @Test
    @DisplayName("atualizarCabecalho deve estar mapeado com PUT /{id}/cabecalho e exigir permissão sst:inspecao:write")
    void atualizarCabecalhoExigePermissaoCorreta() throws Exception {
        Method metodo = InspecaoSegurancaController.class.getMethod(
                "atualizarCabecalho", UUID.class, AtualizarCabecalhoInspecaoRequest.class);

        PutMapping mapping = metodo.getAnnotation(PutMapping.class);
        assertNotNull(mapping);
        assertEquals("/{id}/cabecalho", mapping.value()[0]);

        PreAuthorize auth = metodo.getAnnotation(PreAuthorize.class);
        assertNotNull(auth);
        assertEquals("hasAuthority('sst:inspecao:write')", auth.value());
    }

    @Test
    @DisplayName("obterHistorico deve estar mapeado com GET /{id}/historico e exigir permissão sst:inspecao:read")
    void obterHistoricoExigePermissaoCorreta() throws Exception {
        Method metodo = InspecaoSegurancaController.class.getMethod(
                "obterHistorico", UUID.class, Pageable.class);

        GetMapping mapping = metodo.getAnnotation(GetMapping.class);
        assertNotNull(mapping);
        assertEquals("/{id}/historico", mapping.value()[0]);

        PreAuthorize auth = metodo.getAnnotation(PreAuthorize.class);
        assertNotNull(auth);
        assertEquals("hasAuthority('sst:inspecao:read')", auth.value());
    }
}
