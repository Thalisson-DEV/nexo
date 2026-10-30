package com.nexo.app.modules.obras.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class PermissoesObrasMigrationContractTest {

    private static final Pattern PERMISSAO = Pattern.compile("'(obras:[a-z-]+:[a-z]+)'");

    @Test
    void todaPermissaoDeObrasUsadaNosControllersExisteNasMigracoes() throws IOException {
        Set<String> usadas = coletar(Path.of("src/main/java/com/nexo/app/modules/obras/infrastructure/web"), ".java");
        Set<String> cadastradas = coletarInseridas(Path.of("src/main/resources/db/migration"));

        assertThat(usadas).isNotEmpty();
        assertThat(cadastradas).containsAll(usadas);
    }

    @Test
    void migracaoV198VinculaAsPermissoesNovasAoAdministrador() throws IOException {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V198__Permissoes_Processo_Obras.sql"));

        assertThat(sql)
                .contains("p.nome = 'ADMINISTRADOR'")
                .contains("ON CONFLICT (nome) DO NOTHING")
                .doesNotContain("DELETE FROM");
        for (String permissao : Set.of("obras:kit:import", "obras:planejamento:write", "obras:programacao:write",
                "obras:execucao:write", "obras:medicao:write", "obras:documentacao:write", "obras:central:read")) {
            assertThat(sql).contains("'" + permissao + "'");
        }
    }

    private static Set<String> coletar(Path raiz, String extensao) throws IOException {
        Set<String> encontradas = new TreeSet<>();
        try (Stream<Path> arquivos = Files.walk(raiz)) {
            for (Path arquivo : arquivos.filter(p -> p.toString().endsWith(extensao)).toList()) {
                Matcher m = PERMISSAO.matcher(Files.readString(arquivo, StandardCharsets.UTF_8));
                while (m.find()) {
                    encontradas.add(m.group(1));
                }
            }
        }
        return encontradas;
    }

    /** Só os INSERT em permissoes: o nome aparece como valor de tupla iniciada por gen_random_uuid(). */
    private static Set<String> coletarInseridas(Path migracoes) throws IOException {
        Set<String> inseridas = new TreeSet<>();
        Pattern tupla = Pattern.compile("gen_random_uuid\\(\\)[^)]*'(obras:[a-z-]+:[a-z]+)'\\s*\\)");
        try (Stream<Path> arquivos = Files.list(migracoes)) {
            for (Path arquivo : arquivos.filter(p -> p.toString().endsWith(".sql")).toList()) {
                Matcher m = tupla.matcher(Files.readString(arquivo, StandardCharsets.UTF_8));
                while (m.find()) {
                    inseridas.add(m.group(1));
                }
            }
        }
        return inseridas;
    }
}
