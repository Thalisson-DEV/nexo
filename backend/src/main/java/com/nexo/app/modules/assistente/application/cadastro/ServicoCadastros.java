package com.nexo.app.modules.assistente.application.cadastro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexo.app.modules.assistente.application.agent.AcaoComPrevia.PreviaAcao;
import com.nexo.app.modules.assistente.application.agent.AcaoComPrevia.ResultadoAcao;
import com.nexo.app.modules.assistente.application.agent.JsonSchemas;
import com.nexo.app.modules.assistente.application.agent.ToolException;
import com.nexo.app.shared.application.analytics.SemanticQueryContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Fluxo de um pedido de cadastro: monta o cartao de confirmacao (um ou varios registros), aplica os ajustes
 * feitos no cartao e, quando o usuario confirma, grava pelos casos de uso. Usado pela ferramenta do chat e
 * pelas ferramentas especificas expostas a integracoes.
 */
@Component
public class ServicoCadastros {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(ServicoCadastros.class);

    public static final int MAX_LOTE = 50;

    private final CatalogoCadastros catalogo;
    private final MotorCadastro motor;
    private final ObjectMapper mapper;

    public ServicoCadastros(CatalogoCadastros catalogo, MotorCadastro motor, ObjectMapper mapper) {
        this.catalogo = catalogo;
        this.motor = motor;
        this.mapper = mapper;
    }

    // ---------------------------------------------------------------------------------- previa

    /**
     * Le o pedido ({operacao, registro?, dados? | lote?}) e monta a previa. Erros que o usuario precisa
     * esclarecer antes de existir um cartao (operacao desconhecida, registro a editar ambiguo) viram
     * {@link ToolException} para o modelo perguntar.
     */
    public PreviaAcao preparar(JsonNode argumentos, SemanticQueryContext seguranca) {
        OperacaoCadastro<?> op = operacao(argumentos, seguranca);
        UUID registroId = registroAlvo(op, argumentos);
        List<JsonNode> itens = itens(op, argumentos);
        return previa(op, registroId, itens);
    }

    /** Aplica os ajustes do cartao aos argumentos guardados e monta a previa de novo. */
    public PreviaAcao revisar(JsonNode guardados, JsonNode alteracoes, SemanticQueryContext seguranca) {
        OperacaoCadastro<?> op = operacao(guardados, seguranca);
        UUID registroId = idOuNulo(guardados.path("registroId").asText(null));
        List<JsonNode> itens = aplicar(itens(op, guardados), alteracoes);
        if (itens.isEmpty()) throw new ToolException("Nenhum registro restou no cartão.");
        return previa(op, registroId, itens);
    }

    public ResultadoAcao confirmar(JsonNode guardados, JsonNode alteracoes, SemanticQueryContext seguranca) {
        OperacaoCadastro<?> op = operacao(guardados, seguranca);
        UUID registroId = idOuNulo(guardados.path("registroId").asText(null));
        List<JsonNode> itens = aplicar(itens(op, guardados), alteracoes);
        if (itens.isEmpty()) throw new ToolException("Nenhum registro para gravar.");
        List<MotorCadastro.Preparo> preparos = itens.stream().map(item -> motor.preparar(op, item, registroId)).toList();
        if (preparos.stream().anyMatch(p -> !p.pronto())) {
            return new ResultadoAcao(false, null, null, null, montarPrevia(op, registroId, preparos));
        }
        ArrayNode registros = JsonNodeFactory.instance.arrayNode();
        ArrayNode senhas = JsonNodeFactory.instance.arrayNode();
        List<String> gravados = new ArrayList<>();
        List<String> falhas = new ArrayList<>();
        for (int i = 0; i < preparos.size(); i++) {
            MotorCadastro.Preparo preparo = preparos.get(i);
            ObjectNode registro = registros.addObject();
            registro.put("indice", i);
            registro.put("titulo", preparo.titulo());
            try {
                MotorCadastro.Gravado gravado = motor.executar(preparo, seguranca.usuarioId());
                registro.put("status", "GRAVADO");
                if (gravado.id() != null) registro.put("id", gravado.id());
                registro.put("rotulo", gravado.rotulo());
                if (gravado.destino() != null) {
                    registro.put("destino", gravado.destino());
                    registro.set("parametros", mapper.valueToTree(gravado.parametros()));
                }
                gravados.add(gravado.rotulo() + (gravado.id() == null ? "" : " (id " + gravado.id() + ")"));
                gravado.senhasGeradas().forEach((rotulo, senha) -> {
                    ObjectNode s = senhas.addObject();
                    s.put("registro", gravado.rotulo());
                    s.put("rotulo", rotulo);
                    s.put("valor", senha);
                });
            } catch (MotorCadastro.FalhaCadastro falha) {
                registro.put("status", "FALHOU");
                registro.put("mensagem", falha.getMessage());
                falhas.add(preparo.titulo() + ": " + falha.getMessage());
            }
        }
        ObjectNode resultado = JsonNodeFactory.instance.objectNode();
        resultado.put("tipo", "cadastro_resultado");
        resultado.put("operacao", op.chave());
        resultado.put("modo", op.modo().name());
        resultado.put("registro", op.registro());
        resultado.put("gravados", gravados.size());
        resultado.put("falhas", falhas.size());
        resultado.set("registros", registros);
        String verbo = op.modo() == OperacaoCadastro.Modo.EDITAR ? "atualizado(s)" : "cadastrado(s)";
        StringBuilder historico = new StringBuilder("[Confirmado pelo usuario no cartao] ").append(op.titulo()).append(": ");
        if (!gravados.isEmpty()) historico.append(gravados.size()).append(" ").append(verbo).append(" — ").append(String.join("; ", gravados)).append(". ");
        if (!falhas.isEmpty()) historico.append(falhas.size()).append(" nao gravado(s) — ").append(String.join("; ", falhas)).append(".");
        ObjectNode sigiloso = null;
        if (!senhas.isEmpty()) {
            sigiloso = JsonNodeFactory.instance.objectNode();
            sigiloso.set("senhas", senhas);
        }
        return new ResultadoAcao(!gravados.isEmpty(), resultado, historico.toString().strip(), sigiloso, null);
    }

