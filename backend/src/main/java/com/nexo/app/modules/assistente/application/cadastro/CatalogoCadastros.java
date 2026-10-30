package com.nexo.app.modules.assistente.application.cadastro;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reune o que cada modulo declarou que o Copiloto pode cadastrar, le os campos dos DTOs dos casos de uso e
 * localiza registros referenciados pelo nome.
 */
@Component
public class CatalogoCadastros {

    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogoCadastros.class);
    private static final int LIMITE_BUSCA = 8;
    private static final String COM_ACENTO = "áàâãäéèêëíìîïóòôõöúùûüçñ";
    private static final String SEM_ACENTO = "aaaaaeeeeiiiiooooouuuucn";

    /** Palavras que o nome do campo perde ao virar rotulo ("dataContratacao" -> "Data de contratação"). */
    private static final Map<String, String> PALAVRAS = Map.ofEntries(
            Map.entry("contratacao", "contratação"), Map.entry("funcao", "função"), Map.entry("observacao", "observação"),
            Map.entry("observacoes", "observações"), Map.entry("descricao", "descrição"), Map.entry("numero", "número"),
            Map.entry("codigo", "código"), Map.entry("veiculo", "veículo"), Map.entry("ocorrencia", "ocorrência"),
            Map.entry("municipio", "município"), Map.entry("inspecao", "inspeção"), Map.entry("usuario", "usuário"),
            Map.entry("endereco", "endereço"), Map.entry("razao", "razão"), Map.entry("padrao", "padrão"),
            Map.entry("previsao", "previsão"), Map.entry("solicitacao", "solicitação"), Map.entry("situacao", "situação"),
            Map.entry("inicio", "início"), Map.entry("fim", "fim"), Map.entry("referencia", "referência"),
            Map.entry("unidade", "unidade"), Map.entry("medida", "medida"), Map.entry("servico", "serviço"),
            Map.entry("prioridade", "prioridade"), Map.entry("topico", "tópico"), Map.entry("acao", "ação"),
            Map.entry("acoes", "ações"), Map.entry("periodo", "período"), Map.entry("titulo", "título"),
            Map.entry("conteudo", "conteúdo"), Map.entry("publicacao", "publicação"), Map.entry("matricula", "matrícula"),
            Map.entry("realizacao", "realização"), Map.entry("responsavel", "responsável"), Map.entry("critico", "crítico"),
            Map.entry("valido", "válido"), Map.entry("minimo", "mínimo"), Map.entry("maximo", "máximo"),
            Map.entry("orcamento", "orçamento"), Map.entry("emissao", "emissão"), Map.entry("especificacao", "especificação"),
            Map.entry("telefone", "telefone"), Map.entry("saque", "saque"), Map.entry("investimento", "investimento"),
            Map.entry("faturamento", "faturamento"), Map.entry("negocio", "negócio"), Map.entry("transferencia", "transferência"),
            Map.entry("ultimo", "último"), Map.entry("logistico", "logístico"), Map.entry("unitario", "unitário"),
            Map.entry("horario", "horário"), Map.entry("mes", "mês"), Map.entry("graduacao", "graduação"),
            Map.entry("evidencias", "evidências"), Map.entry("transcricao", "transcrição"), Map.entry("reuniao", "reunião"),
            Map.entry("gravidade", "gravidade"), Map.entry("categoria", "categoria"), Map.entry("placa", "placa"));

    private final ObjectProvider<ProvedorCadastros> provedores;
    private final JdbcTemplate jdbc;
    private volatile Map<String, OperacaoCadastro<?>> operacoes;
    private volatile Map<String, ReferenciaCadastro> referencias;
    private final Map<String, List<CampoDescritor>> camposPorOperacao = new ConcurrentHashMap<>();

    public CatalogoCadastros(ObjectProvider<ProvedorCadastros> provedores, JdbcTemplate jdbc) {
        this.provedores = provedores;
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------------------------- catalogo

    public Collection<OperacaoCadastro<?>> todas() {
        return indice().values();
    }

    public List<OperacaoCadastro<?>> disponiveis(Set<String> autoridades) {
        return indice().values().stream().filter(op -> op.autorizada(autoridades)).toList();
    }

    public Optional<OperacaoCadastro<?>> buscar(String chave) {
        if (chave == null || chave.isBlank()) return Optional.empty();
        String limpa = chave.strip().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (limpa.startsWith("cadastrar_")) limpa = limpa.substring("cadastrar_".length());
        return Optional.ofNullable(indice().get(limpa));
    }

    public Optional<ReferenciaCadastro> referencia(String tipo) {
        return Optional.ofNullable(tipo == null ? null : indiceReferencias().get(tipo));
    }

    public Collection<ReferenciaCadastro> referencias() {
        return indiceReferencias().values();
    }

    /** Tipos de referencia usados pelas operacoes que o usuario pode fazer: so esses podem ser consultados. */
    public Set<String> referenciasPermitidas(Set<String> autoridades) {
        Set<String> tipos = new java.util.TreeSet<>();
        for (OperacaoCadastro<?> op : disponiveis(autoridades)) {
            coletarReferencias(campos(op), tipos);
            if (op.referenciaRegistro() != null) tipos.add(op.referenciaRegistro());
        }
        return tipos;
    }

    private void coletarReferencias(List<CampoDescritor> campos, Set<String> tipos) {
        for (CampoDescritor campo : campos) {
            if (campo.referencia() != null) tipos.add(campo.referencia());
            coletarReferencias(campo.filhos(), tipos);
        }
    }

    private Map<String, OperacaoCadastro<?>> indice() {
        Map<String, OperacaoCadastro<?>> atual = operacoes;
        if (atual != null) return atual;
        synchronized (this) {
            if (operacoes == null) carregar();
            return operacoes;
        }
    }

    private Map<String, ReferenciaCadastro> indiceReferencias() {
        indice();
        return referencias;
    }

    private void carregar() {
        Map<String, OperacaoCadastro<?>> ops = new LinkedHashMap<>();
        Map<String, ReferenciaCadastro> refs = new LinkedHashMap<>();
        provedores.orderedStream().forEach(provedor -> {
            for (ReferenciaCadastro ref : provedor.referencias()) {
                if (refs.putIfAbsent(ref.tipo(), ref) != null) {
                    throw new IllegalStateException("Referencia de cadastro duplicada: " + ref.tipo());
                }
            }
            for (OperacaoCadastro<?> op : provedor.operacoes()) {
                if (ops.putIfAbsent(op.chave(), op) != null) {
                    throw new IllegalStateException("Operacao de cadastro duplicada: " + op.chave());
                }
            }
        });
        this.referencias = Map.copyOf(refs);
        this.operacoes = java.util.Collections.unmodifiableMap(ops);
        for (OperacaoCadastro<?> op : ops.values()) {
            for (CampoDescritor campo : campos(op)) validarReferencias(op, campo);
        }
    }

    private void validarReferencias(OperacaoCadastro<?> op, CampoDescritor campo) {
        if (campo.referencia() != null && !referencias.containsKey(campo.referencia())) {
            throw new IllegalStateException("Operacao " + op.chave() + " usa a referencia inexistente " + campo.referencia());
        }
        campo.filhos().forEach(filho -> validarReferencias(op, filho));
    }

    // ---------------------------------------------------------------------------------------- campos

    /** Campos do DTO da operacao, com os ajustes declarados (sem os campos dinamicos). */
    public List<CampoDescritor> campos(OperacaoCadastro<?> op) {
        return camposPorOperacao.computeIfAbsent(op.chave(), ignored -> ler(op.requisicao(), "", op));
    }

    private List<CampoDescritor> ler(Class<?> tipo, String prefixo, OperacaoCadastro<?> op) {
        if (!tipo.isRecord()) throw new IllegalStateException("O DTO " + tipo.getSimpleName() + " precisa ser um record");
        List<CampoDescritor> campos = new ArrayList<>();
        for (RecordComponent componente : tipo.getRecordComponents()) {
            String nome = componente.getName();
            String caminho = prefixo + nome;
            OperacaoCadastro.Campo ajuste = op.campos().get(caminho);
            Field field = campoDeclarado(tipo, nome);
            boolean obrigatorio = obrigatorio(field);
            Integer maximo = maximo(field);
            String descricao = descricao(field);
            String rotulo = rotulo(nome);
            CampoDescritor campo = descrever(componente.getType(), componente.getGenericType(), nome, rotulo, obrigatorio,
                    descricao, maximo, caminho, op, field);
            if (ajuste != null) campo = ajustar(campo, ajuste);
            campos.add(campo);
        }
        return List.copyOf(campos);
    }

    private CampoDescritor descrever(Class<?> tipo, Type generico, String nome, String rotulo, boolean obrigatorio,
            String descricao, Integer maximo, String caminho, OperacaoCadastro<?> op, Field field) {
        if (Collection.class.isAssignableFrom(tipo)) {
            Class<?> item = argumento(generico);
            if (item != null && item.isRecord()) {
                return new CampoDescritor(nome, rotulo, TipoCampo.LISTA, obrigatorio, descricao, List.of(), null,
                        ler(item, caminho + "[].", op), false, null, false);
            }
            CampoDescritor simples = descrever(item == null ? String.class : item, item, nome, rotulo, obrigatorio,
                    descricao, maximo, caminho, op, field);
            return simples.comMultiplo(true);
        }
        if (tipo.isRecord()) {
            return new CampoDescritor(nome, rotulo, TipoCampo.OBJETO, obrigatorio, descricao, List.of(), null,
                    ler(tipo, caminho + ".", op), false, null, false);
        }
        if (Map.class.isAssignableFrom(tipo)) {
            return CampoDescritor.simples(nome, rotulo, TipoCampo.OBJETO, obrigatorio, descricao);
        }
        if (tipo.isEnum()) {
            return CampoDescritor.opcoes(nome, rotulo, opcoesDoEnum(tipo), obrigatorio, descricao);
        }
        if (tipo == UUID.class) {
            String referencia = adivinharReferencia(nome);
            if (referencia != null) {
                return CampoDescritor.referencia(nome, rotuloDeReferencia(nome, referencia), referencia, obrigatorio, descricao);
            }
            return CampoDescritor.simples(nome, rotulo, TipoCampo.UUID, obrigatorio, descricao);
        }
        TipoCampo tipoCampo = tipoSimples(tipo, nome, field);
        return new CampoDescritor(nome, rotulo, tipoCampo, obrigatorio, descricao, List.of(), null, List.of(), false, maximo, false);
    }

    private static TipoCampo tipoSimples(Class<?> tipo, String nome, Field field) {
        String minusculo = nome.toLowerCase(Locale.ROOT);
        if (tipo == Integer.class || tipo == int.class || tipo == Long.class || tipo == long.class
                || tipo == Short.class || tipo == short.class) {
            return TipoCampo.INTEIRO;
        }
        if (tipo == BigDecimal.class || tipo == Double.class || tipo == double.class || tipo == Float.class || tipo == float.class) {
            return minusculo.contains("valor") || minusculo.contains("preco") || minusculo.contains("custo")
                    ? TipoCampo.MOEDA : TipoCampo.DECIMAL;
        }
        if (tipo == Boolean.class || tipo == boolean.class) return TipoCampo.BOOLEANO;
        if (tipo == LocalDate.class) return TipoCampo.DATA;
        if (tipo == LocalTime.class) return TipoCampo.HORA;
        if (tipo == OffsetDateTime.class || tipo == LocalDateTime.class || tipo == Instant.class || tipo == ZonedDateTime.class) {
            return TipoCampo.DATA_HORA;
        }
        if (minusculo.contains("senha")) return TipoCampo.SENHA;
        if (minusculo.equals("cpf") || minusculo.endsWith("cpf")) return TipoCampo.CPF;
        if (minusculo.equals("cnpj") || minusculo.endsWith("cnpj")) return TipoCampo.CNPJ;
        if (minusculo.contains("email") || (field != null && field.isAnnotationPresent(Email.class))) return TipoCampo.EMAIL;
        if (minusculo.contains("descricao") || minusculo.contains("observ") || minusculo.contains("justificativa")
                || minusculo.contains("conteudo") || minusculo.contains("detalh")) {
            return TipoCampo.TEXTO_LONGO;
        }
        return TipoCampo.TEXTO;
    }

    private static CampoDescritor ajustar(CampoDescritor campo, OperacaoCadastro.Campo ajuste) {
        TipoCampo tipo = ajuste.tipo() != null ? ajuste.tipo() : ajuste.referencia() != null ? TipoCampo.REFERENCIA : campo.tipo();
        return new CampoDescritor(campo.chave(),
                ajuste.rotulo() != null ? ajuste.rotulo() : campo.rotulo(),
                tipo,
                ajuste.obrigatorio() != null ? ajuste.obrigatorio() : campo.obrigatorio(),
                ajuste.descricao() != null ? ajuste.descricao() : campo.descricao(),
                ajuste.opcoes() != null ? ajuste.opcoes() : campo.opcoes(),
                ajuste.referencia() != null ? ajuste.referencia() : tipo == TipoCampo.REFERENCIA ? campo.referencia() : null,
                campo.filhos(), campo.multiplo(), campo.tamanhoMaximo(), ajuste.isOculto() || campo.oculto());
    }

    private String adivinharReferencia(String nome) {
        String base = nome;
        if (base.startsWith("id") && base.length() > 2 && Character.isUpperCase(base.charAt(2))) base = base.substring(2);
        else if (base.endsWith("Id") && base.length() > 2) base = base.substring(0, base.length() - 2);
        else return null;
        List<String> palavras = palavras(base);
        Map<String, ReferenciaCadastro> refs = referencias;
        if (refs == null) return null;
        for (int fim = palavras.size(); fim > 0; fim--) {
            String candidato = String.join("_", palavras.subList(0, fim));
            if (refs.containsKey(candidato)) return candidato;
        }
        return null;
    }

    private String rotuloDeReferencia(String nome, String referencia) {
        ReferenciaCadastro ref = referencias.get(referencia);
        String base = nome.startsWith("id") ? nome.substring(2) : nome.endsWith("Id") ? nome.substring(0, nome.length() - 2) : nome;
        String deNome = rotulo(base);
        String deRef = ref == null ? deNome : ref.rotulo();
        // "idEstoqueOrigem" -> "Estoque origem" carrega mais informacao do que "Estoque"
        return normalizar(deNome).equals(normalizar(deRef)) || palavras(base).size() <= 1 ? deRef : deNome;
    }

    private static List<Opcao> opcoesDoEnum(Class<?> tipo) {
        List<Opcao> opcoes = new ArrayList<>();
        for (Object constante : tipo.getEnumConstants()) {
            String nome = ((Enum<?>) constante).name();
            opcoes.add(new Opcao(nome, rotuloDoEnum(constante), null));
        }
        return opcoes;
    }

    static String rotuloDoEnum(Object constante) {
        for (String metodo : new String[]{"getRotulo", "getDescricao", "getLabel", "getNome", "rotulo", "descricao", "label"}) {
            try {
                Method m = constante.getClass().getMethod(metodo);
                Object valor = m.invoke(constante);
                if (valor instanceof String texto && !texto.isBlank()) return texto;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // tenta o proximo
            }
        }
        String nome = ((Enum<?>) constante).name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return rotulo(nome);
    }

    private static Field campoDeclarado(Class<?> tipo, String nome) {
        try {
            return tipo.getDeclaredField(nome);
        } catch (NoSuchFieldException exception) {
            return null;
        }
    }

    private static boolean obrigatorio(Field field) {
        if (field == null) return false;
        if (field.isAnnotationPresent(NotNull.class) || field.isAnnotationPresent(NotBlank.class)
                || field.isAnnotationPresent(NotEmpty.class)) {
            return true;
        }
        Schema schema = field.getAnnotation(Schema.class);
        return schema != null && schema.requiredMode() == Schema.RequiredMode.REQUIRED;
    }

    private static Integer maximo(Field field) {
        if (field == null) return null;
        Size size = field.getAnnotation(Size.class);
        if (size != null && size.max() < Integer.MAX_VALUE) return size.max();
        Schema schema = field.getAnnotation(Schema.class);
        return schema != null && schema.maxLength() < Integer.MAX_VALUE ? schema.maxLength() : null;
    }

    private static String descricao(Field field) {
        if (field == null) return null;
        Schema schema = field.getAnnotation(Schema.class);
        return schema == null || schema.description().isBlank() ? null : schema.description();
    }

    private static Class<?> argumento(Type generico) {
        if (generico instanceof ParameterizedType parametrizado && parametrizado.getActualTypeArguments().length == 1
                && parametrizado.getActualTypeArguments()[0] instanceof Class<?> classe) {
            return classe;
        }
        return null;
    }

    /** "dataContratacao" -> "Data de contratação"; "idBaseOperacional" -> "Base operacional". */
    public static String rotulo(String nome) {
        List<String> palavras = palavras(nome);
        if (!palavras.isEmpty() && palavras.getFirst().equals("id") && palavras.size() > 1) palavras = palavras.subList(1, palavras.size());
        if (palavras.size() > 1 && palavras.getLast().equals("id")) palavras = palavras.subList(0, palavras.size() - 1);
        List<String> saida = new ArrayList<>();
        for (int i = 0; i < palavras.size(); i++) {
            String palavra = PALAVRAS.getOrDefault(palavras.get(i), palavras.get(i));
            if (i == 1 && palavras.getFirst().equals("data") && substantivo(palavra)) saida.add("de");
            saida.add(palavra);
        }
        String texto = String.join(" ", saida);
        return texto.isEmpty() ? nome : Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }

    private static boolean substantivo(String palavra) {
        return palavra.endsWith("ção") || palavra.endsWith("ência") || palavra.endsWith("são")
                || Set.of("início", "fim", "nascimento", "entrega", "vencimento", "pagamento", "saque", "validade",
                        "referência", "compra", "abertura", "fechamento", "chegada", "saída").contains(palavra);
    }

    private static List<String> palavras(String nome) {
        List<String> palavras = new ArrayList<>();
        for (String parte : nome.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replace('_', ' ').trim().split("\\s+")) {
            if (!parte.isBlank()) palavras.add(parte.toLowerCase(Locale.ROOT));
        }
        return palavras;
    }

    // ----------------------------------------------------------------------------------- referencias

    /** Registros do cadastro cujo nome contem todas as palavras do termo, os mais parecidos primeiro. */
    public List<Opcao> buscarReferencia(String tipo, String termo, int limite) {
        ReferenciaCadastro ref = referencia(tipo).orElseThrow(() -> new IllegalArgumentException("Referencia desconhecida: " + tipo));
        String normalizado = normalizar(termo == null ? "" : termo);
        List<String> palavras = normalizado.isBlank() ? List.of() : List.of(normalizado.split("\\s+"));
        String texto = expressaoNormalizada("coalesce(r.rotulo, '') || ' ' || coalesce(r.detalhe, '')");
        String rotulo = expressaoNormalizada("coalesce(r.rotulo, '')");
        StringBuilder sql = new StringBuilder("select r.id, r.rotulo, r.detalhe from (").append(ref.sql()).append(") r where 1 = 1");
        List<Object> parametros = new ArrayList<>();
        for (String palavra : palavras.stream().limit(6).toList()) {
            sql.append(" and ").append(texto).append(" like ?");
            parametros.add("%" + palavra + "%");
        }
        sql.append(" order by case when ").append(rotulo).append(" = ? then 0 when ").append(rotulo)
                .append(" like ? then 1 else 2 end, length(coalesce(r.rotulo, '')), r.rotulo limit ?");
        parametros.add(normalizado);
        parametros.add(normalizado + "%");
        parametros.add(Math.max(1, Math.min(limite, 50)));
        try {
            return exibir(ref, jdbc.query(sql.toString(),
                    (rs, i) -> new Opcao(rs.getString("id"), rs.getString("rotulo"), rs.getString("detalhe")), parametros.toArray()));
        } catch (RuntimeException exception) {
            LOGGER.warn("Falha ao buscar a referencia {}: {}", tipo, exception.getMessage());
            throw new IllegalStateException("Nao foi possivel consultar " + ref.rotulo(), exception);
        }
    }

    public List<Opcao> buscarReferencia(String tipo, String termo) {
        return buscarReferencia(tipo, termo, LIMITE_BUSCA);
    }

    public Optional<Opcao> referenciaPorId(String tipo, UUID id) {
        ReferenciaCadastro ref = referencia(tipo).orElseThrow(() -> new IllegalArgumentException("Referencia desconhecida: " + tipo));
        List<Opcao> encontrados = jdbc.query("select r.id, r.rotulo, r.detalhe from (" + ref.sql() + ") r where cast(r.id as text) = ?",
                (rs, i) -> new Opcao(rs.getString("id"), rs.getString("rotulo"), rs.getString("detalhe")), id.toString());
        return exibir(ref, encontrados).stream().findFirst();
    }

    private static List<Opcao> exibir(ReferenciaCadastro ref, List<Opcao> opcoes) {
        if (ref.exibicao() == null || opcoes.isEmpty()) return opcoes;
        Map<UUID, String> rotulos = ref.exibicao().apply(opcoes.stream().map(o -> UUID.fromString(o.valor())).toList());
        return opcoes.stream().map(o -> {
            String rotulo = rotulos.get(UUID.fromString(o.valor()));
            return rotulo == null || rotulo.isBlank() ? o : new Opcao(o.valor(), rotulo, o.detalhe());
        }).toList();
    }

    private static String expressaoNormalizada(String expressao) {
        return "translate(lower(" + expressao + "), '" + COM_ACENTO + "', '" + SEM_ACENTO + "')";
    }

    public static String normalizar(String texto) {
        if (texto == null) return "";
        String semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }
}
