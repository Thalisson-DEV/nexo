package com.nexo.app.modules.financeiro.application.usecases;

import com.nexo.app.modules.financeiro.domain.CampoCanonico;
import com.nexo.app.modules.financeiro.domain.FonteReferencia;
import com.nexo.app.modules.financeiro.domain.FormaPagamento;
import com.nexo.app.modules.financeiro.domain.FormularioSolicitacao;
import com.nexo.app.modules.financeiro.domain.NaturezaServicoFrota;
import com.nexo.app.modules.financeiro.domain.TipoSolicitacao;
import com.nexo.app.shared.domain.exception.PreenchimentoInvalidoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * O validador é onde o "trust 0 no frontend" se materializa: tudo o que o schema
 * promete à tela é reaplicado aqui sobre o payload recebido.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ValidadorSolicitacaoTest {

    private static final UUID BASE_EXISTENTE = UUID.randomUUID();

    @Mock
    private ResolvedorReferencias resolvedor;

    private ValidadorSolicitacao validador;
    private FormularioSolicitacao formulario;

    @BeforeEach
    void montarFormulario() {
        validador = new ValidadorSolicitacao(resolvedor);
        // Apenas esta base existe; qualquer outra referência deve ser recusada.
        when(resolvedor.existe(any(), any())).thenReturn(false);
        when(resolvedor.existe(eq(FonteReferencia.BASE_OPERACIONAL), eq(BASE_EXISTENTE))).thenReturn(true);
        when(resolvedor.descreverFonte(any())).thenReturn("Bases operacionais");

        formulario = new FormularioSolicitacao(TipoSolicitacao.COMPRA, "PADRAO", "Compra", null, true);
        formulario.adicionarCampo(CampoCanonico.VALOR, "Valor", null, true);
        formulario.adicionarCampo(CampoCanonico.BASE, "BASE", null, true);
        formulario.adicionarCampo(CampoCanonico.TEM_NF, "Tem NF", null, true);
        formulario.adicionarCampo(CampoCanonico.NUMERO_NF, "nº Nota Fiscal", null, false);
        formulario.adicionarCampo(CampoCanonico.JUSTIFICATIVA_SEM_NF, "Justificativa", null, true);
        formulario.adicionarCampo(CampoCanonico.VENCIMENTO, "Vencimento", null, false);
        formulario.adicionarCampo(CampoCanonico.QUANTIDADE_PARCELAS, "Parcelamento", null, false);
        formulario.adicionarCampo(CampoCanonico.FORMA_PAGAMENTO, "Forma de Pagamento", null, false);
        formulario.adicionarCampo(CampoCanonico.NATUREZA_SERVICO, "Natureza", null, false);
    }

    private Map<String, Object> payloadMinimo() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("valor", "1500.00");
        payload.put("baseId", BASE_EXISTENTE.toString());
        payload.put("temNf", true);
        return payload;
    }

    @Test
    @DisplayName("Converte os valores para os tipos declarados no catálogo")
    void convertePreenchimentoValido() {
        var payload = payloadMinimo();
        payload.put("naturezaServico", "MECANICA");
        payload.put("numeroNf", " 12345 ");

        var valores = validador.validar(formulario, payload);

        assertEquals(new BigDecimal("1500.00"), valores.decimal(CampoCanonico.VALOR));
        assertEquals(BASE_EXISTENTE, valores.referencia(CampoCanonico.BASE));
        assertEquals(Boolean.TRUE, valores.logico(CampoCanonico.TEM_NF));
        assertEquals("12345", valores.texto(CampoCanonico.NUMERO_NF));
        assertEquals(NaturezaServicoFrota.MECANICA,
                valores.enumerado(CampoCanonico.NATUREZA_SERVICO, NaturezaServicoFrota.class));
    }

    @Test
    @DisplayName("Recusa chave que não existe no formulário")
    void recusaChaveDesconhecida() {
        var payload = payloadMinimo();
        payload.put("campoInventado", "x");

        var erro = assertThrows(PreenchimentoInvalidoException.class,
                () -> validador.validar(formulario, payload));
        assertTrue(erro.getErros().containsKey("campoInventado"));
    }

    @Test
    @DisplayName("Acumula todos os obrigatórios ausentes, sem cobrar os que a condição esconde")
    void acumulaObrigatoriosAusentes() {
        var erro = assertThrows(PreenchimentoInvalidoException.class,
                () -> validador.validar(formulario, Map.of()));

        assertTrue(erro.getErros().containsKey("valor"));
        assertTrue(erro.getErros().containsKey("baseId"));
        assertTrue(erro.getErros().containsKey("temNf"));
        // Sem resposta em "Tem NF", nem o número da nota nem a justificativa estão
        // visíveis — este é justamente o caso que o formulário antigo errava.
        assertEquals(3, erro.getErros().size(), erro.getErros().toString());
    }

    @Test
    @DisplayName("Campo oculto pela condição não é cobrado nem gravado")
    void campoOcultoNaoEhCobradoNemGravado() {
        var payload = payloadMinimo();
        // Com NF, a justificativa está oculta — mesmo se o cliente mandar um resíduo.
        payload.put("justificativaSemNf", "resíduo de preenchimento anterior");

        var valores = validador.validar(formulario, payload);

        assertFalse(valores.contem(CampoCanonico.JUSTIFICATIVA_SEM_NF));
    }

    @Test
    @DisplayName("Campo exibido pela condição volta a ser obrigatório")
    void campoVisivelPelaCondicaoEhCobrado() {
        var payload = payloadMinimo();
        payload.put("temNf", false);

        var erro = assertThrows(PreenchimentoInvalidoException.class,
                () -> validador.validar(formulario, payload));
        assertTrue(erro.getErros().containsKey("justificativaSemNf"));

        payload.put("justificativaSemNf", "Fornecedor não emite nota.");
        var valores = validador.validar(formulario, payload);
        assertEquals("Fornecedor não emite nota.", valores.texto(CampoCanonico.JUSTIFICATIVA_SEM_NF));
        assertFalse(valores.contem(CampoCanonico.NUMERO_NF), "Sem NF, o número da nota não se aplica");
    }

    @Test
    @DisplayName("Booleano aceita as formas que a tela e as integrações usam")
    void booleanoAceitaSimENao() {
        var comSim = payloadMinimo();
        comSim.put("temNf", "sim");
        assertEquals(Boolean.TRUE, validador.validar(formulario, comSim).logico(CampoCanonico.TEM_NF));

        var comNao = payloadMinimo();
        comNao.put("temNf", "não");
        comNao.put("justificativaSemNf", "Compra em feira livre.");
        assertEquals(Boolean.FALSE, validador.validar(formulario, comNao).logico(CampoCanonico.TEM_NF));

        var invalido = payloadMinimo();
        invalido.put("temNf", "talvez");
        var erro = assertThrows(PreenchimentoInvalidoException.class,
                () -> validador.validar(formulario, invalido));
        assertTrue(erro.getErros().get("temNf").contains("Sim ou Não"));
    }

    @Test
    @DisplayName("Recusa opção fora da enumeração que apoia o campo")
    void recusaOpcaoInvalida() {
        var payload = payloadMinimo();
        payload.put("naturezaServico", "AERONAUTICA");

        var erro = assertThrows(PreenchimentoInvalidoException.class,
                () -> validador.validar(formulario, payload));
        assertTrue(erro.getErros().get("naturezaServico").contains("não é uma opção válida"));
    }

    @Test
    @DisplayName("Recusa referência que não existe no cadastro de origem")
    void recusaReferenciaInexistente() {
        var payload = payloadMinimo();
        payload.put("baseId", UUID.randomUUID().toString());

        var erro = assertThrows(PreenchimentoInvalidoException.class,
                () -> validador.validar(formulario, payload));
        assertTrue(erro.getErros().get("baseId").contains("não foi encontrada"));
    }

    @Test
    @DisplayName("Aceita valor com vírgula e com separador de milhar, como as equipes digitam")
    void aceitaNotacaoBrasileiraDeValor() {
        var comVirgula = payloadMinimo();
        comVirgula.put("valor", "1500,75");
        assertEquals(new BigDecimal("1500.75"),
                validador.validar(formulario, comVirgula).decimal(CampoCanonico.VALOR));

        var comMilhar = payloadMinimo();
        comMilhar.put("valor", "1.500,75");
        assertEquals(new BigDecimal("1500.75"),
                validador.validar(formulario, comMilhar).decimal(CampoCanonico.VALOR));

        var comPonto = payloadMinimo();
        comPonto.put("valor", "1500.75");
        assertEquals(new BigDecimal("1500.75"),
                validador.validar(formulario, comPonto).decimal(CampoCanonico.VALOR));
    }

    @Test
    @DisplayName("Aceita data ISO e no formato dd/MM/aaaa do formulário antigo")
    void aceitaAmbosOsFormatosDeData() {
        var iso = payloadMinimo();
        iso.put("vencimento", "2026-09-15");
        assertEquals(LocalDate.of(2026, 9, 15),
                validador.validar(formulario, iso).data(CampoCanonico.VENCIMENTO));

        var brasileiro = payloadMinimo();
        brasileiro.put("vencimento", "15/09/2026");
        assertEquals(LocalDate.of(2026, 9, 15),
                validador.validar(formulario, brasileiro).data(CampoCanonico.VENCIMENTO));

        var invalida = payloadMinimo();
        invalida.put("vencimento", "15 de setembro");
        var erro = assertThrows(PreenchimentoInvalidoException.class,
                () -> validador.validar(formulario, invalida));
        assertTrue(erro.getErros().containsKey("vencimento"));
    }

    @Test
    @DisplayName("Aplica os limites que o catálogo declara para o campo")
    void aplicaLimitesDoCatalogo() {
        var abaixoDoMinimo = payloadMinimo();
        abaixoDoMinimo.put("valor", "0.00");
        assertTrue(assertThrows(PreenchimentoInvalidoException.class,
                () -> validador.validar(formulario, abaixoDoMinimo)).getErros().containsKey("valor"));

        var acimaDoMaximo = payloadMinimo();
        acimaDoMaximo.put("quantidadeParcelas", 49);
        acimaDoMaximo.put("vencimento", "10/09/2026");
        assertTrue(assertThrows(PreenchimentoInvalidoException.class,
                () -> validador.validar(formulario, acimaDoMaximo)).getErros().containsKey("quantidadeParcelas"));

        var textoLongo = payloadMinimo();
        textoLongo.put("numeroNf", "N".repeat(61));
        assertTrue(assertThrows(PreenchimentoInvalidoException.class,
                () -> validador.validar(formulario, textoLongo)).getErros().containsKey("numeroNf"));
    }

    @Test
    @DisplayName("Parcelar exige o vencimento da primeira parcela")
    void parcelamentoExigeVencimento() {
        var payload = payloadMinimo();
        payload.put("quantidadeParcelas", 3);

        var erro = assertThrows(PreenchimentoInvalidoException.class,
                () -> validador.validar(formulario, payload));
        assertTrue(erro.getErros().get("vencimento").contains("1ª parcela"));

        payload.put("vencimento", "10/09/2026");
        assertEquals(3, validador.validar(formulario, payload).inteiro(CampoCanonico.QUANTIDADE_PARCELAS));
    }

    @Test
    @DisplayName("Parcelar exige forma de pagamento que admita parcelas — regra derivada do enum")
    void parcelamentoExigeFormaParcelavel() {
        var payload = payloadMinimo();
        payload.put("quantidadeParcelas", 3);
        payload.put("vencimento", "10/09/2026");
        payload.put("formaPagamento", FormaPagamento.CARTAO_DEBITO.name());

        var erro = assertThrows(PreenchimentoInvalidoException.class,
                () -> validador.validar(formulario, payload));
        assertTrue(erro.getErros().get("quantidadeParcelas").contains("não admite parcelamento"));

        payload.put("formaPagamento", FormaPagamento.CARTAO_CREDITO.name());
        assertEquals(3, validador.validar(formulario, payload).inteiro(CampoCanonico.QUANTIDADE_PARCELAS));
    }

    @Test
    @DisplayName("Recusa número quebrado num campo inteiro")
    void recusaDecimalEmCampoInteiro() {
        var payload = payloadMinimo();
        payload.put("quantidadeParcelas", "2.5");
        payload.put("vencimento", "10/09/2026");

        var erro = assertThrows(PreenchimentoInvalidoException.class,
                () -> validador.validar(formulario, payload));
        assertTrue(erro.getErros().get("quantidadeParcelas").contains("inteiros"));
    }

    @Test
    @DisplayName("Oferecendo as duas formas de fornecedor, cobra que uma delas venha preenchida")
    void exigeAlgumFornecedorQuandoOfereceAsDuasFormas() {
        var comFornecedor = new FormularioSolicitacao(TipoSolicitacao.COMPRA, "FORN", "Compra", null, false);
        comFornecedor.adicionarCampo(CampoCanonico.VALOR, "Valor", null, true);
        comFornecedor.adicionarCampo(CampoCanonico.FORNECEDOR, "Fornecedor", null, false);
        comFornecedor.adicionarCampo(CampoCanonico.FORNECEDOR_NOME, "Fornecedor (não cadastrado)",
                null, false);

        var erro = assertThrows(PreenchimentoInvalidoException.class,
                () -> validador.validar(comFornecedor, Map.of("valor", "100.00")));
        assertTrue(erro.getErros().containsKey("fornecedorId"));
        assertTrue(erro.getErros().containsKey("fornecedorNome"));

        var valores = validador.validar(comFornecedor,
                Map.of("valor", "100.00", "fornecedorNome", "Auto Peças do Zé"));
        assertEquals("Auto Peças do Zé", valores.texto(CampoCanonico.FORNECEDOR_NOME));
    }

    @Test
    @DisplayName("Campo opcional em branco não vira valor gravado")
    void opcionalEmBrancoNaoGravaValor() {
        var payload = payloadMinimo();
        payload.put("naturezaServico", "   ");

        var valores = validador.validar(formulario, payload);

        assertFalse(valores.contem(CampoCanonico.NATUREZA_SERVICO));
        assertNull(valores.data(CampoCanonico.VENCIMENTO));
    }
}
