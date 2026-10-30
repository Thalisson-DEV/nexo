package com.nexo.app.modules.assistente.application.cadastro;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexo.app.shared.domain.exception.BaseBusinessException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Prepara e executa cadastros: le o que o usuario (ou o modelo) informou, troca nomes por ids, normaliza
 * datas, numeros e opcoes, aponta o que falta e, na confirmacao, chama o caso de uso do modulo dentro de uma
 * transacao propria por registro.
 */
@Component
public class MotorCadastro {

    private static final Logger LOGGER = LoggerFactory.getLogger(MotorCadastro.class);
    static final ZoneId FUSO = ZoneId.of("America/Bahia");
    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter BR_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final int MAX_CANDIDATOS = 6;

    private final CatalogoCadastros catalogo;
    private final ObjectMapper mapper;
    private final Validator validator;
    private final TransactionTemplate transacao;

    public MotorCadastro(CatalogoCadastros catalogo, ObjectMapper objectMapper, Validator validator,
            PlatformTransactionManager transacoes) {
        this.catalogo = catalogo;
        this.mapper = objectMapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.validator = validator;
        this.transacao = new TransactionTemplate(transacoes);
        this.transacao.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** O que falta para gravar um registro. */
    public record Pendencia(String caminho, String rotulo, String problema, List<Opcao> opcoes) {
    }

    /**
     * Registro preparado.
     *
     * @param dados       valores normalizados (ids no lugar de nomes), guardados na acao pendente
     * @param campos      campos para o cartao (rotulo, valor exibido, problema, candidatos)
     * @param pendencias  o que impede a gravacao
     * @param requisicao  DTO pronto para o caso de uso (nulo se houver pendencia)
     * @param titulo      identificacao curta do registro
     */
    public record Preparo(OperacaoCadastro<?> operacao, UUID registroId, ObjectNode dados, ArrayNode campos,
            List<Pendencia> pendencias, Object requisicao, String titulo, List<String> caminhosSecretos,
            Map<String, String> senhasGeradas) {

        public boolean pronto() {
            return pendencias.isEmpty() && requisicao != null;
        }

        /** Dados que podem ser guardados na acao pendente: senhas nunca ficam gravadas. */
        public ObjectNode dadosArmazenaveis() {
            ObjectNode copia = dados.deepCopy();
            for (String caminho : caminhosSecretos) remover(copia, caminho);
            return copia;
        }

        private static void remover(ObjectNode raiz, String caminho) {
            String[] partes = caminho.split("\\.");
            JsonNode atual = raiz;
            for (int i = 0; i < partes.length - 1 && atual != null; i++) {
                atual = atual.isArray() ? atual.get(Integer.parseInt(partes[i])) : atual.get(partes[i]);
            }
            if (atual instanceof ObjectNode objeto) objeto.remove(partes[partes.length - 1]);
        }
    }

    /** Registro gravado pelo caso de uso; {@code senhasGeradas} so vai na resposta, nunca para o historico. */
    public record Gravado(String id, String rotulo, String destino, Map<String, Object> parametros,
            Map<String, String> senhasGeradas) {
    }

    // ------------------------------------------------------------------------------------- preparo

    public Preparo preparar(OperacaoCadastro<?> op, JsonNode entrada, UUID registroId) {
        ObjectNode informado = entrada != null && entrada.isObject() ? ((ObjectNode) entrada).deepCopy()
                : JsonNodeFactory.instance.objectNode();
        Map<String, String> anteriores = new HashMap<>();
        ObjectNode base = informado;
        ObjectNode atual = null;
        if (op.modo() == OperacaoCadastro.Modo.EDITAR) {
            if (registroId == null) throw new IllegalArgumentException("Informe qual registro editar");
            atual = formaDaRequisicao(catalogo.campos(op), mapper.valueToTree(op.carregador().apply(registroId)));
            base = atual.deepCopy();
            sobrepor(base, informado);
        }
        Contexto contexto = new Contexto();
        ObjectNode dados = JsonNodeFactory.instance.objectNode();
        ArrayNode campos = JsonNodeFactory.instance.arrayNode();
        List<Pendencia> pendencias = new ArrayList<>();
        normalizar(op, catalogo.campos(op), base, dados, campos, pendencias, "", contexto);
        if (atual != null) marcarAlterados(campos, informado, atual);
        Object requisicao = null;
        if (pendencias.isEmpty()) {
            try {
                requisicao = mapper.treeToValue(dados, op.requisicao());
                for (ConstraintViolation<?> violacao : validator.validate(requisicao)) {
                    String caminho = violacao.getPropertyPath().toString();
                    pendencias.add(new Pendencia(caminho, rotuloDoCaminho(campos, caminho), violacao.getMessage(), List.of()));
                    marcarProblema(campos, caminho, violacao.getMessage());
                }
            } catch (Exception exception) {
                LOGGER.debug("Dados de {} nao convertem no DTO: {}", op.chave(), exception.getMessage());
                pendencias.add(new Pendencia("", op.registro(), "Alguns dados não estão no formato esperado. Revise os campos.", List.of()));
            }
            if (!pendencias.isEmpty()) requisicao = null;
        }
        return new Preparo(op, registroId, dados, campos, List.copyOf(pendencias), requisicao, titulo(op, campos),
                List.copyOf(contexto.secretos), Map.copyOf(contexto.geradas));
    }

    private void normalizar(OperacaoCadastro<?> op, List<CampoDescritor> descritores, JsonNode entrada, ObjectNode saida,
            ArrayNode campos, List<Pendencia> pendencias, String prefixo, Contexto contexto) {
        for (CampoDescritor campo : descritores) {
            String caminho = prefixo + campo.chave();
            JsonNode valor = valorInformado(entrada, campo);
            OperacaoCadastro.Campo ajuste = op.campos().get(caminhoDeclarado(caminho));
            if (vazio(valor) && ajuste != null && ajuste.derivado() != null) {
                Object derivado = ajuste.derivado().apply(entrada);
                if (derivado != null) valor = mapper.valueToTree(derivado);
            }
            if (vazio(valor) && ajuste != null && ajuste.padrao() != null) {
                Object padrao = ajuste.padrao().get();
                if (padrao != null) {
                    valor = mapper.valueToTree(padrao);
                    contexto.padroes.add(caminho);
                }
            }
            if (campo.oculto()) {
                if (!vazio(valor)) saida.set(campo.chave(), valor);
                continue;
            }
            ObjectNode visao = visao(campo, caminho);
            campos.add(visao);
            if (vazio(valor)) {
                if (campo.tipo() == TipoCampo.OBJETO && op.camposDinamicos().containsKey(caminhoDeclarado(caminho))) {
                    valor = JsonNodeFactory.instance.objectNode();
                } else {
                    if (campo.obrigatorio()) {
                        String problema = "Informe " + artigo(campo.rotulo());
                        visao.put("problema", problema);
                        pendencias.add(new Pendencia(caminho, campo.rotulo(), problema, sugestoes(campo)));
                        if (campo.tipo() == TipoCampo.OPCAO) visao.set("opcoes", opcoesJson(campo.opcoes()));
                    } else if (campo.tipo() == TipoCampo.OPCAO) {
                        visao.set("opcoes", opcoesJson(campo.opcoes()));
                    }
                    if (campo.tipo() == TipoCampo.LISTA) visao.set("itens", JsonNodeFactory.instance.arrayNode());
                    continue;
                }
            }
            try {
                JsonNode normalizado = normalizarValor(op, campo, valor, caminho, visao, pendencias, contexto, saida);
                if (normalizado != null) saida.set(campo.chave(), normalizado);
                // Nome nao resolvido fica como foi dito: a proxima revisao do cartao ainda mostra as sugestoes.
                else if (visao.has("problema") && valor != null && valor.isValueNode()) saida.set(campo.chave(), valor);
            } catch (ValorInvalido invalido) {
                if (valor != null && valor.isValueNode()) saida.set(campo.chave(), valor);
                visao.put("problema", invalido.getMessage());
                visao.put("exibicao", texto(valor));
                pendencias.add(new Pendencia(caminho, campo.rotulo(), invalido.getMessage(), List.of()));
            }
        }
    }

    private JsonNode normalizarValor(OperacaoCadastro<?> op, CampoDescritor campo, JsonNode valor, String caminho,
            ObjectNode visao, List<Pendencia> pendencias, Contexto contexto, ObjectNode irmaos) {
        if (campo.multiplo()) {
            ArrayNode lista = JsonNodeFactory.instance.arrayNode();
            ArrayNode valores = JsonNodeFactory.instance.arrayNode();
            for (JsonNode item : comoLista(valor)) {
                ObjectNode visaoItem = JsonNodeFactory.instance.objectNode();
                JsonNode normalizado = normalizarSimples(campo, item, caminho, visaoItem, pendencias, contexto);
                if (normalizado != null) lista.add(normalizado);
                else if (visaoItem.has("problema") && item.isValueNode()) lista.add(item);
                valores.add(visaoItem);
            }
            visao.set("valores", valores);
            visao.set("valor", lista);
            visao.put("exibicao", juntar(valores));
            if (lista.isEmpty() && campo.obrigatorio()) {
                String problema = "Informe " + artigo(campo.rotulo());
                visao.put("problema", problema);
                pendencias.add(new Pendencia(caminho, campo.rotulo(), problema, List.of()));
            }
            return lista;
        }
        if (campo.tipo() == TipoCampo.LISTA) {
            ArrayNode lista = JsonNodeFactory.instance.arrayNode();
            ArrayNode itens = JsonNodeFactory.instance.arrayNode();
            int indice = 0;
            for (JsonNode item : comoLista(valor)) {
                ObjectNode saidaItem = JsonNodeFactory.instance.objectNode();
                ArrayNode camposItem = JsonNodeFactory.instance.arrayNode();
                normalizar(op, campo.filhos(), item, saidaItem, camposItem, pendencias, caminho + "." + indice + ".", contexto);
                lista.add(saidaItem);
                itens.add(camposItem);
                indice++;
            }
            visao.set("itens", itens);
            visao.set("modelo", modeloItem(campo.filhos(), caminho));
            if (lista.isEmpty() && campo.obrigatorio()) {
                String problema = "Inclua ao menos um item";
                visao.put("problema", problema);
                pendencias.add(new Pendencia(caminho, campo.rotulo(), problema, List.of()));
            }
            return lista;
        }
        if (campo.tipo() == TipoCampo.OBJETO) {
            OperacaoCadastro.CamposDinamicos dinamicos = op.camposDinamicos().get(caminhoDeclarado(caminho));
            if (dinamicos == null) {
                visao.put("exibicao", valor.isObject() ? valor.size() + " informações" : texto(valor));
                return valor;
            }
            List<CampoDescritor> filhos = dinamicos.campos(irmaos);
            ObjectNode saida = JsonNodeFactory.instance.objectNode();
            ArrayNode camposObjeto = JsonNodeFactory.instance.arrayNode();
            normalizar(op, filhos, valor.isObject() ? valor : JsonNodeFactory.instance.objectNode(), saida, camposObjeto,
                    pendencias, caminho + ".", contexto);
            visao.set("grupo", camposObjeto);
            return saida;
        }
        return normalizarSimples(campo, valor, caminho, visao, pendencias, contexto);
    }

    private JsonNode normalizarSimples(CampoDescritor campo, JsonNode valor, String caminho, ObjectNode visao,
            List<Pendencia> pendencias, Contexto contexto) {
        String texto = texto(valor);
        switch (campo.tipo()) {
            case REFERENCIA -> {
                return resolverReferencia(campo, texto, caminho, visao, pendencias, contexto);
            }
            case OPCAO -> {
                visao.set("opcoes", opcoesJson(campo.opcoes()));
                Optional<Opcao> opcao = escolherOpcao(campo.opcoes(), texto);
                if (opcao.isEmpty()) {
                    String problema = "Escolha uma opção válida para " + campo.rotulo().toLowerCase(Locale.ROOT);
                    visao.put("problema", problema);
                    visao.put("exibicao", texto);
                    pendencias.add(new Pendencia(caminho, campo.rotulo(), problema, campo.opcoes()));
                    return null;
                }
                visao.put("valor", opcao.get().valor());
                visao.put("exibicao", opcao.get().rotulo());
                return JsonNodeFactory.instance.textNode(opcao.get().valor());
            }
            case INTEIRO -> {
                BigDecimal numero = numero(texto);
                if (numero.stripTrailingZeros().scale() > 0) throw new ValorInvalido("Use um número inteiro");
                visao.put("valor", numero.longValue());
                visao.put("exibicao", String.valueOf(numero.longValue()));
                return JsonNodeFactory.instance.numberNode(numero.longValue());
            }
            case DECIMAL, MOEDA -> {
                BigDecimal numero = numero(texto);
                visao.put("valor", numero);
                visao.put("exibicao", campo.tipo() == TipoCampo.MOEDA ? moeda(numero) : numero.stripTrailingZeros().toPlainString().replace('.', ','));
                return JsonNodeFactory.instance.numberNode(numero);
            }
            case BOOLEANO -> {
                boolean sim = booleano(texto);
                visao.put("valor", sim);
                visao.put("exibicao", sim ? "Sim" : "Não");
                return JsonNodeFactory.instance.booleanNode(sim);
            }
            case DATA -> {
                LocalDate data = data(texto);
                visao.put("valor", data.toString());
                visao.put("exibicao", data.format(BR));
                return JsonNodeFactory.instance.textNode(data.toString());
            }
            case DATA_HORA -> {
                OffsetDateTime momento = dataHora(texto);
                visao.put("valor", momento.toString());
                visao.put("exibicao", momento.atZoneSameInstant(FUSO).format(momento.toLocalTime().equals(LocalTime.NOON) ? BR : BR_HORA));
                return JsonNodeFactory.instance.textNode(momento.toString());
            }
            case HORA -> {
                LocalTime hora = hora(texto);
                visao.put("valor", hora.toString());
                visao.put("exibicao", hora.toString());
                return JsonNodeFactory.instance.textNode(hora.toString());
            }
            case UUID -> {
                try {
                    UUID id = UUID.fromString(texto.strip());
                    visao.put("valor", id.toString());
                    visao.put("exibicao", id.toString());
                    return JsonNodeFactory.instance.textNode(id.toString());
                } catch (IllegalArgumentException exception) {
                    throw new ValorInvalido("Identificador inválido");
                }
            }
            case CPF, CNPJ -> {
                String digitos = texto.replaceAll("\\D", "");
                int esperado = campo.tipo() == TipoCampo.CPF ? 11 : 14;
                if (digitos.length() != esperado) throw new ValorInvalido(campo.tipo() == TipoCampo.CPF ? "O CPF precisa de 11 dígitos" : "O CNPJ precisa de 14 dígitos");
                visao.put("valor", digitos);
                visao.put("exibicao", campo.tipo() == TipoCampo.CPF
                        ? digitos.replaceFirst("(\\d{3})(\\d{3})(\\d{3})(\\d{2})", "$1.$2.$3-$4")
                        : digitos.replaceFirst("(\\d{2})(\\d{3})(\\d{3})(\\d{4})(\\d{2})", "$1.$2.$3/$4-$5"));
                return JsonNodeFactory.instance.textNode(digitos);
            }
            case SENHA -> {
                contexto.secretos.add(caminho);
                boolean gerada = contexto.padroes.contains(caminho);
                if (gerada) contexto.geradas.put(campo.rotulo(), texto);
                visao.put("valor", "");
                visao.put("exibicao", gerada ? "Provisória, mostrada ao concluir" : "Definida");
                return JsonNodeFactory.instance.textNode(texto);
            }
            default -> {
                String limpo = texto.strip();
                if (campo.tamanhoMaximo() != null && limpo.length() > campo.tamanhoMaximo()) {
                    throw new ValorInvalido("Use no máximo " + campo.tamanhoMaximo() + " caracteres");
                }
                if (campo.tipo() == TipoCampo.EMAIL && !limpo.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
                    throw new ValorInvalido("E-mail inválido");
                }
                visao.put("valor", limpo);
                visao.put("exibicao", limpo);
                return JsonNodeFactory.instance.textNode(limpo);
            }
        }
    }

    private JsonNode resolverReferencia(CampoDescritor campo, String termo, String caminho, ObjectNode visao,
            List<Pendencia> pendencias, Contexto contexto) {
        Resolucao resolucao = resolver(campo.referencia(), termo, contexto);
        if (resolucao.escolhida() != null) {
            visao.put("valor", resolucao.escolhida().valor());
            visao.put("exibicao", resolucao.escolhida().rotulo());
            if (resolucao.escolhida().detalhe() != null) visao.put("detalhe", resolucao.escolhida().detalhe());
            return JsonNodeFactory.instance.textNode(resolucao.escolhida().valor());
        }
        boolean achouAlgum = !catalogo.buscarReferencia(campo.referencia(), termo, 1).isEmpty();
        String problema = resolucao.candidatos().isEmpty()
                ? "Não encontrei " + artigoIndefinido(campo.rotulo()) + " com \"" + termo + "\". Pesquise pelo nome ou código."
                : achouAlgum
                        ? "Encontrei mais de uma opção para \"" + termo + "\". Qual é?"
                        : "Não encontrei \"" + termo + "\". Seria um destes?";
        visao.put("exibicao", termo);
        visao.put("problema", problema);
        visao.set("candidatos", opcoesJson(resolucao.candidatos()));
        pendencias.add(new Pendencia(caminho, campo.rotulo(), problema, resolucao.candidatos()));
        return null;
    }

    /** Resultado da busca de um nome num cadastro: um registro escolhido ou os candidatos. */
    public record Resolucao(Opcao escolhida, List<Opcao> candidatos) {
    }

    public Resolucao resolver(String tipo, String termo, Contexto contexto) {
        String chave = tipo + "|" + termo;
        Resolucao cache = contexto.resolucoes.get(chave);
        if (cache != null) return cache;
        Resolucao resolucao = resolverSemCache(tipo, termo);
        contexto.resolucoes.put(chave, resolucao);
        return resolucao;
    }

    public Resolucao resolver(String tipo, String termo) {
        return resolverSemCache(tipo, termo);
    }

    private Resolucao resolverSemCache(String tipo, String termo) {
        String limpo = termo == null ? "" : termo.strip();
        try {
            UUID id = UUID.fromString(limpo);
            return catalogo.referenciaPorId(tipo, id).map(opcao -> new Resolucao(opcao, List.of()))
                    .orElse(new Resolucao(null, List.of()));
        } catch (IllegalArgumentException ignored) {
            // nao e um id: busca pelo nome
        }
        List<Opcao> encontrados = catalogo.buscarReferencia(tipo, limpo, MAX_CANDIDATOS + 1);
        if (encontrados.size() == 1) return new Resolucao(encontrados.getFirst(), List.of());
        String alvo = CatalogoCadastros.normalizar(limpo);
        List<Opcao> exatos = encontrados.stream()
                .filter(opcao -> CatalogoCadastros.normalizar(opcao.rotulo()).equals(alvo)
                        || (opcao.detalhe() != null && CatalogoCadastros.normalizar(opcao.detalhe()).equals(alvo)))
                .toList();
        if (exatos.size() == 1) return new Resolucao(exatos.getFirst(), List.of());
        if (!encontrados.isEmpty()) return new Resolucao(null, encontrados.stream().limit(MAX_CANDIDATOS).toList());
        // Nada com todas as palavras: sugere pelo termo mais longo.
        String maior = List.of(alvo.split("\\s+")).stream().max(java.util.Comparator.comparingInt(String::length)).orElse("");
        List<Opcao> sugestoes = maior.length() >= 3 && !maior.equals(alvo)
                ? catalogo.buscarReferencia(tipo, maior, MAX_CANDIDATOS) : List.of();
        // Erro de digitacao ("Talisson", "trasformador"): tenta pelo comeco da palavra.
        for (int tamanho = Math.min(5, maior.length() - 1); sugestoes.isEmpty() && tamanho >= 3; tamanho--) {
            sugestoes = catalogo.buscarReferencia(tipo, maior.substring(0, tamanho), MAX_CANDIDATOS);
        }
        return new Resolucao(null, sugestoes);
    }

    /** Cache de buscas dentro de um preparo (o mesmo material em varios itens do lote). */
    public static final class Contexto {
        private final Map<String, Resolucao> resolucoes = new HashMap<>();
        private final java.util.Set<String> padroes = new java.util.HashSet<>();
        private final List<String> secretos = new ArrayList<>();
        private final Map<String, String> geradas = new LinkedHashMap<>();
    }

    // ------------------------------------------------------------------------------------ execucao

    public Gravado executar(Preparo preparo, UUID usuarioId) {
        if (!preparo.pronto()) throw new FalhaCadastro("Ainda há informações pendentes.");
        OperacaoCadastro<?> op = preparo.operacao();
        Object resposta;
        try {
            resposta = transacao.execute(status -> op.executar(preparo.requisicao(), preparo.registroId(), usuarioId));
        } catch (BaseBusinessException exception) {
            throw new FalhaCadastro(exception.getMessage());
        } catch (ConstraintViolationException exception) {
            String mensagem = exception.getConstraintViolations().stream().map(ConstraintViolation::getMessage)
                    .distinct().reduce((a, b) -> a + " " + b).orElse("Dados inválidos.");
            throw new FalhaCadastro(mensagem);
        } catch (DataIntegrityViolationException exception) {
            LOGGER.info("Cadastro {} recusado pelo banco: {}", op.chave(), exception.getMostSpecificCause().getMessage());
            throw new FalhaCadastro("Já existe um cadastro com esses dados ou alguma informação conflita com outro registro.");
        } catch (AccessDeniedException exception) {
            throw new FalhaCadastro("Você não tem permissão para esta operação.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            String mensagem = exception.getMessage();
            throw new FalhaCadastro(mensagem == null || mensagem.isBlank() ? "Os dados foram recusados." : mensagem);
        } catch (RuntimeException exception) {
            if (exception.getClass().getSimpleName().endsWith("Exception") && exception.getClass().getPackageName().startsWith("com.nexo")) {
                throw new FalhaCadastro(exception.getMessage());
            }
            LOGGER.error("Falha ao gravar o cadastro {}", op.chave(), exception);
            throw new FalhaCadastro("Não foi possível gravar agora. Tente de novo em instantes.");
        }
        return gravado(op, resposta, preparo);
    }

    private Gravado gravado(OperacaoCadastro<?> op, Object resposta, Preparo preparo) {
        String id = null;
        String rotulo = null;
        if (resposta instanceof UUID uuid) {
            id = uuid.toString();
        } else if (resposta instanceof List<?> lista && !lista.isEmpty()) {
            JsonNode primeiro = mapper.valueToTree(lista.getFirst());
            id = primeiro.path("id").asText(null);
            rotulo = rotuloDaResposta(primeiro);
        } else if (resposta != null) {
            JsonNode no = mapper.valueToTree(resposta);
            id = no.path("id").asText(null);
            rotulo = rotuloDaResposta(no);
        }
        if (id == null && preparo.registroId() != null) id = preparo.registroId().toString();
        if (rotulo == null || rotulo.isBlank()) rotulo = preparo.titulo();
        Map<String, Object> parametros = new LinkedHashMap<>();
        if (op.destinoComId() && id != null) parametros.put("id", id);
        return new Gravado(id, rotulo, op.destino(), parametros, preparo.senhasGeradas());
    }

    private static String rotuloDaResposta(JsonNode no) {
        for (String chave : List.of("nomeCompleto", "nome", "titulo", "assunto", "codigo", "numero", "login", "descricao", "placa")) {
            JsonNode valor = no.get(chave);
            if (valor != null && valor.isValueNode() && !valor.asText().isBlank()) {
                return chave.equals("numero") ? "Nº " + valor.asText() : valor.asText();
            }
        }
        return null;
    }

    /** Falha de negocio ao gravar: a mensagem vai para o usuario. */
    public static final class FalhaCadastro extends RuntimeException {
        public FalhaCadastro(String mensagem) {
            super(mensagem);
        }
    }

    private static final class ValorInvalido extends RuntimeException {
        ValorInvalido(String mensagem) {
            super(mensagem);
        }
    }

    // -------------------------------------------------------------------------------------- edicao

    /** Detalhe atual do registro no formato do DTO de atualizacao (relacoes viram ids). */
    private ObjectNode formaDaRequisicao(List<CampoDescritor> campos, JsonNode atual) {
        ObjectNode saida = JsonNodeFactory.instance.objectNode();
        if (atual == null || !atual.isObject()) return saida;
        for (CampoDescritor campo : campos) {
            JsonNode valor = localizar(atual, campo);
            if (vazio(valor)) continue;
            if (campo.tipo() == TipoCampo.LISTA && valor.isArray()) {
                ArrayNode itens = JsonNodeFactory.instance.arrayNode();
                valor.forEach(item -> itens.add(formaDaRequisicao(campo.filhos(), item)));
                saida.set(campo.chave(), itens);
            } else if (campo.multiplo() && valor.isArray()) {
                ArrayNode itens = JsonNodeFactory.instance.arrayNode();
                valor.forEach(item -> itens.add(item.isObject() && item.has("id") ? item.get("id") : item));
                saida.set(campo.chave(), itens);
            } else if (valor.isObject() && campo.tipo() != TipoCampo.OBJETO) {
                for (String chave : List.of("id", "codigo", "valor", "name", "nome")) {
                    if (valor.hasNonNull(chave)) {
                        saida.set(campo.chave(), valor.get(chave));
                        break;
                    }
                }
            } else {
                saida.set(campo.chave(), valor);
            }
        }
        return saida;
    }

    private static JsonNode localizar(JsonNode atual, CampoDescritor campo) {
        String chave = campo.chave();
        if (atual.has(chave)) return atual.get(chave);
        String base = null;
        if (chave.startsWith("id") && chave.length() > 2) base = Character.toLowerCase(chave.charAt(2)) + chave.substring(3);
        else if (chave.endsWith("Id")) base = chave.substring(0, chave.length() - 2);
        if (base != null) {
            JsonNode objeto = atual.get(base);
            if (objeto != null && objeto.isObject() && objeto.has("id")) return objeto.get("id");
            if (atual.has(base + "Id")) return atual.get(base + "Id");
            if (atual.has("id" + Character.toUpperCase(base.charAt(0)) + base.substring(1))) {
                return atual.get("id" + Character.toUpperCase(base.charAt(0)) + base.substring(1));
            }
        }
        return null;
    }

    private static void sobrepor(ObjectNode base, ObjectNode informado) {
        Iterator<Map.Entry<String, JsonNode>> campos = informado.fields();
        while (campos.hasNext()) {
            Map.Entry<String, JsonNode> campo = campos.next();
            base.set(campo.getKey(), campo.getValue());
        }
    }

    private static void marcarAlterados(ArrayNode campos, ObjectNode informado, ObjectNode atual) {
        for (JsonNode campo : campos) {
            String caminho = campo.path("caminho").asText();
            if (caminho.contains(".") || !informado.has(caminho)) continue;
            JsonNode antes = atual.get(caminho);
            JsonNode depois = informado.get(caminho);
            boolean igual = antes != null && (antes.equals(depois) || (antes.isValueNode() && depois.isValueNode()
                    && CatalogoCadastros.normalizar(antes.asText()).equals(CatalogoCadastros.normalizar(depois.asText()))));
            if (!igual) ((ObjectNode) campo).put("alterado", true);
        }
    }

    // -------------------------------------------------------------------------------- utilitarios

    private static JsonNode valorInformado(JsonNode entrada, CampoDescritor campo) {
        if (entrada == null || !entrada.isObject()) return null;
        if (entrada.has(campo.chave())) return entrada.get(campo.chave());
        String alvo = campo.chave().toLowerCase(Locale.ROOT).replace("_", "");
        Iterator<String> nomes = entrada.fieldNames();
        while (nomes.hasNext()) {
            String nome = nomes.next();
            String limpo = nome.toLowerCase(Locale.ROOT).replace("_", "");
            if (limpo.equals(alvo)) return entrada.get(nome);
            // "funcao" para "idFuncao", "colaborador" para "colaboradorId"
            if (campo.tipo() == TipoCampo.REFERENCIA && (("id" + limpo).equals(alvo) || (limpo + "id").equals(alvo))) {
                return entrada.get(nome);
            }
        }
        return null;
    }

    private static String caminhoDeclarado(String caminho) {
        return caminho.replaceAll("\\.\\d+\\.", "[].");
    }

    private static boolean vazio(JsonNode valor) {
        if (valor == null || valor.isNull() || valor.isMissingNode()) return true;
        if (valor.isTextual()) return valor.asText().isBlank();
        if (valor.isArray()) return valor.isEmpty();
        return false;
    }

    private static String texto(JsonNode valor) {
        if (valor == null || valor.isNull()) return "";
        if (valor.isValueNode()) return valor.asText();
        if (valor.isObject()) {
            for (String chave : List.of("id", "valor", "nome", "rotulo")) if (valor.hasNonNull(chave)) return valor.get(chave).asText();
        }
        return valor.toString();
    }

    private static List<JsonNode> comoLista(JsonNode valor) {
        List<JsonNode> itens = new ArrayList<>();
        if (valor == null || valor.isNull()) return itens;
        if (valor.isArray()) {
            valor.forEach(itens::add);
        } else if (valor.isTextual() && valor.asText().contains(",")) {
            for (String parte : valor.asText().split(",")) {
                if (!parte.isBlank()) itens.add(JsonNodeFactory.instance.textNode(parte.strip()));
            }
        } else {
            itens.add(valor);
        }
        return itens;
    }

    private ObjectNode visao(CampoDescritor campo, String caminho) {
        ObjectNode visao = JsonNodeFactory.instance.objectNode();
        visao.put("caminho", caminho);
        visao.put("rotulo", campo.rotulo());
        visao.put("tipo", campo.tipo().name());
        visao.put("obrigatorio", campo.obrigatorio());
        if (campo.multiplo()) visao.put("multiplo", true);
        if (campo.referencia() != null) visao.put("referencia", campo.referencia());
        if (campo.descricao() != null) visao.put("descricao", campo.descricao());
        if (campo.tamanhoMaximo() != null) visao.put("tamanhoMaximo", campo.tamanhoMaximo());
        return visao;
    }

    private ArrayNode modeloItem(List<CampoDescritor> filhos, String caminho) {
        ArrayNode modelo = JsonNodeFactory.instance.arrayNode();
        for (CampoDescritor filho : filhos) {
            if (filho.oculto()) continue;
            ObjectNode visao = visao(filho, caminho + ".#." + filho.chave());
            if (filho.tipo() == TipoCampo.OPCAO) visao.set("opcoes", opcoesJson(filho.opcoes()));
            modelo.add(visao);
        }
        return modelo;
    }

    private static ArrayNode opcoesJson(List<Opcao> opcoes) {
        ArrayNode lista = JsonNodeFactory.instance.arrayNode();
        for (Opcao opcao : opcoes) {
            ObjectNode no = lista.addObject();
            no.put("valor", opcao.valor());
            no.put("rotulo", opcao.rotulo());
            if (opcao.detalhe() != null) no.put("detalhe", opcao.detalhe());
        }
        return lista;
    }

    private static List<Opcao> sugestoes(CampoDescritor campo) {
        return campo.tipo() == TipoCampo.OPCAO ? campo.opcoes() : List.of();
    }

    private static Optional<Opcao> escolherOpcao(List<Opcao> opcoes, String texto) {
        String alvo = CatalogoCadastros.normalizar(texto).replace('_', ' ');
        for (Opcao opcao : opcoes) {
            if (CatalogoCadastros.normalizar(opcao.valor()).replace('_', ' ').equals(alvo)
                    || CatalogoCadastros.normalizar(opcao.rotulo()).equals(alvo)) {
                return Optional.of(opcao);
            }
        }
        List<Opcao> parciais = opcoes.stream()
                .filter(opcao -> !alvo.isBlank() && (CatalogoCadastros.normalizar(opcao.rotulo()).contains(alvo)
                        || CatalogoCadastros.normalizar(opcao.valor()).replace('_', ' ').contains(alvo)))
                .toList();
        return parciais.size() == 1 ? Optional.of(parciais.getFirst()) : Optional.empty();
    }

    static BigDecimal numero(String texto) {
        String limpo = texto.strip().replace("R$", "").replace(" ", "");
        if (limpo.isEmpty()) throw new ValorInvalido("Informe um número");
        if (limpo.contains(",")) limpo = limpo.replace(".", "").replace(",", ".");
        try {
            return new BigDecimal(limpo);
        } catch (NumberFormatException exception) {
            throw new ValorInvalido("Número inválido");
        }
    }

    private static boolean booleano(String texto) {
        String valor = CatalogoCadastros.normalizar(texto);
        if (List.of("true", "sim", "s", "1", "yes", "verdadeiro").contains(valor)) return true;
        if (List.of("false", "nao", "n", "0", "no", "falso").contains(valor)) return false;
        throw new ValorInvalido("Responda sim ou não");
    }

    static LocalDate data(String texto) {
        String valor = CatalogoCadastros.normalizar(texto);
        LocalDate hoje = LocalDate.now(FUSO);
        switch (valor) {
            case "hoje" -> {
                return hoje;
            }
            case "amanha" -> {
                return hoje.plusDays(1);
            }
            case "ontem" -> {
                return hoje.minusDays(1);
            }
            default -> {
                // segue
            }
        }
        try {
            if (valor.matches("\\d{1,2}/\\d{1,2}/\\d{4}")) {
                String[] partes = valor.split("/");
                return LocalDate.of(Integer.parseInt(partes[2]), Integer.parseInt(partes[1]), Integer.parseInt(partes[0]));
            }
            if (valor.matches("\\d{1,2}/\\d{1,2}")) {
                String[] partes = valor.split("/");
                return LocalDate.of(hoje.getYear(), Integer.parseInt(partes[1]), Integer.parseInt(partes[0]));
            }
            return LocalDate.parse(valor.length() >= 10 ? valor.substring(0, 10) : valor);
        } catch (RuntimeException exception) {
            throw new ValorInvalido("Data inválida (use dd/mm/aaaa)");
        }
    }

    private static OffsetDateTime dataHora(String texto) {
        String valor = texto.strip();
        try {
            return OffsetDateTime.parse(valor);
        } catch (RuntimeException ignored) {
            // tenta outros formatos
        }
        try {
            return LocalDateTime.parse(valor).atZone(FUSO).toOffsetDateTime();
        } catch (RuntimeException ignored) {
            // tenta outros formatos
        }
        if (valor.matches("\\d{1,2}/\\d{1,2}/\\d{4}\\s+\\d{1,2}:\\d{2}")) {
            String[] partes = valor.split("\\s+");
            LocalDate dia = data(partes[0]);
            return dia.atTime(LocalTime.parse(partes[1].length() == 4 ? "0" + partes[1] : partes[1])).atZone(FUSO).toOffsetDateTime();
        }
        // Somente a data: meio-dia evita que o fuso mude o dia.
        return data(valor).atTime(LocalTime.NOON).atZone(FUSO).toOffsetDateTime();
    }

    private static LocalTime hora(String texto) {
        String valor = texto.strip().toLowerCase(Locale.ROOT).replace("h", ":").replaceAll(":$", ":00");
        try {
            if (valor.matches("\\d{1,2}")) return LocalTime.of(Integer.parseInt(valor), 0);
            if (valor.matches("\\d:\\d{2}")) valor = "0" + valor;
            return LocalTime.parse(valor.length() > 5 ? valor.substring(0, 5) : valor);
        } catch (RuntimeException exception) {
            throw new ValorInvalido("Hora inválida (use hh:mm)");
        }
    }

    private static String moeda(BigDecimal valor) {
        java.text.NumberFormat formato = java.text.NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"));
        return formato.format(valor).replace('\u00a0', ' ');
    }

    private static String juntar(ArrayNode valores) {
        List<String> textos = new ArrayList<>();
        valores.forEach(v -> textos.add(v.path("exibicao").asText("")));
        return String.join(", ", textos);
    }

    private static String artigo(String rotulo) {
        String minusculo = rotulo.toLowerCase(Locale.ROOT);
        return (feminino(minusculo) ? "a " : "o ") + minusculo;
    }

    private static String artigoIndefinido(String rotulo) {
        String minusculo = rotulo.toLowerCase(Locale.ROOT);
        return (feminino(minusculo) ? "uma " : "um ") + minusculo;
    }

    private static boolean feminino(String palavra) {
        String primeira = palavra.split("\\s+")[0];
        return (primeira.endsWith("a") && !List.of("dia", "tema", "sistema", "problema", "programa").contains(primeira))
                || primeira.endsWith("ção") || primeira.endsWith("são") || primeira.endsWith("dade")
                || List.of("base", "data", "unidade", "função", "observação", "descrição", "quantidade").contains(primeira);
    }

    private static String rotuloDoCaminho(ArrayNode campos, String caminho) {
        JsonNode campo = acharCampo(campos, caminho);
        return campo == null ? caminho : campo.path("rotulo").asText(caminho);
    }

    private static void marcarProblema(ArrayNode campos, String caminho, String problema) {
        JsonNode campo = acharCampo(campos, caminho);
        if (campo instanceof ObjectNode objeto && !objeto.has("problema")) objeto.put("problema", problema);
    }

    private static JsonNode acharCampo(ArrayNode campos, String caminho) {
        String alvo = caminho.replaceAll("\\[(\\d+)]", ".$1");
        for (JsonNode campo : campos) {
            String atual = campo.path("caminho").asText();
            if (atual.equals(alvo)) return campo;
            for (String filhos : List.of("grupo")) {
                if (campo.has(filhos)) {
                    JsonNode achado = acharCampo((ArrayNode) campo.get(filhos), alvo);
                    if (achado != null) return achado;
                }
            }
            if (campo.has("itens")) {
                for (JsonNode item : campo.get("itens")) {
                    JsonNode achado = acharCampo((ArrayNode) item, alvo);
                    if (achado != null) return achado;
                }
            }
        }
        return null;
    }

    private String titulo(OperacaoCadastro<?> op, ArrayNode campos) {
        List<String> partes = new ArrayList<>();
        List<String> preferidos = op.camposTitulo();
        for (String caminho : preferidos) {
            JsonNode campo = acharCampo(campos, caminho);
            if (campo != null && !campo.path("exibicao").asText("").isBlank()) partes.add(campo.path("exibicao").asText());
        }
        if (partes.isEmpty()) {
            for (JsonNode campo : campos) {
                String tipo = campo.path("tipo").asText();
                if ((tipo.equals("TEXTO") || tipo.equals("REFERENCIA")) && !campo.path("exibicao").asText("").isBlank()
                        && !campo.has("problema")) {
                    partes.add(campo.path("exibicao").asText());
                    break;
                }
            }
        }
        return partes.isEmpty() ? op.registro() : String.join(" · ", partes);
    }

    /** Senhas nunca voltam na previa nem no historico. */
    public static Supplier<Object> senhaProvisoria() {
        return () -> {
            String letras = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
            java.security.SecureRandom aleatorio = new java.security.SecureRandom();
            StringBuilder senha = new StringBuilder("Ces@");
            for (int i = 0; i < 8; i++) senha.append(letras.charAt(aleatorio.nextInt(letras.length())));
            return senha.toString();
        };
    }
}
