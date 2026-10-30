package com.nexo.app.modules.automacao.application.nos;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.nexo.app.modules.assistente.CopilotoApi;
import com.nexo.app.modules.automacao.application.motor.Catalogo.EspecCampo;
import com.nexo.app.modules.automacao.application.motor.Catalogo.EspecNo;
import com.nexo.app.modules.automacao.application.motor.Catalogo.EspecParam;
import com.nexo.app.modules.automacao.application.motor.Catalogo.EspecSaida;
import com.nexo.app.modules.automacao.application.motor.Catalogo.ProvedorNos;
import com.nexo.app.modules.automacao.application.motor.ContextoAcao;
import com.nexo.app.modules.automacao.application.motor.ErroAcao;
import com.nexo.app.modules.automacao.domain.ArquivoAutomacao;
import com.nexo.app.modules.automacao.domain.TipoAutomacao;
import com.nexo.app.modules.automacao.domain.expressao.Valores;
import com.nexo.app.modules.automacao.infrastructure.persistence.ArquivoAutomacaoRepository;
import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.shared.infrastructure.email.EmailProperties;

/**
 * Passos de IA: interpretar o que os passos anteriores produziram e entregar texto ou
 * campos estruturados aos seguintes.
 *
 * <p>A IA interpreta, extrai, classifica, resume e analisa; não produz número oficial.
 * Toda saída leva {@code gerado_por_ia} e o verificador avisa quando uma decisão usa
 * resposta de IA antes de um efeito externo sem aprovação humana. O passo "Copiloto
 * analisa" é o agente completo — com as consultas, os indicadores e o relatório PDF do
 * chat —, rodando com as permissões do dono da automação.</p>
 */
@Component
public class NosIa implements ProvedorNos {

    private static final int LIMITE_ENTRADA = 60_000;
    private static final List<List<String>> TIPOS_CAMPO = List.of(List.of("text", "Texto"), List.of("number", "Número"),
            List.of("boolean", "Sim/Não"), List.of("list", "Lista"), List.of("date", "Data"));
    private static final String SISTEMA = "Você é um passo de uma automação do Nexo (Gestão Operacional), sistema de gestão de "
            + "uma empresa de serviços de energia elétrica. Responda em português do Brasil, de forma objetiva. Use somente as "
            + "informações recebidas; se algo não estiver nos dados, diga que não está. Nunca invente números.";
    private static final Pattern JSON = Pattern.compile("\\{.*}|\\[.*]", Pattern.DOTALL);

    private final CopilotoApi copiloto;
    private final UsuarioApi usuarios;
    private final ArquivoAutomacaoRepository arquivos;
    private final EmailProperties emailProperties;

    public NosIa(CopilotoApi copiloto, UsuarioApi usuarios, ArquivoAutomacaoRepository arquivos, EmailProperties emailProperties) {
        this.copiloto = copiloto;
        this.usuarios = usuarios;
        this.arquivos = arquivos;
        this.emailProperties = emailProperties;
    }

    private EspecParam campos(String rotulo, boolean obrigatorio) {
        EspecParam.Builder b = EspecParam.de("fields", rotulo, "fields").padrao(List.of()).estatico()
                .campos(EspecCampo.texto("name", "Nome", "fornecedor"), EspecCampo.selecao("type", "Tipo", TIPOS_CAMPO),
                        EspecCampo.texto("description", "O que é", "Razão social do fornecedor"));
        if (obrigatorio) b.obrigatorio();
        return b.criar();
    }

