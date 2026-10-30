package com.nexo.app.modules.assistente.infrastructure.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexo.app.modules.assistente.application.agent.AcaoComPrevia;
import com.nexo.app.modules.assistente.application.agent.AgentRunContext;
import com.nexo.app.modules.assistente.application.agent.AgentTool;
import com.nexo.app.modules.assistente.application.agent.AgentToolProvider;
import com.nexo.app.modules.assistente.application.agent.BaseTool;
import com.nexo.app.modules.assistente.application.agent.JsonSchemas;
import com.nexo.app.modules.assistente.application.agent.RiskLevel;
import com.nexo.app.modules.assistente.application.agent.ToolArgs;
import com.nexo.app.modules.assistente.application.agent.ToolContext;
import com.nexo.app.modules.assistente.application.agent.ToolException;
import com.nexo.app.modules.assistente.application.agent.ToolKind;
import com.nexo.app.modules.assistente.application.agent.ToolResult;
import com.nexo.app.modules.assistente.application.cadastro.CatalogoCadastros;
import com.nexo.app.modules.assistente.application.cadastro.OperacaoCadastro;
import com.nexo.app.modules.assistente.application.cadastro.Opcao;
import com.nexo.app.modules.assistente.application.cadastro.ReferenciaCadastro;
import com.nexo.app.modules.assistente.application.cadastro.ServicoCadastros;
import com.nexo.app.shared.application.analytics.SemanticQueryContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Cadastros pelo Copiloto. No chat, o modelo descobre o que pode cadastrar ({@code cadastros_disponiveis}), le
 * os campos ({@code formulario_cadastro}) e pede o cadastro ({@code cadastrar}), que vira um cartao de
 * confirmacao editavel: nada e gravado sem o usuario confirmar. Cada operacao tambem existe como ferramenta
 * propria ({@code cadastrar_colaborador}, {@code editar_equipe}...) para integracoes que expoem o registro de
 * ferramentas; essas nao entram na lista do chat.
 */
@Configuration(proxyBeanMethods = false)
public class CadastroTools {

    private static final Duration TEMPO = Duration.ofSeconds(60);

    @Bean
    AgentTool cadastrosDisponiveisTool(ServicoCadastros servico, ObjectMapper mapper) {
        return new BaseTool("cadastros_disponiveis", "Ver o que posso cadastrar",
                "Lista o que o Copiloto pode cadastrar ou editar para este usuario, por modulo, com os campos obrigatorios. "
                        + "Use quando o usuario pedir para cadastrar, criar, abrir, lancar, registrar ou editar algo no Nexo "
                        + "e voce nao souber a 'operacao' exata. Filtre com 'busca' (ex.: 'colaborador', 'compra', 'desconto').",
                JsonSchemas.object().prop("busca", JsonSchemas.string("Palavra para filtrar (opcional)")).build(),
                ToolKind.CATALOG, Set.of(), Duration.ofSeconds(10)) {
            @Override
            public boolean isAvailable(AgentRunContext context) {
                return !servico.catalogo().disponiveis(context.security().autoridades()).isEmpty();
            }

            @Override
            public String progressLabel(JsonNode arguments) {
                return "Vendo o que posso cadastrar";
            }

            @Override
            public ToolResult execute(JsonNode arguments, ToolContext context) {
                String busca = CatalogoCadastros.normalizar(ToolArgs.text(arguments, "busca"));
                Map<String, List<Map<String, Object>>> porModulo = new TreeMap<>();
                int total = 0;
                for (OperacaoCadastro<?> op : servico.catalogo().disponiveis(context.security().autoridades())) {
                    if (!busca.isBlank() && !CatalogoCadastros.normalizar(op.titulo() + " " + op.descricao() + " " + op.chave()
                            + " " + op.modulo()).contains(busca)) {
                        continue;
                    }
                    porModulo.computeIfAbsent(op.modulo(), ignored -> new ArrayList<>()).add(servico.resumoOperacao(op));
                    total++;
                }
                Map<String, Object> resposta = new LinkedHashMap<>();
                resposta.put("modulos", porModulo);
                resposta.put("orientacao", "Para cadastrar, chame cadastrar com a operacao e os dados. Se precisar dos campos "
                        + "completos (opcoes, formato), chame formulario_cadastro antes.");
                return ToolResult.of(json(mapper, resposta), total + " tipos de cadastro");
            }
        };
    }

