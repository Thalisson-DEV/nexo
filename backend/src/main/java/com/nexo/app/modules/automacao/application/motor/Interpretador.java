package com.nexo.app.modules.automacao.application.motor;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.nexo.app.modules.automacao.application.motor.ArmazemExecucao.PedidoAprovacao;
import com.nexo.app.modules.automacao.application.motor.Catalogo.EspecNo;
import com.nexo.app.modules.automacao.application.motor.Catalogo.EspecParam;
import com.nexo.app.modules.automacao.application.motor.Catalogo.EspecSaida;
import com.nexo.app.modules.automacao.domain.StatusEtapa;
import com.nexo.app.modules.automacao.domain.StatusExecucao;
import com.nexo.app.modules.automacao.domain.definicao.DefinicaoAutomacao;
import com.nexo.app.modules.automacao.domain.definicao.DefinicaoAutomacao.Caso;
import com.nexo.app.modules.automacao.domain.definicao.DefinicaoAutomacao.No;
import com.nexo.app.modules.automacao.domain.definicao.DefinicaoAutomacao.PoliticaRepeticao;
import com.nexo.app.modules.automacao.domain.definicao.DefinicaoAutomacao.Ramo;
import com.nexo.app.modules.automacao.domain.definicao.DefinicaoAutomacao.Variavel;
import com.nexo.app.modules.automacao.domain.expressao.Condicoes;
import com.nexo.app.modules.automacao.domain.expressao.ErroExpressao;
import com.nexo.app.modules.automacao.domain.expressao.Expressoes;
import com.nexo.app.modules.automacao.domain.expressao.Valores;

/**
 * Interpretador durável das definições de automação.
 *
 * <p>Cada execução de nó é um {@link RegistroEtapa} gravado pelo {@link ArmazemExecucao}
 * (um por nó e iteração). A execução percorre a árvore do topo a cada vez: passos já
 * concluídos são <em>reaproveitados</em> do registro (saídas, mudanças de variável, o
 * ramo que a condição tomou, a lista que o "Para cada" percorreu). Assim a execução pode
 * parar em qualquer ponto — uma espera, uma aprovação, um intervalo de repetição, o
 * servidor reiniciando — e continuar depois com o mesmo resultado.</p>
 *
 * <p>Passos com efeito externo (e-mail, aviso, HTTP) rodam no máximo uma vez: um
 * registro encontrado {@code EXECUTANDO} na retomada significa que o servidor parou no
 * meio, e o passo falha como interrompido em vez de ser repetido.</p>
 */
public class Interpretador {

    public static final int MAX_ITENS_LOOP = 500;
    public static final int MAX_REPETICOES = 100;
    public static final int REPETICAO_IMEDIATA_SEGUNDOS = 5;
    public static final int MAX_SAIDA_BYTES = 4_000_000;
    public static final int MAX_PREVIA = 4000;

    /** Resultado de uma rodada do interpretador. */
    public record Desfecho(StatusExecucao status, OffsetDateTime retomarEm, String erro, String aguardando, String mensagem) {
    }

    /** Quem executa e com que identidade. */
    public record Identidade(UUID donoId, Set<String> permissoes, String emailTestador) {
    }

    /** Converte destinatários configurados em usuários e e-mails (aprovação). */
    public interface ResolvedorPessoas {
        Pessoas resolver(Object valor, ContextoAcao contexto);

        record Pessoas(List<UUID> usuarios, List<String> emails) {
        }
    }

    private static final class Quadro {
        final String iteracao;
        final Object item;
        final Map<String, Object> loops;

        Quadro(String iteracao, Object item, Map<String, Object> loops) {
            this.iteracao = iteracao;
            this.item = item;
            this.loops = loops;
        }

        Quadro filho(String loopId, int indice, Object novoItem, int total) {
            Map<String, Object> novos = new LinkedHashMap<>(loops);
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("item", novoItem);
            info.put("index", indice);
            info.put("count", total);
            novos.put(loopId, info);
            return new Quadro(iteracao + "/" + loopId + "[" + indice + "]", novoItem, novos);
        }
    }

    private static final class Suspensao extends RuntimeException {
        final OffsetDateTime retomarEm;
        final String motivo;

        Suspensao(OffsetDateTime retomarEm, String motivo) {
            super(motivo, null, false, false);
            this.retomarEm = retomarEm;
            this.motivo = motivo;
        }
    }

    private static final class Encerramento extends RuntimeException {
        final String status;
        final String mensagem;

        Encerramento(String status, String mensagem) {
            super(mensagem, null, false, false);
            this.status = status;
            this.mensagem = mensagem;
        }
    }