    private PreviaAcao previa(OperacaoCadastro<?> op, UUID registroId, List<JsonNode> itens) {
        List<MotorCadastro.Preparo> preparos = itens.stream().map(item -> motor.preparar(op, item, registroId)).toList();
        return montarPrevia(op, registroId, preparos);
    }

    private PreviaAcao montarPrevia(OperacaoCadastro<?> op, UUID registroId, List<MotorCadastro.Preparo> preparos) {
        ObjectNode exibicao = JsonNodeFactory.instance.objectNode();
        String chave = "cadastro:" + op.chave() + (registroId == null ? "" : ":" + registroId) + (preparos.size() > 1 ? ":lote" : "");
        exibicao.put("tipo", "cadastro");
        exibicao.put("chave", chave);
        exibicao.put("operacao", op.chave());
        exibicao.put("modo", op.modo().name());
        exibicao.put("modulo", op.modulo());
        exibicao.put("titulo", op.titulo());
        exibicao.put("registro", op.registro());
        exibicao.put("permiteLote", op.permiteLote());
        if (registroId != null) exibicao.put("registroId", registroId.toString());
        ArrayNode registros = exibicao.putArray("registros");
        ArrayNode paraModelo = JsonNodeFactory.instance.arrayNode();
        ArrayNode lote = JsonNodeFactory.instance.arrayNode();
        int prontos = 0;
        for (int i = 0; i < preparos.size(); i++) {
            MotorCadastro.Preparo preparo = preparos.get(i);
            if (preparo.pronto()) prontos++;
            ObjectNode registro = registros.addObject();
            registro.put("indice", i);
            registro.put("titulo", preparo.titulo());
            registro.put("pronto", preparo.pronto());
            registro.put("pendencias", preparo.pendencias().size());
            registro.set("campos", preparo.campos());
            List<String> gerais = preparo.pendencias().stream().filter(p -> p.caminho().isBlank()).map(MotorCadastro.Pendencia::problema).toList();
            if (!gerais.isEmpty()) registro.set("avisos", mapper.valueToTree(gerais));
            lote.add(preparo.dadosArmazenaveis());

            ObjectNode resumo = paraModelo.addObject();
            resumo.put("registro", preparo.titulo());
            ArrayNode pendencias = resumo.putArray("pendencias");
            for (MotorCadastro.Pendencia pendencia : preparo.pendencias()) {
                ObjectNode p = pendencias.addObject();
                p.put("campo", pendencia.caminho());
                p.put("rotulo", pendencia.rotulo());
                p.put("problema", pendencia.problema());
                if (!pendencia.opcoes().isEmpty()) {
                    p.set("opcoes", mapper.valueToTree(pendencia.opcoes().stream().limit(6)
                            .map(o -> o.detalhe() == null ? o.rotulo() : o.rotulo() + " (" + o.detalhe() + ")").toList()));
                }
            }
        }
        boolean pronta = prontos == preparos.size();
        exibicao.put("pronta", pronta);
        exibicao.put("total", preparos.size());
        exibicao.put("prontos", prontos);

        ObjectNode argumentos = JsonNodeFactory.instance.objectNode();
        argumentos.put("operacao", op.chave());
        if (registroId != null) argumentos.put("registroId", registroId.toString());
        argumentos.set("lote", lote);

        ObjectNode modelo = JsonNodeFactory.instance.objectNode();
        modelo.put("status", "CARTAO_DE_CONFIRMACAO_EXIBIDO");
        modelo.put("nada_foi_gravado", true);
        modelo.put("pronto_para_confirmar", pronta);
        modelo.set("registros", paraModelo);
        modelo.put("orientacao", pronta
                ? "O cartao com os dados ja esta na tela. Responda em uma ou duas frases, sem repetir os campos: diga o que "
                        + "sera cadastrado e que basta conferir e confirmar no cartao. Nao diga que foi gravado: so grava quando o usuario confirmar."
                : "O cartao ja esta na tela com os dados e as pendencias marcadas. Diga em uma frase objetiva o que falta "
                        + "(no maximo 3 itens, com as opcoes quando houver) e que ele pode completar direto no cartao ou responder aqui. "
                        + "Se ele responder no chat, chame cadastrar de novo com TODOS os dados (os anteriores + os novos). Nao invente valores.");
        String resumo = preparos.size() == 1
                ? (pronta ? "Pronto para confirmar: " : "Faltam dados: ") + preparos.getFirst().titulo()
                : prontos + " de " + preparos.size() + " prontos para confirmar";
        return new PreviaAcao(argumentos, exibicao, modelo.toString(), resumo, chave, pronta);
    }

