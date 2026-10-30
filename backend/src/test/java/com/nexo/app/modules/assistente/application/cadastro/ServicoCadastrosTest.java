package com.nexo.app.modules.assistente.application.cadastro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexo.app.modules.assistente.application.agent.AcaoComPrevia;
import com.nexo.app.modules.assistente.application.agent.ToolException;
import com.nexo.app.shared.application.analytics.SemanticQueryContext;
import jakarta.validation.Validation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Motor de cadastros de ponta a ponta com um provedor de teste: normalizacao, nomes resolvidos em ids,
 * pendencias, ajustes do cartao, lote, senha provisoria e permissao.
 */
class ServicoCadastrosTest {

    private static final UUID BASE_FEIRA = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
    private static final UUID BASE_FEIRA_NORTE = UUID.fromString("00000000-0000-0000-0000-0000000000f2");
    private static final UUID BASE_JUAZEIRO = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID MATERIAL_CABO = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    record Item(@NotNull UUID idMaterial, @NotNull BigDecimal quantidade) {
    }

    record PedidoTeste(@NotBlank String nome, @NotNull UUID idBaseOperacional, @NotNull LocalDate dataPrevista,
            BigDecimal valor, @NotEmpty List<Item> itens) {
    }

    record AcessoTeste(@NotBlank String login, String senha) {
    }

    private final List<Object> gravados = new ArrayList<>();
    private DriverManagerDataSource dataSource;
    private ServicoCadastros servico;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final SemanticQueryContext comPermissao = new SemanticQueryContext(UUID.randomUUID(), Set.of("teste:write"));

