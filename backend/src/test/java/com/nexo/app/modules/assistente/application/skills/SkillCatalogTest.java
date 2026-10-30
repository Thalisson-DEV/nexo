package com.nexo.app.modules.assistente.application.skills;

import com.nexo.app.shared.application.analytics.SemanticQueryContext;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SkillCatalogTest {

    private final SkillCatalog catalogo = new SkillCatalog();

    @Test
    void carregaCompetenciasComFrontMatterEConteudo() {
        SkillCatalog.Skill consumo = catalogo.buscar("analise-consumo-materiais").orElseThrow();
        assertThat(consumo.titulo()).isEqualTo("Análise de consumo de materiais");
        assertThat(consumo.autoridades()).containsExactly("estoque:movimentacao:read");
        assertThat(consumo.conteudo()).startsWith("# Análise de consumo de materiais").doesNotContain("---\nnome:");
    }

    @Test
    void filtraCompetenciasPelasPermissoesDoUsuario() {
        SemanticQueryContext semPermissoes = new SemanticQueryContext(UUID.randomUUID(), Set.of());
        assertThat(catalogo.disponiveis(semPermissoes)).extracting(SkillCatalog.Skill::nome)
                .contains("conciliacao-planilha", "relatorio-executivo", "dashboard-operacional")
                .doesNotContain("analise-descontos", "analise-consumo-materiais");

        SemanticQueryContext rh = new SemanticQueryContext(UUID.randomUUID(), Set.of("desconto:read"));
        assertThat(catalogo.disponiveis(rh)).extracting(SkillCatalog.Skill::nome).contains("analise-descontos");
    }

    @Test
    void interpretaFrontMatterSimples() {
        SkillCatalog.Skill skill = SkillCatalog.parse("---\nnome: Teste-X\ntitulo: Teste\ndescricao: Uma descricao\n"
                + "autoridades: [a:read, b:read]\n---\n# Corpo\ntexto", "arquivo.md");
        assertThat(skill.nome()).isEqualTo("teste-x");
        assertThat(skill.autoridades()).containsExactlyInAnyOrder("a:read", "b:read");
        assertThat(skill.conteudo()).isEqualTo("# Corpo\ntexto");
    }
}
