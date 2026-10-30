package com.nexo.app.modules.assistente.application.conexoes;

import com.nexo.app.modules.assistente.application.AssistantSecurityContextFactory;
import com.nexo.app.modules.assistente.application.agent.AgentTool;
import com.nexo.app.modules.assistente.application.dto.ChaveIaCriadaResponse;
import com.nexo.app.modules.assistente.application.dto.ConexaoIaInfoResponse;
import com.nexo.app.modules.assistente.application.dto.ConexaoIaResponse;
import com.nexo.app.modules.assistente.application.dto.CriarChaveIaRequest;
import com.nexo.app.modules.assistente.application.mcp.McpCatalogo;
import com.nexo.app.modules.assistente.domain.ConexaoIa;
import com.nexo.app.modules.assistente.domain.ConversaAssistente;
import com.nexo.app.modules.assistente.domain.CredencialConexaoIa;
import com.nexo.app.modules.assistente.domain.MensagemAssistente;
import com.nexo.app.modules.assistente.domain.StatusMensagemAssistente;
import com.nexo.app.modules.assistente.domain.TipoCredencialIa;
import com.nexo.app.modules.assistente.infrastructure.persistence.ConexaoIaRepository;
import com.nexo.app.modules.assistente.infrastructure.persistence.ConversaAssistenteRepository;
import com.nexo.app.modules.assistente.infrastructure.persistence.CredencialConexaoIaRepository;
import com.nexo.app.modules.assistente.infrastructure.persistence.MensagemAssistenteRepository;
import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.shared.application.analytics.SemanticQueryContext;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import com.nexo.app.shared.infrastructure.security.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Conexoes de IA da pessoa: listar, criar chave de acesso e desconectar. O administrador ve e
 * desconecta as de todos.
 */
@Service
public class ConexaoIaService {

    public static final String PERMISSAO_USAR = "assistente:conexao:use";
    public static final String PERMISSAO_GERENCIAR = "assistente:conexao:manage";

    private final ConexaoIaRepository conexoes;
    private final CredencialConexaoIaRepository credenciais;
    private final ConversaAssistenteRepository conversas;
    private final MensagemAssistenteRepository mensagens;
    private final UsuarioApi usuarios;
    private final McpCatalogo catalogo;
    private final AssistantSecurityContextFactory seguranca;

    public ConexaoIaService(ConexaoIaRepository conexoes, CredencialConexaoIaRepository credenciais,
            ConversaAssistenteRepository conversas, MensagemAssistenteRepository mensagens, UsuarioApi usuarios,
            McpCatalogo catalogo, AssistantSecurityContextFactory seguranca) {
        this.conexoes = conexoes;
        this.credenciais = credenciais;
        this.conversas = conversas;
        this.mensagens = mensagens;
        this.usuarios = usuarios;
        this.catalogo = catalogo;
        this.seguranca = seguranca;
    }

    public ConexaoIaInfoResponse info(String urlServidor) {
        SemanticQueryContext contexto = seguranca.current(usuarioLogado());
        List<AgentTool> todas = catalogo.disponiveis(contexto, true);
        int escrita = (int) todas.stream().filter(McpCatalogo::escreve).count();
        return new ConexaoIaInfoResponse(urlServidor, escrita > 0, todas.size(), escrita);
    }