    private static final class Cancelamento extends RuntimeException {
        Cancelamento() {
            super("cancelada", null, false, false);
        }
    }

    private final DefinicaoAutomacao definicao;
    private final Catalogo catalogo;
    private final ArmazemExecucao armazem;
    private final String modo;
    private final Supplier<OffsetDateTime> relogio;
    private final Consumer<Long> dormir;
    private final Identidade identidade;
    private final ResolvedorPessoas pessoas;
    private final boolean simulacao;
    private final Map<String, RegistroEtapa> registros;
    private final Map<String, Object> gatilho;
    private final Map<String, Object> passos = new LinkedHashMap<>();
    private final Map<String, Object> variaveis = new LinkedHashMap<>();
    private final Map<String, String> tiposVariaveis = new HashMap<>();
    private final Map<String, Object> infoExecucao;
    private final Map<String, Object> infoAutomacao;
    private String primeiroErro;
    private int executados;

    public Interpretador(DefinicaoAutomacao definicao, Catalogo catalogo, ArmazemExecucao armazem,
                         Map<String, Object> saidaGatilho, Map<String, Object> infoExecucao,
                         Map<String, Object> infoAutomacao, String modo, Supplier<OffsetDateTime> relogio,
                         Consumer<Long> dormir, Identidade identidade, ResolvedorPessoas pessoas, boolean simulacao) {
        this.definicao = definicao;
        this.catalogo = catalogo;
        this.armazem = armazem;
        this.modo = modo;
        this.relogio = relogio;
        this.dormir = dormir;
        this.identidade = identidade;
        this.pessoas = pessoas;
        this.simulacao = simulacao;
        this.registros = new HashMap<>(armazem.carregar());
        this.infoExecucao = infoExecucao;
        this.infoAutomacao = infoAutomacao;
        this.gatilho = new LinkedHashMap<>();
        this.gatilho.put("outputs", saidaGatilho == null ? Map.of() : saidaGatilho);
        this.gatilho.put("type", definicao.gatilho().tipo());
        Map<String, Object> escopoInicial = new HashMap<>();
        escopoInicial.put("run", infoExecucao);
        escopoInicial.put("automation", infoAutomacao);
        escopoInicial.put("trigger", gatilho);
        escopoInicial.put("env", Map.of());
        for (Variavel v : definicao.variaveis()) {
            Object valor;
            try {
                valor = Expressoes.renderizar(v.valor(), escopoInicial);
            } catch (ErroExpressao e) {
                valor = null;
            }
            variaveis.put(v.nome(), coagir(v.tipo(), valor));
            tiposVariaveis.put(v.nome(), v.tipo());
        }
    }

    public int executados() {
        return executados;
    }

    // ------------------------------------------------------------------------------ público

    public Desfecho executar() {
        StatusEtapa resultado;
        try {
            resultado = executarLista(definicao.passos(), new Quadro("", null, Map.of()));
        } catch (Suspensao s) {
            return new Desfecho(StatusExecucao.AGUARDANDO, s.retomarEm, null, s.motivo, null);
        } catch (Encerramento e) {
            StatusExecucao status = switch (e.status) {
                case "succeeded" -> StatusExecucao.SUCESSO;
                case "cancelled" -> StatusExecucao.CANCELADA;
                default -> StatusExecucao.FALHA;
            };
            return new Desfecho(status, null, status == StatusExecucao.FALHA ? e.mensagem : null, null,
                    e.mensagem == null || e.mensagem.isBlank() ? null : e.mensagem);
        } catch (Cancelamento c) {
            return new Desfecho(StatusExecucao.CANCELADA, null, null, null, "Execução cancelada");
        }
        if (resultado == StatusEtapa.FALHA || resultado == StatusEtapa.TEMPO_ESGOTADO) {
            return new Desfecho(StatusExecucao.FALHA, null, primeiroErro == null ? "Um passo falhou" : primeiroErro, null, null);
        }
        return new Desfecho(StatusExecucao.SUCESSO, null, null, null, null);
    }

    // ------------------------------------------------------------------------------ escopo

    private Map<String, Object> escopo(Quadro quadro, Map<String, Object> extra) {
        Map<String, Object> valores = new HashMap<>();
        valores.put("trigger", gatilho);
        valores.put("steps", passos);
        valores.put("vars", variaveis);
        valores.put("item", quadro.item);
        valores.put("loop", quadro.loops);
        valores.put("run", infoExecucao);
        valores.put("automation", infoAutomacao);
        valores.put("env", Map.of());
        if (extra != null) valores.putAll(extra);
        return valores;
    }

    // ------------------------------------------------------------------------------ listas

