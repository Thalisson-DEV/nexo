package com.nexo.app.modules.notificacao.domain;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Um recado do administrador para um público — o "mural" da aplicação.
 *
 * <p>Existe uma única linha por comunicado, por maior que seja o alcance. Quem ele
 * atinge é descrito por {@link SegmentoComunicado}, e o que cada usuário fez com ele
 * fica em {@link InteracaoComunicado}, criada apenas quando alguém age. Publicar
 * para a empresa inteira, portanto, não escreve nada por usuário.</p>
 *
 * <p>O que é editável depende do estado: em rascunho tudo muda; publicado, só o
 * texto (correção de erro), porque alterar o público de algo que já foi ao ar
 * mudaria retroativamente quem "deveria" ter lido — e é justamente esse registro que
 * o comunicado com confirmação existe para produzir.</p>
 */
@Entity
@Table(name = "comunicados")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comunicado extends BaseEntity {

    private static final int LIMITE_ANEXOS = 10;

    @Column(name = "titulo", nullable = false, length = 150)
    private String titulo;

    @Column(name = "resumo", length = 300)
    private String resumo;

    @Column(name = "conteudo", nullable = false, columnDefinition = "TEXT")
    private String conteudo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoComunicado tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "apresentacao", nullable = false, length = 20)
    private ApresentacaoComunicado apresentacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusComunicado status = StatusComunicado.RASCUNHO;

    /** Link externo (YouTube/Vimeo). Vídeo não sobe por upload — ver a V74. */
    @Column(name = "video_url", length = 500)
    private String videoUrl;

    @Column(name = "acao_rotulo", length = 50)
    private String acaoRotulo;

    @Column(name = "acao_deeplink", length = 500)
    private String acaoDeeplink;

    @Column(name = "data_inicio")
    private OffsetDateTime dataInicio;

    @Column(name = "data_fim")
    private OffsetDateTime dataFim;

    @Column(name = "publicado_em")
    private OffsetDateTime publicadoEm;

    @Column(name = "fixado", nullable = false)
    private boolean fixado = false;

    @Column(name = "exige_confirmacao", nullable = false)
    private boolean exigeConfirmacao = false;

    @Column(name = "notificar_no_sininho", nullable = false)
    private boolean notificarNoSininho = false;

    /**
     * Se o informe também vai para a caixa de e-mail do público.
     *
     * <p>Escolha separada de {@link #notificarNoSininho} porque as duas respondem a
     * perguntas diferentes. O sininho pergunta "isto merece interromper quem já está
     * no sistema?"; o e-mail pergunta "isto precisa alcançar quem não vai abrir o
     * sistema hoje?" — e é aí que mora o informe que de fato justifica o canal: a
     * mudança de política, o comunicado de parada, o aviso que não pode esperar o
     * próximo login.</p>
     */
    @Column(name = "notificar_por_email", nullable = false)
    private boolean notificarPorEmail = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "prioridade_notificacao", nullable = false, length = 20)
    private PrioridadeNotificacao prioridadeNotificacao = PrioridadeNotificacao.NORMAL;

    @JsonManagedReference("comunicado-segmentos")
    @OneToMany(mappedBy = "comunicado", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SegmentoComunicado> segmentos = new ArrayList<>();

    @JsonManagedReference("comunicado-anexos")
    @OneToMany(mappedBy = "comunicado", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("dataCriacao ASC")
    private List<AnexoComunicado> anexos = new ArrayList<>();

    public Comunicado(String titulo, String resumo, String conteudo, TipoComunicado tipo,
                      ApresentacaoComunicado apresentacao, String videoUrl,
                      String acaoRotulo, String acaoDeeplink, boolean fixado,
                      boolean exigeConfirmacao, boolean notificarNoSininho,
                      boolean notificarPorEmail, PrioridadeNotificacao prioridadeNotificacao) {
        aplicarConteudo(titulo, resumo, conteudo, videoUrl, acaoRotulo, acaoDeeplink);
        this.tipo = exigirPreenchido(tipo, "O tipo do comunicado é obrigatório.");
        this.apresentacao = exigirPreenchido(apresentacao, "A forma de apresentação é obrigatória.");
        this.fixado = fixado;
        this.exigeConfirmacao = exigeConfirmacao;
        this.notificarNoSininho = notificarNoSininho;
        this.notificarPorEmail = notificarPorEmail;
        this.prioridadeNotificacao = prioridadeNotificacao == null
                ? PrioridadeNotificacao.NORMAL : prioridadeNotificacao;
        this.status = StatusComunicado.RASCUNHO;
    }

    /**
     * Edição completa, permitida apenas enquanto o comunicado é rascunho.
     */
    public void atualizar(String titulo, String resumo, String conteudo, TipoComunicado tipo,
                          ApresentacaoComunicado apresentacao, String videoUrl,
                          String acaoRotulo, String acaoDeeplink, boolean fixado,
                          boolean exigeConfirmacao, boolean notificarNoSininho,
                          boolean notificarPorEmail,
                          PrioridadeNotificacao prioridadeNotificacao) {
        exigirRascunho("alterar o comunicado");
        aplicarConteudo(titulo, resumo, conteudo, videoUrl, acaoRotulo, acaoDeeplink);
        this.tipo = exigirPreenchido(tipo, "O tipo do comunicado é obrigatório.");
        this.apresentacao = exigirPreenchido(apresentacao, "A forma de apresentação é obrigatória.");
        this.fixado = fixado;
        this.exigeConfirmacao = exigeConfirmacao;
        this.notificarNoSininho = notificarNoSininho;
        this.notificarPorEmail = notificarPorEmail;
        this.prioridadeNotificacao = prioridadeNotificacao == null
                ? PrioridadeNotificacao.NORMAL : prioridadeNotificacao;
    }

    /**
     * Correção de texto depois de publicado: erro de digitação num aviso no ar não
     * deveria obrigar a despublicar e refazer. O público e as regras de exibição
     * ficam de fora justamente porque já produziram efeito.
     */
    public void corrigirTexto(String titulo, String resumo, String conteudo) {
        if (this.status == StatusComunicado.ARQUIVADO) {
            throw new InvarianteVioladaException("Um comunicado arquivado não pode ser alterado.");
        }
        this.titulo = exigirTexto(titulo, 150, "O título do comunicado");
        this.resumo = limitarOpcional(resumo, 300, "O resumo do comunicado");
        this.conteudo = exigirConteudo(conteudo);
    }

    /**
     * Coloca no ar. Sem público-alvo o comunicado não alcançaria ninguém, e sem
     * conteúdo não haveria o que ler — as duas checagens acontecem aqui, e não na
     * criação, para que o rascunho possa ser salvo pela metade.
     */
    public void publicar(OffsetDateTime dataInicio, OffsetDateTime dataFim) {
        exigirRascunho("publicar");
        if (segmentosAtivos().isEmpty()) {
            throw new InvarianteVioladaException(
                    "Defina ao menos um público-alvo antes de publicar o comunicado.");
        }
        OffsetDateTime inicio = dataInicio == null ? OffsetDateTime.now() : dataInicio;
        if (dataFim != null && !dataFim.isAfter(inicio)) {
            throw new InvarianteVioladaException(
                    "O fim da exibição deve ser posterior ao início.");
        }
        this.dataInicio = inicio;
        this.dataFim = dataFim;
        this.publicadoEm = OffsetDateTime.now();
        this.status = StatusComunicado.PUBLICADO;
    }

    /** Tira do ar sem apagar: o histórico de confirmações continua consultável. */
    public void arquivar() {
        if (this.status == StatusComunicado.ARQUIVADO) {
            return;
        }
        this.status = StatusComunicado.ARQUIVADO;
        this.dataFim = OffsetDateTime.now();
    }

    public SegmentoComunicado adicionarSegmento(TipoAlvoSegmento tipoAlvo, UUID idAlvo, String valorAlvo) {
        exigirRascunho("alterar o público-alvo");

        List<SegmentoComunicado> ativos = segmentosAtivos();
        if (tipoAlvo == TipoAlvoSegmento.TODOS && !ativos.isEmpty()) {
            throw new InvarianteVioladaException(
                    "O público 'todos os usuários' já abrange os demais; remova os outros segmentos antes.");
        }
        if (ativos.stream().anyMatch(s -> s.getTipoAlvo() == TipoAlvoSegmento.TODOS)) {
            throw new InvarianteVioladaException(
                    "O comunicado já é para todos os usuários; segmentos adicionais não teriam efeito.");
        }
        if (ativos.stream().anyMatch(s -> s.corresponde(tipoAlvo, idAlvo, valorAlvo))) {
            throw new InvarianteVioladaException("Este público-alvo já foi adicionado ao comunicado.");
        }

        SegmentoComunicado segmento = new SegmentoComunicado(this, tipoAlvo, idAlvo, valorAlvo);
        this.segmentos.add(segmento);
        return segmento;
    }

    public void removerSegmento(UUID idSegmento) {
        exigirRascunho("alterar o público-alvo");
        SegmentoComunicado segmento = this.segmentos.stream()
                .filter(s -> idSegmento.equals(s.getId()))
                .findFirst()
                .orElseThrow(() -> new InvarianteVioladaException(
                        "Público-alvo não encontrado neste comunicado."));
        this.segmentos.remove(segmento);
    }

    public AnexoComunicado adicionarAnexo(String nomeArquivo, String contentType,
                                          long tamanhoBytes, String objectKey) {
        if (this.status == StatusComunicado.ARQUIVADO) {
            throw new InvarianteVioladaException("Um comunicado arquivado não aceita novos anexos.");
        }
        if (anexos.stream().filter(AnexoComunicado::isAtivo).count() >= LIMITE_ANEXOS) {
            throw new InvarianteVioladaException(
                    "O comunicado aceita no máximo " + LIMITE_ANEXOS + " anexos.");
        }
        AnexoComunicado anexo = new AnexoComunicado(this, nomeArquivo, contentType, tamanhoBytes, objectKey);
        this.anexos.add(anexo);
        return anexo;
    }

    public AnexoComunicado buscarAnexo(UUID idAnexo) {
        return this.anexos.stream()
                .filter(anexo -> anexo.isAtivo() && anexo.getId().equals(idAnexo))
                .findFirst()
                .orElseThrow(() -> new InvarianteVioladaException(
                        "Anexo não encontrado neste comunicado."));
    }

    public void removerAnexo(UUID idAnexo) {
        this.anexos.remove(buscarAnexo(idAnexo));
    }

    /** Está publicado e dentro da janela de exibição. */
    public boolean estaVigente(OffsetDateTime momento) {
        return isAtivo()
                && this.status == StatusComunicado.PUBLICADO
                && this.dataInicio != null
                && !this.dataInicio.isAfter(momento)
                && (this.dataFim == null || this.dataFim.isAfter(momento));
    }

    /** Publicado com início no futuro — o que a interface chama de "agendado". */
    public boolean estaAgendado(OffsetDateTime momento) {
        return this.status == StatusComunicado.PUBLICADO
                && this.dataInicio != null
                && this.dataInicio.isAfter(momento);
    }

    /**
     * Comunicado que exige confirmação não pode ser adiado: "ver depois" viraria uma
     * forma silenciosa de nunca confirmar.
     */
    public boolean permiteAdiar() {
        return !this.exigeConfirmacao;
    }

    public List<SegmentoComunicado> segmentosAtivos() {
        return this.segmentos.stream().filter(SegmentoComunicado::isAtivo).toList();
    }

    public List<AnexoComunicado> anexosAtivos() {
        return this.anexos.stream().filter(AnexoComunicado::isAtivo).toList();
    }

    private void aplicarConteudo(String titulo, String resumo, String conteudo, String videoUrl,
                                 String acaoRotulo, String acaoDeeplink) {
        this.titulo = exigirTexto(titulo, 150, "O título do comunicado");
        this.resumo = limitarOpcional(resumo, 300, "O resumo do comunicado");
        this.conteudo = exigirConteudo(conteudo);
        this.videoUrl = validarVideoUrl(videoUrl);
        this.acaoRotulo = limitarOpcional(acaoRotulo, 50, "O rótulo da ação");
        this.acaoDeeplink = limitarOpcional(acaoDeeplink, 500, "O destino da ação");
        if (StringUtils.hasText(this.acaoRotulo) != StringUtils.hasText(this.acaoDeeplink)) {
            throw new InvarianteVioladaException(
                    "A ação do comunicado precisa de rótulo e destino, ou de nenhum dos dois.");
        }
    }

    /**
     * Só http(s): o campo vai virar href no navegador do usuário, e aceitar esquemas
     * livres abriria espaço para {@code javascript:} vindo de um administrador
     * comprometido.
     */
    private static String validarVideoUrl(String videoUrl) {
        if (!StringUtils.hasText(videoUrl)) {
            return null;
        }
        String url = videoUrl.trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new InvarianteVioladaException(
                    "O link do vídeo deve começar com http:// ou https://.");
        }
        return limitar(url, 500, "O link do vídeo");
    }

    private void exigirRascunho(String acao) {
        if (this.status != StatusComunicado.RASCUNHO) {
            throw new InvarianteVioladaException(
                    "Só é possível " + acao + " enquanto ele é um rascunho.");
        }
    }

    private static String exigirConteudo(String conteudo) {
        if (!StringUtils.hasText(conteudo)) {
            throw new InvarianteVioladaException("O conteúdo do comunicado é obrigatório.");
        }
        return conteudo.trim();
    }

    private static <T> T exigirPreenchido(T valor, String mensagem) {
        if (valor == null) {
            throw new InvarianteVioladaException(mensagem);
        }
        return valor;
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
