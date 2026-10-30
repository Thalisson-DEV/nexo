package com.nexo.app.modules.financeiro.application.usecases;

import com.nexo.app.modules.financeiro.application.dto.CampoValorResponse;
import com.nexo.app.modules.financeiro.application.dto.DocumentoResumoResponse;
import com.nexo.app.modules.financeiro.application.dto.FornecedorSolicitacaoResponse;
import com.nexo.app.modules.financeiro.application.dto.SolicitacaoCompraResumoResponse;
import com.nexo.app.modules.financeiro.domain.CampoCanonico;
import com.nexo.app.modules.financeiro.domain.CampoFormulario;
import com.nexo.app.modules.financeiro.domain.SituacaoDocumento;
import com.nexo.app.modules.financeiro.domain.SolicitacaoBase;
import com.nexo.app.modules.financeiro.domain.SolicitacaoCompra;
import com.nexo.app.modules.financeiro.domain.SolicitacaoPagamento;
import com.nexo.app.modules.financeiro.domain.StatusSolicitacao;
import com.nexo.app.modules.financeiro.domain.TipoSolicitacao;
import com.nexo.app.modules.financeiro.infrastructure.persistence.SolicitacaoCompraRepository;
import com.nexo.app.modules.financeiro.infrastructure.persistence.SolicitacaoPagamentoRepository;
import com.nexo.app.shared.application.notificacao.DetalhesNotificacao.Campo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A solicitação inteira em pares rótulo/valor, na ordem de quem decide: quem pede,
 * para onde, de quem, quanto, até quando, e depois o resto do formulário.
 *
 * <p>Serve ao e-mail e à página de decisão sem login, que precisam mostrar a mesma
 * coisa: quem aprova pelo e-mail não abre o sistema, então o que não estiver aqui ele
 * não vê.</p>
 */
@Component
@RequiredArgsConstructor
class FichaSolicitacaoFinanceira {

    private static final Locale BRASIL = Locale.of("pt", "BR");
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");
    private static final int LIMITE_TEXTO = 400;

    private static final Map<StatusSolicitacao, String> SITUACAO = Map.of(
            StatusSolicitacao.RASCUNHO, "Rascunho",
            StatusSolicitacao.SOLICITADO, "Aguardando aprovação",
            StatusSolicitacao.APROVADO, "Aprovada",
            StatusSolicitacao.REJEITADO, "Rejeitada",
            StatusSolicitacao.CANCELADO, "Cancelada");

    /** Os campos já mostrados na parte de cima; não se repetem no resto do formulário. */
    private static final Set<CampoCanonico> EM_DESTAQUE = EnumSet.of(
            CampoCanonico.SOLICITANTE_COLABORADOR, CampoCanonico.BASE, CampoCanonico.PROCESSO,
            CampoCanonico.OBRA, CampoCanonico.VEICULO, CampoCanonico.FORNECEDOR,
            CampoCanonico.FORNECEDOR_NOME, CampoCanonico.FORNECEDOR_RAZAO_SOCIAL, CampoCanonico.VALOR,
            CampoCanonico.FORMA_PAGAMENTO, CampoCanonico.VENCIMENTO, CampoCanonico.QUANTIDADE_PARCELAS,
            CampoCanonico.TIPO_DESPESA, CampoCanonico.PRIORIDADE, CampoCanonico.TEM_NF,
            CampoCanonico.NUMERO_NF, CampoCanonico.DESCRICAO, CampoCanonico.SOLICITACAO_COMPRA,
            CampoCanonico.POSSUI_COMPRA_PREVIA);

    /**
     * Fica fora do e-mail e da página sem login: os dados bancários são cifrados no
     * banco, e um e-mail encaminhado não pode levar a conta de quem recebe.
     */
    private static final Set<CampoCanonico> RESERVADOS = EnumSet.of(CampoCanonico.DADOS_PAGAMENTO);

    private final SolicitacaoCompraRepository compraRepository;
    private final SolicitacaoPagamentoRepository pagamentoRepository;
    private final MontadorSolicitacao montador;

