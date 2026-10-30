package com.nexo.app.modules.sst.application.port;

import java.time.OffsetDateTime;
import java.util.List;

/** Porta SST para redação assistida. Não depende do módulo Assistente. */
public interface GeracaoAtaGateway {

    Resultado gerar(Entrada entrada);

    record Entrada(
            String versaoPrompt,
            String tipoCorpo,
            String tipoReuniao,
            OffsetDateTime dataHora,
            String localOuMeio,
            String pauta,
            List<Participante> participantes,
            String transcricaoConfirmada,
            String snapshotModelo,
            String versaoBase,
            String instrucaoCorrecao) {
        public Entrada {
            participantes = List.copyOf(participantes);
        }
    }

    record Participante(String nome, String papel, String presenca) {}

    record Resultado(boolean sucesso, String conteudo, String codigoFalha) {
        public static Resultado sucesso(String conteudo) {
            return new Resultado(true, conteudo, null);
        }
        public static Resultado falha(String codigo) {
            return new Resultado(false, null, codigo);
        }
    }
}
