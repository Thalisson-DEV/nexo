package com.nexo.app.modules.notificacao.application.usecases;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.nexo.app.modules.notificacao.application.dto.NotificacaoResponse;
import com.nexo.app.modules.notificacao.domain.CategoriaNotificacao;
import com.nexo.app.modules.notificacao.domain.PrioridadeNotificacao;
import com.nexo.app.shared.application.notificacao.AnexoNotificacao;
import com.nexo.app.shared.application.notificacao.DetalhesNotificacao;
import com.nexo.app.shared.application.notificacao.FornecedorAnexosNotificacao;
import com.nexo.app.shared.application.notificacao.FornecedorDetalhesNotificacao;
import com.nexo.app.shared.infrastructure.email.EmailProperties;
import com.nexo.app.shared.infrastructure.email.MensagemEmail;
import com.nexo.app.shared.infrastructure.email.MontadorEmailHtml;
import com.nexo.app.shared.infrastructure.storage.ObjectStorageService;
import com.nexo.app.shared.infrastructure.storage.StorageObject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Cobre a tradução de uma notificação em e-mail: o que vai para o assunto, quando a
 * prioridade é anunciada e o que da ficha chega a quem lê.
 *
 * <p>Sem armazenamento nem fornecedores de anexo — este teste é sobre o texto. Os
 * anexos têm o seu próprio caminho, e misturá-los aqui esconderia a regra que importa.</p>
 */
class MontadorEmailNotificacaoTest {

    private MontadorEmailNotificacao montador;
    private EmailProperties propriedades;

    @BeforeEach
    void preparar() {
        propriedades = new EmailProperties();
        propriedades.setUrlBase("https://app.exemplo.com.br");
        montador = new MontadorEmailNotificacao(
                propriedades, new MontadorEmailHtml(propriedades), null, List.of(), List.of());
    }

    private NotificacaoResponse notificacao(PrioridadeNotificacao prioridade,
                                            Map<String, Object> contexto) {
        return new NotificacaoResponse(
                null, null, null,
                CategoriaNotificacao.APROVACAO, "Aprovações",
                prioridade, prioridade.getRotulo(),
                "Desconto aguardando validação",
                "Um desconto por Avaria em 3x aguarda validação.",
                "/dp/descontos/abc-123",
                contexto, false, null, null, null);
    }

    private NotificacaoResponse notificacaoComReferencia(UUID idReferencia) {
        return new NotificacaoResponse(
                UUID.randomUUID(), null, idReferencia,
                CategoriaNotificacao.APROVACAO, "Aprovações",
                PrioridadeNotificacao.NORMAL, "Normal",
                "Compra aguardando aprovação", "Material elétrico", null,
                Map.of(), false, null, null, null);
    }

    @Test
    void oAssuntoCarregaOPrefixoDoSistema() {
        MensagemEmail mensagem = montador.montar(
                "ana@exemplo.com.br", notificacao(PrioridadeNotificacao.ALTA, Map.of()), null);

        assertThat(mensagem.assunto()).isEqualTo("[Nexo] Desconto aguardando validação");
    }

    @Test
    void suportaMultiplosDestinatariosNoEmail() {
        Set<String> destinatarios = Set.of("ana@exemplo.com.br", "bruno@exemplo.com.br", "claudio@exemplo.com.br");
        MensagemEmail mensagem = montador.montar(
                destinatarios, notificacao(PrioridadeNotificacao.NORMAL, Map.of()), null);

        assertThat(mensagem.destinatarios())
                .containsExactlyInAnyOrder("ana@exemplo.com.br", "bruno@exemplo.com.br", "claudio@exemplo.com.br");
    }

    @Test
    void aPrioridadeSoEAnunciadaQuandoMudaOQueSeEsperaDeQuemLe() {
        // Rotina não se anuncia: "prioridade normal" em todo aviso gasta a palavra e
        // tira peso de quando ela finalmente diz "crítica".
        String rotina = montador.montar("ana@exemplo.com.br",
                notificacao(PrioridadeNotificacao.NORMAL, Map.of()), null).html();
        assertThat(rotina).doesNotContain("Prioridade");

        String urgente = montador.montar("ana@exemplo.com.br",
                notificacao(PrioridadeNotificacao.CRITICA, Map.of()), null).html();
        assertThat(urgente).contains("Prioridade crítica");
    }

