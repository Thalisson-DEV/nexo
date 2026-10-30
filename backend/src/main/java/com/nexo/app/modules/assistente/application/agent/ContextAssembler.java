package com.nexo.app.modules.assistente.application.agent;

import com.nexo.app.modules.assistente.application.AssistenteProperties;
import com.nexo.app.modules.assistente.application.files.ArquivoService;
import com.nexo.app.modules.assistente.application.llm.LlmMessage;
import com.nexo.app.modules.assistente.domain.AcaoPendenteAssistente;
import com.nexo.app.modules.assistente.domain.ArquivoAssistente;
import com.nexo.app.modules.assistente.domain.MensagemAssistente;
import com.nexo.app.modules.assistente.domain.PapelMensagem;
import com.nexo.app.modules.assistente.domain.StatusMensagemAssistente;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.app.modules.assistente.infrastructure.persistence.AcaoPendenteAssistenteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Monta a lista de mensagens enviada ao modelo: prompt de sistema, historico recente da conversa dentro do
 * orcamento de tokens e a pergunta atual com a descricao dos anexos. O historico leva apenas o texto final
 * das respostas; os dados completos continuam acessiveis pelos resultados rN listados no prompt.
 */
@Component
public class ContextAssembler {

    /** Aproximacao conservadora de caracteres por token para portugues com JSON. */
    private static final double CARACTERES_POR_TOKEN = 3.2;
    private static final int RESERVA_TOKENS = 12_000;
    private static final int MAX_CARACTERES_MENSAGEM_HISTORICO = 12_000;

    private final AssistenteProperties properties;
    private final ArquivoService arquivos;
    private final ObjectMapper objectMapper;

    private AcaoPendenteAssistenteRepository acoes;

    public ContextAssembler(AssistenteProperties properties, ArquivoService arquivos, ObjectMapper objectMapper) {
        this.properties = properties;
        this.arquivos = arquivos;
        this.objectMapper = objectMapper;
    }

    /** Opcional: com ele, o historico conta ao modelo o que o usuario decidiu nos cartoes de confirmacao. */
    @Autowired(required = false)
    void setAcoes(AcaoPendenteAssistenteRepository acoes) {
        this.acoes = acoes;
    }

    public List<LlmMessage> montar(String promptSistema, List<MensagemAssistente> historico, String pergunta,
            List<ArquivoAssistente> anexos) {
        return montar(promptSistema, historico, pergunta, anexos, null);
    }

    /**
     * Como {@link #montar(String, List, String, List)}, marcando cada pergunta feita em um painel com o recorte
     * de tela daquele momento (snapshot). Assim "e agora?" depois de trocar de filtro nao herda o recorte antigo.
     */
    public List<LlmMessage> montar(String promptSistema, List<MensagemAssistente> historico, String pergunta,
            List<ArquivoAssistente> anexos, ContextoCes contextoAtual) {
        int orcamento = (int) ((Math.max(16_000, properties.getLlm().getMaxInputTokens()) - RESERVA_TOKENS) * CARACTERES_POR_TOKEN);
        String atual = comRecorte(perguntaComAnexos(pergunta, anexos), contextoAtual, "agora");
        int usado = promptSistema.length() + atual.length();

        List<LlmMessage> anteriores = new ArrayList<>();
        int limiteMensagens = Math.max(2, properties.getMaxHistoryMessages());
        List<MensagemAssistente> candidatas = new ArrayList<>(historico);
        Map<UUID, String> decisoes = decisoes(candidatas);
        Collections.reverse(candidatas);
        for (MensagemAssistente mensagem : candidatas) {
            if (anteriores.size() >= limiteMensagens) break;
            if (!relevante(mensagem)) continue;
            String conteudo = mensagem.getConteudo();
            if (conteudo.length() > MAX_CARACTERES_MENSAGEM_HISTORICO) {
                conteudo = conteudo.substring(0, MAX_CARACTERES_MENSAGEM_HISTORICO) + "\n[...mensagem longa resumida no historico]";
            }
            if (mensagem.getStatus() == StatusMensagemAssistente.CANCELADA) {
                conteudo = conteudo + "\n[resposta interrompida pelo usuario]";
            }
            String decisao = decisoes.get(mensagem.getId());
            if (decisao != null) conteudo = conteudo + "\n" + decisao;
            if (mensagem.getPapel() == PapelMensagem.USUARIO) {
                conteudo = comRecorte(conteudo, snapshot(mensagem), "nesta pergunta");
            }
            if (usado + conteudo.length() > orcamento) break;
            usado += conteudo.length();
            anteriores.add(mensagem.getPapel() == PapelMensagem.USUARIO
                    ? new LlmMessage.User(conteudo) : new LlmMessage.Assistant(conteudo));
        }
        Collections.reverse(anteriores);
        // O historico deve comecar por uma pergunta do usuario.
        while (!anteriores.isEmpty() && !(anteriores.getFirst() instanceof LlmMessage.User)) anteriores.removeFirst();

        List<LlmMessage> mensagens = new ArrayList<>();
        mensagens.add(new LlmMessage.System(promptSistema));
        mensagens.addAll(anteriores);
        mensagens.add(new LlmMessage.User(atual));
        return mensagens;
    }

