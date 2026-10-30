package com.nexo.app.modules.sst.domain;

import java.util.UUID;

/**
 * A atividade e o gestor responsável <b>como estavam</b> no instante do registro.
 *
 * <p>É cópia, não vínculo. Trocar o gestor de "Detonação" amanhã muda quem responde
 * pelos próximos eventos e não toca em nenhum evento passado — que é exatamente o que
 * se espera de um registro consultado anos depois, em processo trabalhista.</p>
 *
 * <p>{@code gestorSubstituto} marca o caso em que quem registrou informou um gestor
 * diferente do gestor da atividade (uma cobertura de férias, tipicamente). O dado é
 * guardado como qualquer outro snapshot, mas fica sinalizado para que o indicador
 * consiga distinguir o titular do substituto.</p>
 */
public record SnapshotAtividade(
        UUID idAtividade,
        String nome,
        UUID idGestor,
        String gestorNome,
        String gestorFuncao,
        boolean gestorSubstituto
) {
}