    @Transactional(readOnly = true)
    public List<ConexaoIaResponse> listarMinhas() {
        OffsetDateTime agora = OffsetDateTime.now();
        return conexoes.findByUsuarioIdAndAtivoTrueOrderByDataCriacaoDesc(usuarioLogado()).stream()
                .map(conexao -> resposta(conexao, agora, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<ConexaoIaResponse> listarTodas(int pagina, int tamanho) {
        Page<ConexaoIa> page = conexoes.findByAtivoTrue(PageRequest.of(Math.max(0, pagina),
                Math.min(100, Math.max(1, tamanho)), Sort.by(Sort.Direction.DESC, "dataCriacao")));
        Map<UUID, String> logins = usuarios.buscarLoginsPorIds(page.getContent().stream().map(ConexaoIa::getUsuarioId)
                .distinct().toList());
        OffsetDateTime agora = OffsetDateTime.now();
        return PageResponse.fromPage(page.map(conexao -> resposta(conexao, agora,
                logins.getOrDefault(conexao.getUsuarioId(), "—"))));
    }

    @Transactional
    public ChaveIaCriadaResponse criarChave(CriarChaveIaRequest request) {
        UUID usuario = usuarioLogado();
        String chave = CredenciaisIa.gerar(CredenciaisIa.PREFIXO_CHAVE);
        OffsetDateTime expira = OffsetDateTime.now().plusDays(request.validadeDias());
        ConexaoIa conexao = conexoes.save(ConexaoIa.porChave(usuario, request.nome(), request.permiteEscrita(),
                CredenciaisIa.prefixoVisivel(chave), expira));
        credenciais.save(new CredencialConexaoIa(conexao.getId(), TipoCredencialIa.CHAVE, CredenciaisIa.hash(chave), expira));
        return new ChaveIaCriadaResponse(resposta(conexao, OffsetDateTime.now(), null), chave);
    }

    /** Desconecta: todas as credenciais da conexao param de valer na requisicao seguinte. */
    @Transactional
    public ConexaoIaResponse desconectar(UUID id) {
        UUID usuario = usuarioLogado();
        ConexaoIa conexao = conexoes.findForUpdate(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Conexão não encontrada."));
        if (!conexao.pertenceA(usuario) && !SecurityUtils.possuiAutoridade(PERMISSAO_GERENCIAR)) {
            throw new AccessDeniedException("Esta conexão é de outra pessoa.");
        }
        conexao.revogar(usuario);
        return resposta(conexao, OffsetDateTime.now(), null);
    }

    /**
     * Conversa tecnica da conexao (resultados r1, r2... e auditoria das ferramentas), criada no primeiro
     * uso. Fica fora do historico do Copiloto.
     */
    @Transactional
    public ConexaoIa garantirConversa(UUID conexaoId) {
        ConexaoIa conexao = conexoes.findForUpdate(conexaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Conexão não encontrada."));
        if (conexao.temConversa()) return conexao;
        ConversaAssistente conversa = conversas.save(ConversaAssistente.deConexao(conexao.getUsuarioId(), conexao.getNome()));
        MensagemAssistente mensagem = mensagens.save(MensagemAssistente.resposta(conversa, UUID.randomUUID(),
                StatusMensagemAssistente.CONCLUIDA, "Chamadas da conexão " + conexao.getNome() + ".", "[]", "[]"));
        conexao.associarConversa(conversa.getId(), mensagem.getId());
        return conexao;
    }

    @Transactional
    public void registrarChamada(UUID conexaoId) {
        conexoes.contarChamada(conexaoId, OffsetDateTime.now());
    }

    /** Nome que o aplicativo informa ao conectar (ex.: "claude-ai", "Cursor"), para a pessoa reconhecer a conexao. */
    @Transactional
    public void registrarAplicativo(UUID conexaoId, String nome, String versao) {
        if (nome == null || nome.isBlank()) return;
        String texto = nome.strip() + (versao == null || versao.isBlank() ? "" : " " + versao.strip());
        conexoes.registrarAplicativo(conexaoId, texto.length() <= 120 ? texto : texto.substring(0, 120));
    }

    private ConexaoIaResponse resposta(ConexaoIa conexao, OffsetDateTime agora, String login) {
        String situacao = conexao.isRevogada() ? "DESCONECTADA" : conexao.isVencida(agora) ? "VENCIDA" : "CONECTADA";
        return new ConexaoIaResponse(conexao.getId(), conexao.getNome(), conexao.getTipo(), conexao.isPermiteEscrita(),
                situacao, conexao.getPrefixoChave(), conexao.getAplicativo(), conexao.getDataCriacao(),
                conexao.getUltimoUsoEm(), conexao.getExpiraEm(), conexao.getRevogadaEm(), conexao.getTotalChamadas(),
                login == null ? null : conexao.getUsuarioId(), login);
    }

    private static UUID usuarioLogado() {
        return Objects.requireNonNull(SecurityUtils.getUsuarioLogadoId(), "Usuario autenticado obrigatorio");
    }
}
