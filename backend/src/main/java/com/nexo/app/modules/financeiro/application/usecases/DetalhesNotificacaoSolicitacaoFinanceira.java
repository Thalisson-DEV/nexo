package com.nexo.app.modules.financeiro.application.usecases;

import com.nexo.app.modules.financeiro.domain.SolicitacaoBase;
import com.nexo.app.modules.financeiro.domain.StatusSolicitacao;
import com.nexo.app.shared.application.notificacao.DetalhesNotificacao;
import com.nexo.app.shared.application.notificacao.DetalhesNotificacao.Acao;
import com.nexo.app.shared.application.notificacao.DetalhesNotificacao.Estilo;
import com.nexo.app.shared.application.notificacao.FornecedorDetalhesNotificacao;
import com.nexo.app.shared.domain.events.SolicitacaoFinanceiraSubmetidaEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A ficha das compras e pagamentos nos e-mails, e os botões de decisão.
 *
 * <p>Quem recebe o aviso de "aguardando aprovação" decide pela própria mensagem: a
 * solicitação inteira vem na ficha, e Aprovar e Rejeitar levam a uma página de
 * confirmação que não pede login. Cada aprovador recebe links só dele
 * ({@link DecisaoPorEmailUseCases}), por isso este aviso sai um e-mail por pessoa.</p>
 *
 * <p>Os botões só aparecem para quem tem alçada e enquanto a solicitação ainda espera
 * decisão. Os demais avisos do financeiro (aprovada, rejeitada, decisão desfeita) levam
 * só a ficha.</p>
 */
@Component
@RequiredArgsConstructor
class DetalhesNotificacaoSolicitacaoFinanceira implements FornecedorDetalhesNotificacao {

    private static final String NOTA_LINKS = "Os botões decidem sem entrar no Nexo, depois de uma confirmação. "
            + "O link é pessoal: vale para uma decisão, em seu nome, por 7 dias. Não encaminhe este e-mail.";

    private final FichaSolicitacaoFinanceira ficha;
    private final DecisaoPorEmailUseCases decisaoPorEmail;

    @Override
    public boolean atende(String chaveGatilho) {
        return chaveGatilho != null && chaveGatilho.startsWith("financeiro.")
                && !chaveGatilho.equals("financeiro.solicitacoes_pendentes");
    }

    @Override
    public boolean acoesPessoais(String chaveGatilho) {
        return SolicitacaoFinanceiraSubmetidaEvent.CHAVE_GATILHO.equals(chaveGatilho);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DetalhesNotificacao> detalhesDe(String chaveGatilho, UUID idSolicitacao) {
        return ficha.buscar(idSolicitacao).map(s -> new DetalhesNotificacao(ficha.montar(s), List.of()));
    }

    /** Escreve: gera o link de decisão do destinatário. */
    @Override
    @Transactional
    public Optional<DetalhesNotificacao> detalhesPara(String chaveGatilho, UUID idSolicitacao, UUID idDestinatario) {
        return ficha.buscar(idSolicitacao).map(s -> new DetalhesNotificacao(
                ficha.montar(s), acoes(chaveGatilho, s, idDestinatario), NOTA_LINKS));
    }

    private List<Acao> acoes(String chaveGatilho, SolicitacaoBase solicitacao, UUID idDestinatario) {
        if (!acoesPessoais(chaveGatilho)
                || solicitacao.getStatus() != StatusSolicitacao.SOLICITADO
                || !decisaoPorEmail.podeDecidir(idDestinatario, solicitacao.getTipoSolicitacao())) {
            return List.of();
        }
        String caminho = "/decisao/" + decisaoPorEmail.emitir(solicitacao, idDestinatario);
        return List.of(
                new Acao("Aprovar", caminho + "?acao=aprovar", Estilo.POSITIVA),
                new Acao("Rejeitar", caminho + "?acao=rejeitar", Estilo.NEGATIVA));
    }
}