    // ------------------------------------------------------------------------------- argumentos

    public OperacaoCadastro<?> operacao(JsonNode argumentos, SemanticQueryContext seguranca) {
        String chave = argumentos == null ? null : argumentos.path("operacao").asText(null);
        if (chave == null || chave.isBlank()) {
            throw new ToolException("Informe 'operacao' (chame cadastros_disponiveis para ver as opcoes).");
        }
        OperacaoCadastro<?> op = catalogo.buscar(chave).orElseThrow(() -> new ToolException(
                "Operacao de cadastro desconhecida: '" + chave + "'. Parecidas: " + parecidas(chave, seguranca)
                        + ". Chame cadastros_disponiveis para ver todas."));
        if (!op.autorizada(seguranca.autoridades())) {
            throw new ToolException("O usuario nao tem permissao para " + op.titulo().toLowerCase(Locale.ROOT)
                    + ". Explique a limitacao sem detalhes internos.");
        }
        return op;
    }

    private String parecidas(String chave, SemanticQueryContext seguranca) {
        String alvo = CatalogoCadastros.normalizar(chave).replace('_', ' ');
        List<String> nomes = catalogo.disponiveis(seguranca.autoridades()).stream()
                .filter(op -> alvo.contains(op.id().replace('_', ' ')) || op.id().replace('_', ' ').contains(alvo)
                        || CatalogoCadastros.normalizar(op.titulo()).contains(alvo))
                .map(OperacaoCadastro::chave).limit(6).toList();
        return nomes.isEmpty() ? "nenhuma" : String.join(", ", nomes);
    }

    private UUID registroAlvo(OperacaoCadastro<?> op, JsonNode argumentos) {
        if (op.modo() != OperacaoCadastro.Modo.EDITAR) return null;
        String informado = argumentos.path("registro").asText(argumentos.path("registroId").asText(""));
        if (informado.isBlank()) {
            throw new ToolException("Informe em 'registro' qual " + op.registro().toLowerCase(Locale.ROOT)
                    + " editar (nome, codigo ou id).");
        }
        MotorCadastro.Resolucao resolucao = motor.resolver(op.referenciaRegistro(), informado);
        if (resolucao.escolhida() != null) return UUID.fromString(resolucao.escolhida().valor());
        if (resolucao.candidatos().isEmpty()) {
            throw new ToolException("Nao encontrei " + op.registro().toLowerCase(Locale.ROOT) + " com \"" + informado
                    + "\". Peca ao usuario o nome exato ou o codigo.");
        }
        List<String> opcoes = resolucao.candidatos().stream()
                .map(o -> o.rotulo() + (o.detalhe() == null ? "" : " (" + o.detalhe() + ")") + " [id " + o.valor() + "]").toList();
        throw new ToolException("Mais de um registro corresponde a \"" + informado + "\": " + String.join("; ", opcoes)
                + ". Pergunte ao usuario qual (mostre as opcoes como lista curta) e chame de novo com o id em 'registro'.");
    }