    Optional<SolicitacaoBase> buscar(UUID idSolicitacao) {
        return compraRepository.findById(idSolicitacao)
                .map(SolicitacaoBase.class::cast)
                .or(() -> pagamentoRepository.findById(idSolicitacao));
    }

    static String situacao(StatusSolicitacao status) {
        return SITUACAO.get(status);
    }

    static String titulo(SolicitacaoBase solicitacao) {
        return (solicitacao.getTipoSolicitacao() == TipoSolicitacao.COMPRA ? "Compra" : "Pagamento")
                + " nº " + solicitacao.getNumero();
    }

    static String caminho(SolicitacaoBase solicitacao) {
        return (solicitacao.getTipoSolicitacao() == TipoSolicitacao.COMPRA
                ? "/financeiro/compras/" : "/financeiro/pagamentos/") + solicitacao.getId();
    }

    /** O que a ficha usa da resposta montada, igual para compra e pagamento. */
    private record Montada(Map<String, CampoValorResponse> valores, FornecedorSolicitacaoResponse fornecedor,
                           List<DocumentoResumoResponse> documentos, String abertaPor,
                           SolicitacaoCompraResumoResponse compraOrigem) {
    }

    List<Campo> montar(SolicitacaoBase solicitacao) {
        Montada montada = montarResposta(solicitacao);
        Map<String, CampoValorResponse> valores = montada.valores();
        String abertaPor = montada.abertaPor();

        List<Campo> ficha = new ArrayList<>();
        ficha.add(new Campo("Solicitação", titulo(solicitacao)));
        ficha.add(new Campo("Situação", situacao(solicitacao.getStatus())));

        String solicitante = exibicao(solicitacao, valores, CampoCanonico.SOLICITANTE_COLABORADOR);
        ficha.add(new Campo("Solicitante", primeiro(solicitante, abertaPor)));
        if (solicitante != null && abertaPor != null && !solicitante.equalsIgnoreCase(abertaPor)) {
            ficha.add(new Campo("Aberta por", abertaPor));
        }
        adicionar(ficha, "Base operacional", exibicao(solicitacao, valores, CampoCanonico.BASE));
        adicionar(ficha, "Setor / centro de custo", exibicao(solicitacao, valores, CampoCanonico.PROCESSO));
        adicionar(ficha, "Obra / contrato", exibicao(solicitacao, valores, CampoCanonico.OBRA));
        adicionar(ficha, "Veículo", exibicao(solicitacao, valores, CampoCanonico.VEICULO));

        FornecedorSolicitacaoResponse fornecedor = montada.fornecedor();
        if (fornecedor != null) {
            ficha.add(new Campo("Fornecedor", fornecedor.nome() + (fornecedor.cadastrado() ? "" : " (não cadastrado)")));
        }
        ficha.add(new Campo("Valor solicitado", moeda(solicitacao.getNucleo().getValor())));
        if (solicitacao.getValorAprovado() != null) {
            ficha.add(new Campo("Valor autorizado", moeda(solicitacao.getValorAprovado())));
        }
        adicionar(ficha, "Forma de pagamento", exibicao(solicitacao, valores, CampoCanonico.FORMA_PAGAMENTO));
        adicionar(ficha, "Vencimento", exibicao(solicitacao, valores, CampoCanonico.VENCIMENTO));
        adicionar(ficha, "Parcelas", exibicao(solicitacao, valores, CampoCanonico.QUANTIDADE_PARCELAS));
        adicionar(ficha, "Tipo de despesa", exibicao(solicitacao, valores, CampoCanonico.TIPO_DESPESA));
        adicionar(ficha, "Prioridade", exibicao(solicitacao, valores, CampoCanonico.PRIORIDADE));
        adicionar(ficha, "Nota fiscal", notaFiscal(solicitacao));

        if (montada.compraOrigem() != null) {
            SolicitacaoCompraResumoResponse origem = montada.compraOrigem();
            ficha.add(new Campo("Compra de origem", "Compra nº " + origem.numero()
                    + (origem.valorAprovado() == null ? "" : " · autorizado " + moeda(origem.valorAprovado()))));
        }
        adicionar(ficha, "Descrição", resumir(exibicao(solicitacao, valores, CampoCanonico.DESCRICAO)));

        // O resto do formulário, na ordem da variante: é a solicitação inteira.
        for (CampoFormulario campo : solicitacao.getFormulario().camposOrdenados()) {
            if (campo.getCampoCanonico() == null || EM_DESTAQUE.contains(campo.getCampoCanonico())
                    || RESERVADOS.contains(campo.getCampoCanonico())) {
                continue;
            }
            CampoValorResponse valor = valores.get(campo.getChave());
            if (valor != null && StringUtils.hasText(valor.exibicao())) {
                ficha.add(new Campo(campo.getRotulo(), resumir(valor.exibicao().trim())));
            }
        }

        ficha.add(new Campo("Documentos", documentos(montada.documentos())));
        if (solicitacao.getDataSubmissao() != null) {
            ficha.add(new Campo("Enviada em", dataHora(solicitacao.getDataSubmissao())));
        }
        return ficha;
    }