    @Bean
    AgentTool formularioCadastroTool(ServicoCadastros servico, ObjectMapper mapper) {
        return new BaseTool("formulario_cadastro", "Ler o formulário do cadastro",
                "Campos de uma operacao de cadastro: rotulo, tipo, se e obrigatorio, opcoes validas e como preencher. "
                        + "Em formularios que dependem de escolhas (ex.: solicitacao de compra ou pagamento, cujo formulario "
                        + "muda conforme o tipo), envie em 'dados_parciais' o que ja sabe para receber os campos especificos.",
                JsonSchemas.object()
                        .required("operacao", JsonSchemas.string("Chave da operacao (de cadastros_disponiveis)"))
                        .prop("dados_parciais", JsonSchemas.anyValue("Dados ja conhecidos, no formato de 'dados' de cadastrar"))
                        .build(),
                ToolKind.CATALOG, Set.of(), TEMPO) {
            @Override
            public boolean isAvailable(AgentRunContext context) {
                return !servico.catalogo().disponiveis(context.security().autoridades()).isEmpty();
            }

            @Override
            public String progressLabel(JsonNode arguments) {
                return "Lendo o formulário do cadastro";
            }

            @Override
            public ToolResult execute(JsonNode arguments, ToolContext context) {
                OperacaoCadastro<?> op = servico.operacao(arguments, context.security());
                JsonNode parciais = arguments.get("dados_parciais");
                if (parciais != null && parciais.isTextual()) {
                    try {
                        parciais = mapper.readTree(parciais.asText());
                    } catch (Exception exception) {
                        parciais = null;
                    }
                }
                Map<String, Object> resposta = new LinkedHashMap<>();
                resposta.put("operacao", op.chave());
                resposta.put("titulo", op.titulo());
                resposta.put("quando_usar", op.descricao());
                if (op.modo() == OperacaoCadastro.Modo.EDITAR) {
                    resposta.put("como_editar", "Informe em 'registro' qual " + op.registro().toLowerCase() + " editar e, em 'dados', "
                            + "somente os campos que mudam; os demais ficam como estao.");
                }
                resposta.put("campos", servico.descreverCampos(op, parciais != null && parciais.isObject() ? parciais : null));
                resposta.put("lote", op.permiteLote() ? "aceita varios registros em 'lote'" : "um registro por vez");
                if (!op.exemplos().isEmpty()) resposta.put("exemplos_de_pedido", op.exemplos());
                return ToolResult.of(json(mapper, resposta), op.titulo());
            }
        };
    }