    @Test
    void aFichaDescartaIdentificadoresQueNaoDizemNadaAQuemLe() {
        String html = montador.montar("ana@exemplo.com.br",
                notificacao(PrioridadeNotificacao.ALTA, Map.of(
                        "motivo", "Avaria",
                        "idDesconto", "9f1c2e77-0b2a-4a1d-9a1e-8a4d2b0f7c31",
                        "limiar", "5")),
                null).html();

        assertThat(html).contains("Avaria");
        assertThat(html).doesNotContain("9f1c2e77");
        assertThat(html).doesNotContain("Limiar");
    }

    @Test
    void oBotaoDizOQueSeEsperaDaPessoa() {
        String html = montador.montar("ana@exemplo.com.br",
                notificacao(PrioridadeNotificacao.ALTA, Map.of()), null).html();

        assertThat(html).contains("Analisar e decidir");
        assertThat(html).contains("https://app.exemplo.com.br/dp/descontos/abc-123");
    }

    @Test
    void oCorpoViraUmParagrafoPorLinha() {
        NotificacaoResponse duasLinhas = new NotificacaoResponse(
                null, null, null, CategoriaNotificacao.APROVACAO, "Aprovações",
                PrioridadeNotificacao.NORMAL, "Normal",
                "Solicitação nº 118 rejeitada",
                "Renovação de licenças\nMotivo informado: orçamento comprometido.",
                null, Map.of(), false, null, null, null);

        String html = montador.montar("ana@exemplo.com.br", duasLinhas, null).html();

        assertThat(html).contains("Renovação de licenças</p>");
        assertThat(html).contains("Motivo informado: orçamento comprometido.</p>");
    }

    @Test
    void anexaOQueConsegueBaixarEMantemOEmailQuandoOutroDownloadFalha() {
        UUID idReferencia = UUID.randomUUID();
        FornecedorAnexosNotificacao fornecedor = mock(FornecedorAnexosNotificacao.class);
        ObjectStorageService storage = mock(ObjectStorageService.class);
        when(fornecedor.atende("financeiro.solicitacao_submetida")).thenReturn(true);
        when(fornecedor.anexosDe(idReferencia)).thenReturn(List.of(
                new AnexoNotificacao("orcamento.pdf", "application/pdf", "ok", 3),
                new AnexoNotificacao("indisponivel.pdf", "application/pdf", "falha", 4)));
        when(storage.baixar("ok")).thenReturn(new StorageObject(
                new ByteArrayInputStream(new byte[]{1, 2, 3}), "application/pdf", 3));
        when(storage.baixar("falha")).thenThrow(new IllegalStateException("R2 indisponível"));
        EmailProperties propriedades = new EmailProperties();
        MontadorEmailNotificacao comAnexos = new MontadorEmailNotificacao(
                propriedades, new MontadorEmailHtml(propriedades), storage, List.of(fornecedor), List.of());

        MensagemEmail mensagem = comAnexos.montar(
                "diretor@exemplo.com.br", notificacaoComReferencia(idReferencia),
                "financeiro.solicitacao_submetida");

        assertThat(mensagem.anexos()).singleElement()
                .satisfies(anexo -> assertThat(anexo.nomeArquivo()).isEqualTo("orcamento.pdf"));
        assertThat(mensagem.html()).contains("indisponivel.pdf");
    }

