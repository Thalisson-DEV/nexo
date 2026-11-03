package com.nexo.app.modules.obras.domain;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * O checklist como foi respondido. Guarda o texto das perguntas, para o registro antigo continuar legível quando o
 * modelo mudar.
 */
public record RespostasChecklist(List<Item> itens) {

    public record Informada(String idPergunta, String valor, List<String> selecionadas, Boolean naoSeAplica) {
    }

    public record Item(String idPergunta, String grupo, String enunciado, EstruturaChecklist.TipoPergunta tipo, boolean obrigatoria,
                       String valor, List<String> selecionadas, boolean naoSeAplica) {

        boolean respondido() {
            return naoSeAplica || (valor != null && !valor.isBlank()) || (selecionadas != null && !selecionadas.isEmpty());
        }

        /** Texto da resposta para documentos e listagens. */
        public String resposta() {
            if (naoSeAplica) {
                return "Não se aplica";
            }
            if (selecionadas != null && !selecionadas.isEmpty()) {
                return String.join(", ", selecionadas);
            }
            return valor != null && !valor.isBlank() ? valor : "Não respondido";
        }
    }

    /** Monta as respostas na ordem do modelo. Valor inválido é recusado; pergunta sem resposta fica como pendência. */
    public static RespostasChecklist de(EstruturaChecklist estrutura, Collection<Informada> informadas) {
        Map<String, Informada> porPergunta = informadas.stream()
                .collect(Collectors.toMap(Informada::idPergunta, Function.identity(), (primeira, segunda) -> segunda));
        List<Item> itens = estrutura.grupos().stream()
                .flatMap(grupo -> grupo.perguntas().stream().map(pergunta -> item(grupo, pergunta, porPergunta.get(pergunta.id()))))
                .toList();
        return new RespostasChecklist(itens);
    }

    private static Item item(EstruturaChecklist.Grupo grupo, EstruturaChecklist.Pergunta pergunta, Informada informada) {
        boolean naoSeAplica = informada != null && Boolean.TRUE.equals(informada.naoSeAplica());
        if (naoSeAplica && !pergunta.aceitaNaoSeAplica()) {
            throw new InvarianteVioladaException("\"" + pergunta.enunciado() + "\" não aceita \"Não se aplica\".");
        }
        String valor = informada == null || naoSeAplica || informada.valor() == null || informada.valor().isBlank() ? null : informada.valor().trim();
        List<String> selecionadas = informada == null || naoSeAplica || informada.selecionadas() == null ? List.of() : List.copyOf(informada.selecionadas());
        validar(pergunta, valor, selecionadas);
        return new Item(pergunta.id(), grupo.titulo(), pergunta.enunciado(), pergunta.tipo(), pergunta.obrigatoria(),
                pergunta.tipo().temOpcoes() ? null : valor, pergunta.tipo().temOpcoes() ? selecionadas : List.of(), naoSeAplica);
    }

    private static void validar(EstruturaChecklist.Pergunta pergunta, String valor, List<String> selecionadas) {
        if (pergunta.tipo().temOpcoes()) {
            if (!pergunta.opcoes().containsAll(selecionadas)
                    || (pergunta.tipo() == EstruturaChecklist.TipoPergunta.ESCOLHA_UNICA && selecionadas.size() > 1)) {
                throw new InvarianteVioladaException("Resposta inválida em \"" + pergunta.enunciado() + "\".");
            }
            return;
        }
        if (valor == null) {
            return;
        }
        try {
            if (pergunta.tipo() == EstruturaChecklist.TipoPergunta.NUMERO) {
                new BigDecimal(valor);
            } else if (pergunta.tipo() == EstruturaChecklist.TipoPergunta.DATA) {
                LocalDate.parse(valor);
            }
        } catch (NumberFormatException | DateTimeParseException e) {
            throw new InvarianteVioladaException("Resposta inválida em \"" + pergunta.enunciado() + "\".");
        }
        if (valor.length() > 2000) {
            throw new InvarianteVioladaException("A resposta de \"" + pergunta.enunciado() + "\" excede 2000 caracteres.");
        }
    }

    /** Perguntas obrigatórias ainda sem resposta. */
    public List<String> pendencias() {
        return itens.stream().filter(item -> item.obrigatoria() && !item.respondido()).map(Item::enunciado).toList();
    }
}
