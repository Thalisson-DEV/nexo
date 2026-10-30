package com.nexo.app.modules.sst.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Os campos próprios do evento — o que ele é, quando aconteceu, o que se sabe da lesão
 * e da CAT. Tudo o que vem de outro cadastro (atividade, gestor, base, equipe, veículo,
 * pessoas) entra pelos snapshots, não por aqui.
 *
 * <p>Existe para que registrar e editar um evento sejam a <b>mesma</b> operação de
 * domínio, com o mesmo conjunto de invariantes, em vez de dois construtores de vinte
 * parâmetros que se afastam com o tempo.</p>
 *
 * <p>Os quatro identificadores de catálogo são referências vivas (classificação, local
 * e natureza da lesão, agente causador): corrigir o nome de uma entrada corrige em todo
 * evento que aponta para ela. É o oposto da regra dos snapshots, e de propósito —
 * vocabulário estável deve propagar, gente não.</p>
 */
public record DadosEventoSst(
        TipoEventoSst tipo,
        OffsetDateTime dataOcorrencia,
        OffsetDateTime dataComunicacao,
        UUID idClassificacao,
        UUID idProcesso,
        RegistroEvidencia telemetria,
        RegistroEvidencia filmagem,
        String localOcorrencia,
        BigDecimal latitude,
        BigDecimal longitude,
        String descricao,
        UUID idLocalLesao,
        UUID idNaturezaLesao,
        UUID idAgenteCausador,
        Integer diasPerdidos,
        boolean possuiCat,
        TipoCat tipoCat,
        String numeroCat,
        LocalDate dataEmissaoCat
) {
}