    @BeforeEach
    void montar() {
        dataSource = new DriverManagerDataSource("jdbc:h2:mem:cadastros-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("create table bases (id uuid primary key, nome varchar(80), uf varchar(2))");
        jdbc.execute("create table materiais (id uuid primary key, descricao varchar(80), codigo varchar(20))");
        jdbc.update("insert into bases values (?, 'Feira de Santana', 'BA')", BASE_FEIRA);
        jdbc.update("insert into bases values (?, 'Feira Norte', 'BA')", BASE_FEIRA_NORTE);
        jdbc.update("insert into bases values (?, 'Juazeiro', 'BA')", BASE_JUAZEIRO);
        jdbc.update("insert into materiais values (?, 'Cabo de cobre 16mm', '1234')", MATERIAL_CABO);

        ProvedorCadastros provedor = new ProvedorCadastros() {
            @Override
            public List<ReferenciaCadastro> referencias() {
                return List.of(
                        new ReferenciaCadastro("base_operacional", "Base operacional",
                                "select id, nome as rotulo, uf as detalhe from bases"),
                        new ReferenciaCadastro("material", "Material",
                                "select id, descricao as rotulo, codigo as detalhe from materiais"));
            }

            @Override
            public List<OperacaoCadastro<?>> operacoes() {
                return List.of(
                        OperacaoCadastro.criar("pedido", PedidoTeste.class)
                                .modulo("Teste").titulo("Novo pedido").registro("Pedido")
                                .autoridade("teste:write")
                                .campo("idBaseOperacional", c -> c.rotulo("Base").referencia("base_operacional"))
                                .campo("itens[].idMaterial", c -> c.rotulo("Material").referencia("material"))
                                .identificadoPor("nome")
                                .executor((r, id, usuario) -> {
                                    gravados.add(r);
                                    return UUID.randomUUID();
                                })
                                .destino("TESTE")
                                .build(),
                        OperacaoCadastro.criar("acesso", AcessoTeste.class)
                                .modulo("Teste").titulo("Novo acesso").registro("Acesso")
                                .autoridade("teste:write")
                                .campo("senha", c -> c.padrao(MotorCadastro.senhaProvisoria()))
                                .identificadoPor("login")
                                .executor((r, id, usuario) -> {
                                    gravados.add(r);
                                    return UUID.randomUUID();
                                })
                                .build(),
                        OperacaoCadastro.criar("restrito", AcessoTeste.class)
                                .modulo("Teste").titulo("Cadastro restrito").autoridade("outro:write")
                                .executor((r, id, usuario) -> null)
                                .build());
            }
        };
        @SuppressWarnings("unchecked")
        ObjectProvider<ProvedorCadastros> provedores = mock(ObjectProvider.class);
        when(provedores.orderedStream()).thenAnswer(invocacao -> Stream.of(provedor));
        CatalogoCadastros catalogo = new CatalogoCadastros(provedores, jdbc);
        MotorCadastro motor = new MotorCadastro(catalogo, mapper, Validation.buildDefaultValidatorFactory().getValidator(),
                new DataSourceTransactionManager(dataSource));
        servico = new ServicoCadastros(catalogo, motor, mapper);
    }

    @AfterEach
    void limpar() {
        new JdbcTemplate(dataSource).execute("shutdown");
    }

    @Test
    void normalizaValoresEResolveNomesAntesDeGravar() throws Exception {
        AcaoComPrevia.PreviaAcao previa = servico.preparar(json("""
                {"operacao": "pedido", "dados": {"nome": "Pedido da obra", "idBaseOperacional": "juazeiro",
                 "dataPrevista": "05/11/2026", "valor": "R$ 1.234,56",
                 "itens": [{"idMaterial": "cabo cobre", "quantidade": "20,5"}]}}"""), comPermissao);

        assertThat(previa.pronta()).isTrue();
        JsonNode lote = previa.argumentos().path("lote").get(0);
        assertThat(lote.path("idBaseOperacional").asText()).isEqualTo(BASE_JUAZEIRO.toString());
        assertThat(lote.path("dataPrevista").asText()).isEqualTo("2026-11-05");
        assertThat(lote.path("valor").decimalValue()).isEqualByComparingTo("1234.56");
        assertThat(lote.path("itens").get(0).path("idMaterial").asText()).isEqualTo(MATERIAL_CABO.toString());
        assertThat(campo(previa.exibicao(), 0, "idBaseOperacional").path("exibicao").asText()).isEqualTo("Juazeiro");

        AcaoComPrevia.ResultadoAcao resultado = servico.confirmar(previa.argumentos(), null, comPermissao);

        assertThat(resultado.sucesso()).isTrue();
        assertThat(resultado.resultado().path("gravados").asInt()).isEqualTo(1);
        assertThat(resultado.resultado().path("registros").get(0).path("destino").asText()).isEqualTo("TESTE");
        PedidoTeste gravado = (PedidoTeste) gravados.getFirst();
        assertThat(gravado.itens().getFirst().quantidade()).isEqualByComparingTo("20.5");
    }

    @Test
    void nomeAmbiguoViraPendenciaComCandidatosEOCartaoCorrige() throws Exception {
        AcaoComPrevia.PreviaAcao previa = servico.preparar(json("""
                {"operacao": "pedido", "dados": {"nome": "Pedido", "idBaseOperacional": "feira",
                 "dataPrevista": "amanhã", "itens": [{"idMaterial": "cabo", "quantidade": 1}]}}"""), comPermissao);

        assertThat(previa.pronta()).isFalse();
        JsonNode base = campo(previa.exibicao(), 0, "idBaseOperacional");
        assertThat(base.path("problema").asText()).isNotBlank();
        assertThat(base.path("candidatos")).hasSize(2);

        AcaoComPrevia.PreviaAcao corrigida = servico.revisar(previa.argumentos(),
                json("[{\"registro\": 0, \"caminho\": \"idBaseOperacional\", \"valor\": \"" + BASE_FEIRA_NORTE + "\"}]"),
                comPermissao);

        assertThat(corrigida.pronta()).isTrue();
        assertThat(corrigida.argumentos().path("lote").get(0).path("idBaseOperacional").asText())
                .isEqualTo(BASE_FEIRA_NORTE.toString());
        assertThat(campo(corrigida.exibicao(), 0, "idBaseOperacional").path("exibicao").asText()).isEqualTo("Feira Norte");
    }

    @Test
    void nomeNaoResolvidoSobreviveARevisaoDeOutroCampo() throws Exception {
        AcaoComPrevia.PreviaAcao previa = servico.preparar(json("""
                {"operacao": "pedido", "dados": {"nome": "Pedido", "idBaseOperacional": "feira",
                 "dataPrevista": "hoje", "itens": [{"idMaterial": "cabo", "quantidade": 1}]}}"""), comPermissao);

        AcaoComPrevia.PreviaAcao revisada = servico.revisar(previa.argumentos(),
                json("[{\"registro\": 0, \"caminho\": \"nome\", \"valor\": \"Pedido revisado\"}]"), comPermissao);

        JsonNode base = campo(revisada.exibicao(), 0, "idBaseOperacional");
        assertThat(base.path("exibicao").asText()).isEqualTo("feira");
        assertThat(base.path("candidatos")).hasSize(2);
    }

    @Test
    void nomeComErroDeDigitacaoTrazSugestoes() throws Exception {
        AcaoComPrevia.PreviaAcao previa = servico.preparar(json("""
                {"operacao": "pedido", "dados": {"nome": "Pedido", "idBaseOperacional": "Juazero",
                 "dataPrevista": "hoje", "itens": [{"idMaterial": "cabo", "quantidade": 1}]}}"""), comPermissao);

        JsonNode base = campo(previa.exibicao(), 0, "idBaseOperacional");
        assertThat(base.path("candidatos")).extracting(c -> c.path("rotulo").asText()).contains("Juazeiro");
    }

    @Test
    void confirmarComPendenciaNaoGravaEDevolveOCartao() throws Exception {
        AcaoComPrevia.PreviaAcao previa = servico.preparar(json("""
                {"operacao": "pedido", "dados": {"nome": "Sem base", "dataPrevista": "hoje",
                 "itens": [{"idMaterial": "cabo", "quantidade": 1}]}}"""), comPermissao);

        AcaoComPrevia.ResultadoAcao resultado = servico.confirmar(previa.argumentos(), null, comPermissao);

        assertThat(resultado.pendente()).isTrue();
        assertThat(resultado.previa().pronta()).isFalse();
        assertThat(gravados).isEmpty();
    }

    @Test
    void loteAceitaRemoverRegistroEIncluirItem() throws Exception {
        AcaoComPrevia.PreviaAcao previa = servico.preparar(json("""
                {"operacao": "pedido", "lote": [
                  {"nome": "A", "idBaseOperacional": "Juazeiro", "dataPrevista": "hoje", "itens": [{"idMaterial": "cabo", "quantidade": 1}]},
                  {"nome": "B", "idBaseOperacional": "Juazeiro", "dataPrevista": "hoje", "itens": []},
                  {"nome": "C", "idBaseOperacional": "Juazeiro", "dataPrevista": "hoje", "itens": [{"idMaterial": "cabo", "quantidade": 2}]}
                ]}"""), comPermissao);

        assertThat(previa.exibicao().path("registros")).hasSize(3);
        assertThat(previa.exibicao().path("prontos").asInt()).isEqualTo(2);

        AcaoComPrevia.PreviaAcao ajustada = servico.revisar(previa.argumentos(), json("""
                [{"registro": 1, "caminho": "itens.#", "valor": {"idMaterial": "cabo", "quantidade": 3}},
                 {"registro": 2, "remover": true}]"""), comPermissao);

        assertThat(ajustada.pronta()).isTrue();
        assertThat(ajustada.exibicao().path("registros")).hasSize(2);
        AcaoComPrevia.ResultadoAcao resultado = servico.confirmar(ajustada.argumentos(), null, comPermissao);
        assertThat(resultado.resultado().path("gravados").asInt()).isEqualTo(2);
        assertThat(gravados).extracting(g -> ((PedidoTeste) g).nome()).containsExactly("A", "B");
    }

    @Test
    void loteAceitaItensEmTextoOuSoONome() throws Exception {
        AcaoComPrevia.PreviaAcao previa = servico.preparar(json("""
                {"operacao": "acesso", "lote": ["{\\"login\\": \\"ana\\"}", "bruno"]}"""), comPermissao);

        assertThat(previa.argumentos().path("lote")).extracting(r -> r.path("login").asText()).containsExactly("ana", "bruno");
    }

    @Test
    void senhaProvisoriaNuncaFicaGuardadaEVoltaSoNoSigiloso() throws Exception {
        AcaoComPrevia.PreviaAcao previa = servico.preparar(json("{\"operacao\": \"acesso\", \"dados\": {\"login\": \"joao.silva\"}}"),
                comPermissao);

        assertThat(previa.argumentos().toString()).doesNotContain("Ces@");
        assertThat(previa.exibicao().toString()).doesNotContain("Ces@");

        AcaoComPrevia.ResultadoAcao resultado = servico.confirmar(previa.argumentos(), null, comPermissao);

        String senha = ((AcessoTeste) gravados.getFirst()).senha();
        assertThat(senha).startsWith("Ces@");
        assertThat(resultado.sigiloso().path("senhas").get(0).path("valor").asText()).isEqualTo(senha);
        assertThat(resultado.resultado().toString()).doesNotContain(senha);
        assertThat(resultado.paraModelo()).doesNotContain(senha);
    }

    @Test
    void operacaoSemPermissaoNaoPrepara() {
        assertThatThrownBy(() -> servico.preparar(json("{\"operacao\": \"restrito\", \"dados\": {\"login\": \"x\"}}"), comPermissao))
                .isInstanceOf(ToolException.class)
                .hasMessageContaining("permissao");
    }

    private JsonNode json(String texto) throws Exception {
        return mapper.readTree(texto);
    }

    private static JsonNode campo(JsonNode exibicao, int registro, String caminho) {
        for (JsonNode campo : (ArrayNode) exibicao.path("registros").get(registro).path("campos")) {
            if (campo.path("caminho").asText().equals(caminho)) return campo;
        }
        return ((ObjectNode) exibicao).objectNode();
    }
}
