package com.nexo.app;

import org.junit.jupiter.api.Test;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.springframework.modulith.core.ApplicationModules;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectureVerificationTest {

    @Test
    void inventoryUsesOnlyDeclaredModuleDependenciesAndPublicApis() {
        var modules = ApplicationModules.of(CesApplication.class);
        var inventory = modules.stream()
                .filter(module -> module.getBasePackage().getName()
                        .equals("com.nexo.app.modules.estoque"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Modulo estoque nao encontrado"));

        inventory.verifyDependencies(modules);
    }

    @Test
    void assistantUsesOnlyBusinessModuleRootApis() {
        Set<String> businessModules = Set.of("sst", "desconto", "iam");
        var violations = new ClassFileImporter().importPackages("com.nexo.app.modules.assistente").stream()
                .flatMap(source -> source.getDirectDependenciesFromSelf().stream())
                .filter(dependency -> dependency.getTargetClass().getPackageName()
                        .startsWith("com.nexo.app.modules."))
                .filter(dependency -> {
                    String packageName = dependency.getTargetClass().getPackageName();
                    return businessModules.stream().anyMatch(module -> packageName.startsWith(
                            "com.nexo.app.modules." + module + "."));
                })
                .map(Object::toString)
                .toList();

        assertThat(violations).as("dependencias do assistente para internals de outros modulos").isEmpty();
    }
}