    @Override
    public Collection<EspecNo> nos() {
        List<EspecNo> nos = new ArrayList<>();
        nos.add(EspecNo.de("ai.copiloto", "ai", "Copiloto analisa",
                        "O Copiloto consulta os dados do Nexo, analisa e escreve um relatório (com PDF, gráficos e tabelas), "
                                + "com as permissões do responsável pela automação. Ótimo para relatórios semanais.", "sparkles")
                .params(
                        EspecParam.de("prompt", "O que analisar", "textarea").obrigatorio()
                                .placeholder("Analise a produtividade das equipes das bases Juazeiro e Petrolina na semana passada, compare com a semana anterior e destaque quem ficou abaixo da meta.")
                                .criar(),
                        EspecParam.de("context", "Dados extras para a análise", "textarea")
                                .placeholder("Opcional: inclua dados de passos anteriores").criar(),
                        EspecParam.de("pdf", "Gerar relatório PDF", "boolean").padrao(true).estatico()
                                .ajuda("O PDF fica disponível para anexar no e-mail.").criar(),
                        EspecParam.de("title", "Título da conversa salva", "text").placeholder("Relatório semanal de produtividade").avancado().criar(),
                        EspecParam.de("timeout_minutes", "Tempo máximo (minutos)", "number").padrao(10).estatico().faixa(1, 30).avancado().criar())
                .saidas(EspecSaida.de("text", "Análise (texto)", "string"),
                        EspecSaida.de("html", "Análise formatada (HTML para e-mail)", "string"),
                        EspecSaida.lista("files", "Arquivos gerados", List.of(EspecSaida.de("nome", "Nome", "string"),
                                EspecSaida.de("tipo", "Tipo", "string"), EspecSaida.de("id", "Id", "string"))),
                        EspecSaida.objeto("pdf", "Relatório PDF", List.of(EspecSaida.de("nome", "Nome", "string"))),
                        EspecSaida.de("conversation_link", "Link da conversa no Copiloto", "string"),
                        EspecSaida.de("gerado_por_ia", "Gerado por IA", "boolean"))
                .executor(this::analisar)
                .repeticao("fixed", 1, 60)
                .satisfaz(TipoAutomacao.DADOS_IA)
                .ia()
                .palavras("copiloto", "análise", "relatório", "produtividade", "agente", "pdf", "semanal")
                .criar());
        nos.add(EspecNo.de("ai.prompt", "ai", "Executar prompt de IA",
                        "A IA lê os dados que você indicar e responde em texto ou em campos que os próximos passos usam.", "sparkle")
                .params(EspecParam.de("instructions", "Instruções", "textarea").obrigatorio()
                                .placeholder("Resuma os dados abaixo para a diretoria, em 5 linhas.").criar(),
                        EspecParam.de("input", "Dados para a IA", "textarea").placeholder("Escolha os dados de um passo anterior").criar(),
                        EspecParam.de("output", "Resposta em", "select").padrao("text").estatico()
                                .opcoes(List.of(List.of("text", "Texto"), List.of("fields", "Campos estruturados"))).criar(),
                        withShow(campos("Campos de saída", false), "output", "fields"),
                        EspecParam.de("temperature", "Criatividade (0 a 1)", "number").padrao(0).estatico().faixa(0, 1).avancado().criar())
                .saidas(EspecSaida.de("text", "Resposta", "string"), EspecSaida.de("fields", "Campos", "object"),
                        EspecSaida.de("gerado_por_ia", "Gerado por IA", "boolean"))
                .executor(this::prompt)
                .repeticao("exponential", 2, 5)
                .satisfaz(TipoAutomacao.DADOS_IA)
                .saidasDinamicas("fields")
                .ia()
                .palavras("gpt", "llm", "prompt", "ia", "inteligência")
                .criar());
        nos.add(EspecNo.de("ai.extract", "ai", "Extrair informações", "Tira campos estruturados de um texto livre (e-mail, observação, descrição).", "scan")
                .params(EspecParam.de("input", "Texto", "textarea").obrigatorio().placeholder("Escolha o texto de um passo anterior").criar(),
                        campos("Campos a extrair", true))
                .saidas(EspecSaida.de("fields", "Campos", "object"), EspecSaida.de("missing", "Campos não encontrados", "array"),
                        EspecSaida.de("complete", "Encontrou todos", "boolean"), EspecSaida.de("gerado_por_ia", "Gerado por IA", "boolean"))
                .executor(this::extrair)
                .repeticao("exponential", 2, 5)
                .satisfaz(TipoAutomacao.DADOS_IA)
                .saidasDinamicas("fields")
                .ia()
                .palavras("extrair", "ler", "documento", "estruturar")
                .criar());
        nos.add(EspecNo.de("ai.classify", "ai", "Classificar com IA",
                        "Escolhe uma categoria para o conteúdo, com confiança e motivo. Combine com 'Escolher caso'.", "tag")
                .params(EspecParam.de("input", "Conteúdo", "textarea").obrigatorio().criar(),
                        EspecParam.de("categories", "Categorias", "list").obrigatorio().padrao(List.of()).placeholder("Urgente").criar(),
                        EspecParam.de("guidance", "Critério", "textarea").placeholder("Urgente quando o valor passa de R$ 10 mil").criar())
                .saidas(EspecSaida.de("category", "Categoria", "string"), EspecSaida.de("confidence", "Confiança (0 a 1)", "number"),
                        EspecSaida.de("reason", "Motivo", "string"), EspecSaida.de("gerado_por_ia", "Gerado por IA", "boolean"))
                .executor(this::classificar)
                .repeticao("exponential", 2, 5)
                .satisfaz(TipoAutomacao.DADOS_IA)
                .ia()
                .palavras("categoria", "triagem", "classificar", "prioridade")
                .criar());
        nos.add(EspecNo.de("ai.summarize", "ai", "Resumir com IA", "Resume dados ou textos em linguagem de negócio.", "text")
                .params(EspecParam.de("input", "O que resumir", "textarea").obrigatorio().criar(),
                        EspecParam.de("style", "Formato", "select").padrao("executive").estatico()
                                .opcoes(List.of(List.of("executive", "Resumo executivo"), List.of("bullets", "Tópicos"), List.of("email", "Parágrafo de e-mail"))).criar(),
                        EspecParam.de("max_lines", "Máximo de linhas", "number").padrao(7).estatico().faixa(1, 40).criar())
                .saidas(EspecSaida.de("text", "Resumo", "string"), EspecSaida.de("gerado_por_ia", "Gerado por IA", "boolean"))
                .executor(this::resumir)
                .repeticao("exponential", 2, 5)
                .satisfaz(TipoAutomacao.DADOS_IA)
                .ia()
                .palavras("resumo", "síntese", "executivo")
                .criar());
        return nos;
    }