    private StatusEtapa executarLista(List<No> nos, Quadro quadro) {
        StatusEtapa anterior = StatusEtapa.SUCESSO;
        StatusEtapa resultado = StatusEtapa.SUCESSO;
        for (No no : nos) {
            StatusEtapa status = executarNo(no, quadro, anterior);
            anterior = status;
            if (status == StatusEtapa.FALHA || status == StatusEtapa.TEMPO_ESGOTADO || status == StatusEtapa.CANCELADA) {
                resultado = status == StatusEtapa.CANCELADA ? StatusEtapa.FALHA : status;
            } else if (status == StatusEtapa.SUCESSO) {
                resultado = StatusEtapa.SUCESSO;
            }
        }
        return resultado;
    }

    private void publicar(No no, RegistroEtapa registro) {
        Map<String, Object> passo = new LinkedHashMap<>();
        passo.put("status", registro.status.situacao());
        passo.put("outputs", registro.saidas == null ? Map.of() : registro.saidas);
        passo.put("error", registro.erro);
        passos.put(no.id(), passo);
        EspecNo spec = catalogo.buscar(no.tipo());
        if (spec != null && "variables".equals(spec.grupo()) && registro.status == StatusEtapa.SUCESSO
                && registro.saidas != null && registro.entradas != null && registro.entradas.containsKey("name")) {
            variaveis.put(Valores.texto(registro.entradas.get("name")), registro.saidas.get("value"));
        }
    }

    private void salvar(RegistroEtapa registro) {
        registros.put(registro.chave(), registro);
        armazem.salvar(registro.copia());
    }

    private StatusEtapa falhar(No no, RegistroEtapa registro, String mensagem, StatusEtapa status) {
        registro.status = status;
        registro.erro = cortar(mensagem, 2000);
        registro.fim = relogio.get();
        registro.proximaEm = null;
        salvar(registro);
        publicar(no, registro);
        if (primeiroErro == null) primeiroErro = cortar(no.nomeExibicao() + ": " + mensagem, 1000);
        return status;
    }

    private StatusEtapa falhar(No no, RegistroEtapa registro, String mensagem) {
        return falhar(no, registro, mensagem, StatusEtapa.FALHA);
    }

    @SuppressWarnings("unchecked")
    private StatusEtapa concluir(No no, RegistroEtapa registro, Map<String, Object> saidas) {
        Map<String, Object> seguras = (Map<String, Object>) Valores.comoJson(saidas == null ? Map.of() : saidas);
        if (Valores.paraJson(seguras).length() > MAX_SAIDA_BYTES) {
            return falhar(no, registro, "A saída deste passo é grande demais (limite 4 MB)");
        }
        registro.status = StatusEtapa.SUCESSO;
        registro.saidas = seguras;
        registro.erro = null;
        registro.fim = relogio.get();
        registro.proximaEm = null;
        salvar(registro);
        publicar(no, registro);
        return StatusEtapa.SUCESSO;
    }

    private RegistroEtapa novo(No no, Quadro quadro, StatusEtapa status) {
        return new RegistroEtapa(no.id(), quadro.iteracao, no.tipo(), status, relogio.get());
    }

    // ------------------------------------------------------------------------------ nós

    private StatusEtapa executarNo(No no, Quadro quadro, StatusEtapa anterior) {
        RegistroEtapa registro = registros.get(no.id() + "|" + quadro.iteracao);
        EspecNo spec = catalogo.buscar(no.tipo());
        if (spec == null) {
            RegistroEtapa r = registro != null ? registro : novo(no, quadro, StatusEtapa.EXECUTANDO);
            return falhar(no, r, "Tipo de passo desconhecido: " + no.tipo());
        }
        if (!no.executarApos().contains(anterior.situacao())) {
            if (registro == null || registro.status != StatusEtapa.IGNORADA) {
                registro = novo(no, quadro, StatusEtapa.IGNORADA);
                registro.fim = relogio.get();
                salvar(registro);
            }
            publicar(no, registro);
            return StatusEtapa.IGNORADA;
        }
        if (spec.conteiner() != null) return executarConteiner(no, spec, quadro, registro);
        if (registro != null && registro.status.isFinal()) {
            publicar(no, registro);
            if (no.tipo().equals("control.terminate") && registro.status == StatusEtapa.SUCESSO) {
                Map<String, Object> entradas = registro.entradas == null ? Map.of() : registro.entradas;
                throw new Encerramento(Valores.texto(entradas.getOrDefault("status", "succeeded")), Valores.texto(entradas.get("message")));
            }
            return registro.status;
        }
        if (armazem.cancelada()) throw new Cancelamento();
        armazem.sinalVital();
        return switch (no.tipo()) {
            case "control.wait" -> esperar(no, spec, quadro, registro);
            case "approval.request" -> aprovacao(no, spec, quadro, registro);
            case "control.terminate" -> encerrar(no, spec, quadro, registro);
            default -> acao(no, spec, quadro, registro);
        };
    }

