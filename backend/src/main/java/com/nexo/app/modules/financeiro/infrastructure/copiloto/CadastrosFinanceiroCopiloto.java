package com.nexo.app.modules.financeiro.infrastructure.copiloto;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexo.app.modules.assistente.application.cadastro.CampoDescritor;
import com.nexo.app.modules.assistente.application.cadastro.Opcao;
import com.nexo.app.modules.assistente.application.cadastro.OperacaoCadastro;
import com.nexo.app.modules.assistente.application.cadastro.ProvedorCadastros;
import com.nexo.app.modules.assistente.application.cadastro.ReferenciaCadastro;
import com.nexo.app.modules.assistente.application.cadastro.TipoCampo;
import com.nexo.app.modules.financeiro.application.dto.CampoSchemaResponse;
import com.nexo.app.modules.financeiro.application.dto.CondicaoSchema;
import com.nexo.app.modules.financeiro.application.dto.FormularioResumoResponse;
import com.nexo.app.modules.financeiro.application.dto.FornecedorRequest;
import com.nexo.app.modules.financeiro.application.dto.SolicitacaoRequest;
import com.nexo.app.modules.financeiro.application.usecases.FormularioSolicitacaoUseCases;
import com.nexo.app.modules.financeiro.application.usecases.FornecedorUseCases;
import com.nexo.app.modules.financeiro.application.usecases.SolicitacaoCompraUseCases;
import com.nexo.app.modules.financeiro.application.usecases.SolicitacaoPagamentoUseCases;
import com.nexo.app.modules.financeiro.domain.FonteReferencia;
import com.nexo.app.modules.financeiro.domain.TipoSolicitacao;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * O que o Copiloto cadastra no Financeiro: solicitacoes de compra e de pagamento (com os campos do formulario
 * escolhido, que o administrador configura) e fornecedores.
 */
@Component
public class CadastrosFinanceiroCopiloto implements ProvedorCadastros {

    private static final String MODULO = "Financeiro";

    /** Solicitacao financeira: o formulario define os campos de {@code valores}. */
    public record SolicitacaoFinanceiraCadastro(
            @NotNull @Schema(description = "Formulario (variante) da solicitacao; se nao souber, use o padrao") UUID formularioId,
            @Schema(description = "Respostas do formulario, pela chave de cada campo") Map<String, Object> valores,
            @Schema(description = "Ja envia para aprovacao; sem isso fica como rascunho para anexar documentos") Boolean enviarParaAprovacao) {
    }

    private final SolicitacaoCompraUseCases compras;
    private final SolicitacaoPagamentoUseCases pagamentos;
    private final FormularioSolicitacaoUseCases formularios;
    private final FornecedorUseCases fornecedores;

    public CadastrosFinanceiroCopiloto(SolicitacaoCompraUseCases compras, SolicitacaoPagamentoUseCases pagamentos,
            FormularioSolicitacaoUseCases formularios, FornecedorUseCases fornecedores) {
        this.compras = compras;
        this.pagamentos = pagamentos;
        this.formularios = formularios;
        this.fornecedores = fornecedores;
    }

    @Override
    public List<ReferenciaCadastro> referencias() {
        return List.of(
                new ReferenciaCadastro("formulario_compra", "Tipo de solicitação de compra",
                        "select id, titulo as rotulo, codigo as detalhe from financeiro_formulario_solicitacao "
                                + "where ativo and tipo_solicitacao = 'COMPRA'"),
                new ReferenciaCadastro("formulario_pagamento", "Tipo de solicitação de pagamento",
                        "select id, titulo as rotulo, codigo as detalhe from financeiro_formulario_solicitacao "
                                + "where ativo and tipo_solicitacao = 'PAGAMENTO'"),
                new ReferenciaCadastro("fornecedor", "Fornecedor",
                        "select id, nome_fantasia as rotulo, concat_ws(' · ', razao_social, documento) as detalhe "
                                + "from financeiro_fornecedor where ativo"),
                new ReferenciaCadastro("contrato", "Contrato",
                        "select id, numero_contrato as rotulo, cliente as detalhe from contratos where ativo"),
                new ReferenciaCadastro("solicitacao_compra_aprovada", "Solicitação de compra aprovada",
                        "select id, concat('Compra nº ', numero) as rotulo, concat_ws(' · ', fornecedor_nome, descricao) as detalhe "
                                + "from financeiro_solicitacao_compra where ativo and status = 'APROVADO'"));
    }

