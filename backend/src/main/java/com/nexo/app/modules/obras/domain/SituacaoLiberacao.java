package com.nexo.app.modules.obras.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Se a equipe pode executar um serviço no dia. A APR é lançada a qualquer hora do dia de trabalho, e pode haver mais
 * de uma no mesmo dia: cada serviço é liberado pela sua. Ela só é exigida de quem vai fazer o serviço: registrado que
 * não há condição de fazer, ela deixa de ser necessária e o apontamento fica bloqueado até uma APR concluída depois.
 */
public enum SituacaoLiberacao {
    PENDENTE,
    EM_PREENCHIMENTO,
    LIBERADA,
    IMPEDIDA;

    /**
     * Considera só os registros do dia: APR de ontem não libera o serviço de hoje, nem em turno que atravessou a noite.
     *
     * @param idProgramacao o serviço programado; nulo para atividade em obra não programada, que vale por obra.
     */
    public static SituacaoLiberacao avaliar(UUID idObra, UUID idProgramacao, LocalDate dia, ZoneId fuso,
                                            Collection<AprTurno> aprs, Collection<OcorrenciaTurno> ocorrencias) {
        Optional<OffsetDateTime> impedimento = ocorrencias.stream()
                .filter(o -> o.isImpedeExecucao() && idObra.equals(o.getIdObra()))
                .map(SituacaoLiberacao::momento)
                .filter(momento -> doDia(momento, dia, fuso))
                .max(OffsetDateTime::compareTo);
        var doServico = aprs.stream()
                .filter(a -> idObra.equals(a.getIdObra()) && (idProgramacao == null || idProgramacao.equals(a.getIdProgramacao())))
                .toList();
        Optional<OffsetDateTime> concluida = doServico.stream()
                .filter(a -> a.getStatus() == StatusAprTurno.CONCLUIDA)
                .map(AprTurno::getDataConclusao)
                .filter(Objects::nonNull)
                .filter(momento -> doDia(momento, dia, fuso))
                .max(OffsetDateTime::compareTo);
        if (impedimento.isPresent() && (concluida.isEmpty() || concluida.get().isBefore(impedimento.get()))) {
            return IMPEDIDA;
        }
        if (concluida.isPresent()) {
            return LIBERADA;
        }
        return doServico.stream().anyMatch(a -> a.getStatus() == StatusAprTurno.RASCUNHO && doDia(a.getDataCriacao(), dia, fuso))
                ? EM_PREENCHIMENTO : PENDENTE;
    }

    /** Ainda sem data de gravação, o registro é de agora. */
    private static OffsetDateTime momento(OcorrenciaTurno ocorrencia) {
        if (ocorrencia.getDataUltimaAlteracao() != null) {
            return ocorrencia.getDataUltimaAlteracao();
        }
        return ocorrencia.getDataCriacao() != null ? ocorrencia.getDataCriacao() : OffsetDateTime.now();
    }

    private static boolean doDia(OffsetDateTime momento, LocalDate dia, ZoneId fuso) {
        return momento == null || momento.atZoneSameInstant(fuso).toLocalDate().equals(dia);
    }
}