    private Map<String, Object> resolverParams(No no, EspecNo spec, Quadro quadro) {
        Map<String, Object> resolvidos = new LinkedHashMap<>();
        Map<String, Object> escopo = escopo(quadro, null);
        for (EspecParam param : spec.params()) {
            Object bruto = no.params().containsKey(param.chave()) ? no.params().get(param.chave()) : param.padrao();
            try {
                resolvidos.put(param.chave(), param.resolver() ? Expressoes.renderizar(bruto, escopo) : bruto);
            } catch (ErroExpressao e) {
                throw new ErroExpressao(param.rotulo() + ": " + e.getMessage());
            }
        }
        return resolvidos;
    }

    private ContextoAcao contexto(No no, Quadro quadro, int tentativa) {
        Map<String, Object> escopo = escopo(quadro, null);
        return new ContextoAcao(
                (UUID) infoAutomacao.get("uuid"), Valores.texto(infoAutomacao.get("name")),
                (UUID) infoExecucao.get("uuid"), modo, no.id(), quadro.iteracao, tentativa, relogio.get(), escopo,
                (valor, extra) -> Expressoes.renderizar(valor, escopo(quadro, extra)), no.limiteSegundos(),
                identidade.donoId(), identidade.permissoes(), identidade.emailTestador(), simulacao);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> previa(Map<String, Object> entradas) {
        return (Map<String, Object>) Valores.comoJson(compactar(entradas, MAX_PREVIA));
    }

    private StatusEtapa acao(No no, EspecNo spec, Quadro quadro, RegistroEtapa registro) {
        PoliticaRepeticao repeticao = no.repeticao() != null ? no.repeticao() : spec.repeticaoPadrao();
        if (registro != null && registro.status == StatusEtapa.EXECUTANDO) {
            if (spec.efeitoExterno()) {
                return falhar(no, registro, "Interrompido no meio do envio. Para não duplicar, o passo não foi repetido; "
                        + "confira e reenvie a execução se precisar.");
            }
            registro.erro = "Interrompido; repetido automaticamente";
        }
        if (registro != null && registro.status == StatusEtapa.AGUARDANDO && registro.proximaEm != null
                && relogio.get().isBefore(registro.proximaEm)) {
            throw new Suspensao(registro.proximaEm, "repeticao");
        }
        int tentativa = registro == null ? 0 : registro.tentativa;
        if (registro == null) registro = novo(no, quadro, StatusEtapa.EXECUTANDO);
        while (true) {
            tentativa++;
            registro.tentativa = tentativa;
            registro.status = StatusEtapa.EXECUTANDO;
            registro.proximaEm = null;
            Map<String, Object> entradas;
            try {
                entradas = resolverParams(no, spec, quadro);
            } catch (ErroExpressao e) {
                return falhar(no, registro, e.getMessage());
            }
            registro.entradas = previa(entradas);
            if (spec.efeitoExterno()) salvar(registro);
            if (spec.executor() == null) return falhar(no, registro, "O passo " + spec.rotulo() + " não pode ser executado");
            executados++;
            if (simulacao && (spec.efeitoExterno() || spec.ia())) return concluir(no, registro, simulado(spec));
            String mensagem;
            boolean transitoria;
            try {
                Map<String, Object> saidas = new LinkedHashMap<>(spec.executor().executar(contexto(no, quadro, tentativa), entradas));
                if (spec.ia()) saidas.put("gerado_por_ia", true);
                if ("variables".equals(spec.grupo())) {
                    String nome = Valores.texto(entradas.get("name"));
                    saidas.put("value", coagir(tiposVariaveis.getOrDefault(nome, "string"), saidas.get("value")));
                }
                return concluir(no, registro, saidas);
            } catch (ErroAcao e) {
                mensagem = e.getMessage();
                transitoria = e.isTransitoria();
            } catch (ErroExpressao e) {
                mensagem = e.getMessage();
                transitoria = false;
            } catch (RuntimeException e) {
                mensagem = cortar("Falha inesperada (" + e.getClass().getSimpleName() + "): " + e.getMessage(), 500);
                transitoria = !spec.efeitoExterno();
            }
            int feitas = tentativa - 1;
            if (transitoria && feitas < repeticao.quantidade()) {
                long atraso = repeticao.atraso(tentativa);
                registro.erro = cortar(mensagem, 2000);
                if (atraso <= REPETICAO_IMEDIATA_SEGUNDOS) {
                    salvar(registro);
                    dormir.accept(atraso * 1000);
                    continue;
                }
                registro.status = StatusEtapa.AGUARDANDO;
                registro.proximaEm = relogio.get().plusSeconds(atraso);
                salvar(registro);
                throw new Suspensao(registro.proximaEm, "repeticao");
            }
            return falhar(no, registro, mensagem);
        }
    }

    private StatusEtapa esperar(No no, EspecNo spec, Quadro quadro, RegistroEtapa registro) {
        if (registro != null && registro.status == StatusEtapa.AGUARDANDO) {
            if (registro.proximaEm == null || !relogio.get().isBefore(registro.proximaEm)) {
                Map<String, Object> saidas = new LinkedHashMap<>(registro.saidas == null ? Map.of() : registro.saidas);
                saidas.put("waited", true);
                return concluir(no, registro, saidas);
            }
            throw new Suspensao(registro.proximaEm, "espera");
        }
        registro = novo(no, quadro, StatusEtapa.EXECUTANDO);
        registro.tentativa = 1;
        OffsetDateTime ate;
        Map<String, Object> entradas;
        try {
            entradas = resolverParams(no, spec, quadro);
            ate = Espera.ate(entradas, relogio.get());
        } catch (ErroExpressao | IllegalArgumentException e) {
            return falhar(no, registro, e.getMessage());
        }
        registro.entradas = previa(entradas);
        if ("TESTE".equals(modo) || simulacao) {
            return concluir(no, registro, Map.of("resume_at", ate.toString(), "waited", false, "pulada_no_teste", true));
        }
        registro.status = StatusEtapa.AGUARDANDO;
        registro.saidas = new LinkedHashMap<>(Map.of("resume_at", ate.toString()));
        registro.proximaEm = ate;
        salvar(registro);
        throw new Suspensao(ate, "espera");
    }

    private StatusEtapa aprovacao(No no, EspecNo spec, Quadro quadro, RegistroEtapa registro) {
        if (registro != null && registro.status == StatusEtapa.AGUARDANDO) {
            String id = Valores.texto(registro.saidas == null ? null : registro.saidas.get("approval_id"));
            Map<String, Object> resultado = id.isEmpty() ? null : armazem.resultadoAprovacao(UUID.fromString(id));
            if (resultado != null) {
                Map<String, Object> saidas = new LinkedHashMap<>(resultado);
                saidas.put("approval_id", id);
                return concluir(no, registro, saidas);
            }
            if (registro.proximaEm != null && !relogio.get().isBefore(registro.proximaEm)) {
                if (!id.isEmpty()) armazem.encerrarAprovacao(UUID.fromString(id), "EXPIRADA");
                registro.saidas = new LinkedHashMap<>(Map.of("approval_id", id, "outcome", "Expirada", "approved", false));
                return falhar(no, registro, "Ninguém respondeu à aprovação no prazo", StatusEtapa.TEMPO_ESGOTADO);
            }
            throw new Suspensao(registro.proximaEm, "aprovacao");
        }
        registro = novo(no, quadro, StatusEtapa.EXECUTANDO);
        registro.tentativa = 1;
        Map<String, Object> entradas;
        try {
            entradas = resolverParams(no, spec, quadro);
        } catch (ErroExpressao e) {
            return falhar(no, registro, e.getMessage());
        }
        registro.entradas = previa(entradas);
        List<String> opcoes = new ArrayList<>();
        for (Object o : Valores.lista(entradas.get("options"))) if (!Valores.texto(o).isBlank()) opcoes.add(Valores.texto(o).strip());
        if (!"custom".equals(Valores.texto(entradas.getOrDefault("kind", "approve_reject"))) || opcoes.size() < 2) {
            opcoes = List.of("Aprovar", "Recusar");
        }
        if ("TESTE".equals(modo) || simulacao) {
            Map<String, Object> saidas = new LinkedHashMap<>();
            saidas.put("outcome", opcoes.get(0));
            saidas.put("approved", true);
            saidas.put("responder", identidade.emailTestador() == null ? "teste" : identidade.emailTestador());
            saidas.put("comment", "Aprovado automaticamente no teste");
            saidas.put("decided_at", relogio.get().toString());
            return concluir(no, registro, saidas);
        }
        ResolvedorPessoas.Pessoas aprovadores;
        try {
            aprovadores = pessoas.resolver(entradas.get("approvers"), contexto(no, quadro, 1));
        } catch (ErroAcao e) {
            return falhar(no, registro, e.getMessage());
        }
        if (aprovadores.usuarios().isEmpty()) {
            return falhar(no, registro, "Nenhum aprovador com usuário ativo no Nexo foi encontrado");
        }
        double horas = Valores.dbl(entradas.get("expires_after_hours"));
        OffsetDateTime expira = horas > 0 ? relogio.get().plusMinutes(Math.round(horas * 60)) : null;
        registro.status = StatusEtapa.AGUARDANDO;
        registro.proximaEm = expira;
        salvar(registro);
        String titulo = Valores.texto(entradas.get("title")).isBlank() ? no.nomeExibicao() : Valores.texto(entradas.get("title"));
        UUID aprovacaoId = armazem.abrirAprovacao(registro, new PedidoAprovacao(no.id(), quadro.iteracao,
                aprovadores.usuarios(), aprovadores.emails(), titulo, Valores.texto(entradas.get("details")), opcoes, expira));
        registro.saidas = new LinkedHashMap<>(Map.of("approval_id", aprovacaoId.toString()));
        salvar(registro);
        throw new Suspensao(expira, "aprovacao");
    }

    private StatusEtapa encerrar(No no, EspecNo spec, Quadro quadro, RegistroEtapa registro) {
        if (registro == null) registro = novo(no, quadro, StatusEtapa.EXECUTANDO);
        registro.tentativa = 1;
        Map<String, Object> entradas;
        try {
            entradas = resolverParams(no, spec, quadro);
        } catch (ErroExpressao e) {
            return falhar(no, registro, e.getMessage());
        }
        registro.entradas = previa(entradas);
        concluir(no, registro, Map.of());
        throw new Encerramento(Valores.texto(entradas.getOrDefault("status", "succeeded")), Valores.texto(entradas.get("message")));
    }

    // ------------------------------------------------------------------------------ contêineres

    private interface Decisao {
        Map<String, Object> decidir();
    }

    private record Decidido(RegistroEtapa registro, Map<String, Object> decisao) {
    }

    /** Avalia a decisão de um contêiner uma vez e a guarda para as retomadas. */
    @SuppressWarnings("unchecked")
    private Object decidir(No no, Quadro quadro, RegistroEtapa registro, Decisao decisao) {
        if (registro != null && registro.saidas != null && registro.saidas.get("decision") instanceof Map<?, ?> d) {
            return new Decidido(registro, (Map<String, Object>) d);
        }
        registro = novo(no, quadro, StatusEtapa.EXECUTANDO);
        registro.tentativa = 1;
        Map<String, Object> decidido;
        try {
            decidido = decisao.decidir();
        } catch (ErroExpressao | IllegalArgumentException | ErroAcao e) {
            return falhar(no, registro, e.getMessage());
        }
        registro.saidas = new LinkedHashMap<>();
        registro.saidas.put("decision", Valores.comoJson(decidido));
        salvar(registro);
        return new Decidido(registro, (Map<String, Object>) registro.saidas.get("decision"));
    }

    private StatusEtapa finalizarConteiner(No no, RegistroEtapa registro, StatusEtapa resultado, Map<String, Object> saidas) {
        StatusEtapa status = resultado == StatusEtapa.SUCESSO || resultado == StatusEtapa.IGNORADA ? StatusEtapa.SUCESSO : resultado;
        Object resultadoAnterior = registro.saidas == null ? null : registro.saidas.get("result");
        Object novoResultado = Valores.comoJson(saidas);
        boolean mudou = registro.status != status || !java.util.Objects.equals(resultadoAnterior, novoResultado);
        registro.status = status;
        Map<String, Object> todas = new LinkedHashMap<>(registro.saidas == null ? Map.of() : registro.saidas);
        todas.put("result", novoResultado);
        registro.saidas = todas;
        if (registro.fim == null || mudou) registro.fim = relogio.get();
        if (status != StatusEtapa.SUCESSO && registro.erro == null) registro.erro = "Um passo dentro deste bloco falhou";
        if (mudou) salvar(registro);
        Map<String, Object> passo = new LinkedHashMap<>();
        passo.put("status", status.situacao());
        passo.put("outputs", saidas);
        passo.put("error", registro.erro);
        passos.put(no.id(), passo);
        return status;
    }

    private StatusEtapa executarConteiner(No no, EspecNo spec, Quadro quadro, RegistroEtapa registro) {
        if (registro == null && armazem.cancelada()) throw new Cancelamento();
        Map<String, Object> escopo = escopo(quadro, null);
        switch (spec.conteiner()) {
            case "condition" -> {
                Object obtido = decidir(no, quadro, registro, () -> Map.of("result", Condicoes.avaliar(no.params().get("condition"), escopo)));
                if (obtido instanceof StatusEtapa s) return s;
                Decidido d = (Decidido) obtido;
                boolean resultado = Valores.verdadeiro(d.decisao().get("result"));
                StatusEtapa saida = executarLista(resultado ? lista(no.entao()) : lista(no.senao()), quadro);
                return finalizarConteiner(no, d.registro(), saida, Map.of("result", resultado));
            }
            case "switch" -> {
                Object obtido = decidir(no, quadro, registro, () -> {
                    String valor = Valores.texto(Expressoes.renderizar(no.params().getOrDefault("on", ""), escopo)).strip().toLowerCase();
                    Map<String, Object> escolha = new LinkedHashMap<>();
                    escolha.put("value", valor);
                    escolha.put("case", null);
                    for (Caso caso : lista(no.casos())) {
                        for (String opcao : Valores.texto(Expressoes.renderizar(caso.valor(), escopo)).split(";")) {
                            if (!opcao.isBlank() && opcao.strip().equalsIgnoreCase(valor)) {
                                escolha.put("case", caso.id());
                                return escolha;
                            }
                        }
                    }
                    return escolha;
                });
                if (obtido instanceof StatusEtapa s) return s;
                Decidido d = (Decidido) obtido;
                String casoId = d.decisao().get("case") == null ? null : Valores.texto(d.decisao().get("case"));
                Caso caso = lista(no.casos()).stream().filter(c -> c.id().equals(casoId)).findFirst().orElse(null);
                StatusEtapa saida = executarLista(caso != null ? caso.passos() : lista(no.padrao()), quadro);
                Map<String, Object> resultado = new LinkedHashMap<>();
                resultado.put("case", casoId);
                resultado.put("value", d.decisao().get("value"));
                return finalizarConteiner(no, d.registro(), saida, resultado);
            }
            case "loop" -> {
                Object obtido = decidir(no, quadro, registro, () -> {
                    Object valor = Expressoes.renderizar(no.params().getOrDefault("items", ""), escopo);
                    List<Object> itens;
                    if (valor == null || "".equals(valor)) itens = List.of();
                    else if (valor instanceof List<?>) itens = Valores.lista(valor);
                    else if (valor instanceof Map<?, ?>) itens = List.of(valor);
                    else throw new IllegalArgumentException("'Para cada' precisa de uma lista (ex.: {{ steps.x.outputs.items }})");
                    if (itens.size() > MAX_ITENS_LOOP) {
                        throw new IllegalArgumentException("'Para cada' aceita até " + MAX_ITENS_LOOP + " itens; recebeu " + itens.size());
                    }
                    return Map.of("items", itens);
                });
                if (obtido instanceof StatusEtapa s) return s;
                Decidido d = (Decidido) obtido;
                List<Object> itens = Valores.lista(d.decisao().get("items"));
                StatusEtapa resultado = StatusEtapa.SUCESSO;
                int falhas = 0;
                for (int i = 0; i < itens.size(); i++) {
                    StatusEtapa s = executarLista(lista(no.passos()), quadro.filho(no.id(), i, itens.get(i), itens.size()));
                    if (s == StatusEtapa.FALHA || s == StatusEtapa.TEMPO_ESGOTADO) {
                        resultado = StatusEtapa.FALHA;
                        falhas++;
                    }
                }
                return finalizarConteiner(no, d.registro(), resultado, Map.of("count", itens.size(), "failed", falhas));
            }
            case "until" -> {
                Object obtido = decidir(no, quadro, registro, () -> Map.of("started", true));
                if (obtido instanceof StatusEtapa s) return s;
                Decidido d = (Decidido) obtido;
                int limite = (int) Math.min(MAX_REPETICOES, Math.max(1, Valores.dbl(no.params().getOrDefault("max_iterations", 20))));
                StatusEtapa resultado = StatusEtapa.SUCESSO;
                int vezes = 0;
                boolean atingiu = false;
                for (int i = 0; i < limite; i++) {
                    Quadro filho = quadro.filho(no.id(), i, quadro.item, limite);
                    resultado = executarLista(lista(no.passos()), filho);
                    vezes = i + 1;
                    if (resultado == StatusEtapa.FALHA || resultado == StatusEtapa.TEMPO_ESGOTADO) break;
                    try {
                        if (Condicoes.avaliar(no.params().get("condition"), escopo(filho, null))) {
                            atingiu = true;
                            break;
                        }
                    } catch (ErroExpressao e) {
                        d.registro().erro = e.getMessage();
                        resultado = StatusEtapa.FALHA;
                        break;
                    }
                }
                return finalizarConteiner(no, d.registro(), resultado, Map.of("iterations", vezes, "condition_met", atingiu));
            }
            case "scope" -> {
                Object obtido = decidir(no, quadro, registro, () -> Map.of("started", true));
                if (obtido instanceof StatusEtapa s) return s;
                Decidido d = (Decidido) obtido;
                return finalizarConteiner(no, d.registro(), executarLista(lista(no.passos()), quadro), Map.of());
            }
            case "parallel" -> {
                Object obtido = decidir(no, quadro, registro, () -> Map.of("started", true));
                if (obtido instanceof StatusEtapa s) return s;
                Decidido d = (Decidido) obtido;
                StatusEtapa resultado = StatusEtapa.SUCESSO;
                List<Suspensao> pausas = new ArrayList<>();
                for (Ramo ramo : lista(no.ramos())) {
                    try {
                        StatusEtapa s = executarLista(ramo.passos(), quadro);
                        if (s == StatusEtapa.FALHA || s == StatusEtapa.TEMPO_ESGOTADO) resultado = StatusEtapa.FALHA;
                    } catch (Suspensao pausa) {
                        pausas.add(pausa);
                    }
                }
                if (!pausas.isEmpty()) {
                    OffsetDateTime menor = null;
                    boolean todas = true;
                    for (Suspensao p : pausas) {
                        if (p.retomarEm == null) todas = false;
                        else if (menor == null || p.retomarEm.isBefore(menor)) menor = p.retomarEm;
                    }
                    throw new Suspensao(todas ? menor : null, pausas.get(0).motivo);
                }
                return finalizarConteiner(no, d.registro(), resultado, Map.of());
            }
            default -> {
                RegistroEtapa r = registro != null ? registro : novo(no, quadro, StatusEtapa.EXECUTANDO);
                return falhar(no, r, "Bloco desconhecido: " + spec.conteiner());
            }
        }
    }

    // ------------------------------------------------------------------------------ apoio

    private static <T> List<T> lista(List<T> valor) {
        return valor == null ? List.of() : valor;
    }

    private static Map<String, Object> simulado(EspecNo spec) {
        Map<String, Object> valores = new LinkedHashMap<>();
        for (EspecSaida saida : spec.saidas()) {
            valores.put(saida.chave(), switch (saida.tipo()) {
                case "string" -> "[" + saida.rotulo() + ": preenchido na execução]";
                case "number" -> 0;
                case "boolean" -> true;
                case "array" -> List.of();
                default -> Map.of();
            });
        }
        valores.put("simulado", true);
        return valores;
    }

    static Object compactar(Object valor, int limite) {
        if (valor instanceof String s) return s.length() <= limite ? s : s.substring(0, limite) + "…";
        if (valor instanceof List<?> l) {
            List<Object> cabeca = new ArrayList<>();
            for (int i = 0; i < Math.min(20, l.size()); i++) cabeca.add(compactar(l.get(i), limite / 4));
            if (l.size() > 20) cabeca.add("… +" + (l.size() - 20) + " itens");
            return cabeca;
        }
        if (valor instanceof Map<?, ?> m) {
            Map<String, Object> r = new LinkedHashMap<>();
            int i = 0;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (i++ >= 60) break;
                r.put(String.valueOf(e.getKey()), compactar(e.getValue(), limite / 2));
            }
            return r;
        }
        return valor;
    }

