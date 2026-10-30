package com.nexo.app.modules.financeiro.application.usecases;

import com.nexo.app.modules.financeiro.domain.CampoCanonico;
import com.nexo.app.modules.financeiro.domain.CampoFormulario;
import com.nexo.app.modules.financeiro.domain.FormaPagamento;
import com.nexo.app.modules.financeiro.domain.FormularioSolicitacao;
import com.nexo.app.modules.financeiro.domain.TipoCampo;
import com.nexo.app.modules.financeiro.domain.ValoresCanonicos;
import com.nexo.app.shared.domain.exception.PreenchimentoInvalidoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Confere o payload de uma solicitação contra o formulário da variante escolhida.
 *
 * <p>É o ponto onde o "trust 0 no frontend" deixa de ser intenção e vira código: o
 * mesmo formulário que gerou a tela é reaplicado aqui — chave desconhecida é
 * recusada, campo oculto pela condição não é cobrado nem gravado, opção fora da
 * enumeração é rejeitada, referência inexistente não passa, e os limites do campo
 * valem de novo. A tela pode validar por conveniência; a garantia é esta.</p>
 *
 * <p>Os problemas são acumulados e reportados de uma vez em
 * {@link PreenchimentoInvalidoException}, para o usuário corrigir tudo numa
 * passada em vez de descobrir um erro por submissão.</p>
 */
@Component
@RequiredArgsConstructor
public class ValidadorSolicitacao {

    private static final DateTimeFormatter FORMATO_BR = DateTimeFormatter.ofPattern("dd/MM/uuuu");
    private static final int ESCALA_MOEDA = 2;

    private final ResolvedorReferencias resolvedor;

    /**
     * Valida e converte o preenchimento.
     *
     * @throws PreenchimentoInvalidoException com o detalhamento campo a campo
     */
    public ValoresCanonicos validar(FormularioSolicitacao formulario, Map<String, Object> payload) {
        formulario.validarEstrutura();

        Map<String, CampoFormulario> porChave = new LinkedHashMap<>();
        formulario.camposOrdenados().forEach(campo -> porChave.put(campo.getChave(), campo));

        Map<String, String> erros = new LinkedHashMap<>();
        Map<String, Object> valores = payload == null ? Map.of() : payload;

        valores.keySet().stream()
                .filter(chave -> !porChave.containsKey(chave))
                .forEach(chave -> erros.put(chave, "O campo '" + chave + "' não existe neste formulário."));

        Map<CampoCanonico, Object> canonicos = new EnumMap<>(CampoCanonico.class);
        for (CampoFormulario campo : porChave.values()) {
            if (!visivel(campo, porChave, valores)) {
                continue;
            }
            Object bruto = valores.get(campo.getChave());
            if (vazio(bruto)) {
                if (campo.isObrigatorio()) {
                    erros.put(campo.getChave(), "O campo '" + campo.getRotulo() + "' é obrigatório.");
                }
                continue;
            }
            try {
                canonicos.put(campo.getCampoCanonico(), converter(campo, bruto));
            } catch (ErroDeCampo e) {
                erros.put(campo.getChave(), e.getMessage());
            }
        }

        if (erros.isEmpty()) {
            validarRegrasCruzadas(formulario, porChave, canonicos, erros);
        }
        if (!erros.isEmpty()) {
            throw new PreenchimentoInvalidoException(erros);
        }
        return new ValoresCanonicos(canonicos);
    }

    // ── Visibilidade ────────────────────────────────────────────────────────────

    /**
     * Um campo condicionado só é considerado visível se a resposta do campo que o
     * governa satisfizer a condição <b>e</b> esse governante estiver ele próprio
     * visível — do contrário, esconder um campo deixaria os que dependem dele
     * órfãos e ainda assim obrigatórios.
     *
     * <p>A recursão não precisa de trava contra ciclos porque as condições são uma
     * tabela fixa declarada em {@link CampoCanonico}, e um teste garante que ela
     * permanece acíclica.</p>
     */
    private boolean visivel(CampoFormulario campo, Map<String, CampoFormulario> porChave,
                            Map<String, Object> valores) {
        var condicao = campo.getCondicao().orElse(null);
        if (condicao == null) {
            return true;
        }
        CampoFormulario governante = porChave.get(condicao.governante().getChave());
        if (governante == null) {
            return true;
        }
        return visivel(governante, porChave, valores)
                && condicao.satisfeitaPor(valores.get(governante.getChave()));
    }

    // ── Conversão e limites ─────────────────────────────────────────────────────