    private static EspecParam withShow(EspecParam p, String outro, String valor) {
        return new EspecParam(p.chave(), p.rotulo(), p.tipo(), p.obrigatorio(), p.padrao(), p.ajuda(), p.placeholder(), p.opcoes(),
                p.resolver(), p.dinamico(), p.minimo(), p.maximo(), p.campos(), List.of(outro, valor), p.avancado());
    }

    // ------------------------------------------------------------------------------ execução

    private String chamar(ContextoAcao ctx, String usuario, double temperatura) {
        if (!copiloto.disponivel()) throw new ErroAcao("Nenhum modelo de IA configurado no Nexo");
        try {
            return copiloto.completar(SISTEMA, usuario, temperatura, ctx.limiteSegundos() == null ? 120 : ctx.limiteSegundos());
        } catch (RuntimeException e) {
            throw new ErroAcao("O modelo de IA não respondeu: " + e.getClass().getSimpleName(), true);
        }
    }

    private static String entrada(Object valor) {
        String texto = valor instanceof String s ? s : Valores.paraJson(valor);
        return texto.length() > LIMITE_ENTRADA ? texto.substring(0, LIMITE_ENTRADA) + "\n[… cortado]" : texto;
    }

    private static Object json(String texto) {
        Matcher m = JSON.matcher(texto);
        if (!m.find()) throw new ErroAcao("A IA não devolveu JSON", true);
        try {
            return Valores.json().readValue(m.group(), Object.class);
        } catch (JsonProcessingException e) {
            throw new ErroAcao("A IA devolveu JSON inválido", true);
        }
    }

    private static List<Map<String, Object>> camposLimpos(Map<String, Object> p) {
        List<Map<String, Object>> lista = new ArrayList<>();
        for (Object f : Valores.lista(p.get("fields"))) {
            Map<String, Object> m = Valores.mapa(f);
            if (!Valores.texto(m.get("name")).isBlank()) lista.add(m);
        }
        return lista;
    }

