package com.nexo.app.modules.obras;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Leitura agregada do que as equipes executaram nas obras, para painéis de outros módulos. O valor é o mesmo
 * que o boletim e os turnos de obra somam: o total dos apontamentos ativos, no dia do apontamento.
 */
public interface ExecucaoObrasApi {

    /** Processos de negócio que têm obra cadastrada: as equipes deles são as equipes de obras. */
    Set<UUID> processosComObras();

    /** Executado por obra, equipe e dia, entre {@code de} e {@code ate} (inclusive). Sem equipe, lista vazia. */
    List<ExecutadoNoDia> executadoPorDia(Collection<UUID> idsEquipes, LocalDate de, LocalDate ate);

    /**
     * Soma dos apontamentos de uma equipe numa obra num dia.
     *
     * @param servicos apontamentos de itens de serviço (os de material não contam)
     */
    record ExecutadoNoDia(
            UUID idObra,
            String numeroObra,
            String tituloObra,
            UUID idEquipe,
            LocalDate data,
            BigDecimal valor,
            long servicos
    ) {
    }
}
