package com.nexo.app.modules.assistente.application.cadastro;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Algo que o Copiloto sabe cadastrar (ou editar) num modulo: o DTO de entrada do caso de uso, a permissao
 * exigida e o executor, que chama o caso de uso real do modulo — mesmas validacoes, regras, eventos,
 * automacoes e e-mails da tela. Os campos sao lidos do DTO; a operacao so ajusta rotulos, referencias e
 * valores padrao.
 *
 * <pre>{@code
 * OperacaoCadastro.criar("colaborador", ColaboradorRequest.class)
 *     .modulo("Pessoas").titulo("Novo colaborador").registro("Colaborador")
 *     .autoridade("rh:colaborador:write")
 *     .campo("idFuncao", c -> c.rotulo("Função").referencia("funcao"))
 *     .executor((request, id, usuario) -> colaboradores.criar(request))
 *     .destino("RH_COLABORADORES")
 *     .build();
 * }</pre>
 */
public final class OperacaoCadastro<R> {

    public enum Modo { CRIAR, EDITAR }

    /** Chama o caso de uso. {@code registroId} so vem na edicao; {@code usuarioId} e quem confirmou. */
    @FunctionalInterface
    public interface Executor<R> {
        Object executar(R requisicao, UUID registroId, UUID usuarioId);
    }

    /** Campos que dependem do que ja foi preenchido (ex.: os campos do formulario financeiro escolhido). */
    @FunctionalInterface
    public interface CamposDinamicos {
        List<CampoDescritor> campos(JsonNode dadosParciais);
    }

    /** Ajuste de um campo lido do DTO. */
    public static final class Campo {
        private String rotulo;
        private String descricao;
        private String referencia;
        private TipoCampo tipo;
        private boolean oculto;
        private Boolean obrigatorio;
        private Supplier<Object> padrao;
        private Function<JsonNode, Object> derivado;
        private List<Opcao> opcoes;

        public Campo rotulo(String valor) {
            this.rotulo = valor;
            return this;
        }

        public Campo descricao(String valor) {
            this.descricao = valor;
            return this;
        }

        public Campo referencia(String tipoReferencia) {
            this.referencia = tipoReferencia;
            return this;
        }

        public Campo tipo(TipoCampo valor) {
            this.tipo = valor;
            return this;
        }

        /** Preenchido pelo sistema (com {@link #padrao}); nao aparece ao modelo nem no cartao. */
        public Campo oculto() {
            this.oculto = true;
            return this;
        }

        public Campo obrigatorio(boolean valor) {
            this.obrigatorio = valor;
            return this;
        }

        public Campo padrao(Supplier<Object> valor) {
            this.padrao = valor;
            return this;
        }

        /** Valor sugerido a partir dos demais campos informados no mesmo nivel (ex.: primeiro nome do nome completo). */
        public Campo derivado(Function<JsonNode, Object> valor) {
            this.derivado = valor;
            return this;
        }

        public Function<JsonNode, Object> derivado() {
            return derivado;
        }

        public Campo opcoes(List<Opcao> valor) {
            this.opcoes = valor;
            return this;
        }

        public String rotulo() {
            return rotulo;
        }

        public String descricao() {
            return descricao;
        }

        public String referencia() {
            return referencia;
        }

        public TipoCampo tipo() {
            return tipo;
        }

        public boolean isOculto() {
            return oculto;
        }

        public Boolean obrigatorio() {
            return obrigatorio;
        }

        public Supplier<Object> padrao() {
            return padrao;
        }

        public List<Opcao> opcoes() {
            return opcoes;
        }
    }

    private final String id;
    private final Modo modo;
    private final String modulo;
    private final String titulo;
    private final String registro;
    private final String descricao;
    private final Set<String> autoridades;
    private final Class<R> requisicao;
    private final Executor<R> executor;
    private final Function<UUID, Object> carregador;
    private final String referenciaRegistro;
    private final Map<String, Campo> campos;
    private final Map<String, CamposDinamicos> camposDinamicos;
    private final String destino;
    private final boolean destinoComId;
    private final List<String> exemplos;
    private final List<String> camposTitulo;
    private final boolean permiteLote;

    private OperacaoCadastro(Builder<R> b) {
        this.id = Objects.requireNonNull(b.id);
        this.modo = b.modo;
        this.modulo = Objects.requireNonNull(b.modulo, "modulo");
        this.titulo = Objects.requireNonNull(b.titulo, "titulo");
        this.registro = b.registro == null ? b.titulo : b.registro;
        this.descricao = b.descricao == null ? b.titulo : b.descricao;
        this.autoridades = Set.copyOf(b.autoridades);
        this.requisicao = Objects.requireNonNull(b.requisicao);
        this.executor = Objects.requireNonNull(b.executor, "executor");
        this.carregador = b.carregador;
        this.referenciaRegistro = b.referenciaRegistro;
        this.campos = Map.copyOf(b.campos);
        this.camposDinamicos = Map.copyOf(b.camposDinamicos);
        this.destino = b.destino;
        this.destinoComId = b.destinoComId;
        this.exemplos = List.copyOf(b.exemplos);
        this.camposTitulo = List.copyOf(b.camposTitulo);
        this.permiteLote = b.permiteLote;
        if (!id.matches("[a-z][a-z0-9_]{1,40}")) throw new IllegalArgumentException("Id de operacao invalido: " + id);
        if (autoridades.isEmpty()) throw new IllegalArgumentException("A operacao " + id + " precisa de permissao");
        if (modo == Modo.EDITAR && (carregador == null || referenciaRegistro == null)) {
            throw new IllegalArgumentException("A edicao " + id + " precisa do carregador e da referencia do registro");
        }
    }

