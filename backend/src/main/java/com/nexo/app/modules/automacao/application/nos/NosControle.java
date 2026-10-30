package com.nexo.app.modules.automacao.application.nos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.nexo.app.modules.automacao.application.motor.Catalogo.EspecNo;
import com.nexo.app.modules.automacao.application.motor.Catalogo.EspecParam;
import com.nexo.app.modules.automacao.application.motor.Catalogo.EspecSaida;
import com.nexo.app.modules.automacao.application.motor.Catalogo.ProvedorNos;
import com.nexo.app.modules.automacao.application.motor.ContextoAcao;
import com.nexo.app.modules.automacao.application.motor.ErroAcao;
import com.nexo.app.modules.automacao.application.motor.Recorrencia;
import com.nexo.app.modules.automacao.domain.expressao.Valores;

/**
 * Nós de controle (o comportamento deles mora no interpretador) e de variáveis
 * ("valores guardados" — declarados na automação e alterados por estes passos).
 */
@Component
public class NosControle implements ProvedorNos {

    private static final Map<String, Object> GRUPO_VAZIO = Map.of("op", "and", "rules", List.of());

    @Override
    public Collection<EspecNo> nos() {
        List<List<String>> dias = new ArrayList<>();
        for (int i = 0; i < 7; i++) dias.add(List.of(String.valueOf(i), Recorrencia.DIAS[i]));
        List<EspecNo> nos = new ArrayList<>();
        nos.add(EspecNo.de("control.condition", "control", "Condição",
                        "Se as regras forem verdadeiras, segue pelo Sim; senão, pelo Não. Regras com 'e' e 'ou', em grupos.", "branch")
                .conteiner("condition")
                .params(EspecParam.de("condition", "Regras", "condition").obrigatorio().padrao(GRUPO_VAZIO).semResolver().criar())
                .saidas(EspecSaida.de("result", "Resultado (verdadeiro/falso)", "boolean"))
                .palavras("se", "if", "então", "senão", "decisão")
                .criar());
        nos.add(EspecNo.de("control.switch", "control", "Escolher caso",
                        "Compara um valor com vários casos e segue pelo caso igual; senão, pelo Padrão. Num caso, separe valores com ;", "switch")
                .conteiner("switch")
                .params(EspecParam.de("on", "Valor comparado", "expression").obrigatorio().semResolver()
                        .placeholder("Escolha o valor de um passo anterior (ex.: a base do colaborador)").criar())
                .saidas(EspecSaida.de("case", "Caso escolhido", "string"), EspecSaida.de("value", "Valor comparado", "string"))
                .palavras("switch", "caso", "opções", "base", "rotear")
                .criar());
        nos.add(EspecNo.de("control.foreach", "control", "Para cada",
                        "Repete os passos internos para cada item de uma lista. Dentro, use o item atual.", "repeat")
                .conteiner("loop")
                .params(EspecParam.de("items", "Lista", "expression").obrigatorio().semResolver()
                        .placeholder("Escolha a lista de um passo anterior").criar())
                .saidas(EspecSaida.de("count", "Itens percorridos", "number"), EspecSaida.de("failed", "Itens com falha", "number"))
                .palavras("loop", "apply to each", "repetir", "cada", "lista")
                .criar());
        nos.add(EspecNo.de("control.until", "control", "Repetir até",
                        "Repete os passos internos até as regras serem verdadeiras (ou o limite de vezes).", "refresh")
                .conteiner("until")
                .params(EspecParam.de("condition", "Parar quando", "condition").obrigatorio().padrao(GRUPO_VAZIO).semResolver().criar(),
                        EspecParam.de("max_iterations", "No máximo (vezes)", "number").padrao(20).estatico().faixa(1, 100).criar())
                .saidas(EspecSaida.de("iterations", "Vezes executadas", "number"), EspecSaida.de("condition_met", "Condição atingida", "boolean"))
                .palavras("do until", "enquanto", "loop")
                .criar());
        nos.add(EspecNo.de("control.scope", "control", "Escopo",
                        "Agrupa passos. Falha se algum passo dentro falhar: use com 'Executar após falha' para tratar erros (tentar/tratar).", "box")
                .conteiner("scope")
                .palavras("try", "catch", "tentar", "tratar", "grupo")
                .criar());
        nos.add(EspecNo.de("control.parallel", "control", "Ramos paralelos",
                        "Ramos independentes: a falha ou a espera de um não impede os outros.", "columns")
                .conteiner("parallel")
                .palavras("paralelo", "ramos", "simultâneo")
                .criar());
        nos.add(EspecNo.de("control.wait", "control", "Esperar",
                        "Pausa a execução por um tempo ou até um momento. A execução é retomada sozinha.", "clock")
                .params(
                        EspecParam.de("mode", "Esperar", "select").padrao("duration").estatico()
                                .opcoes(List.of(List.of("duration", "Por um tempo"), List.of("weekday", "Até um dia da semana"),
                                        List.of("datetime", "Até uma data e hora"))).criar(),
                        EspecParam.de("days", "Dias", "number").padrao(0).faixa(0, 90).mostrarSe("mode", "duration").criar(),
                        EspecParam.de("hours", "Horas", "number").padrao(0).faixa(0, 23).mostrarSe("mode", "duration").criar(),
                        EspecParam.de("minutes", "Minutos", "number").padrao(0).faixa(0, 59).mostrarSe("mode", "duration").criar(),
                        EspecParam.de("weekday", "Dia", "select").padrao("4").opcoes(dias).mostrarSe("mode", "weekday").criar(),
                        EspecParam.de("time", "Horário (Brasília)", "time").padrao("17:00").estatico().mostrarSe("mode", "weekday").criar(),
                        EspecParam.de("until", "Data e hora", "text").placeholder("31/10/2026 17:00").mostrarSe("mode", "datetime").criar())
                .saidas(EspecSaida.de("resume_at", "Retomou em", "string"))
                .resumo(NosControle::resumoEspera)
                .palavras("delay", "aguardar", "pausa")
                .criar());
        nos.add(EspecNo.de("control.terminate", "control", "Encerrar",
                        "Termina a execução aqui, com sucesso, falha ou cancelada.", "stop")
                .params(EspecParam.de("status", "Situação final", "select").padrao("succeeded").estatico()
                                .opcoes(List.of(List.of("succeeded", "Sucesso"), List.of("failed", "Falha"), List.of("cancelled", "Cancelada"))).criar(),
                        EspecParam.de("message", "Mensagem", "text").placeholder("Por que terminou aqui").criar())
                .palavras("terminate", "parar", "fim")
                .criar());

        EspecParam nome = EspecParam.de("name", "Variável", "select").obrigatorio().estatico()
                .ajuda("Declare a variável em Configurações › Valores guardados.").criar();
        List<EspecSaida> valor = List.of(EspecSaida.de("value", "Novo valor", "any"));
        nos.add(EspecNo.de("variable.set", "variables", "Definir valor guardado", "Troca o valor de uma variável.", "variable")
                .params(nome, EspecParam.de("value", "Valor", "textarea").criar())
                .saidas(valor)
                .executor((ctx, p) -> {
                    atual(ctx, p);
                    return Map.of("value", p.get("value") == null ? "" : p.get("value"));
                })
                .palavras("set", "atribuir", "valor", "variável")
                .criar());
        nos.add(EspecNo.de("variable.increment", "variables", "Somar ao valor guardado",
                        "Soma um número a uma variável numérica (use negativo para diminuir).", "plus")
                .params(nome, EspecParam.de("by", "Somar", "number").padrao(1).criar())
                .saidas(valor)
                .executor((ctx, p) -> {
                    double atual = Valores.dbl(atual(ctx, p));
                    Number passo = Valores.numero(p.get("by"));
                    return Map.of("value", Valores.limpo(atual + (passo == null ? 1 : passo.doubleValue())));
                })
                .palavras("contador", "somar", "increment")
                .criar());
        nos.add(EspecNo.de("variable.append", "variables", "Acrescentar ao valor guardado",
                        "Acrescenta um item a uma lista ou um texto ao fim de um texto.", "list")
                .params(nome, EspecParam.de("value", "Valor", "textarea").criar(),
                        EspecParam.de("spread", "Se o valor for uma lista, acrescentar cada item", "boolean").padrao(false).estatico().criar())
                .saidas(valor)
                .executor((ctx, p) -> {
                    Object atual = atual(ctx, p);
                    Object novo = p.get("value");
                    if (atual instanceof List<?>) {
                        List<Object> lista = Valores.lista(atual);
                        if (novo instanceof List<?> l && Valores.verdadeiro(p.get("spread"))) lista.addAll(l);
                        else lista.add(novo);
                        return Map.of("value", lista);
                    }
                    return Map.of("value", Valores.texto(atual) + Valores.texto(novo));
                })
                .palavras("append", "lista", "juntar")
                .criar());
        return nos;
    }