    @Override
    public List<OperacaoCadastro<?>> operacoes() {
        return List.of(
                solicitacao("solicitacao_compra", TipoSolicitacao.COMPRA, "Nova solicitação de compra", "Solicitação de compra",
                        "financeiro:compra:write", "formulario_compra", "FINANCEIRO_COMPRA_DETALHE",
                        "Abre uma solicitacao de compra (o que comprar, valor, fornecedor, base, setor, prioridade...). "
                                + "Os campos dependem do tipo de formulario: PADRAO, FROTA (servico em veiculo) ou OBRA_CIVIL.",
                        "Abre uma compra de 2 pneus para o veiculo ABC1D23, R$ 1.800, fornecedor Pneus Bahia, urgente"),
                solicitacao("solicitacao_pagamento", TipoSolicitacao.PAGAMENTO, "Nova solicitação de pagamento",
                        "Solicitação de pagamento", "financeiro:pagamento:write", "formulario_pagamento",
                        "FINANCEIRO_PAGAMENTO_DETALHE",
                        "Abre uma solicitacao de pagamento (valor, fornecedor, vencimento, forma de pagamento, nota fiscal...).",
                        "Solicita pagamento de R$ 950 a Pneus Bahia, vencimento dia 10, boleto, nota 1234"),
                OperacaoCadastro.criar("fornecedor", FornecedorRequest.class)
                        .modulo(MODULO).titulo("Novo fornecedor").registro("Fornecedor")
                        .descricao("Cadastra um fornecedor (nome, razao social, CNPJ/CPF, dados bancarios). "
                                + "Ele fica pendente de homologacao pelo financeiro.")
                        .autoridade("financeiro:fornecedor:write")
                        .campo("nomeFantasia", c -> c.rotulo("Nome"))
                        .campo("razaoSocial", c -> c.rotulo("Razão social"))
                        .campo("documento", c -> c.rotulo("CNPJ ou CPF"))
                        .campo("dadosBancarios", c -> c.rotulo("Dados bancários").tipo(TipoCampo.TEXTO_LONGO))
                        .campo("observacoes", c -> c.rotulo("Observações").tipo(TipoCampo.TEXTO_LONGO))
                        .identificadoPor("nomeFantasia")
                        .executor((r, id, usuario) -> fornecedores.criar(r))
                        .destino("FINANCEIRO_FORNECEDORES")
                        .build());
    }

    private OperacaoCadastro<SolicitacaoFinanceiraCadastro> solicitacao(String id, TipoSolicitacao tipo, String titulo,
            String registro, String autoridade, String referenciaFormulario, String destino, String descricao, String exemplo) {
        return OperacaoCadastro.criar(id, SolicitacaoFinanceiraCadastro.class)
                .modulo(MODULO).titulo(titulo).registro(registro).descricao(descricao)
                .autoridade(autoridade)
                .campo("formularioId", c -> c.rotulo("Tipo de solicitação").referencia(referenciaFormulario)
                        .padrao(() -> formularioPadrao(tipo)))
                .campo("valores", c -> c.rotulo("Dados da solicitação"))
                .campo("enviarParaAprovacao", c -> c.rotulo("Enviar para aprovação agora").padrao(() -> Boolean.FALSE))
                .camposDinamicos("valores", dados -> camposDoFormulario(dados))
                .identificadoPor("valores.descricao", "valores.valor")
                .executor((r, registroId, usuario) -> {
                    SolicitacaoRequest request = new SolicitacaoRequest(r.formularioId(), r.valores());
                    boolean enviar = Boolean.TRUE.equals(r.enviarParaAprovacao());
                    if (tipo == TipoSolicitacao.COMPRA) {
                        UUID criada = compras.criar(request, usuario).id();
                        if (enviar) compras.submeter(criada);
                        return criada;
                    }
                    UUID criada = pagamentos.criar(request, usuario).id();
                    if (enviar) pagamentos.submeter(criada);
                    return criada;
                })
                .destinoDetalhe(destino)
                .exemplo(exemplo)
                .build();
    }