    private List<JsonNode> itens(OperacaoCadastro<?> op, JsonNode argumentos) {
        List<JsonNode> itens = new ArrayList<>();
        JsonNode lote = argumentos.get("lote");
        if (lote != null && lote.isTextual()) lote = lerJson(lote.asText());
        if (lote != null && lote.isArray() && !lote.isEmpty()) {
            if (lote.size() > 1 && !op.permiteLote()) {
                throw new ToolException(op.titulo() + " e feito um de cada vez. Prepare o primeiro e siga para os outros depois.");
            }
            if (lote.size() > MAX_LOTE) throw new ToolException("No maximo " + MAX_LOTE + " registros por vez.");
            lote.forEach(item -> itens.add(registroDoLote(op, item)));
            if (itens.stream().allMatch(JsonNode::isEmpty)) {
                LOGGER.info("Lote de {} sem campos reconhecidos: {}", op.chave(), lote.toString().substring(0, Math.min(400, lote.toString().length())));
            }
            return itens;
        }
        JsonNode dados = argumentos.get("dados");
        if (dados != null && dados.isTextual()) {
            try {
                dados = mapper.readTree(dados.asText());
            } catch (Exception exception) {
                throw new ToolException("'dados' deve ser um objeto JSON com os campos do cadastro.");
            }
        }
        itens.add(dados != null && dados.isObject() ? dados : JsonNodeFactory.instance.objectNode());
        return itens;
    }

    /** Item do lote como o modelo mandou: objeto, objeto em texto ou so o nome do registro ("Eletricista"). */
    private JsonNode registroDoLote(OperacaoCadastro<?> op, JsonNode item) {
        if (item.isObject()) {
            // {"dados": {...}} ou {"registro": {...}}: o modelo embrulhou o registro.
            if (item.size() == 1) {
                JsonNode unico = item.elements().next();
                if (unico.isObject()) return unico;
                if (unico.isTextual()) {
                    JsonNode lido = lerJson(unico.asText());
                    if (lido != null && lido.isObject()) return lido;
                }
            }
            return item;
        }
        if (item.isTextual()) {
            JsonNode lido = lerJson(item.asText());
            if (lido != null && lido.isObject()) return lido;
            if (!op.camposTitulo().isEmpty() && !item.asText().isBlank()) {
                ObjectNode registro = JsonNodeFactory.instance.objectNode();
                registro.put(op.camposTitulo().getFirst(), item.asText().strip());
                return registro;
            }
        }
        return JsonNodeFactory.instance.objectNode();
    }

    private JsonNode lerJson(String texto) {
        String limpo = texto == null ? "" : texto.strip();
        if (!limpo.startsWith("{") && !limpo.startsWith("[")) return null;
        try {
            return mapper.readTree(limpo);
        } catch (Exception exception) {
            return null;
        }
    }

    /**
     * Ajustes do cartao: [{registro, caminho, valor}] troca um valor; {registro, remover: true} tira o registro
     * do lote; {registro, caminho: "itens.2", remover: true} tira um item; {registro, caminho: "itens.#", valor}
     * inclui um item.
     */
    private List<JsonNode> aplicar(List<JsonNode> itens, JsonNode alteracoes) {
        List<JsonNode> resultado = new ArrayList<>();
        itens.forEach(item -> resultado.add(item.deepCopy()));
        if (alteracoes == null || !alteracoes.isArray()) return resultado;
        List<Integer> remover = new ArrayList<>();
        for (JsonNode alteracao : alteracoes) {
            int indice = alteracao.path("registro").asInt(0);
            if (indice < 0 || indice >= resultado.size()) continue;
            String caminho = alteracao.path("caminho").asText("");
            boolean removerItem = alteracao.path("remover").asBoolean(false);
            if (caminho.isBlank()) {
                if (removerItem) remover.add(indice);
                continue;
            }
            definir((ObjectNode) resultado.get(indice), caminho, removerItem ? null : alteracao.get("valor"), removerItem);
        }
        remover.stream().sorted(java.util.Comparator.reverseOrder()).forEach(i -> resultado.remove((int) i));
        return resultado;
    }