    /** Por mensagem do assistente, o desfecho dos cartoes de confirmacao que ela abriu. */
    private Map<UUID, String> decisoes(List<MensagemAssistente> mensagens) {
        if (acoes == null) return Map.of();
        List<UUID> ids = mensagens.stream().filter(m -> m.getPapel() != PapelMensagem.USUARIO).map(MensagemAssistente::getId)
                .filter(Objects::nonNull).toList();
        if (ids.isEmpty()) return Map.of();
        Map<UUID, List<String>> notas = new HashMap<>();
        try {
            for (AcaoPendenteAssistente acao : acoes.findByMensagemIdIn(ids)) {
                String nota = nota(acao);
                if (nota != null) notas.computeIfAbsent(acao.getMensagemId(), ignored -> new ArrayList<>()).add(nota);
            }
        } catch (RuntimeException exception) {
            return Map.of();
        }
        Map<UUID, String> resultado = new HashMap<>();
        notas.forEach((id, lista) -> resultado.put(id, String.join("\n", lista)));
        return resultado;
    }

    private String nota(AcaoPendenteAssistente acao) {
        String titulo = "cartao de confirmacao";
        try {
            titulo = objectMapper.readTree(acao.getPreviaJson()).path("titulo").asText(titulo);
        } catch (JsonProcessingException ignored) {
            // mantem o titulo generico
        }
        return switch (acao.getStatus()) {
            case PENDENTE -> "[Cartao \"" + titulo + "\" ainda aguardando o usuario confirmar; nada gravado]";
            case REJEITADA -> "[O usuario cancelou o cartao \"" + titulo + "\"; nada foi gravado]";
            case CONFIRMADA, FALHOU -> {
                try {
                    String historico = objectMapper.readTree(acao.getResultadoJson()).path("historico").asText(null);
                    yield historico == null ? "[Cartao \"" + titulo + "\" confirmado pelo usuario]" : historico;
                } catch (JsonProcessingException | IllegalArgumentException exception) {
                    yield "[Cartao \"" + titulo + "\" confirmado pelo usuario]";
                }
            }
            case EXPIRADA -> null;
        };
    }

    private static boolean relevante(MensagemAssistente mensagem) {
        if (mensagem.getConteudo() == null || mensagem.getConteudo().isBlank()) return false;
        if (mensagem.getPapel() == PapelMensagem.USUARIO) return true;
        return mensagem.getStatus() == StatusMensagemAssistente.CONCLUIDA
                || mensagem.getStatus() == StatusMensagemAssistente.CANCELADA
                || mensagem.getStatus() == StatusMensagemAssistente.DADOS_INSUFICIENTES;
    }

    /** Prefixa a pergunta com o recorte do painel (periodo, filtros, foco) quando ela veio de um dashboard. */
    static String comRecorte(String conteudo, ContextoCes contexto, String quando) {
        if (contexto == null || contexto.painel() == null) return conteudo;
        return "[Recorte do painel " + quando + ": " + contexto.painel().assinatura() + "]\n" + conteudo;
    }

    private ContextoCes snapshot(MensagemAssistente mensagem) {
        String json = mensagem.getContextoJson();
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, ContextoCes.class);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            return null;
        }
    }

    private String perguntaComAnexos(String pergunta, List<ArquivoAssistente> anexos) {
        if (anexos == null || anexos.isEmpty()) return pergunta;
        StringBuilder texto = new StringBuilder(pergunta.strip()).append("\n\n[Anexos desta mensagem]\n");
        for (ArquivoAssistente anexo : anexos) {
            texto.append("- ").append(anexo.getCodigo()).append(": ").append(anexo.getNome())
                    .append(" (").append(anexo.getCategoria().name());
            Map<String, Object> resumo = arquivos.resumo(anexo);
            Object planilhas = resumo.get("planilhas");
            if (planilhas instanceof List<?> lista && !lista.isEmpty()) {
                List<String> abas = new ArrayList<>();
                for (Object item : lista) {
                    if (item instanceof Map<?, ?> aba) abas.add(aba.get("nome") + " (" + aba.get("linhas") + " linhas)");
                }
                texto.append("; abas: ").append(String.join(", ", abas));
            }
            if (resumo.get("paginas") != null) texto.append("; ").append(resumo.get("paginas")).append(" paginas");
            if (anexo.getCategoria() == ArquivoAssistente.Categoria.IMAGEM && !properties.getLlm().isSupportsVision()) {
                texto.append("; imagem: o modelo atual nao enxerga imagens — use executar_python (PIL) para metadados ou peca descricao ao usuario");
            }
            if (anexo.getStatus() == ArquivoAssistente.Status.FALHOU) texto.append("; leitura automatica falhou: ").append(anexo.getErro());
            texto.append(")\n");
        }
        texto.append("Use ler_arquivo/buscar_em_arquivo para ler e executar_python para processar.");
        return texto.toString();
    }
}
