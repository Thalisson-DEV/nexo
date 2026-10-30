package com.nexo.app.modules.frota;

import com.nexo.app.CesApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/** A frota só pode depender das APIs públicas de outros módulos (RH, IAM) e do shared. */
class FrotaModuloArquiteturaTest {

    @Test
    void frotaUsaApenasApisPublicasDeOutrosModulos() {
        var modules = ApplicationModules.of(CesApplication.class);
        var frota = modules.stream()
                .filter(module -> module.getBasePackage().getName().equals("com.nexo.app.modules.frota"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Modulo frota nao encontrado"));

        frota.verifyDependencies(modules);
    }
}