    static void definir(ObjectNode raiz, String caminho, JsonNode valor, boolean remover) {
        String[] partes = caminho.split("\\.");
        JsonNode atual = raiz;
        for (int i = 0; i < partes.length - 1; i++) {
            String parte = partes[i];
            String proxima = partes[i + 1];
            boolean proximaLista = proxima.matches("\\d+") || proxima.equals("#");
            JsonNode filho;
            if (atual instanceof ArrayNode lista) {
                int indice = Integer.parseInt(parte);
                while (lista.size() <= indice) lista.addObject();
                filho = lista.get(indice);
            } else {
                ObjectNode objeto = (ObjectNode) atual;
                filho = objeto.get(parte);
                if (filho == null || filho.isNull() || (proximaLista && !filho.isArray()) || (!proximaLista && !filho.isObject())) {
                    filho = proximaLista ? objeto.putArray(parte) : objeto.putObject(parte);
                }
            }
            atual = filho;
        }
        String ultima = partes[partes.length - 1];
        if (atual instanceof ArrayNode lista) {
            if (ultima.equals("#")) {
                lista.add(valor == null ? JsonNodeFactory.instance.objectNode() : valor);
            } else {
                int indice = Integer.parseInt(ultima);
                if (remover) {
                    if (indice < lista.size()) lista.remove(indice);
                } else {
                    while (lista.size() <= indice) lista.addNull();
                    lista.set(indice, valor == null ? JsonNodeFactory.instance.nullNode() : valor);
                }
            }
        } else if (atual instanceof ObjectNode objeto) {
            if (remover || valor == null) objeto.remove(ultima);
            else objeto.set(ultima, valor);
        }
    }