    private static String cortar(String texto, int limite) {
        if (texto == null) return null;
        return texto.length() <= limite ? texto : texto.substring(0, limite);
    }

    public static Object coagir(String tipo, Object valor) {
        switch (tipo) {
            case "number": {
                Number n = Valores.numero(valor);
                return n == null ? 0L : n;
            }
            case "boolean":
                return valor instanceof Boolean b ? b : List.of("true", "sim", "1").contains(Valores.texto(valor).strip().toLowerCase());
            case "array": {
                if (valor instanceof List<?>) return valor;
                if (valor instanceof String s && s.strip().startsWith("[")) {
                    try {
                        Object lido = Valores.json().readValue(s, Object.class);
                        return lido instanceof List<?> ? lido : new ArrayList<>(List.of(lido));
                    } catch (Exception e) {
                        return new ArrayList<>(List.of(s));
                    }
                }
                return valor == null || "".equals(valor) ? new ArrayList<>() : new ArrayList<>(List.of(valor));
            }
            case "object": {
                if (valor instanceof Map<?, ?>) return valor;
                if (valor instanceof String s && s.strip().startsWith("{")) {
                    try {
                        Object lido = Valores.json().readValue(s, Object.class);
                        return lido instanceof Map<?, ?> ? lido : new LinkedHashMap<>();
                    } catch (Exception e) {
                        return new LinkedHashMap<>();
                    }
                }
                return new LinkedHashMap<>();
            }
            default:
                return valor == null ? "" : valor instanceof String ? valor : Valores.texto(valor);
        }
    }

    /** Uso para repetir: limita uma duração em segundos. */
    static Duration segundos(long s) {
        return Duration.ofSeconds(s);
    }
}
