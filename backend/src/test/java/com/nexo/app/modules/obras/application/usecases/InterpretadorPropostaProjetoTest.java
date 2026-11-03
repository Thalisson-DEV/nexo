package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.obras.application.usecases.InterpretadorPropostaProjeto.KitCatalogo;
import com.nexo.app.modules.obras.domain.PropostaPlanejamento;
import com.nexo.app.modules.obras.domain.TipoPontoPlanejamento;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InterpretadorPropostaProjetoTest {

    private static final RenderizadorPaginaProjeto.Bloco PAGINA_INTEIRA =
            new RenderizadorPaginaProjeto.Bloco(0, 0, 1, 1, "");

    private static final List<KitCatalogo> CATALOGO = List.of(
            new KitCatalogo(UUID.randomUUID(), "LMI-0057", "9/400 DT", "POSTE"),
            new KitCatalogo(UUID.randomUUID(), "LMI-0688", "BT 1F - (IM-1)", "ESTRUTURA BT"),
            new KitCatalogo(UUID.randomUUID(), "LMI-0703", "BT 1F - (IM-R)", "ESTRUTURA BT"),
            new KitCatalogo(UUID.randomUUID(), "LMI-0900", "N1 - CCT - DT", "ESTRUTURA MT"),
            new KitCatalogo(UUID.randomUUID(), "LMI-0901", "N1 - CCT - 3F", "ESTRUTURA MT"),
            new KitCatalogo(UUID.randomUUID(), "LMI-0168", "CABO BT - 1×25(25) mm", "VÃO BT"));

    private static InterpretadorPropostaProjeto novo(Set<String> existentes) {
        return new InterpretadorPropostaProjeto(1, existentes, CATALOGO);
    }

    @Test
    void converteCoordenadasDoBlocoParaAPagina() {
        InterpretadorPropostaProjeto interpretador = novo(Set.of());

        interpretador.adicionarBloco("""
                {"pontos":[{"numero":"4","tipo":"POSTE","x":0.5,"y":0.5,"confianca":0.9}],"vaos":[]}
                """, new RenderizadorPaginaProjeto.Bloco(0.5, 0.25, 0.5, 0.5, ""));

        PropostaPlanejamento.PontoSugerido ponto = interpretador.concluir().pontos().get(0);
        assertThat(ponto.x()).isEqualByComparingTo("0.75");
        assertThat(ponto.y()).isEqualByComparingTo("0.5");
        assertThat(ponto.pagina()).isEqualTo(1);
    }

    @Test
    void uneOMesmoPontoVistoEmDoisBlocosFicandoComOMaisConfiavel() {
        InterpretadorPropostaProjeto interpretador = novo(Set.of());

        interpretador.adicionarBloco("""
                {"pontos":[{"numero":"4","x":0.1,"y":0.1,"barramento":"K051159","estruturas":["IM1"],"confianca":0.4}]}
                """, PAGINA_INTEIRA);
        interpretador.adicionarBloco("""
                {"pontos":[{"numero":"4","x":0.2,"y":0.2,"poste":"9/400","confianca":0.9}]}
                """, PAGINA_INTEIRA);

        List<PropostaPlanejamento.PontoSugerido> pontos = interpretador.concluir().pontos();
        assertThat(pontos).hasSize(1);
        assertThat(pontos.get(0).x()).isEqualByComparingTo("0.2");
        assertThat(pontos.get(0).barramento()).isEqualTo("K051159");
        assertThat(pontos.get(0).textosLidos()).containsExactlyInAnyOrder("IM1", "9/400");
    }

    @Test
    void marcaConflitoQuandoONumeroJaExisteNaObra() {
        InterpretadorPropostaProjeto interpretador = novo(Set.of("4"));

        interpretador.adicionarBloco("""
                {"pontos":[{"numero":"4","x":0.1,"y":0.1},{"numero":"5","x":0.2,"y":0.2}]}
                """, PAGINA_INTEIRA);

        assertThat(interpretador.concluir().pontos())
                .extracting(PropostaPlanejamento.PontoSugerido::numero, PropostaPlanejamento.PontoSugerido::conflito)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("4", true), org.assertj.core.groups.Tuple.tuple("5", false));
    }

    @Test
    void procuraOKitPeloTextoLidoEMarcaSoQuandoEUnico() {
        InterpretadorPropostaProjeto interpretador = novo(Set.of());

        interpretador.adicionarBloco("""
                {"pontos":[{"numero":"1","x":0.1,"y":0.1,"poste":"9/400","estruturas":["IM1","N1","XYZ9"]}]}
                """, PAGINA_INTEIRA);

        List<PropostaPlanejamento.KitSugerido> kits = interpretador.concluir().pontos().get(0).kits();
        assertThat(kits).extracting(PropostaPlanejamento.KitSugerido::codigo)
                .containsExactly("LMI-0057", "LMI-0688", "LMI-0900", "LMI-0901");
        assertThat(kits).filteredOn(PropostaPlanejamento.KitSugerido::marcado)
                .extracting(PropostaPlanejamento.KitSugerido::codigo)
                .containsExactly("LMI-0057", "LMI-0688");
    }

    @Test
    void vaoTrazMetragemEKitDeCaboPelaBitola() {
        InterpretadorPropostaProjeto interpretador = novo(Set.of());

        interpretador.adicionarBloco("""
                {"pontos":[{"numero":"2","x":0.1,"y":0.1},{"numero":"4","x":0.2,"y":0.2}],
                 "vaos":[{"de":"2","para":"4","metragem":"34,3m","condutor":"1x25+1x25mm²","confianca":0.8}]}
                """, PAGINA_INTEIRA);

        PropostaPlanejamento.VaoSugerido vao = interpretador.concluir().vaos().get(0);
        assertThat(vao.metragem()).isEqualByComparingTo("34.3");
        assertThat(vao.kits()).extracting(PropostaPlanejamento.KitSugerido::codigo).containsExactly("LMI-0168");
    }

    @Test
    void vaoComPontoDesconhecidoFicaDeForaComAviso() {
        InterpretadorPropostaProjeto interpretador = novo(Set.of("2"));

        interpretador.adicionarBloco("""
                {"pontos":[],"vaos":[{"de":"2","para":"99"},{"de":"2","para":"2"}]}
                """, PAGINA_INTEIRA);

        PropostaPlanejamento proposta = interpretador.concluir();
        assertThat(proposta.vaos()).isEmpty();
        assertThat(proposta.avisos()).anyMatch(a -> a.contains("99"));
    }

    @Test
    void aceitaJsonEntreCercasEAvisaQuandoARespostaNaoEJson() {
        InterpretadorPropostaProjeto interpretador = novo(Set.of());

        interpretador.adicionarBloco("```json\n{\"pontos\":[{\"numero\":\"7\",\"tipo\":\"trafo\",\"x\":0.3,\"y\":0.3}]}\n```", PAGINA_INTEIRA);
        interpretador.adicionarBloco("não consegui ler", PAGINA_INTEIRA);

        PropostaPlanejamento proposta = interpretador.concluir();
        assertThat(proposta.pontos().get(0).tipo()).isEqualTo(TipoPontoPlanejamento.OUTRO);
        assertThat(proposta.avisos()).anyMatch(a -> a.contains("Parte da folha"));
    }

    private static LeitorCamadaProjeto.CamadaPagina camada(LeitorCamadaProjeto.Candidato... candidatos) {
        return new LeitorCamadaProjeto.CamadaPagina(List.of(candidatos), 1000, 800, 600);
    }

    private static final LeitorCamadaProjeto.Candidato D1 = new LeitorCamadaProjeto.Candidato("D1234567", 0.10, 0.10, List.of());
    private static final LeitorCamadaProjeto.Candidato D2 = new LeitorCamadaProjeto.Candidato("D2234567", 0.10, 0.50, List.of("9/400", "IM-1"));
    private static final LeitorCamadaProjeto.Candidato D3 = new LeitorCamadaProjeto.Candidato("D3234567", 0.10, 0.90, List.of());

    @Test
    void textoDoPdfValeMesmoSemResposta() {
        InterpretadorPropostaProjeto interpretador = novo(Set.of());
        interpretador.adicionarCamada(camada(D1, D2));

        PropostaPlanejamento proposta = interpretador.concluir();

        assertThat(proposta.pontos()).extracting(PropostaPlanejamento.PontoSugerido::barramento).containsExactly("D1234567", "D2234567");
        assertThat(proposta.pontos().get(1).kits()).extracting(PropostaPlanejamento.KitSugerido::codigo).contains("LMI-0057", "LMI-0688");
    }

    @Test
    void modeloAjustaAPosicaoPertoDaEtiquetaMasNaoTrocaOCodigo() {
        InterpretadorPropostaProjeto interpretador = novo(Set.of());
        interpretador.adicionarCamada(camada(D1, D2));

        interpretador.adicionarBloco("""
                {"pontos":[{"id":"D1234567","tipo":"TRANSFORMADOR","x":0.12,"y":0.11,"barramento":"XXX"},
                           {"id":"D2234567","x":0.9,"y":0.9}]}
                """, PAGINA_INTEIRA);

        List<PropostaPlanejamento.PontoSugerido> pontos = interpretador.concluir().pontos();
        assertThat(pontos.get(0).barramento()).isEqualTo("D1234567");
        assertThat(pontos.get(0).tipo()).isEqualTo(TipoPontoPlanejamento.TRANSFORMADOR);
        assertThat(pontos.get(0).x()).isEqualByComparingTo("0.12");
        // Longe demais da etiqueta: é erro do modelo, e fica a posição do texto.
        assertThat(pontos.get(1).x()).isEqualByComparingTo("0.1");
    }

    @Test
    void numeraOsPontosSemNumeroNaSequenciaDaRede() {
        InterpretadorPropostaProjeto interpretador = novo(Set.of("7"));
        interpretador.adicionarCamada(camada(D2, D3, D1));

        interpretador.adicionarBloco("""
                {"vaos":[{"de":"D1234567","para":"D2234567"},{"de":"D2234567","para":"D3234567"}]}
                """, PAGINA_INTEIRA);

        PropostaPlanejamento proposta = interpretador.concluir();
        assertThat(proposta.pontos())
                .extracting(PropostaPlanejamento.PontoSugerido::referencia, PropostaPlanejamento.PontoSugerido::numero)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("D1234567", "8"),
                        org.assertj.core.groups.Tuple.tuple("D2234567", "9"),
                        org.assertj.core.groups.Tuple.tuple("D3234567", "10"));
    }

    @Test
    void estimaAMetragemPelaEscalaEAvisaQueEEstimada() {
        InterpretadorPropostaProjeto interpretador = novo(Set.of());
        interpretador.adicionarCamada(camada(D1, D2));

        interpretador.adicionarBloco("""
                {"vaos":[{"de":"D1234567","para":"D2234567"}]}
                """, PAGINA_INTEIRA);

        // 0,4 de 600 pt = 240 pt = 84,67 mm no papel, ou 84,7 m em 1:1000.
        PropostaPlanejamento.VaoSugerido vao = interpretador.concluir().vaos().get(0);
        assertThat(vao.metragem()).isEqualByComparingTo("84.7");
        assertThat(vao.textosLidos()).anyMatch(t -> t.contains("estimada pela escala 1:1000"));
    }

    @Test
    void posteDaOutraFolhaLigaAoQueJaExisteSemDuplicar() {
        InterpretadorPropostaProjeto interpretador = new InterpretadorPropostaProjeto(2, Set.of("5"), java.util.Map.of("D1234567", "5"), CATALOGO);
        interpretador.adicionarCamada(camada(D1, D2));

        interpretador.adicionarBloco("""
                {"vaos":[{"de":"D1234567","para":"D2234567","metragem":40}]}
                """, PAGINA_INTEIRA);

        PropostaPlanejamento proposta = interpretador.concluir();
        assertThat(proposta.pontos()).extracting(PropostaPlanejamento.PontoSugerido::referencia).containsExactly("D2234567");
        assertThat(proposta.vaos().get(0).referenciaA()).isEqualTo("5");
        assertThat(proposta.vaos().get(0).referenciaB()).isEqualTo("D2234567");
    }

    @Test
    void caixaLigadaAUmPontoViraInformacaoDele() {
        InterpretadorPropostaProjeto interpretador = novo(Set.of());
        interpretador.adicionarCamada(camada(D1, new LeitorCamadaProjeto.Candidato(null, 0.3, 0.3, List.of("9/400", "IM-1"))));

        interpretador.adicionarBloco("""
                {"pontos":[{"id":"D1234567","x":0.11,"y":0.11},{"id":"CX1","pertence_a":"D1234567","numero":"4"}],
                 "vaos":[{"de":"CX1","para":"D1234567"}]}
                """, PAGINA_INTEIRA);

        PropostaPlanejamento proposta = interpretador.concluir();
        assertThat(proposta.pontos()).hasSize(1);
        assertThat(proposta.pontos().get(0).numero()).isEqualTo("4");
        assertThat(proposta.pontos().get(0).barramento()).isEqualTo("D1234567");
        assertThat(proposta.pontos().get(0).kits()).extracting(PropostaPlanejamento.KitSugerido::codigo).contains("LMI-0057");
        assertThat(proposta.vaos()).isEmpty();
    }

    @Test
    void toleraComentarioEVirgulaSobrandoEAvisaQuemChamaQuandoNaoEJson() {
        InterpretadorPropostaProjeto interpretador = novo(Set.of());

        assertThat(interpretador.tentarBloco("{\"pontos\":[{\"numero\":\"1\",\"x\":0.1,\"y\":0.1,},], // fim\n}", PAGINA_INTEIRA)).isTrue();
        assertThat(interpretador.tentarBloco("não consegui ler", PAGINA_INTEIRA)).isFalse();
        assertThat(interpretador.tentarBloco("", PAGINA_INTEIRA)).isFalse();

        PropostaPlanejamento proposta = interpretador.concluir();
        assertThat(proposta.pontos()).hasSize(1);
        assertThat(proposta.avisos()).isEmpty();
    }

    @Test
    void zeroAEsquerdaNaoDuplicaOPonto() {
        InterpretadorPropostaProjeto interpretador = novo(Set.of());

        interpretador.adicionarBloco("""
                {"pontos":[{"numero":"03","x":0.1,"y":0.1,"confianca":0.5},{"numero":"3","x":0.2,"y":0.2,"confianca":0.9}]}
                """, PAGINA_INTEIRA);

        assertThat(interpretador.concluir().pontos()).extracting(PropostaPlanejamento.PontoSugerido::numero).containsExactly("3");
    }

    @Test
    void blocosCobremAFolhaInteiraComSobreposicao() {
        int colunas = RenderizadorPaginaProjeto.quantidade(4000);
        double tamanho = RenderizadorPaginaProjeto.tamanhoRelativo(colunas);

        assertThat(colunas).isEqualTo(3);
        assertThat(RenderizadorPaginaProjeto.inicio(0, colunas, tamanho)).isZero();
        assertThat(RenderizadorPaginaProjeto.inicio(2, colunas, tamanho) + tamanho).isCloseTo(1d, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(RenderizadorPaginaProjeto.inicio(1, colunas, tamanho)).isLessThan(tamanho);
        assertThat(RenderizadorPaginaProjeto.quantidade(1000)).isEqualTo(1);
    }
}