    private static UUID idOuNulo(String valor) {
        if (valor == null || valor.isBlank()) return null;
        try {
            return UUID.fromString(valor);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    // ------------------------------------------------------------------------------------- schema

    /** Campos da operacao para o modelo (e para integracoes): rotulo, tipo, obrigatoriedade, opcoes. */
    public ArrayNode descreverCampos(OperacaoCadastro<?> op, JsonNode dadosParciais) {
        return descrever(op, catalogo.campos(op), "", dadosParciais == null ? JsonNodeFactory.instance.objectNode() : dadosParciais);
    }

    private ArrayNode descrever(OperacaoCadastro<?> op, List<CampoDescritor> campos, String prefixo, JsonNode parciais) {
        ArrayNode lista = JsonNodeFactory.instance.arrayNode();
        for (CampoDescritor campo : campos) {
            if (campo.oculto()) continue;
            String caminho = prefixo + campo.chave();
            ObjectNode no = lista.addObject();
            no.put("campo", campo.chave());
            no.put("rotulo", campo.rotulo());
            no.put("tipo", campo.tipo().name() + (campo.multiplo() ? " (lista)" : ""));
            OperacaoCadastro.Campo ajuste = op.campos().get(caminho);
            no.put("obrigatorio", campo.obrigatorio() && (ajuste == null || (ajuste.padrao() == null && ajuste.derivado() == null)));
            if (campo.descricao() != null) no.put("descricao", campo.descricao());
            if (campo.referencia() != null) {
                no.put("como_preencher", "nome do(a) " + catalogo.referencia(campo.referencia()).map(ReferenciaCadastro::rotulo).orElse(campo.referencia())
                        + " como o usuario falou; o sistema localiza o cadastro");
            }
            if (campo.tipo() == TipoCampo.OPCAO) {
                no.set("opcoes", mapper.valueToTree(campo.opcoes().stream().map(o -> o.valor() + " = " + o.rotulo()).toList()));
            }
            if (campo.tipo() == TipoCampo.DATA) no.put("como_preencher", "dd/mm/aaaa, 'hoje' ou 'amanha'");
            if (campo.tipo() == TipoCampo.SENHA) no.put("como_preencher", "deixe vazio: o sistema gera uma senha provisoria");
            if (campo.tipo() == TipoCampo.LISTA) no.set("itens", descrever(op, campo.filhos(), caminho + "[].", JsonNodeFactory.instance.objectNode()));
            OperacaoCadastro.CamposDinamicos dinamicos = op.camposDinamicos().get(caminho);
            if (dinamicos != null) {
                List<CampoDescritor> filhos = dinamicos.campos(parciais);
                if (filhos.isEmpty()) {
                    no.put("como_preencher", "os campos dependem dos outros dados: informe-os e chame formulario_cadastro de novo com dados_parciais");
                } else {
                    no.set("campos", descrever(op, filhos, caminho + ".", JsonNodeFactory.instance.objectNode()));
                }
            }
        }
        return lista;
    }

    /** JSON Schema da operacao para uma ferramenta especifica (integracoes). */
    public ObjectNode schema(OperacaoCadastro<?> op) {
        JsonSchemas.ObjectBuilder builder = JsonSchemas.object();
        if (op.modo() == OperacaoCadastro.Modo.EDITAR) {
            builder.required("registro", JsonSchemas.string("Qual " + op.registro().toLowerCase(Locale.ROOT)
                    + " editar: nome, codigo ou id. Informe so os campos que mudam."));
        }
        for (CampoDescritor campo : catalogo.campos(op)) {
            if (campo.oculto()) continue;
            OperacaoCadastro.Campo ajuste = op.campos().get(campo.chave());
            boolean obrigatorio = op.modo() == OperacaoCadastro.Modo.CRIAR && campo.obrigatorio()
                    && (ajuste == null || (ajuste.padrao() == null && ajuste.derivado() == null));
            JsonNode schema = schemaDoCampo(op, campo, campo.chave());
            if (obrigatorio) builder.required(campo.chave(), schema);
            else builder.prop(campo.chave(), schema);
        }
        return builder.build();
    }

    private JsonNode schemaDoCampo(OperacaoCadastro<?> op, CampoDescritor campo, String caminho) {
        String descricao = campo.rotulo() + (campo.descricao() == null ? "" : ". " + campo.descricao());
        ObjectNode base = switch (campo.tipo()) {
            case INTEIRO -> JsonSchemas.integer(descricao, null, null);
            case DECIMAL, MOEDA -> JsonSchemas.number(descricao);
            case BOOLEANO -> JsonSchemas.bool(descricao);
            case OPCAO -> JsonSchemas.enumString(descricao, campo.opcoes().stream().map(Opcao::valor).toList());
            case REFERENCIA -> JsonSchemas.string(descricao + ". Nome (ou id) do(a) "
                    + catalogo.referencia(campo.referencia()).map(ReferenciaCadastro::rotulo).orElse(campo.referencia()));
            case DATA -> JsonSchemas.string(descricao + ". Data (dd/mm/aaaa ou AAAA-MM-DD)");
            case DATA_HORA -> JsonSchemas.string(descricao + ". Data e hora (dd/mm/aaaa hh:mm) ou so a data");
            case SENHA -> JsonSchemas.string(descricao + ". Opcional: sem ela o sistema gera uma senha provisoria");
            case LISTA -> {
                JsonSchemas.ObjectBuilder item = JsonSchemas.object();
                for (CampoDescritor filho : campo.filhos()) {
                    if (filho.oculto()) continue;
                    JsonNode schemaFilho = schemaDoCampo(op, filho, caminho + "[]." + filho.chave());
                    if (filho.obrigatorio()) item.required(filho.chave(), schemaFilho);
                    else item.prop(filho.chave(), schemaFilho);
                }
                yield JsonSchemas.array(descricao, item.build());
            }
            case OBJETO -> JsonSchemas.anyValue(descricao + (op.camposDinamicos().containsKey(caminho)
                    ? ". Objeto {chave: valor}; os campos dependem dos demais dados" : ""));
            default -> JsonSchemas.string(descricao);
        };
        return campo.multiplo() && campo.tipo() != TipoCampo.LISTA ? JsonSchemas.array(descricao, base) : base;
    }

    public CatalogoCadastros catalogo() {
        return catalogo;
    }

    public Map<String, Object> resumoOperacao(OperacaoCadastro<?> op) {
        List<String> obrigatorios = catalogo.campos(op).stream()
                .filter(c -> c.obrigatorio() && !c.oculto())
                .filter(c -> op.campos().get(c.chave()) == null || (op.campos().get(c.chave()).padrao() == null && op.campos().get(c.chave()).derivado() == null))
                .map(CampoDescritor::rotulo).toList();
        Map<String, Object> resumo = new java.util.LinkedHashMap<>();
        resumo.put("operacao", op.chave());
        resumo.put("titulo", op.titulo());
        resumo.put("quando_usar", op.descricao());
        if (op.modo() == OperacaoCadastro.Modo.CRIAR) resumo.put("obrigatorios", obrigatorios);
        if (!op.exemplos().isEmpty()) resumo.put("exemplo", op.exemplos().getFirst());
        return resumo;
    }
}
