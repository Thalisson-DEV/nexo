package com.nexo.app.modules.automacao.application.nos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.nexo.app.modules.automacao.application.usecases.ResolvedorDestinatarios;
import com.nexo.app.modules.automacao.infrastructure.persistence.ArquivoAutomacaoRepository;
import com.nexo.app.modules.automacao.infrastructure.persistence.SilencioAutomacaoRepository;
import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.notificacao.NotificacaoApi;
import com.nexo.app.shared.infrastructure.email.EmailProperties;
import com.nexo.app.shared.infrastructure.email.MontadorEmailHtml;
import com.nexo.app.shared.infrastructure.email.ServicoEmail;

class PreviaEmailTest {

    private final NosAvisos avisos;

    PreviaEmailTest() {
        EmailProperties propriedades = new EmailProperties();
        propriedades.setUrlBase("https://app.exemplo.com.br");
        avisos = new NosAvisos(mock(ResolvedorDestinatarios.class), mock(ServicoEmail.class), new MontadorEmailHtml(propriedades),
                propriedades, mock(NotificacaoApi.class), mock(UsuarioApi.class), mock(ArquivoAutomacaoRepository.class),
                mock(SilencioAutomacaoRepository.class));
    }

    @Test
    void previaUsaOLayoutInstitucionalComALogoDaEmpresa() {
        NosAvisos.Previa previa = avisos.previa(Map.of(
                "subject", "Comercial {{ item }} — semana",
                "label", "Comercial",
                "body", "<p>Olá, <span data-expr=\"trigger.outputs.colaborador.nome\"></span>!</p>"
                        + "<div data-block=\"table\" data-source=\"steps.q.outputs.rows\"></div>"
                        + "<div data-block=\"button\" data-source=\"run.link\" data-label=\"Abrir relatório\"></div>",
                "button_link", "/notificacoes/automacoes",
                "attachments", "{{ steps.analise.outputs.files }}"),
                Map.of("item", "Para cada › Item", "trigger.outputs.colaborador.nome", "Colaborador › Nome",
                        "steps.q.outputs.rows", "Execuções › Linhas"));

        assertThat(previa.assunto()).isEqualTo("Comercial [Para cada › Item] — semana");
        assertThat(previa.html())
                .contains("https://app.exemplo.com.br/assets/logo-email.png")
                .contains("Nexo")
                .contains("[Colaborador › Nome]")
                .contains("Execuções › Linhas")
                .contains("Abrir relatório")
                .contains("relatorio.pdf (exemplo)")
                .doesNotContain("data-expr")
                .doesNotContain("{{");
    }

    @Test
    void mensagemVaziaAvisaEmVezDeMostrarCartaoEmBranco() {
        NosAvisos.Previa previa = avisos.previa(Map.of("subject", "", "body", "<p></p>"), Map.of());
        assertThat(previa.html()).contains("A mensagem ainda está vazia").contains("Assunto do e-mail");
        assertThat(List.of(previa.assunto())).containsExactly("");
    }
}