    private UUID formularioPadrao(TipoSolicitacao tipo) {
        List<FormularioResumoResponse> variantes = formularios.listarVariantes(tipo);
        return variantes.isEmpty() ? null : variantes.getFirst().id();
    }

    /** Campos do formulario escolhido, so os visiveis com o que ja foi respondido (mesma regra da tela). */
    private List<CampoDescritor> camposDoFormulario(JsonNode dados) {
        JsonNode formulario = dados == null ? null : dados.get("formularioId");
        if (formulario == null || formulario.isNull() || formulario.asText().isBlank()) return List.of();
        UUID id;
        try {
            id = UUID.fromString(formulario.asText());
        } catch (IllegalArgumentException invalido) {
            return List.of();
        }
        JsonNode valores = dados.path("valores");
        List<CampoDescritor> campos = new ArrayList<>();
        for (CampoSchemaResponse campo : formularios.buscarSchema(id).campos()) {
            if (!visivel(campo.exibirQuando(), valores)) continue;
            campos.add(descritor(campo));
        }
        return campos;
    }

    private static boolean visivel(CondicaoSchema condicao, JsonNode valores) {
        if (condicao == null || condicao.campoChave() == null) return true;
        JsonNode atual = valores == null ? null : valores.get(condicao.campoChave());
        if (atual == null || atual.isNull()) return false;
        String texto = atual.asText();
        return condicao.valores().stream().anyMatch(v -> v.equalsIgnoreCase(texto));
    }

    private static CampoDescritor descritor(CampoSchemaResponse campo) {
        String descricao = campo.ajuda();
        return switch (campo.tipoCampo()) {
            case REFERENCIA -> CampoDescritor.referencia(campo.chave(), campo.rotulo(),
                    referencia(campo.referencia() == null ? null : campo.referencia().fonte()), campo.obrigatorio(), descricao);
            case SELECAO_UNICA -> CampoDescritor.opcoes(campo.chave(), campo.rotulo(),
                    campo.opcoes().stream().map(o -> Opcao.de(o.valor(), o.rotulo())).toList(), campo.obrigatorio(), descricao);
            case TEXTO -> CampoDescritor.simples(campo.chave(), campo.rotulo(), TipoCampo.TEXTO, campo.obrigatorio(), descricao);
            case TEXTO_LONGO -> CampoDescritor.simples(campo.chave(), campo.rotulo(), TipoCampo.TEXTO_LONGO, campo.obrigatorio(), descricao);
            case INTEIRO -> CampoDescritor.simples(campo.chave(), campo.rotulo(), TipoCampo.INTEIRO, campo.obrigatorio(), descricao);
            case DECIMAL -> CampoDescritor.simples(campo.chave(), campo.rotulo(), TipoCampo.DECIMAL, campo.obrigatorio(), descricao);
            case MOEDA -> CampoDescritor.simples(campo.chave(), campo.rotulo(), TipoCampo.MOEDA, campo.obrigatorio(), descricao);
            case DATA -> CampoDescritor.simples(campo.chave(), campo.rotulo(), TipoCampo.DATA, campo.obrigatorio(), descricao);
            case BOOLEANO -> CampoDescritor.simples(campo.chave(), campo.rotulo(), TipoCampo.BOOLEANO, campo.obrigatorio(), descricao);
        };
    }

    private static String referencia(FonteReferencia fonte) {
        if (fonte == null) return null;
        return switch (fonte) {
            case BASE_OPERACIONAL -> "base_operacional";
            case PROCESSO -> "processo";
            case FORNECEDOR -> "fornecedor";
            case COLABORADOR -> "colaborador";
            case VEICULO -> "veiculo";
            case OBRA -> "contrato";
            case SOLICITACAO_COMPRA -> "solicitacao_compra_aprovada";
        };
    }
}