    private static String esquema(List<Map<String, Object>> campos) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> f : campos) {
            sb.append("- \"").append(Valores.texto(f.get("name")).strip()).append("\" (")
                    .append(Valores.texto(f.getOrDefault("type", "text"))).append("): ").append(Valores.texto(f.get("description"))).append('\n');
        }
        return sb.toString();
    }

    private static Map<String, Object> coagir(List<Map<String, Object>> campos, Object bruto) {
        Map<String, Object> dados = Valores.mapa(bruto);
        Map<String, Object> r = new LinkedHashMap<>();
        for (Map<String, Object> f : campos) {
            String nome = Valores.texto(f.get("name")).strip();
            Object v = dados.get(nome);
            switch (Valores.texto(f.getOrDefault("type", "text"))) {
                case "number" -> v = Valores.numero(v);
                case "boolean" -> v = v instanceof Boolean ? v : List.of("true", "sim", "1").contains(Valores.texto(v).strip().toLowerCase());
                case "list" -> v = v instanceof List<?> ? v : Valores.vazio(v) ? List.of() : List.of(v);
                default -> v = v == null ? null : Valores.texto(v);
            }
            r.put(nome, v);
        }
        return r;
    }

    private Map<String, Object> prompt(ContextoAcao ctx, Map<String, Object> p) {
        String instrucoes = Valores.texto(p.get("instructions")).strip();
        if (instrucoes.isEmpty()) throw new ErroAcao("Escreva as instruções do prompt");
        String usuario = instrucoes;
        if (!Valores.vazio(p.get("input"))) usuario += "\n\nDADOS:\n" + entrada(p.get("input"));
        List<Map<String, Object>> campos = camposLimpos(p);
        double temperatura = Valores.dbl(p.get("temperature"));
        Map<String, Object> r = new LinkedHashMap<>();
        if ("fields".equals(Valores.texto(p.get("output"))) && !campos.isEmpty()) {
            usuario += "\n\nResponda SOMENTE com um objeto JSON com estes campos:\n" + esquema(campos) + "Use null quando a informação não existir.";
            String texto = chamar(ctx, usuario, temperatura);
            r.put("text", texto);
            r.put("fields", coagir(campos, json(texto)));
            return r;
        }
        r.put("text", chamar(ctx, usuario, temperatura).strip());
        r.put("fields", Map.of());
        return r;
    }

    private Map<String, Object> extrair(ContextoAcao ctx, Map<String, Object> p) {
        List<Map<String, Object>> campos = camposLimpos(p);
        if (campos.isEmpty()) throw new ErroAcao("Informe os campos a extrair");
        if (Valores.vazio(p.get("input"))) throw new ErroAcao("Informe o texto de onde extrair");
        String usuario = "Extraia os campos abaixo do conteúdo. Responda SOMENTE com um objeto JSON.\n" + esquema(campos)
                + "Use null quando o campo não estiver no conteúdo; não deduza valores.\n\nCONTEÚDO:\n" + entrada(p.get("input"));
        Map<String, Object> valores = coagir(campos, json(chamar(ctx, usuario, 0)));
        List<String> faltando = new ArrayList<>();
        valores.forEach((k, v) -> {
            if (Valores.vazio(v)) faltando.add(k);
        });
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("fields", valores);
        r.put("missing", faltando);
        r.put("complete", faltando.isEmpty());
        return r;
    }

    private Map<String, Object> classificar(ContextoAcao ctx, Map<String, Object> p) {
        List<String> categorias = new ArrayList<>();
        Object bruto = p.get("categories");
        if (bruto instanceof String s) {
            for (String c : s.split(",")) if (!c.isBlank()) categorias.add(c.strip());
        } else {
            for (Object c : Valores.lista(bruto)) if (!Valores.texto(c).isBlank()) categorias.add(Valores.texto(c).strip());
        }
        if (categorias.size() < 2) throw new ErroAcao("Informe ao menos duas categorias");
        if (Valores.vazio(p.get("input"))) throw new ErroAcao("Informe o que classificar");
        String criterio = Valores.texto(p.get("guidance")).strip();
        String usuario = "Classifique o conteúdo em UMA das categorias. Responda SOMENTE com JSON "
                + "{\"category\": \"...\", \"confidence\": 0.0 a 1.0, \"reason\": \"uma frase\"}.\nCategorias: "
                + Valores.paraJson(categorias) + "\n" + (criterio.isEmpty() ? "" : "Critério: " + criterio + "\n")
                + "\nCONTEÚDO:\n" + entrada(p.get("input"));
        Map<String, Object> resposta = Valores.mapa(json(chamar(ctx, usuario, 0)));
        String categoria = Valores.texto(resposta.get("category")).strip();
        String escolhida = categorias.stream().filter(c -> c.equalsIgnoreCase(categoria)).findFirst()
                .orElseThrow(() -> new ErroAcao("A IA respondeu uma categoria fora da lista: " + categoria, true));
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("category", escolhida);
        r.put("confidence", Valores.numero(resposta.get("confidence")));
        r.put("reason", Valores.texto(resposta.get("reason")));
        return r;
    }

    private Map<String, Object> resumir(ContextoAcao ctx, Map<String, Object> p) {
        if (Valores.vazio(p.get("input"))) throw new ErroAcao("Informe o que resumir");
        String estilo = switch (Valores.texto(p.getOrDefault("style", "executive"))) {
            case "bullets" -> "tópicos curtos";
            case "email" -> "um parágrafo cordial para um e-mail";
            default -> "um resumo executivo para a diretoria, com o que importa e o que fazer";
        };
        int linhas = (int) Math.max(1, Valores.dbl(p.getOrDefault("max_lines", 7)));
        String usuario = "Escreva " + estilo + ", em no máximo " + linhas + " linhas.\n\nDADOS:\n" + entrada(p.get("input"));
        return NosDados.mapa("text", chamar(ctx, usuario, 0.2).strip());
    }

    private Map<String, Object> analisar(ContextoAcao ctx, Map<String, Object> p) {
        if (!copiloto.disponivel()) throw new ErroAcao("O Copiloto está indisponível: o provedor de IA não está configurado.");
        String pedido = Valores.texto(p.get("prompt")).strip();
        if (pedido.isEmpty()) throw new ErroAcao("Diga o que o Copiloto deve analisar");
        boolean pdf = !Boolean.FALSE.equals(p.get("pdf"));
        StringBuilder pergunta = new StringBuilder(pedido);
        if (!Valores.vazio(p.get("context"))) pergunta.append("\n\nDados de apoio (vindos da automação):\n").append(entrada(p.get("context")));
        pergunta.append("\n\n---\nEsta análise é executada por uma automação do Nexo (\"").append(ctx.automacaoNome())
                .append("\") e será enviada automaticamente por e-mail. Não faça perguntas de volta: se faltar alguma informação, ")
                .append("assuma o razoável e diga o que assumiu. Consulte os dados reais antes de concluir. Responda em markdown, ")
                .append("começando por um resumo executivo curto, depois os destaques com números e as recomendações.");
        if (pdf) pergunta.append(" Ao final, gere também um relatório PDF com a ferramenta de relatório, com gráficos e tabelas da análise.");
        String titulo = Valores.texto(p.get("title")).isBlank() ? ctx.automacaoNome() : Valores.texto(p.get("title"));
        int limite = (int) Math.max(60, Math.min(1800, Valores.dbl(p.getOrDefault("timeout_minutes", 10)) * 60));
        String nome = usuarios.buscarNomePorId(ctx.exigirDono()).orElse(null);
        CopilotoApi.Analise analise = copiloto.analisar(new CopilotoApi.PedidoAnalise(ctx.donoId(), ctx.donoPermissoes(), nome,
                titulo + " — " + Valores.formatarData(ctx.agora(), "dd/mm/aaaa"), pergunta.toString(), limite));
        if (!analise.concluida() || analise.texto().isBlank()) {
            throw new ErroAcao(analise.erro() == null ? "O Copiloto não concluiu a análise" : analise.erro(), true);
        }
        List<Map<String, Object>> refs = new ArrayList<>();
        Map<String, Object> pdfRef = null;
        for (CopilotoApi.Arquivo arquivo : analise.arquivos()) {
            ArquivoAutomacao salvo = arquivos.save(new ArquivoAutomacao(ctx.execucaoId(), arquivo.nome(), arquivo.tipoConteudo(),
                    arquivo.conteudo(), OffsetDateTime.now()));
            Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("id", salvo.getId().toString());
            ref.put("nome", salvo.getNome());
            ref.put("tipo", salvo.getTipoConteudo());
            ref.put("tamanho", salvo.getTamanhoBytes());
            ref.put("arquivo_automacao", true);
            refs.add(ref);
            if (pdfRef == null && salvo.getTipoConteudo().contains("pdf")) pdfRef = ref;
        }
        // Sem PDF (o agente às vezes responde sem chamar a ferramenta de relatório), o texto segue mesmo assim.
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("text", analise.texto());
        r.put("html", MarkdownHtml.converter(analise.texto()));
        r.put("files", refs);
        r.put("pdf", pdfRef);
        r.put("conversation_link", analise.conversaId() == null ? null : emailProperties.resolverUrl("/copiloto/" + analise.conversaId()));
        r.put("duracao_ms", analise.duracaoMs());
        return r;
    }
}
