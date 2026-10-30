package com.nexo.app.modules.lookup.infrastructure.web;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class LookupControllerContractTest {

    @Test
    void bootstrapExigeTodasAsAutoridadesDosCatalogosAgregados() throws Exception {
        Method method = LookupController.class.getMethod("bootstrap", String.class);
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo(
                "hasAuthority('rh:base-operacional:read') "
                        + "and hasAuthority('rh:processo-negocio:read') "
                        + "and hasAuthority('estoque:movimentacao:read') "
                        + "and hasAuthority('estoque:solicitacao:read')");
    }
}
