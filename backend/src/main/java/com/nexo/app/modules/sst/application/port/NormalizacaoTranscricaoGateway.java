package com.nexo.app.modules.sst.application.port;

import java.util.List;

/** Porta específica da etapa de organização factual; não redige nem aprova a ata. */
public interface NormalizacaoTranscricaoGateway {

    Resultado normalizar(Entrada entrada);

    String versaoModelo();

    record Entrada(String versaoModelo, String transcricaoConfirmada) {}

    record Item(String texto, String trechoOrigem, boolean requerConfirmacao) {}

    record Conteudo(List<Item> topicos, List<Item> decisoesCandidatas,
                    List<Item> acoesCandidatas, List<Item> incertezas) {
        public Conteudo {
            topicos = copia(topicos);
            decisoesCandidatas = copia(decisoesCandidatas);
            acoesCandidatas = copia(acoesCandidatas);
            incertezas = copia(incertezas);
        }

        private static List<Item> copia(List<Item> itens) {
            return itens == null ? List.of() : List.copyOf(itens);
        }
    }

    record Resultado(boolean sucesso, Conteudo conteudo, String codigoFalha) {
        public static Resultado sucesso(Conteudo conteudo) {
            return new Resultado(true, conteudo, null);
        }

        public static Resultado falha(String codigo) {
            return new Resultado(false, null, codigo);
        }
    }
}
