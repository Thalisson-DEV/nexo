package com.nexo.app.modules.sst.infrastructure.web;

import com.nexo.app.modules.sst.application.dto.AtivacaoCorpoSstRequest;
import com.nexo.app.modules.sst.application.dto.CorpoGovernancaSstRequest;
import com.nexo.app.modules.sst.application.dto.EncerramentoCorpoSstRequest;
import com.nexo.app.modules.sst.application.dto.SaidaMembroCorpoSstRequest;
import com.nexo.app.modules.sst.application.dto.SubstituicaoMembroCorpoSstRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestHeader;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CorpoGovernancaSstControllerContractTest {

    @Test
    void ativacaoEEncerramentoExigemAutoridadeDeAprovacao() throws Exception {
        Method ativar = CorpoGovernancaSstController.class.getMethod(
                "ativar", String.class, UUID.class, AtivacaoCorpoSstRequest.class);
        Method encerrar = CorpoGovernancaSstController.class.getMethod(
                "encerrar", String.class, UUID.class, EncerramentoCorpoSstRequest.class);

        assertEquals("hasAuthority('sst:governanca:aprovar')",
                ativar.getAnnotation(PreAuthorize.class).value());
        assertEquals("hasAuthority('sst:governanca:aprovar')",
                encerrar.getAnnotation(PreAuthorize.class).value());
    }

    @Test
    void comandosCriticosExigemIdempotencyKey() throws Exception {
        assertIdempotencyKey(CorpoGovernancaSstController.class.getMethod(
                "criar", String.class, CorpoGovernancaSstRequest.class));
        assertIdempotencyKey(CorpoGovernancaSstController.class.getMethod(
                "ativar", String.class, UUID.class, AtivacaoCorpoSstRequest.class));
        assertIdempotencyKey(CorpoGovernancaSstController.class.getMethod(
                "registrarSaida", String.class, UUID.class, UUID.class,
                SaidaMembroCorpoSstRequest.class));
        assertIdempotencyKey(CorpoGovernancaSstController.class.getMethod(
                "substituir", String.class, UUID.class, UUID.class,
                SubstituicaoMembroCorpoSstRequest.class));
        assertIdempotencyKey(CorpoGovernancaSstController.class.getMethod(
                "encerrar", String.class, UUID.class, EncerramentoCorpoSstRequest.class));
    }

    private static void assertIdempotencyKey(Method metodo) {
        RequestHeader header = metodo.getParameters()[0].getAnnotation(RequestHeader.class);
        assertNotNull(header);
        assertEquals("Idempotency-Key", header.value());
    }
}
