package com.nexo.app.modules.obras.domain;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;

import java.util.List;
import java.util.UUID;

/**
 * O que a equipe analisou antes de executar. Guarda o texto de riscos, medidas e perguntas como foram respondidos,
 * para a APR antiga continuar legível quando a lista de opções mudar.
 */
public record FormularioApr(String local, List<String> riscos, String outroRisco, List<String> medidas,
                            String medidasAdicionais, List<Verificacao> verificacoes, List<UUID> idsParticipantes) {

    public enum Resposta {
        SIM,
        NAO,
        NAO_SE_APLICA
    }

    public record Verificacao(String pergunta, Resposta resposta) {
    }

    /** Resposta "Não" impede concluir: ou se corrige antes, ou o serviço não é feito. */
    void validarConclusao() {
        if ((riscos == null || riscos.isEmpty()) && !preenchido(outroRisco)) {
            throw new InvarianteVioladaException("Informe ao menos um risco da atividade antes de concluir a APR.");
        }
        if ((medidas == null || medidas.isEmpty()) && !preenchido(medidasAdicionais)) {
            throw new InvarianteVioladaException("Informe ao menos uma medida de controle antes de concluir a APR.");
        }
        if (verificacoes == null || verificacoes.isEmpty() || verificacoes.stream().anyMatch(v -> v.resposta() == null)) {
            throw new InvarianteVioladaException("Responda todas as verificações antes de concluir a APR.");
        }
        if (verificacoes.stream().anyMatch(v -> v.resposta() == Resposta.NAO)) {
            throw new InvarianteVioladaException("Há verificação respondida com \"Não\": corrija antes de executar ou registre que o serviço não tem condição de ser feito.");
        }
        if (idsParticipantes == null || idsParticipantes.isEmpty()) {
            throw new InvarianteVioladaException("Informe quem da equipe participou da APR antes de concluir.");
        }
    }

    private static boolean preenchido(String texto) {
        return texto != null && !texto.isBlank();
    }
}