    private Montada montarResposta(SolicitacaoBase solicitacao) {
        if (solicitacao instanceof SolicitacaoCompra compra) {
            var r = montador.montar(compra);
            return new Montada(r.valores(), r.fornecedor(), r.documentos(),
                    primeiro(r.nomeColaboradorSolicitante(), r.usuarioSolicitante()), null);
        }
        var r = montador.montar((SolicitacaoPagamento) solicitacao);
        return new Montada(r.valores(), r.fornecedor(), r.documentos(),
                primeiro(r.nomeColaboradorSolicitante(), r.usuarioSolicitante()), r.compraVinculada());
    }

    /** O texto legível do campo, se a variante do formulário o tiver e ele estiver preenchido. */
    private static String exibicao(SolicitacaoBase solicitacao, Map<String, CampoValorResponse> valores,
                                   CampoCanonico canonico) {
        CampoFormulario campo = solicitacao.getFormulario().buscarCampoPorCanonico(canonico).orElse(null);
        if (campo == null) {
            return null;
        }
        CampoValorResponse valor = valores.get(campo.getChave());
        return valor == null || !StringUtils.hasText(valor.exibicao()) ? null : valor.exibicao().trim();
    }

    private static String notaFiscal(SolicitacaoBase solicitacao) {
        Boolean temNf = solicitacao.getNucleo().getTemNf();
        if (temNf == null) {
            return null;
        }
        if (!temNf) {
            return "Sem nota fiscal";
        }
        String numero = solicitacao.getNucleo().getNumeroNf();
        return StringUtils.hasText(numero) ? "Sim · nº " + numero.trim() : "Sim";
    }

    private static String documentos(List<DocumentoResumoResponse> documentos) {
        if (documentos == null || documentos.isEmpty()) {
            return "Nenhum anexo";
        }
        long pendencia = documentos.stream().filter(d -> d.situacao() == SituacaoDocumento.COM_PENDENCIA).count();
        String total = documentos.size() == 1 ? "1 anexo" : documentos.size() + " anexos";
        return pendencia == 0 ? total : total + " · " + pendencia + " com pendência";
    }

    private static void adicionar(List<Campo> ficha, String rotulo, String valor) {
        if (StringUtils.hasText(valor)) {
            ficha.add(new Campo(rotulo, valor));
        }
    }

    private static String primeiro(String preferido, String alternativo) {
        return StringUtils.hasText(preferido) ? preferido : alternativo;
    }

    private static String resumir(String texto) {
        if (texto == null || texto.length() <= LIMITE_TEXTO) {
            return texto;
        }
        return texto.substring(0, LIMITE_TEXTO).stripTrailing() + "…";
    }

    static String moeda(BigDecimal valor) {
        return valor == null ? "—" : NumberFormat.getCurrencyInstance(BRASIL).format(valor);
    }

    private static String dataHora(OffsetDateTime data) {
        return data.atZoneSameInstant(FUSO).format(DATA_HORA);
    }
}
