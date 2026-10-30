package com.nexo.app.shared.domain.events;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Uma ocorrência de segurança acabou de ser registrada.
 *
 * <p>É o evento que tira o acidente da planilha e o coloca na frente de quem responde
 * por ele: o SESMT precisa saber que aconteceu antes de alguém abrir a tela. O módulo
 * publica e segue adiante — quem é avisado, e com que texto, é decisão das regras
 * configuradas para o gatilho {@code sst.evento.registrado}.</p>
 *
 * <p><b>Nenhum nome de pessoa viaja aqui.</b> O contexto vira texto de notificação, que
 * chega ao sininho e ao e-mail de quem a regra endereçar; nome de acidentado é dado
 * pessoal, e quem tem permissão para vê-lo abre o registro pelo deeplink.</p>
 */
public record EventoSstRegistradoEvent(
        UUID idEvento,
        String numeroRegistro,
        String tipo,
        String tipoRotulo,
        String atividade,
        String base,
        OffsetDateTime dataOcorrencia,
        boolean comLesao,
        boolean comunicacaoAtrasada,
        UUID idGestor,
        Set<UUID> idsTecnicos
) implements EventoNotificavel {

    public static final String CHAVE_GATILHO = "sst.evento.registrado";

    @Override
    public String chaveGatilho() {
        return CHAVE_GATILHO;
    }

    @Override
    public UUID idReferencia() {
        return idEvento;
    }

    @Override
    public Map<String, Object> contexto() {
        // HashMap porque valores nulos são legítimos (um evento pode não ter equipe,
        // veículo ou local) e Map.of não os aceita.
        Map<String, Object> contexto = new HashMap<>();
        contexto.put("numeroRegistro", numeroRegistro);
        contexto.put("tipo", tipo);
        contexto.put("tipoRotulo", tipoRotulo);
        contexto.put("atividade", atividade);
        contexto.put("base", base);
        contexto.put("dataOcorrencia", dataOcorrencia);
        contexto.put("comLesao", comLesao);
        contexto.put("comunicacaoAtrasada", comunicacaoAtrasada);
        contexto.put("idEvento", idEvento);
        return contexto;
    }

    /**
     * Sem papéis dinâmicos: gestor e técnicos são <b>colaboradores</b>, e o motor
     * endereça <b>usuários</b>. A regra de fábrica aponta para quem tem a permissão de
     * SST, que é o endereçamento que não depende dessa tradução.
     */
    @Override
    public Map<String, Set<UUID>> papeis() {
        return Map.of();
    }
}
