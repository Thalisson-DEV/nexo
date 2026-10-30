package com.nexo.app.modules.assistente.application.navigation;

import com.nexo.app.modules.assistente.application.agent.ContextoPainel;
import com.nexo.app.modules.assistente.application.dto.AcaoInterface;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Valida um filtro proposto pelo agente para uma tabela do painel aberto ({@code APPLY_FILTER}). So aceita a
 * tabela, os campos e os operadores que a propria tela declarou no contexto; converte o valor para o tipo da
 * coluna. O frontend repete a validacao e so aplica depois do clique do usuario.
 */
@Component
public class PainelFiltroResolver {

    static final int MAX_FILTROS = 5;
    static final int MAX_TEXTO = 80;

    public Resolution resolve(ContextoPainel painel, String alvo, String rotulo, List<Map<String, Object>> filtros) {
        if (painel == null) return Resolution.rejected(Rejection.SEM_PAINEL);
        Optional<ContextoPainel.Tabela> tabela = painel.tabela(alvo);
        if (tabela.isEmpty()) return Resolution.rejected(Rejection.TABELA_DESCONHECIDA);
        if (filtros == null || filtros.isEmpty() || filtros.size() > MAX_FILTROS) return Resolution.rejected(Rejection.FILTROS_INVALIDOS);
        String rotuloLimpo = rotulo == null ? "" : rotulo.replaceAll("[\\p{Cntrl}]", " ").trim();
        if (rotuloLimpo.isEmpty() || rotuloLimpo.length() > 120) return Resolution.rejected(Rejection.ROTULO_INVALIDO);

        List<AcaoInterface.FiltroProposto> convertidos = new ArrayList<>();
        for (Map<String, Object> filtro : filtros) {
            if (filtro == null) return Resolution.rejected(Rejection.FILTROS_INVALIDOS);
            String campo = texto(filtro.get("campo"));
            String operador = texto(filtro.get("operador"));
            Optional<ContextoPainel.Coluna> coluna = campo == null ? Optional.empty() : tabela.get().coluna(campo);
            if (coluna.isEmpty()) return Resolution.rejected(Rejection.CAMPO_DESCONHECIDO);
            if (operador == null || !coluna.get().operadores().contains(operador)) return Resolution.rejected(Rejection.OPERADOR_INVALIDO);
            Object valor;
            try {
                valor = converter(coluna.get().tipo(), filtro.get("valor"));
            } catch (IllegalArgumentException exception) {
                return Resolution.rejected(Rejection.VALOR_INVALIDO);
            }
            convertidos.add(new AcaoInterface.FiltroProposto(campo, operador, valor));
        }
        return Resolution.accepted(AcaoInterface.aplicarFiltro(alvo, rotuloLimpo, convertidos));
    }

    private Object converter(String tipo, Object bruto) {
        if (bruto == null) throw new IllegalArgumentException("Valor nulo");
        return switch (tipo) {
            case "numero", "percentual" -> {
                if (bruto instanceof Number numero) yield new BigDecimal(numero.toString()).doubleValue();
                String texto = String.valueOf(bruto).trim().replace("%", "").replace(',', '.');
                double valor = new BigDecimal(texto).doubleValue();
                if (!Double.isFinite(valor)) throw new IllegalArgumentException("Numero invalido");
                yield valor;
            }
            case "booleano" -> {
                if (bruto instanceof Boolean booleano) yield booleano;
                String texto = String.valueOf(bruto).trim().toLowerCase(Locale.ROOT);
                if (!texto.equals("true") && !texto.equals("false")) throw new IllegalArgumentException("Booleano invalido");
                yield Boolean.parseBoolean(texto);
            }
            case "texto" -> {
                String texto = String.valueOf(bruto).replaceAll("[\\p{Cntrl}]", " ").trim();
                if (texto.isEmpty() || texto.length() > MAX_TEXTO) throw new IllegalArgumentException("Texto invalido");
                yield texto;
            }
            default -> throw new IllegalArgumentException("Tipo desconhecido");
        };
    }

    private static String texto(Object valor) {
        if (valor == null) return null;
        String texto = String.valueOf(valor).trim();
        return texto.isEmpty() ? null : texto;
    }

    public enum Rejection {
        SEM_PAINEL,
        TABELA_DESCONHECIDA,
        FILTROS_INVALIDOS,
        ROTULO_INVALIDO,
        CAMPO_DESCONHECIDO,
        OPERADOR_INVALIDO,
        VALOR_INVALIDO
    }

    public record Resolution(Optional<AcaoInterface> action, Rejection rejection) {
        static Resolution accepted(AcaoInterface action) {
            return new Resolution(Optional.of(action), null);
        }

        static Resolution rejected(Rejection rejection) {
            return new Resolution(Optional.empty(), rejection);
        }

        public boolean accepted() {
            return action.isPresent();
        }
    }
}