    @Test
    void falhaAoConsultarFornecedorNaoInterrompeAMontagemDoEmail() {
        UUID idReferencia = UUID.randomUUID();
        FornecedorAnexosNotificacao fornecedor = mock(FornecedorAnexosNotificacao.class);
        when(fornecedor.atende("financeiro.solicitacao_submetida")).thenReturn(true);
        when(fornecedor.anexosDe(idReferencia)).thenThrow(new IllegalStateException("banco indisponível"));
        EmailProperties propriedades = new EmailProperties();
        MontadorEmailNotificacao comFalha = new MontadorEmailNotificacao(
                propriedades, new MontadorEmailHtml(propriedades), mock(ObjectStorageService.class),
                List.of(fornecedor), List.of());

        MensagemEmail mensagem = comFalha.montar(
                "diretor@exemplo.com.br", notificacaoComReferencia(idReferencia),
                "financeiro.solicitacao_submetida");

        assertThat(mensagem.assunto()).contains("Compra aguardando aprovação");
        assertThat(mensagem.anexos()).isEmpty();
    }

    @Test
    void usaAFichaEOsBotoesDeDecisaoDoModuloDeOrigem() {
        UUID idReferencia = UUID.randomUUID();
        FornecedorDetalhesNotificacao fornecedor = mock(FornecedorDetalhesNotificacao.class);
        when(fornecedor.atende("financeiro.solicitacao_submetida")).thenReturn(true);
        when(fornecedor.detalhesDe("financeiro.solicitacao_submetida", idReferencia)).thenReturn(Optional.of(
                new DetalhesNotificacao(
                        List.of(new DetalhesNotificacao.Campo("Fornecedor", "HC PNEUS"),
                                new DetalhesNotificacao.Campo("Valor solicitado", "R$ 123,00")),
                        List.of(new DetalhesNotificacao.Acao("Aprovar", "/financeiro/compras/x?acao=aprovar",
                                        DetalhesNotificacao.Estilo.POSITIVA),
                                new DetalhesNotificacao.Acao("Rejeitar", "/financeiro/compras/x?acao=rejeitar",
                                        DetalhesNotificacao.Estilo.NEGATIVA)))));
        MontadorEmailNotificacao comDetalhes = new MontadorEmailNotificacao(
                propriedades, new MontadorEmailHtml(propriedades), null, List.of(), List.of(fornecedor));

        MensagemEmail mensagem = comDetalhes.montar("ana@exemplo.com.br",
                comDeeplink(notificacaoComReferencia(idReferencia)), "financeiro.solicitacao_submetida");

        assertThat(mensagem.html())
                .contains("HC PNEUS", "R$ 123,00", "Decida por aqui",
                        "https://app.exemplo.com.br/financeiro/compras/x?acao=aprovar",
                        "https://app.exemplo.com.br/financeiro/compras/x?acao=rejeitar",
                        "Abrir o relatório completo")
                .doesNotContain("Analisar e decidir");
        assertThat(mensagem.texto()).contains("Aprovar:", "Rejeitar:");
    }

    @Test
    void semDetalhesOuComFalhaOEmailSaiComAFichaGenerica() {
        UUID idReferencia = UUID.randomUUID();
        FornecedorDetalhesNotificacao fornecedor = mock(FornecedorDetalhesNotificacao.class);
        when(fornecedor.atende("desconto.solicitado")).thenReturn(true);
        when(fornecedor.detalhesDe("desconto.solicitado", idReferencia)).thenThrow(new IllegalStateException("banco fora"));
        MontadorEmailNotificacao comFalha = new MontadorEmailNotificacao(
                propriedades, new MontadorEmailHtml(propriedades), null, List.of(), List.of(fornecedor));

        String html = comFalha.montar("ana@exemplo.com.br",
                comDeeplink(notificacaoComReferencia(idReferencia)), "desconto.solicitado").html();

        assertThat(html).contains("Analisar e decidir").doesNotContain("Decida por aqui");
    }

    private NotificacaoResponse comDeeplink(NotificacaoResponse base) {
        return new NotificacaoResponse(
                base.id(), base.origem(), base.idReferencia(), base.categoria(), base.categoriaRotulo(),
                base.prioridade(), base.prioridadeRotulo(), base.titulo(), base.corpo(),
                "/financeiro/compras/x", base.contexto(), base.lida(), base.lidaEm(), base.expiraEm(), base.dataCriacao());
    }
}