    private static Object atual(ContextoAcao ctx, Map<String, Object> p) {
        String nome = Valores.texto(p.get("name"));
        Map<String, Object> vars = Valores.mapa(ctx.escopo().get("vars"));
        if (!vars.containsKey(nome)) throw new ErroAcao("A variável '" + nome + "' não foi declarada");
        return vars.get(nome);
    }

    private static String resumoEspera(Map<String, Object> p) {
        String modo = Valores.texto(p.getOrDefault("mode", "duration"));
        if (modo.equals("datetime")) return "Esperar até a data informada";
        if (modo.equals("weekday")) {
            int dia = (int) Math.max(0, Math.min(6, Valores.dbl(p.getOrDefault("weekday", 4))));
            return "Esperar até " + Recorrencia.DIAS[dia] + " às " + Valores.texto(p.getOrDefault("time", "17:00"));
        }
        List<String> partes = new ArrayList<>();
        if (Valores.dbl(p.get("days")) > 0) partes.add(Valores.texto(p.get("days")) + " dia(s)");
        if (Valores.dbl(p.get("hours")) > 0) partes.add(Valores.texto(p.get("hours")) + " hora(s)");
        if (Valores.dbl(p.get("minutes")) > 0) partes.add(Valores.texto(p.get("minutes")) + " minuto(s)");
        return "Esperar " + (partes.isEmpty() ? "…" : String.join(" e ", partes));
    }
}
