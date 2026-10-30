package com.nexo.app.modules.notificacao.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.util.StringUtils;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import jakarta.persistence.Transient;
import lombok.NoArgsConstructor;

/**
 * A parte configurável do motor: o que fazer quando um gatilho acontece.
 *
 * <p>Tudo aqui é moldável por um administrador — condições, limiar, cooldown,
 * destinatários e textos. O que não é moldável é o gatilho em si, que vem do
 * catálogo fechado {@link GatilhoNotificacao}. Essa fronteira é o desenho central do
 * módulo: quem configura ganha liberdade sobre <em>a quem, quando e como avisar</em>,
 * sem nunca ganhar a capacidade de fazer o servidor executar algo que ele escreveu.</p>
 *
 * <p>Os textos são validados contra as variáveis que o gatilho declara. Um
 * {@code {{placeholder}}} inexistente é recusado ao salvar, e não descoberto depois
 * pelo usuário final lendo um texto quebrado.</p>
 */
@Entity
@Table(name = "regras_notificacao")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegraNotificacao extends BaseEntity {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*}}");

    @Column(name = "chave_gatilho", nullable = false, updatable = false, length = 80)
    private String chaveGatilho;

    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    @Column(name = "descricao", length = 500)
    private String descricao;

    /**
     * Regra de fábrica: pode ser editada e desligada, mas não removida. Sem isso, um
     * administrador apaga a notificação de aprovação pendente e ninguém percebe até a
     * fila estourar.
     */
    @Column(name = "sistema", nullable = false, updatable = false)
    private boolean sistema = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "categoria", nullable = false, length = 30)
    private CategoriaNotificacao categoria;

    @Enumerated(EnumType.STRING)
    @Column(name = "prioridade", nullable = false, length = 20)
    private PrioridadeNotificacao prioridade = PrioridadeNotificacao.NORMAL;

    @Column(name = "titulo_template", nullable = false, length = 150)
    private String tituloTemplate;

    @Column(name = "corpo_template", nullable = false, length = 1000)
    private String corpoTemplate;

    @Column(name = "deeplink_template", length = 500)
    private String deeplinkTemplate;

    /** Só para gatilhos por limiar: a partir de quantos a regra dispara. */
    @Column(name = "limiar")
    private Integer limiar;

    /**
     * Silêncio mínimo entre dois disparos do mesmo escopo. É o que impede a mesma
     * fila pendente de gerar uma notificação a cada varredura.
     */
    @Column(name = "cooldown_minutos", nullable = false)
    private int cooldownMinutos = 240;

    /**
     * Se o aviso desta regra também sai por e-mail.
     *
     * <p>Fica na regra, e não no código, porque a resposta muda por assunto e por
     * empresa: um desconto aguardando validação merece tirar alguém do trabalho; a
     * décima mensagem de um chamado em andamento, não. Quem sabe distinguir isso é o
     * administrador que já configura texto, destinatário e silêncio nesta mesma tela.</p>
     *
     * <p>Nasce ligado, na mesma lógica do sininho: a regra que o administrador acabou
     * de criar alcança as pessoas, e desligar o canal é um gesto deliberado. Continua
     * valendo o que o domínio decide — categoria que não aceita e-mail
     * ({@code MENCAO}) ignora este campo, e cada usuário pode se retirar do canal.</p>
     */
    @Column(name = "enviar_email", nullable = false)
    private boolean enviarEmail = true;

    /**
     * Automação que substituiu esta regra. Preenchida uma única vez, na conversão para o
     * motor de automações; a regra fica desligada e só é mantida como histórico.
     */
    @Column(name = "id_automacao")
    private UUID idAutomacao;

    @JsonManagedReference("regra-condicoes")
    @OneToMany(mappedBy = "regra", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CondicaoRegra> condicoes = new ArrayList<>();

    @JsonManagedReference("regra-destinatarios")
    @OneToMany(mappedBy = "regra", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DestinatarioRegra> destinatarios = new ArrayList<>();

    public RegraNotificacao(GatilhoNotificacao gatilho, String nome, String descricao,
                            CategoriaNotificacao categoria, PrioridadeNotificacao prioridade,
                            String tituloTemplate, String corpoTemplate, String deeplinkTemplate,
                            Integer limiar, Integer cooldownMinutos, boolean enviarEmail,
                            boolean sistema) {
        if (gatilho == null) {
            throw new InvarianteVioladaException("Escolha o gatilho da regra.");
        }
        this.chaveGatilho = gatilho.getChave();
        this.sistema = sistema;
        this.categoria = categoria == null ? gatilho.getCategoriaPadrao() : categoria;
        aplicar(gatilho, nome, descricao, prioridade, tituloTemplate, corpoTemplate,
                deeplinkTemplate, limiar, cooldownMinutos, enviarEmail);
    }

    public void atualizar(String nome, String descricao, CategoriaNotificacao categoria,
                          PrioridadeNotificacao prioridade, String tituloTemplate,
                          String corpoTemplate, String deeplinkTemplate,
                          Integer limiar, Integer cooldownMinutos, boolean enviarEmail) {
        GatilhoNotificacao gatilho = exigirGatilho();
        this.categoria = categoria == null ? gatilho.getCategoriaPadrao() : categoria;
        aplicar(gatilho, nome, descricao, prioridade, tituloTemplate, corpoTemplate,
                deeplinkTemplate, limiar, cooldownMinutos, enviarEmail);
    }

    public CondicaoRegra adicionarCondicao(String campo, OperadorCondicao operador, String valor) {
        GatilhoNotificacao gatilho = exigirGatilho();
        if (!gatilho.declaraVariavel(campo)) {
            throw new InvarianteVioladaException(
                    "O gatilho '" + gatilho.getNome() + "' não fornece a informação '" + campo
                            + "'. Disponíveis: " + String.join(", ", gatilho.getVariaveis()) + ".");
        }
        CondicaoRegra condicao = new CondicaoRegra(this, campo, operador, valor);
        this.condicoes.add(condicao);
        return condicao;
    }

    public DestinatarioRegra adicionarDestinatario(TipoDestinatarioRegra tipo, UUID idAlvo, String valorAlvo) {
        GatilhoNotificacao gatilho = exigirGatilho();
        if (tipo == TipoDestinatarioRegra.PAPEL_DINAMICO) {
            if (gatilho.getModo() != ModoGatilho.POR_OCORRENCIA) {
                throw new InvarianteVioladaException(
                        "Papéis do registro só existem em gatilhos por ocorrência; "
                                + "'" + gatilho.getNome() + "' é avaliado por limiar.");
            }
            if (!gatilho.declaraPapel(valorAlvo)) {
                throw new InvarianteVioladaException(
                        "O gatilho '" + gatilho.getNome() + "' não define o papel '" + valorAlvo
                                + "'. Disponíveis: " + String.join(", ", gatilho.getPapeis()) + ".");
            }
        }
        DestinatarioRegra destinatario = new DestinatarioRegra(this, tipo, idAlvo, valorAlvo);
        this.destinatarios.add(destinatario);
        return destinatario;
    }

    /** Desliga a regra em favor da automação que a substitui. */
    public void converterEmAutomacao(UUID idAutomacao) {
        if (idAutomacao == null) {
            throw new InvarianteVioladaException("Informe a automação que substitui a regra.");
        }
        this.idAutomacao = idAutomacao;
        if (isAtivo()) {
            desativar();
        }
    }

    public boolean isConvertida() {
        return this.idAutomacao != null;
    }

    public void limparCondicoes() {
        this.condicoes.clear();
    }

    public void limparDestinatarios() {
        this.destinatarios.clear();
    }

    /**
     * Uma regra sem destinatário não notifica ninguém e uma regra por limiar sem
     * limiar não sabe quando disparar — as duas checagens ficam aqui e não no
     * construtor, para que a regra possa ser montada em etapas.
     */
    public void exigirUtilizavel() {
        GatilhoNotificacao gatilho = exigirGatilho();
        if (destinatariosAtivos().isEmpty()) {
            throw new InvarianteVioladaException(
                    "Defina ao menos um destinatário antes de ativar a regra.");
        }
        if (gatilho.getModo() == ModoGatilho.POR_LIMIAR && this.limiar == null) {
            throw new InvarianteVioladaException(
                    "Regras por limiar precisam do número a partir do qual devem disparar.");
        }
    }

    public GatilhoNotificacao exigirGatilho() {
        return GatilhoNotificacao.porChave(this.chaveGatilho)
                .orElseThrow(() -> new InvarianteVioladaException(
                        "A regra aponta para um gatilho que não existe mais: " + this.chaveGatilho));
    }

    @Transient
    public ModoGatilho getModo() {
        return GatilhoNotificacao.porChave(this.chaveGatilho)
                .map(GatilhoNotificacao::getModo)
                .orElse(null);
    }

    public List<CondicaoRegra> condicoesAtivas() {
        return this.condicoes.stream().filter(CondicaoRegra::isAtivo).toList();
    }

    public List<DestinatarioRegra> destinatariosAtivos() {
        return this.destinatarios.stream().filter(DestinatarioRegra::isAtivo).toList();
    }

    private void aplicar(GatilhoNotificacao gatilho, String nome, String descricao,
                         PrioridadeNotificacao prioridade, String tituloTemplate,
                         String corpoTemplate, String deeplinkTemplate,
                         Integer limiar, Integer cooldownMinutos, boolean enviarEmail) {
        this.nome = exigirTexto(nome, 120, "O nome da regra");
        this.descricao = limitarOpcional(descricao, 500, "A descrição da regra");
        this.prioridade = prioridade == null ? PrioridadeNotificacao.NORMAL : prioridade;

        this.tituloTemplate = validarTemplate(
                exigirTexto(tituloTemplate, 150, "O título da notificação"), gatilho);
        this.corpoTemplate = validarTemplate(
                exigirTexto(corpoTemplate, 1000, "O texto da notificação"), gatilho);
        this.deeplinkTemplate = validarTemplate(
                limitarOpcional(deeplinkTemplate, 500, "O destino da notificação"), gatilho);

        if (gatilho.getModo() == ModoGatilho.POR_LIMIAR) {
            if (limiar != null && limiar < 1) {
                throw new InvarianteVioladaException("O limiar deve ser de ao menos 1.");
            }
            this.limiar = limiar;
        } else {
            // Limiar em gatilho por ocorrência não teria o que comparar.
            this.limiar = null;
        }

        int cooldown = cooldownMinutos == null ? 240 : cooldownMinutos;
        if (cooldown < 0) {
            throw new InvarianteVioladaException("O intervalo mínimo entre avisos não pode ser negativo.");
        }
        this.cooldownMinutos = cooldown;

        // O veto da categoria vence o padrão ligado, em silêncio e não por exceção: o
        // canal vem marcado por omissão, então recusar aqui transformaria "criar uma
        // regra de marcação" em erro sem que ninguém tivesse pedido e-mail nenhum. A
        // tela recebe permiteEmail na resposta e desabilita a opção.
        this.enviarEmail = enviarEmail && this.categoria.permiteEmail();
    }

    /**
     * Recusa placeholders que o gatilho não fornece. É o que garante que, na hora de
     * renderizar, toda variável tenha valor — o usuário final nunca vê
     * {@code {{algumaCoisa}}} cru na tela.
     */
    private static String validarTemplate(String template, GatilhoNotificacao gatilho) {
        if (template == null) {
            return null;
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        while (matcher.find()) {
            String variavel = matcher.group(1);
            if (!gatilho.declaraVariavel(variavel)) {
                throw new InvarianteVioladaException(
                        "O texto usa {{" + variavel + "}}, que o gatilho '" + gatilho.getNome()
                                + "' não fornece. Disponíveis: "
                                + String.join(", ", gatilho.getVariaveis()) + ".");
            }
        }
        return template;
    }

    private static String exigirTexto(String valor, int limite, String descricao) {
        if (!StringUtils.hasText(valor)) {
            throw new InvarianteVioladaException(descricao + " é obrigatório.");
        }
        return limitar(valor, limite, descricao);
    }

    private static String limitarOpcional(String valor, int limite, String descricao) {
        return StringUtils.hasText(valor) ? limitar(valor, limite, descricao) : null;
    }

    private static String limitar(String valor, int limite, String descricao) {
        String texto = valor.trim();
        if (texto.length() > limite) {
            throw new InvarianteVioladaException(
                    descricao + " excede o limite de " + limite + " caracteres.");
        }
        return texto;
    }
}
