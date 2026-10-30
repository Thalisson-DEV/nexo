package com.nexo.app.modules.financeiro.application.usecases;

import com.nexo.app.modules.financeiro.application.dto.AprovacaoRequest;
import com.nexo.app.modules.financeiro.application.dto.DecisaoEmailRequest;
import com.nexo.app.modules.financeiro.application.dto.DecisaoEmailResponse;
import com.nexo.app.modules.financeiro.application.dto.DecisaoEmailResponse.CampoFicha;
import com.nexo.app.modules.financeiro.application.dto.DecisaoEmailResponse.Situacao;
import com.nexo.app.modules.financeiro.domain.DecisaoPorEmail;
import com.nexo.app.modules.financeiro.domain.SolicitacaoBase;
import com.nexo.app.modules.financeiro.domain.StatusSolicitacao;
import com.nexo.app.modules.financeiro.domain.TipoSolicitacao;
import com.nexo.app.modules.financeiro.infrastructure.persistence.DecisaoPorEmailRepository;
import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

/**
 * Decisão de compras e pagamentos pelo link do e-mail, sem login.
 *
 * <p>O link é a credencial: 256 bits aleatórios, enviados a um aprovador só, válidos
 * por uma decisão e por {@link DecisaoPorEmail#VALIDADE}. Ele prova quem recebeu o
 * e-mail; o resto é conferido no momento do uso, como seria logado — a solicitação
 * ainda espera decisão, o usuário continua ativo e com alçada.</p>
 *
 * <p>A decisão roda pelos mesmos casos de uso da tela, autenticada como o dono do
 * link. Histórico, auditoria, conferência dos documentos e avisos saem iguais; para o
 * resto do sistema, foi aquele diretor que decidiu.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DecisaoPorEmailUseCases {

    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final int BYTES_TOKEN = 32;

    private final DecisaoPorEmailRepository repository;
    private final FichaSolicitacaoFinanceira ficha;
    private final SolicitacaoCompraUseCases compraUseCases;
    private final SolicitacaoPagamentoUseCases pagamentoUseCases;
    private final UsuarioApi usuarioApi;
    private final ColaboradorApi colaboradorApi;

    // ── Emissão (envio do e-mail) ─────────────────────────────────────────────────

    /** Quem recebe o aviso só ganha botões se puder, de fato, decidir. */
    boolean podeDecidir(UUID idUsuario, TipoSolicitacao tipo) {
        return idUsuario != null && usuarioApi.buscarPermissoes(idUsuario).contains(permissaoAprovar(tipo));
    }

    /** Gera o link de um destinatário. Devolve o token cru, que só existe dentro do e-mail. */
    @Transactional
    String emitir(SolicitacaoBase solicitacao, UUID idUsuario) {
        byte[] bytes = new byte[BYTES_TOKEN];
        ALEATORIO.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repository.save(new DecisaoPorEmail(hash(token), solicitacao, idUsuario, OffsetDateTime.now()));
        return token;
    }

    // ── Página do link ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public DecisaoEmailResponse consultar(String token) {
        DecisaoPorEmail link = buscarLink(token);
        return responder(link, buscarSolicitacao(link), OffsetDateTime.now());
    }

    @Transactional
    public DecisaoEmailResponse decidir(String token, DecisaoEmailRequest request) {
        DecisaoPorEmail link = buscarLink(token);
        SolicitacaoBase solicitacao = buscarSolicitacao(link);
        OffsetDateTime agora = OffsetDateTime.now();

        Situacao situacao = situacao(link, solicitacao, agora);
        if (situacao != Situacao.DISPONIVEL) {
            throw new InvarianteVioladaException(mensagem(situacao, link, solicitacao));
        }

        Set<String> permissoes = usuarioApi.buscarPermissoes(link.getIdUsuario());
        comoDonoDoLink(link.getIdUsuario(), permissoes, () -> aplicar(link, request));
        link.usar(request.acao(), agora);
        repository.save(link);

        log.info("Solicitação {} decidida pelo link do e-mail ({}) pelo usuário {}.",
                solicitacao.getId(), request.acao(), link.getIdUsuario());
        return responder(link, buscarSolicitacao(link), agora);
    }

    private void aplicar(DecisaoPorEmail link, DecisaoEmailRequest request) {
        UUID id = link.getIdSolicitacao();
        UUID usuario = link.getIdUsuario();
        boolean compra = link.getTipoSolicitacao() == TipoSolicitacao.COMPRA;
        String texto = StringUtils.hasText(request.texto()) ? request.texto().trim() : null;
        switch (request.acao()) {
            case APROVAR -> {
                var aprovacao = new AprovacaoRequest(request.valorAprovado(), texto);
                if (compra) {
                    compraUseCases.aprovar(id, aprovacao, usuario);
                } else {
                    pagamentoUseCases.aprovar(id, aprovacao, usuario);
                }
            }
            case REJEITAR -> {
                if (compra) {
                    compraUseCases.rejeitar(id, texto, usuario);
                } else {
                    pagamentoUseCases.rejeitar(id, texto, usuario);
                }
            }
        }
    }

    // ── Situação e resposta ─────────────────────────────────────────────────────────

    private DecisaoEmailResponse responder(DecisaoPorEmail link, SolicitacaoBase solicitacao, OffsetDateTime agora) {
        Situacao situacao = situacao(link, solicitacao, agora);
        return new DecisaoEmailResponse(
                situacao,
                mensagem(situacao, link, solicitacao),
                FichaSolicitacaoFinanceira.titulo(solicitacao),
                FichaSolicitacaoFinanceira.situacao(solicitacao.getStatus()),
                solicitacao.getNucleo().getValor(),
                nome(link.getIdUsuario()),
                link.getExpiraEm(),
                link.getAcao(),
                ficha.montar(solicitacao).stream().map(c -> new CampoFicha(c.rotulo(), c.valor())).toList());
    }

    private Situacao situacao(DecisaoPorEmail link, SolicitacaoBase solicitacao, OffsetDateTime agora) {
        if (link.usado()) {
            return Situacao.DECIDIDA_POR_ESTE_LINK;
        }
        if (solicitacao.getStatus() != StatusSolicitacao.SOLICITADO) {
            return Situacao.JA_DECIDIDA;
        }
        if (link.expirado(agora)) {
            return Situacao.EXPIRADO;
        }
        if (!podeDecidir(link.getIdUsuario(), link.getTipoSolicitacao())) {
            return Situacao.SEM_PERMISSAO;
        }
        return Situacao.DISPONIVEL;
    }

    private static String mensagem(Situacao situacao, DecisaoPorEmail link, SolicitacaoBase solicitacao) {
        return switch (situacao) {
            case DISPONIVEL -> "Confira os dados e confirme a sua decisão.";
            case DECIDIDA_POR_ESTE_LINK -> (link.getAcao() == DecisaoPorEmail.Acao.APROVAR ? "Aprovação" : "Rejeição")
                    + " registrada por este link. Não há mais nada a fazer aqui.";
            case JA_DECIDIDA -> "Esta solicitação já está " + FichaSolicitacaoFinanceira
                    .situacao(solicitacao.getStatus()).toLowerCase() + ". Não há decisão pendente.";
            case EXPIRADO -> "Este link expirou. Para decidir, entre no Nexo.";
            case SEM_PERMISSAO -> "Seu perfil não tem mais alçada para decidir esta solicitação.";
        };
    }

    // ── Apoio ───────────────────────────────────────────────────────────────────────

    /**
     * Executa como o dono do link: os casos de uso, a auditoria e o histórico leem o
     * usuário do contexto de segurança, como numa requisição logada.
     */
    private void comoDonoDoLink(UUID idUsuario, Set<String> permissoes, Runnable acao) {
        SecurityContext anterior = SecurityContextHolder.getContext();
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        String login = usuarioApi.buscarNomePorId(idUsuario).orElse(idUsuario.toString());
        contexto.setAuthentication(new UsernamePasswordAuthenticationToken(
                new DonoDoLink(idUsuario, login), null,
                permissoes.stream().map(SimpleGrantedAuthority::new).toList()));
        SecurityContextHolder.setContext(contexto);
        try {
            acao.run();
        } finally {
            SecurityContextHolder.setContext(anterior);
        }
    }

    /** Principal com {@code getId()}, que é o que {@code SecurityUtils} procura, e o login como nome. */
    public record DonoDoLink(UUID id, String login) implements Principal {
        public UUID getId() {
            return id;
        }

        @Override
        public String getName() {
            return login;
        }
    }

    /** Link inexistente e link adulterado dão a mesma resposta: não se confirma o que existe. */
    private DecisaoPorEmail buscarLink(String token) {
        if (!StringUtils.hasText(token) || token.length() > 100) {
            throw new RecursoNaoEncontradoException("Link de decisão inválido.");
        }
        return repository.findByHashToken(hash(token.trim()))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Link de decisão inválido."));
    }

    private SolicitacaoBase buscarSolicitacao(DecisaoPorEmail link) {
        return ficha.buscar(link.getIdSolicitacao())
                .orElseThrow(() -> new RecursoNaoEncontradoException("A solicitação deste link não existe mais."));
    }

    private String nome(UUID idUsuario) {
        return colaboradorApi.buscarNomeCompletoPorUsuario(idUsuario)
                .or(() -> usuarioApi.buscarNomePorId(idUsuario))
                .orElse(null);
    }

    private static String permissaoAprovar(TipoSolicitacao tipo) {
        return tipo == TipoSolicitacao.COMPRA ? "financeiro:compra:aprovar" : "financeiro:pagamento:aprovar";
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