    public static <R> Builder<R> criar(String id, Class<R> requisicao) {
        return new Builder<>(id, Modo.CRIAR, requisicao);
    }

    /**
     * Edicao: o registro atual e carregado pelo {@code carregador} (a resposta de detalhe do modulo) e os campos
     * informados substituem os atuais antes de chamar o caso de uso de atualizacao.
     */
    public static <R> Builder<R> editar(String id, Class<R> requisicao) {
        return new Builder<>(id, Modo.EDITAR, requisicao);
    }

    /** Chave publica: "colaborador" ou "editar_colaborador". */
    public String chave() {
        return modo == Modo.CRIAR ? id : "editar_" + id;
    }

    public String id() {
        return id;
    }

    public Modo modo() {
        return modo;
    }

    public String modulo() {
        return modulo;
    }

    public String titulo() {
        return titulo;
    }

    public String registro() {
        return registro;
    }

    public String descricao() {
        return descricao;
    }

    public Set<String> autoridades() {
        return autoridades;
    }

    public Class<R> requisicao() {
        return requisicao;
    }

    public Executor<R> executor() {
        return executor;
    }

    public Function<UUID, Object> carregador() {
        return carregador;
    }

    public String referenciaRegistro() {
        return referenciaRegistro;
    }

    public Map<String, Campo> campos() {
        return campos;
    }

    public Map<String, CamposDinamicos> camposDinamicos() {
        return camposDinamicos;
    }

    public String destino() {
        return destino;
    }

    public boolean destinoComId() {
        return destinoComId;
    }

    public List<String> exemplos() {
        return exemplos;
    }

    public List<String> camposTitulo() {
        return camposTitulo;
    }

    public boolean permiteLote() {
        return permiteLote;
    }

    public boolean autorizada(Set<String> concedidas) {
        return autoridades.stream().anyMatch(concedidas::contains);
    }

    @SuppressWarnings("unchecked")
    Object executar(Object requisicaoPronta, UUID registroId, UUID usuarioId) {
        return executor.executar((R) requisicaoPronta, registroId, usuarioId);
    }

    public static final class Builder<R> {
        private final String id;
        private final Modo modo;
        private final Class<R> requisicao;
        private String modulo;
        private String titulo;
        private String registro;
        private String descricao;
        private final List<String> autoridades = new ArrayList<>();
        private Executor<R> executor;
        private Function<UUID, Object> carregador;
        private String referenciaRegistro;
        private final Map<String, Campo> campos = new LinkedHashMap<>();
        private final Map<String, CamposDinamicos> camposDinamicos = new LinkedHashMap<>();
        private String destino;
        private boolean destinoComId;
        private final List<String> exemplos = new ArrayList<>();
        private final List<String> camposTitulo = new ArrayList<>();
        private boolean permiteLote = true;

        private Builder(String id, Modo modo, Class<R> requisicao) {
            this.id = id;
            this.modo = modo;
            this.requisicao = requisicao;
        }

        public Builder<R> modulo(String valor) {
            this.modulo = valor;
            return this;
        }

        /** Titulo do cartao ("Novo colaborador", "Editar equipe"). */
        public Builder<R> titulo(String valor) {
            this.titulo = valor;
            return this;
        }

        /** Nome do registro no singular ("Colaborador"). */
        public Builder<R> registro(String valor) {
            this.registro = valor;
            return this;
        }

        /** Quando usar, para o modelo. */
        public Builder<R> descricao(String valor) {
            this.descricao = valor;
            return this;
        }

        /** Basta uma das permissoes (as mesmas do endpoint da tela). */
        public Builder<R> autoridade(String... valores) {
            this.autoridades.addAll(List.of(valores));
            return this;
        }

        public Builder<R> executor(Executor<R> valor) {
            this.executor = valor;
            return this;
        }

        /** Edicao: carrega o detalhe atual e diz em qual cadastro o registro e localizado pelo nome. */
        public Builder<R> carregador(String referencia, Function<UUID, Object> valor) {
            this.referenciaRegistro = referencia;
            this.carregador = valor;
            return this;
        }

        public Builder<R> campo(String caminho, Consumer<Campo> ajuste) {
            ajuste.accept(campos.computeIfAbsent(caminho, ignored -> new Campo()));
            return this;
        }

        public Builder<R> referencia(String caminho, String tipoReferencia, String rotulo) {
            return campo(caminho, c -> c.referencia(tipoReferencia).rotulo(rotulo));
        }

        public Builder<R> oculto(String caminho, Supplier<Object> padrao) {
            return campo(caminho, c -> c.oculto().padrao(padrao));
        }

        public Builder<R> camposDinamicos(String caminho, CamposDinamicos valor) {
            camposDinamicos.put(caminho, valor);
            return this;
        }

        /** Tela aberta depois do cadastro (chave do catalogo de navegacao). */
        public Builder<R> destino(String chave) {
            this.destino = chave;
            this.destinoComId = false;
            return this;
        }

        /** Tela de detalhe do registro criado (a chave recebe o parametro id). */
        public Builder<R> destinoDetalhe(String chave) {
            this.destino = chave;
            this.destinoComId = true;
            return this;
        }

        public Builder<R> exemplo(String... valores) {
            this.exemplos.addAll(List.of(valores));
            return this;
        }

        /** Campos que identificam o registro na lista do lote ("nomeCompleto", "codigo"). */
        public Builder<R> identificadoPor(String... caminhos) {
            this.camposTitulo.addAll(List.of(caminhos));
            return this;
        }

        public Builder<R> semLote() {
            this.permiteLote = false;
            return this;
        }

        public OperacaoCadastro<R> build() {
            return new OperacaoCadastro<>(this);
        }
    }
}
