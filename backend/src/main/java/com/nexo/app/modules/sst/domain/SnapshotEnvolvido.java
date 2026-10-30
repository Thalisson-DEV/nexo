package com.nexo.app.modules.sst.domain;

import java.util.UUID;

/**
 * Uma pessoa envolvida no evento, copiada como estava no registro.
 *
 * <p>Para {@link TipoEnvolvido#COLABORADOR}, {@code idColaborador} é obrigatório e é
 * ele que sustenta qualquer agrupamento por pessoa — o nome vai cifrado e não se
 * presta a {@code GROUP BY}. Para {@link TipoEnvolvido#TERCEIRO} não há id: quem
 * identifica é o par nome + empresa, e por isso a empresa é obrigatória.</p>
 */
public record SnapshotEnvolvido(
        TipoEnvolvido tipo,
        PapelEnvolvido papel,
        UUID idColaborador,
        String nome,
        String funcao,
        String baseOperacional,
        String empresa
) {

    /** Colaborador da casa: id do RH mais o retrato de nome, função e base. */
    public static SnapshotEnvolvido colaborador(PapelEnvolvido papel, UUID idColaborador,
                                                String nome, String funcao, String baseOperacional) {
        return new SnapshotEnvolvido(TipoEnvolvido.COLABORADOR, papel, idColaborador,
                nome, funcao, baseOperacional, null);
    }

    /** Pessoa de fora: sem id, identificada pelo nome e pela empresa a que pertence. */
    public static SnapshotEnvolvido terceiro(PapelEnvolvido papel, String nome, String funcao, String empresa) {
        return new SnapshotEnvolvido(TipoEnvolvido.TERCEIRO, papel, null, nome, funcao, null, empresa);
    }
}