    private Object converter(CampoFormulario campo, Object bruto) {
        return switch (campo.getTipoCampo()) {
            case TEXTO, TEXTO_LONGO -> converterTexto(campo, bruto);
            case INTEIRO -> converterInteiro(campo, bruto);
            case DECIMAL, MOEDA -> converterDecimal(campo, bruto);
            case DATA -> converterData(campo, bruto);
            case BOOLEANO -> converterBooleano(campo, bruto);
            case SELECAO_UNICA -> converterSelecaoUnica(campo, bruto);
            case REFERENCIA -> converterReferencia(campo, bruto);
        };
    }

    private String converterTexto(CampoFormulario campo, Object bruto) {
        String texto = bruto.toString().trim();
        Integer maximo = campo.getRestricoes().tamanhoMaximo();
        if (maximo != null && texto.length() > maximo) {
            throw new ErroDeCampo("O campo '" + campo.getRotulo() + "' aceita no máximo " + maximo + " caracteres.");
        }
        return texto;
    }

    private Integer converterInteiro(CampoFormulario campo, Object bruto) {
        BigDecimal numero = paraDecimal(campo, bruto);
        if (numero.stripTrailingZeros().scale() > 0) {
            throw new ErroDeCampo("O campo '" + campo.getRotulo() + "' aceita apenas números inteiros.");
        }
        validarFaixa(campo, numero);
        return numero.intValue();
    }

    private BigDecimal converterDecimal(CampoFormulario campo, Object bruto) {
        BigDecimal numero = paraDecimal(campo, bruto);
        validarFaixa(campo, numero);
        return campo.getTipoCampo() == TipoCampo.MOEDA
                ? numero.setScale(ESCALA_MOEDA, RoundingMode.HALF_UP)
                : numero;
    }

    private LocalDate converterData(CampoFormulario campo, Object bruto) {
        if (bruto instanceof LocalDate data) {
            return data;
        }
        String texto = bruto.toString().trim();
        try {
            return LocalDate.parse(texto);
        } catch (DateTimeParseException ignorado) {
            // As equipes vêm do formulário antigo, que pedia dd/MM/yyyy — aceitar os
            // dois formatos evita erro por hábito.
            try {
                return LocalDate.parse(texto, FORMATO_BR);
            } catch (DateTimeParseException e) {
                throw new ErroDeCampo("O campo '" + campo.getRotulo()
                        + "' deve conter uma data válida (dd/MM/aaaa).");
            }
        }
    }

    private Boolean converterBooleano(CampoFormulario campo, Object bruto) {
        if (bruto instanceof Boolean logico) {
            return logico;
        }
        String texto = bruto.toString().trim().toLowerCase();
        return switch (texto) {
            case "true", "sim", "1" -> Boolean.TRUE;
            case "false", "nao", "não", "0" -> Boolean.FALSE;
            default -> throw new ErroDeCampo("O campo '" + campo.getRotulo() + "' aceita apenas Sim ou Não.");
        };
    }

    private String converterSelecaoUnica(CampoFormulario campo, Object bruto) {
        String valor = bruto.toString().trim();
        if (!campo.valoresAceitos().contains(valor)) {
            throw new ErroDeCampo("O valor '" + valor + "' não é uma opção válida para '"
                    + campo.getRotulo() + "'.");
        }
        return valor;
    }

    private UUID converterReferencia(CampoFormulario campo, Object bruto) {
        UUID id;
        try {
            id = bruto instanceof UUID uuid ? uuid : UUID.fromString(bruto.toString().trim());
        } catch (IllegalArgumentException e) {
            throw new ErroDeCampo("O campo '" + campo.getRotulo() + "' deve conter um identificador válido.");
        }
        if (!resolvedor.existe(campo.getFonteReferencia(), id)) {
            throw new ErroDeCampo("A opção escolhida em '" + campo.getRotulo()
                    + "' não foi encontrada em " + resolvedor.descreverFonte(campo.getFonteReferencia()) + ".");
        }

        // Duas referências não bastam existir: precisam estar num estado que admita
        // uso. É aqui, e não na entidade, porque a mensagem é de preenchimento.
        switch (campo.getCampoCanonico()) {
            case FORNECEDOR -> resolvedor.buscarFornecedor(id).ifPresent(fornecedor -> {
                try {
                    fornecedor.exigirUtilizavel();
                } catch (RuntimeException e) {
                    throw new ErroDeCampo(e.getMessage());
                }
            });
            case SOLICITACAO_COMPRA -> resolvedor.buscarCompra(id).ifPresent(compra -> {
                if (!compra.isAprovada()) {
                    throw new ErroDeCampo("A solicitação de compra nº " + compra.getNumero()
                            + " ainda não foi aprovada.");
                }
            });
            default -> { /* demais referências bastam existir */ }
        }
        return id;
    }

