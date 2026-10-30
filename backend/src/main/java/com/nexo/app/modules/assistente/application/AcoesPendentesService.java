package com.nexo.app.modules.assistente.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexo.app.modules.assistente.application.agent.AcaoComPrevia;
import com.nexo.app.modules.assistente.application.agent.AgentTool;
import com.nexo.app.modules.assistente.application.agent.ToolException;
import com.nexo.app.modules.assistente.application.agent.ToolRegistry;
import com.nexo.app.modules.assistente.application.cadastro.CatalogoCadastros;
import com.nexo.app.modules.assistente.application.cadastro.Opcao;
import com.nexo.app.modules.assistente.domain.AcaoPendenteAssistente;
import com.nexo.app.modules.assistente.infrastructure.persistence.AcaoPendenteAssistenteRepository;
import com.nexo.app.modules.assistente.infrastructure.persistence.ConversaAssistenteRepository;
import com.nexo.app.shared.application.analytics.SemanticQueryContext;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import com.nexo.app.shared.infrastructure.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Decisao do usuario sobre os cartoes de confirmacao do Copiloto: revisar campos, confirmar (a gravacao
 * acontece aqui, na requisicao de quem confirmou, com as permissoes dele) ou cancelar.
 */
@Service
public class AcoesPendentesService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AcoesPendentesService.class);

    private final AcaoPendenteAssistenteRepository acoes;
    private final ConversaAssistenteRepository conversas;
    private final ToolRegistry ferramentas;
    private final AssistantSecurityContextFactory seguranca;
    private final CatalogoCadastros catalogo;
    private final ObjectMapper mapper;

    public AcoesPendentesService(AcaoPendenteAssistenteRepository acoes, ConversaAssistenteRepository conversas,
            ToolRegistry ferramentas, AssistantSecurityContextFactory seguranca, CatalogoCadastros catalogo, ObjectMapper mapper) {
        this.acoes = acoes;
        this.conversas = conversas;
        this.ferramentas = ferramentas;
        this.seguranca = seguranca;
        this.catalogo = catalogo;
        this.mapper = mapper;
    }

    /** Estado de um cartao para a interface. {@code sigiloso} so existe na resposta da confirmacao. */
    public record AcaoView(UUID acaoId, UUID mensagemId, String ferramenta, String status, JsonNode previa,
            JsonNode resultado, JsonNode sigiloso, String mensagem, OffsetDateTime decididaEm) {
    }

    public List<AcaoView> listar(UUID conversaId) {
        UUID usuarioId = usuario();
        conversas.findByIdAndUsuarioIdAndAtivoTrue(conversaId, usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Conversa não encontrada"));
        OffsetDateTime agora = OffsetDateTime.now();
        return acoes.findByConversaIdAndUsuarioIdOrderByDataCriacao(conversaId, usuarioId).stream()
                .map(acao -> {
                    if (acao.getStatus() == AcaoPendenteAssistente.Status.PENDENTE && acao.expirada(agora)) {
                        acao.expirar();
                        acoes.save(acao);
                    }
                    return view(acao, null, null);
                })
                .toList();
    }

    public AcaoView revisar(UUID acaoId, JsonNode alteracoes) {
        AcaoPendenteAssistente acao = pendente(acaoId);
        AcaoComPrevia ferramenta = ferramenta(acao);
        try {
            AcaoComPrevia.PreviaAcao previa = ferramenta.revisar(ler(acao.getArgumentosJson()), alteracoes, contexto());
            acao.revisar(previa.argumentos().toString(), previa.exibicao().toString());
            acoes.save(acao);
            return view(acao, null, null);
        } catch (ToolException exception) {
            throw new InvarianteVioladaException(exception.getMessage());
        }
    }

    public AcaoView confirmar(UUID acaoId, JsonNode alteracoes) {
        AcaoPendenteAssistente acao = pendente(acaoId);
        AcaoComPrevia ferramenta = ferramenta(acao);
        AcaoComPrevia.ResultadoAcao resultado;
        try {
            resultado = ferramenta.confirmar(ler(acao.getArgumentosJson()), alteracoes, contexto());
        } catch (ToolException exception) {
            throw new InvarianteVioladaException(exception.getMessage());
        }
        if (resultado.pendente()) {
            acao.revisar(resultado.previa().argumentos().toString(), resultado.previa().exibicao().toString());
            acoes.save(acao);
            return view(acao, null, "Ainda falta completar alguns campos.");
        }
        if (!resultado.sucesso() && resultado.resultado() != null) {
            // Nada foi gravado (regra do modulo recusou): o cartao continua aberto para corrigir e tentar de novo.
            List<String> motivos = new java.util.ArrayList<>();
            resultado.resultado().path("registros").forEach(r -> {
                if (r.hasNonNull("mensagem")) motivos.add(r.path("mensagem").asText());
            });
            LOGGER.info("Cartao {} do Copiloto recusado pelo modulo; segue aberto", acao.getId());
            return view(acao, null, motivos.isEmpty() ? "Não foi possível gravar. Revise os dados." : String.join(" ", motivos));
        }
        ObjectNode guardado = JsonNodeFactory.instance.objectNode();
        guardado.set("resultado", resultado.resultado());
        guardado.put("historico", resultado.paraModelo());
        if (resultado.sucesso()) acao.confirmar(guardado.toString());
        else acao.falhar(guardado.toString());
        acoes.save(acao);
        LOGGER.info("Cartao {} do Copiloto decidido: {} ({})", acao.getId(), acao.getStatus(), acao.getFerramenta());
        return view(acao, resultado.sigiloso(), null);
    }

    public AcaoView rejeitar(UUID acaoId) {
        AcaoPendenteAssistente acao = pendente(acaoId);
        acao.rejeitar();
        acoes.save(acao);
        return view(acao, null, null);
    }

    /** Busca para os campos de referencia do cartao: so cadastros que o usuario pode usar num cadastro. */
    public List<Opcao> buscarReferencia(String tipo, String termo) {
        SemanticQueryContext contexto = contexto();
        if (!catalogo.referenciasPermitidas(contexto.autoridades()).contains(tipo)) {
            throw new RecursoNaoEncontradoException("Cadastro não encontrado");
        }
        return catalogo.buscarReferencia(tipo, termo == null ? "" : termo, 12);
    }

    // ---------------------------------------------------------------------------------- apoio

    private AcaoPendenteAssistente pendente(UUID acaoId) {
        AcaoPendenteAssistente acao = acoes.findByIdAndUsuarioId(acaoId, usuario())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ação não encontrada"));
        if (acao.getStatus() == AcaoPendenteAssistente.Status.PENDENTE && acao.expirada(OffsetDateTime.now())) {
            acao.expirar();
            acoes.save(acao);
        }
        if (acao.getStatus() != AcaoPendenteAssistente.Status.PENDENTE) {
            throw new InvarianteVioladaException(switch (acao.getStatus()) {
                case CONFIRMADA, FALHOU -> "Este cartão já foi confirmado.";
                case REJEITADA -> "Este cartão foi cancelado.";
                default -> "Este cartão expirou. Peça de novo ao Copiloto.";
            });
        }
        return acao;
    }

    private AcaoComPrevia ferramenta(AcaoPendenteAssistente acao) {
        AgentTool tool = ferramentas.buscar(acao.getFerramenta())
                .orElseThrow(() -> new InvarianteVioladaException("Esta ação não está mais disponível."));
        if (!ferramentas.autorizada(tool, contexto())) {
            throw new InvarianteVioladaException("Você não tem permissão para esta ação.");
        }
        if (!(tool instanceof AcaoComPrevia comPrevia)) {
            throw new InvarianteVioladaException("Esta ação precisa ser feita na tela do módulo.");
        }
        return comPrevia;
    }

    private JsonNode ler(String json) {
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new InvarianteVioladaException("Os dados da ação estão corrompidos. Peça de novo ao Copiloto.");
        }
    }

    private AcaoView view(AcaoPendenteAssistente acao, JsonNode sigiloso, String mensagem) {
        JsonNode previa = lerOuNulo(acao.getPreviaJson());
        JsonNode resultado = lerOuNulo(acao.getResultadoJson());
        return new AcaoView(acao.getId(), acao.getMensagemId(), acao.getFerramenta(), acao.getStatus().name(), previa,
                resultado == null ? null : resultado.path("resultado"), sigiloso, mensagem, acao.getDecididaEm());
    }

    private JsonNode lerOuNulo(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private SemanticQueryContext contexto() {
        return seguranca.current(usuario());
    }

    private static UUID usuario() {
        UUID usuarioId = SecurityUtils.getUsuarioLogadoId();
        if (usuarioId == null) throw new IllegalStateException("Usuário não autenticado");
        return usuarioId;
    }
}
