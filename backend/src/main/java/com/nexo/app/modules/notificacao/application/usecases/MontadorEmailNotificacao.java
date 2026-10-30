package com.nexo.app.modules.notificacao.application.usecases;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.nexo.app.modules.notificacao.application.dto.NotificacaoResponse;
import com.nexo.app.modules.notificacao.domain.CategoriaNotificacao;
import com.nexo.app.modules.notificacao.domain.PrioridadeNotificacao;
import com.nexo.app.shared.application.notificacao.AnexoNotificacao;
import com.nexo.app.shared.application.notificacao.FornecedorAnexosNotificacao;
import com.nexo.app.shared.application.notificacao.DetalhesNotificacao;
import com.nexo.app.shared.application.notificacao.FornecedorDetalhesNotificacao;
import com.nexo.app.shared.infrastructure.email.AnexoEmail;
import com.nexo.app.shared.infrastructure.email.ConteudoEmail;
import com.nexo.app.shared.infrastructure.email.DestaqueEmail;
import com.nexo.app.shared.infrastructure.email.EmailProperties;
import com.nexo.app.shared.infrastructure.email.MensagemEmail;
import com.nexo.app.shared.infrastructure.email.MontadorEmailHtml;
import com.nexo.app.shared.infrastructure.storage.ObjectStorageService;
import com.nexo.app.shared.infrastructure.storage.StorageObject;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Traduz uma notificação já gravada na mensagem de e-mail correspondente.
 *
 * <p>É aqui que a notificação vira as mesmas peças que todo e-mail do Nexo tem: a
 * categoria vira etiqueta, a prioridade vira cor, o corpo vira parágrafos, o contexto
 * do gatilho vira a ficha de dados e o deeplink vira o botão. Nenhum módulo escolhe
 * como seu e-mail se parece — o layout é um só, e o que varia é o que preenche cada
 * campo.</p>
 *
 * <p>Os anexos são resolvidos agora, e não quando o evento foi publicado: em vários
 * fluxos do Nexo o registro nasce numa chamada e os arquivos sobem na seguinte.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MontadorEmailNotificacao {

    /** Chaves do contexto que não interessam a um humano lendo o e-mail. */
    private static final Set<String> CHAVES_TECNICAS = Set.of("limiar", "link");

    /** Identifica o sistema na listagem da caixa e permite regra de pasta no Outlook. */
    private static final String PREFIXO_ASSUNTO = "[Nexo]";

    private final EmailProperties propriedades;
    private final MontadorEmailHtml montadorHtml;
    private final ObjectStorageService armazenamento;
    private final List<FornecedorAnexosNotificacao> fornecedoresAnexos;
    private final List<FornecedorDetalhesNotificacao> fornecedoresDetalhes;

    public MensagemEmail montar(Set<String> destinatarios, NotificacaoResponse notificacao,
                                String chaveGatilho) {
        return montar(destinatarios, notificacao, chaveGatilho, null);
    }

    /**
     * Monta a mensagem de um destinatário só, com as ações pessoais dele (links que só
     * ele pode usar). Ver {@link #exigeEnvioIndividual(String)}.
     */
    public MensagemEmail montarPara(String destinatario, UUID idDestinatario, NotificacaoResponse notificacao,
                                    String chaveGatilho) {
        return montar(Set.of(destinatario), notificacao, chaveGatilho, idDestinatario);
    }

    /**
     * Se o gatilho leva ações pessoais. Então cada destinatário recebe a própria
     * mensagem: um link de decisão no "Para" coletivo valeria para todos com o nome de um.
     */
    public boolean exigeEnvioIndividual(String chaveGatilho) {
        return StringUtils.hasText(chaveGatilho) && fornecedoresDetalhes.stream()
                .anyMatch(fornecedor -> fornecedor.atende(chaveGatilho) && fornecedor.acoesPessoais(chaveGatilho));
    }

    private MensagemEmail montar(Set<String> destinatarios, NotificacaoResponse notificacao,
                                 String chaveGatilho, UUID idDestinatario) {
        List<AnexoEmail> anexados = new ArrayList<>();
        List<ConteudoEmail.Anexo> listados = resolverAnexos(notificacao, chaveGatilho, anexados);
        DetalhesNotificacao detalhes = buscarDetalhes(chaveGatilho, notificacao.idReferencia(), idDestinatario);
        List<ConteudoEmail.Acao> acoes = acoes(detalhes);

        ConteudoEmail conteudo = ConteudoEmail.builder()
                .etiqueta(notificacao.categoriaRotulo())
                .prioridade(prioridadeVisivel(notificacao))
                .destaque(destaqueDe(notificacao.prioridade()))
                .titulo(notificacao.titulo())
                .paragrafos(paragrafos(notificacao.corpo()))
                .dados(detalhes.ficha().isEmpty() ? ficha(notificacao.contexto()) : ficha(detalhes))
                .acoes(acoes)
                .notaAcoes(detalhes.notaAcoes())
                // Com os botões de decisão na mensagem, o botão principal deixa de ser
                // "decidir" e passa a ser o caminho para a análise completa.
                .rotuloAcao(acoes.isEmpty() ? rotuloAcao(notificacao.categoria()) : "Abrir o relatório completo")
                .urlAcao(propriedades.resolverUrl(notificacao.deeplink()))
                .anexos(listados)
                .build();

        return MensagemEmail.builder()
                .destinatarios(destinatarios)
                .assunto(assunto(notificacao.titulo()))
                .html(montadorHtml.montar(conteudo))
                .texto(montadorHtml.montarTexto(conteudo))
                .anexos(anexados)
                .build();
    }

    public MensagemEmail montar(String destinatario, NotificacaoResponse notificacao,
                                String chaveGatilho) {
        return montar(Set.of(destinatario), notificacao, chaveGatilho);
    }

    /**
     * O assunto carrega o prefixo do sistema para que a caixa de entrada corporativa
     * possa separá-lo por regra — é a diferença entre o aviso cair numa pasta de
     * trabalho e disputar espaço com a correspondência do dia. O título já vem pronto
     * do template da regra; aqui só se identifica a origem.
     */
    private static String assunto(String titulo) {
        return PREFIXO_ASSUNTO + " " + (StringUtils.hasText(titulo) ? titulo.trim() : "Aviso");
    }

    /**
     * Prioridade só aparece quando muda o que se espera de quem lê.
     *
     * <p>Anunciar "prioridade normal" em todo aviso gastaria a palavra: depois do
     * quinto e-mail, a linha vira decoração e ninguém repara quando ela finalmente diz
     * "crítica". Rotina não se anuncia.</p>
     */
    private static String prioridadeVisivel(NotificacaoResponse notificacao) {
        PrioridadeNotificacao prioridade = notificacao.prioridade();
        if (prioridade != PrioridadeNotificacao.ALTA
                && prioridade != PrioridadeNotificacao.CRITICA) {
            return null;
        }
        String rotulo = StringUtils.hasText(notificacao.prioridadeRotulo())
                ? notificacao.prioridadeRotulo() : prioridade.getRotulo();
        return "Prioridade " + rotulo.toLowerCase(Locale.of("pt", "BR"));
    }

    /**
     * Baixa o que couber no orçamento e lista o resto.
     *
     * <p>Os menores vêm primeiro para que o orçamento renda o maior número de
     * arquivos — entre anexar um PDF de 7 MB e as quatro fotos que somam 2 MB, as
     * fotos servem melhor a quem vai decidir. O que ficou de fora aparece na lista
     * marcado, porque um anexo omitido em silêncio faria a pessoa concluir que não há
     * documento nenhum.</p>
     */
    private List<ConteudoEmail.Anexo> resolverAnexos(NotificacaoResponse notificacao,
                                                     String chaveGatilho,
                                                     List<AnexoEmail> destino) {
        if (notificacao.idReferencia() == null || !StringUtils.hasText(chaveGatilho)) {
            return List.of();
        }
        List<AnexoNotificacao> disponiveis = buscarDisponiveis(chaveGatilho, notificacao.idReferencia());
        if (disponiveis.isEmpty()) {
            return List.of();
        }

        List<ConteudoEmail.Anexo> listados = new ArrayList<>();
        long orcamento = propriedades.getLimiteAnexosBytes();
        long usado = 0;

        for (AnexoNotificacao anexo : disponiveis.stream()
                .sorted(Comparator.comparingLong(AnexoNotificacao::tamanhoBytes))
                .toList()) {

            if (usado + anexo.tamanhoBytes() > orcamento) {
                listados.add(new ConteudoEmail.Anexo(
                        anexo.nomeArquivo(), anexo.tamanhoBytes(), false));
                continue;
            }
            byte[] conteudo = baixar(anexo);
            if (conteudo == null) {
                listados.add(new ConteudoEmail.Anexo(
                        anexo.nomeArquivo(), anexo.tamanhoBytes(), false));
                continue;
            }
            destino.add(new AnexoEmail(anexo.nomeArquivo(), anexo.contentType(), conteudo));
            listados.add(new ConteudoEmail.Anexo(anexo.nomeArquivo(), conteudo.length, true));
            usado += conteudo.length;
        }
        return listados;
    }

    private List<AnexoNotificacao> buscarDisponiveis(String chaveGatilho, UUID idReferencia) {
        FornecedorAnexosNotificacao fornecedor = fornecedoresAnexos.stream()
                .filter(candidato -> candidato.atende(chaveGatilho))
                .findFirst()
                .orElse(null);
        if (fornecedor == null) {
            return List.of();
        }
        try {
            List<AnexoNotificacao> anexos = fornecedor.anexosDe(idReferencia);
            return anexos == null ? List.of() : anexos;
        } catch (RuntimeException e) {
            log.warn("Não foi possível consultar anexos do gatilho '{}' para a referência {}. O e-mail seguirá sem eles: {}",
                    chaveGatilho, idReferencia, e.getMessage());
            return List.of();
        }
    }

    /**
     * Armazenamento indisponível não pode impedir o aviso de sair: o e-mail vale mais
     * sem o anexo do que não vale nenhum e-mail.
     */
    private byte[] baixar(AnexoNotificacao anexo) {
        try {
            StorageObject objeto = armazenamento.baixar(anexo.chaveObjeto());
            try (InputStream entrada = objeto.conteudo()) {
                return entrada.readAllBytes();
            }
        } catch (IOException | RuntimeException e) {
            log.warn("Não foi possível anexar '{}' ({}) ao e-mail: {}",
                    anexo.nomeArquivo(), anexo.chaveObjeto(), e.getMessage());
            return null;
        }
    }

    /**
     * A ficha e as ações que o módulo de origem sabe montar para o registro. Falha ou
     * ausência não segura o e-mail: ele sai com a ficha genérica, sem botões.
     */
    private DetalhesNotificacao buscarDetalhes(String chaveGatilho, UUID idReferencia, UUID idDestinatario) {
        DetalhesNotificacao vazio = new DetalhesNotificacao(List.of(), List.of());
        if (idReferencia == null || !StringUtils.hasText(chaveGatilho)) {
            return vazio;
        }
        FornecedorDetalhesNotificacao fornecedor = fornecedoresDetalhes.stream()
                .filter(candidato -> candidato.atende(chaveGatilho))
                .findFirst()
                .orElse(null);
        if (fornecedor == null) {
            return vazio;
        }
        try {
            return (idDestinatario == null
                    ? fornecedor.detalhesDe(chaveGatilho, idReferencia)
                    : fornecedor.detalhesPara(chaveGatilho, idReferencia, idDestinatario)).orElse(vazio);
        } catch (RuntimeException e) {
            log.warn("Não foi possível montar os detalhes do gatilho '{}' para a referência {}. O e-mail seguirá com a ficha genérica: {}",
                    chaveGatilho, idReferencia, e.getMessage());
            return vazio;
        }
    }

    private static List<ConteudoEmail.Dado> ficha(DetalhesNotificacao detalhes) {
        return detalhes.ficha().stream()
                .filter(campo -> StringUtils.hasText(campo.valor()))
                .map(campo -> new ConteudoEmail.Dado(campo.rotulo(), campo.valor()))
                .toList();
    }

    private List<ConteudoEmail.Acao> acoes(DetalhesNotificacao detalhes) {
        return detalhes.acoes().stream()
                .map(acao -> new ConteudoEmail.Acao(acao.rotulo(), propriedades.resolverUrl(acao.caminho()),
                        switch (acao.estilo() == null ? DetalhesNotificacao.Estilo.NEUTRA : acao.estilo()) {
                            case POSITIVA -> ConteudoEmail.Tom.POSITIVA;
                            case NEGATIVA -> ConteudoEmail.Tom.NEGATIVA;
                            case NEUTRA -> ConteudoEmail.Tom.NEUTRA;
                        }))
                .filter(acao -> StringUtils.hasText(acao.url()))
                .toList();
    }

    /**
     * O contexto do gatilho vira a ficha: protocolo, prioridade, equipe, valor. Os
     * identificadores são descartados — quem lê não faz nada com um UUID, e o caminho
     * até o registro é o botão.
     */
    private static List<ConteudoEmail.Dado> ficha(Map<String, Object> contexto) {
        if (contexto == null || contexto.isEmpty()) {
            return List.of();
        }
        List<ConteudoEmail.Dado> dados = new ArrayList<>();
        for (Map.Entry<String, Object> entrada : contexto.entrySet()) {
            String chave = entrada.getKey();
            Object valor = entrada.getValue();

            if (valor == null || chave == null || ehTecnica(chave)) {
                continue;
            }
            String texto = valor.toString().trim();
            if (texto.isEmpty() || texto.length() > 120) {
                continue;
            }
            dados.add(new ConteudoEmail.Dado(rotular(chave), texto));
        }
        return dados;
    }

    private static boolean ehTecnica(String chave) {
        if (CHAVES_TECNICAS.contains(chave)) {
            return true;
        }
        // idDesconto, idChamado, idSolicitacao... o padrão do catálogo de gatilhos.
        return chave.length() > 2 && chave.startsWith("id")
                && Character.isUpperCase(chave.charAt(2));
    }

    /** {@code motivoRejeicao} vira "Motivo rejeicao" — legível sem tabela de tradução. */
    private static String rotular(String chave) {
        StringBuilder rotulo = new StringBuilder(chave.length() + 4);
        rotulo.append(Character.toUpperCase(chave.charAt(0)));
        for (int i = 1; i < chave.length(); i++) {
            char atual = chave.charAt(i);
            if (Character.isUpperCase(atual)) {
                rotulo.append(' ').append(Character.toLowerCase(atual));
            } else {
                rotulo.append(atual);
            }
        }
        return rotulo.toString();
    }

    private static List<String> paragrafos(String corpo) {
        if (!StringUtils.hasText(corpo)) {
            return List.of();
        }
        return corpo.lines()
                .map(String::trim)
                .filter(linha -> !linha.isEmpty())
                .toList();
    }

    /** O botão diz o que se espera da pessoa, não apenas para onde ele leva. */
    private static String rotuloAcao(CategoriaNotificacao categoria) {
        if (categoria == null) {
            return "Acessar o registro";
        }
        return switch (categoria) {
            case APROVACAO -> "Analisar e decidir";
            case CHAMADO -> "Abrir o chamado";
            case SEGURANCA -> "Ver o registro";
            case COMUNICADO -> "Ler o comunicado";
            case OPERACIONAL, SISTEMA, MENCAO -> "Acessar o registro";
        };
    }

    private static DestaqueEmail destaqueDe(PrioridadeNotificacao prioridade) {
        if (prioridade == null) {
            return DestaqueEmail.NEUTRO;
        }
        return switch (prioridade) {
            case CRITICA -> DestaqueEmail.CRITICO;
            case ALTA -> DestaqueEmail.ATENCAO;
            case NORMAL -> DestaqueEmail.INFORMATIVO;
            case BAIXA -> DestaqueEmail.NEUTRO;
        };
    }

}