    @Bean
    AgentTool localizarCadastroTool(ServicoCadastros servico, ObjectMapper mapper) {
        return new BaseTool("localizar_cadastro", "Localizar cadastro",
                "Procura registros de um cadastro do Nexo pelo nome (colaborador, equipe, funcao, base, material, fornecedor...) "
                        + "e devolve as opcoes com id. Use para confirmar com o usuario qual registro ele quis dizer, ou para achar "
                        + "o registro a editar. Para cadastrar nao e preciso: cadastrar ja localiza os nomes sozinho.",
                JsonSchemas.object()
                        .required("tipo", JsonSchemas.string("Tipo do cadastro (ex.: colaborador, equipe, funcao, base_operacional, material)"))
                        .required("termo", JsonSchemas.string("Nome, codigo ou parte dele"))
                        .build(),
                ToolKind.DATA, Set.of(), Duration.ofSeconds(20)) {
            @Override
            public boolean isAvailable(AgentRunContext context) {
                return !servico.catalogo().referenciasPermitidas(context.security().autoridades()).isEmpty();
            }

            @Override
            public String progressLabel(JsonNode arguments) {
                String termo = ToolArgs.text(arguments, "termo");
                return termo == null ? "Localizando cadastro" : "Localizando \"" + termo + "\"";
            }

            @Override
            public ToolResult execute(JsonNode arguments, ToolContext context) {
                String tipo = ToolArgs.requiredText(arguments, "tipo").toLowerCase().replace(' ', '_').replace('-', '_');
                String termo = ToolArgs.requiredText(arguments, "termo");
                Set<String> permitidas = servico.catalogo().referenciasPermitidas(context.security().autoridades());
                if (!permitidas.contains(tipo)) {
                    throw new ToolException("Tipo de cadastro indisponivel: '" + tipo + "'. Disponiveis: " + String.join(", ", permitidas));
                }
                List<Opcao> opcoes = servico.catalogo().buscarReferencia(tipo, termo, 10);
                ReferenciaCadastro ref = servico.catalogo().referencia(tipo).orElseThrow();
                Map<String, Object> resposta = new LinkedHashMap<>();
                resposta.put("tipo", ref.rotulo());
                resposta.put("encontrados", opcoes.stream().map(o -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", o.valor());
                    item.put("nome", o.rotulo());
                    if (o.detalhe() != null) item.put("detalhe", o.detalhe());
                    return item;
                }).toList());
                return ToolResult.of(json(mapper, resposta), opcoes.size() + " encontrado(s)");
            }
        };
    }

    @Bean
    AgentTool cadastrarTool(ServicoCadastros servico) {
        return new FerramentaCadastro("cadastrar", "Cadastrar no Nexo",
                "Cadastra ou edita registros no Nexo: pessoas, equipes, usuarios, solicitacoes de material, compras e pagamentos, "
                        + "fornecedores, descontos, chamados, SST, estoque, obras, contratos e demais cadastros de cadastros_disponiveis. "
                        + "NUNCA grava direto: abre para o usuario um cartao de confirmacao editavel com os dados e as pendencias; so "
                        + "grava quando ele confirmar no cartao. Preencha somente o que o usuario disse ou o que esta nos arquivos dele; "
                        + "nao invente valores. Cadastros relacionados (funcao, base, equipe, material, fornecedor...) vao pelo NOME, "
                        + "o sistema localiza. Varios registros do mesmo tipo (planilha, lista) vao juntos em 'lote'.",
                JsonSchemas.object()
                        .required("operacao", JsonSchemas.string("Chave da operacao, de cadastros_disponiveis (ex.: colaborador, equipe, "
                                + "solicitacao_material, solicitacao_compra, desconto, editar_colaborador)"))
                        .prop("dados", JsonSchemas.anyValue("Campos de UM registro, {campo: valor}, como em formulario_cadastro"))
                        .prop("lote", JsonSchemas.array("Varios registros do mesmo tipo (ate 50), cada um no formato de 'dados'",
                                JsonSchemas.anyValue("Registro")))
                        .prop("registro", JsonSchemas.string("So nas operacoes editar_*: qual registro editar (nome, codigo ou id)"))
                        .build(),
                servico, null);
    }

    /** Uma ferramenta por operacao, para integracoes (MCP); no chat a generica cobre todas. */
    @Bean
    AgentToolProvider cadastrosEspecificosProvider(ServicoCadastros servico) {
        return () -> servico.catalogo().todas().stream()
                .map(op -> (AgentTool) new FerramentaCadastro(
                        op.modo() == OperacaoCadastro.Modo.CRIAR ? "cadastrar_" + op.id() : op.chave(),
                        op.titulo(),
                        op.descricao() + " Grava pelos casos de uso do Nexo, com as mesmas validacoes e permissoes da tela. "
                                + "Cadastros relacionados vao pelo nome.",
                        servico.schema(op), servico, op))
                .toList();
    }

    private static String json(ObjectMapper mapper, Object valor) {
        try {
            return mapper.writeValueAsString(valor);
        } catch (Exception exception) {
            throw new ToolException("Falha ao montar a resposta.");
        }
    }

    /**
     * Ferramenta de cadastro. No chat, o executor chama {@link #preparar} e mostra o cartao; a gravacao so
     * acontece em {@link #confirmar}. Em integracoes que chamam {@link #execute} (a confirmacao fica a cargo do
     * cliente), a previa e montada e, se nada faltar, o cadastro e gravado; senao volta a lista do que falta.
     */
    static final class FerramentaCadastro extends BaseTool implements AcaoComPrevia {

        private final ServicoCadastros servico;
        private final OperacaoCadastro<?> fixa;

        FerramentaCadastro(String nome, String titulo, String descricao, JsonNode schema, ServicoCadastros servico,
                OperacaoCadastro<?> fixa) {
            super(nome, titulo, descricao, schema, ToolKind.WRITE, Set.of(), TEMPO);
            this.servico = servico;
            this.fixa = fixa;
        }

        @Override
        public RiskLevel riskLevel() {
            return RiskLevel.WRITE_LOW;
        }

        @Override
        public boolean oferecidaNoChat() {
            return fixa == null;
        }

        @Override
        public Set<String> requiredAuthorities() {
            if (fixa != null) return fixa.autoridades();
            return servico.catalogo().todas().stream().flatMap(op -> op.autoridades().stream()).collect(Collectors.toUnmodifiableSet());
        }

        @Override
        public String progressLabel(JsonNode arguments) {
            OperacaoCadastro<?> op = fixa != null ? fixa
                    : servico.catalogo().buscar(ToolArgs.text(arguments, "operacao")).orElse(null);
            return op == null ? "Preparando o cadastro" : "Preparando: " + op.titulo().toLowerCase();
        }

        @Override
        public JsonNode displayArguments(JsonNode arguments) {
            ObjectNode exibicao = JsonNodeFactory.instance.objectNode();
            OperacaoCadastro<?> op = fixa != null ? fixa
                    : servico.catalogo().buscar(ToolArgs.text(arguments, "operacao")).orElse(null);
            exibicao.put("cadastro", op == null ? ToolArgs.text(arguments, "operacao") : op.titulo());
            JsonNode lote = arguments.get("lote");
            if (lote != null && lote.isArray()) exibicao.put("registros", lote.size());
            return exibicao;
        }

        private JsonNode argumentos(JsonNode arguments) {
            if (fixa == null) return arguments;
            ObjectNode normalizados = JsonNodeFactory.instance.objectNode();
            normalizados.put("operacao", fixa.chave());
            ObjectNode dados = arguments != null && arguments.isObject() ? ((ObjectNode) arguments).deepCopy()
                    : JsonNodeFactory.instance.objectNode();
            JsonNode registro = dados.remove("registro");
            if (registro != null) normalizados.set("registro", registro);
            normalizados.set("dados", dados);
            return normalizados;
        }

        @Override
        public PreviaAcao preparar(JsonNode argumentos, SemanticQueryContext seguranca) {
            return servico.preparar(argumentos(argumentos), seguranca);
        }

        @Override
        public PreviaAcao revisar(JsonNode argumentos, JsonNode alteracoes, SemanticQueryContext seguranca) {
            return servico.revisar(argumentos, alteracoes, seguranca);
        }

        @Override
        public ResultadoAcao confirmar(JsonNode argumentos, JsonNode alteracoes, SemanticQueryContext seguranca) {
            return servico.confirmar(argumentos, alteracoes, seguranca);
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            PreviaAcao previa = preparar(arguments, context.security());
            if (!previa.pronta()) {
                return ToolResult.of(previa.paraModelo(), previa.resumo()).asFailure();
            }
            ResultadoAcao resultado = confirmar(previa.argumentos(), null, context.security());
            if (resultado.pendente()) return ToolResult.of(resultado.previa().paraModelo(), resultado.previa().resumo()).asFailure();
            ObjectNode conteudo = resultado.resultado().deepCopy();
            if (resultado.sigiloso() != null) conteudo.set("sigiloso", resultado.sigiloso());
            ToolResult saida = ToolResult.of(conteudo.toString(), resultado.paraModelo());
            return resultado.sucesso() ? saida : saida.asFailure();
        }
    }
}