    private BigDecimal paraDecimal(CampoFormulario campo, Object bruto) {
        if (bruto instanceof BigDecimal decimal) {
            return decimal;
        }
        if (bruto instanceof Number numero) {
            return new BigDecimal(numero.toString());
        }
        // O formulário antigo instruía a usar ponto para centavos, mas o hábito da
        // vírgula persiste. Aceitar as duas notações evita rejeitar um valor correto:
        // o último separador presente é o decimal, e o outro é agrupador de milhar.
        String texto = bruto.toString().trim().replace(" ", "");
        if (texto.contains(",")) {
            texto = texto.contains(".")
                    ? texto.replace(".", "").replace(",", ".")
                    : texto.replace(",", ".");
        }
        try {
            return new BigDecimal(texto);
        } catch (NumberFormatException e) {
            throw new ErroDeCampo("O campo '" + campo.getRotulo() + "' deve conter um número válido.");
        }
    }

    private void validarFaixa(CampoFormulario campo, BigDecimal numero) {
        var restricoes = campo.getRestricoes();
        if (restricoes.valorMinimo() != null && numero.compareTo(restricoes.valorMinimo()) < 0) {
            throw new ErroDeCampo("O campo '" + campo.getRotulo() + "' deve ser no mínimo "
                    + restricoes.valorMinimo().toPlainString() + ".");
        }
        if (restricoes.valorMaximo() != null && numero.compareTo(restricoes.valorMaximo()) > 0) {
            throw new ErroDeCampo("O campo '" + campo.getRotulo() + "' deve ser no máximo "
                    + restricoes.valorMaximo().toPlainString() + ".");
        }
    }

    // ── Regras que envolvem mais de um campo ────────────────────────────────────

    /**
     * Regras que um campo sozinho não expressa, porque relacionam campos entre si.
     * Só rodam quando não há erro individual pendente, para não empilhar mensagens
     * derivadas de um campo que já se sabe inválido.
     */
    private void validarRegrasCruzadas(FormularioSolicitacao formulario, Map<String, CampoFormulario> porChave,
                                       Map<CampoCanonico, Object> canonicos, Map<String, String> erros) {
        exigirAlgumFornecedor(formulario, canonicos, erros);
        validarParcelamento(porChave, canonicos, erros);
    }

    /**
     * O fornecedor pode vir do cadastro ou como texto livre, mas alguma das duas
     * formas precisa estar preenchida quando o formulário oferece as duas.
     */
    private void exigirAlgumFornecedor(FormularioSolicitacao formulario,
                                       Map<CampoCanonico, Object> canonicos, Map<String, String> erros) {
        boolean ofereceCadastro = formulario.possuiCanonico(CampoCanonico.FORNECEDOR);
        boolean ofereceTextoLivre = formulario.possuiCanonico(CampoCanonico.FORNECEDOR_NOME);
        if (!ofereceCadastro || !ofereceTextoLivre) {
            return;
        }
        if (canonicos.get(CampoCanonico.FORNECEDOR) == null && canonicos.get(CampoCanonico.FORNECEDOR_NOME) == null) {
            String mensagem = "Escolha um fornecedor cadastrado ou informe o nome do fornecedor.";
            erros.put(CampoCanonico.FORNECEDOR.getChave(), mensagem);
            erros.put(CampoCanonico.FORNECEDOR_NOME.getChave(), mensagem);
        }
    }

    /**
     * Parcelar exige um vencimento de partida e uma forma de pagamento que admita
     * parcelas — sem isso quem paga não tem como montar o cronograma.
     */
    private void validarParcelamento(Map<String, CampoFormulario> porChave,
                                     Map<CampoCanonico, Object> canonicos, Map<String, String> erros) {
        if (!(canonicos.get(CampoCanonico.QUANTIDADE_PARCELAS) instanceof Integer quantidade) || quantidade <= 1) {
            return;
        }
        boolean ofereceVencimento = porChave.containsKey(CampoCanonico.VENCIMENTO.getChave());
        if (ofereceVencimento && canonicos.get(CampoCanonico.VENCIMENTO) == null) {
            erros.put(CampoCanonico.VENCIMENTO.getChave(),
                    "Informe o vencimento da 1ª parcela para parcelar em " + quantidade + " vezes.");
        }
        if (canonicos.get(CampoCanonico.FORMA_PAGAMENTO) instanceof String forma
                && !FormaPagamento.valueOf(forma).isParcelavel()) {
            erros.put(CampoCanonico.QUANTIDADE_PARCELAS.getChave(),
                    "A forma de pagamento escolhida não admite parcelamento.");
        }
    }

    private static boolean vazio(Object valor) {
        if (valor == null) {
            return true;
        }
        if (valor instanceof String texto) {
            return texto.isBlank();
        }
        return valor instanceof Collection<?> colecao && colecao.isEmpty();
    }

    /** Sinaliza o problema de um campo específico para ser acumulado pelo laço principal. */
    private static class ErroDeCampo extends RuntimeException {
        ErroDeCampo(String mensagem) {
            super(mensagem, null, false, false);
        }
    }
}
