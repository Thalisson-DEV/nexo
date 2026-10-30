package com.nexo.app.modules.telemetria.application.usecases;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.app.modules.telemetria.application.dto.TelemetriaMobileDtos.EventoRequest;
import com.nexo.app.modules.telemetria.application.dto.TelemetriaMobileDtos.LoteRequest;
import com.nexo.app.modules.telemetria.application.dto.TelemetriaMobileDtos.LoteResponse;
import com.nexo.app.modules.telemetria.infrastructure.persistence.TelemetriaMobileRepository;
import com.nexo.app.modules.telemetria.infrastructure.persistence.TelemetriaMobileRepository.Linha;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Recebe a telemetria técnica do app de campo.
 *
 * <p>LGPD por construção: o servidor não confia no aparelho para "não mandar dado
 * pessoal". Só sobrevivem chaves curtas com valores primitivos; textos são cortados
 * em 80 caracteres; objetos e listas são descartados. Nenhum vínculo com o usuário
 * autenticado é gravado.</p>
 *
 * @author <b>Thalisson</b>
 */
@Slf4j
@Service
public class TelemetriaMobileUseCases {

    static final int MAX_CHAVES = 20;
    static final int MAX_TEXTO = 80;
    static final Duration JANELA_PASSADO = Duration.ofDays(30);
    static final Duration JANELA_FUTURO = Duration.ofDays(1);
    static final Duration RETENCAO = Duration.ofDays(90);
    private static final Pattern CHAVE_VALIDA = Pattern.compile("^[a-zA-Z][a-zA-Z0-9]{0,30}$");

    private final TelemetriaMobileRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public TelemetriaMobileUseCases(TelemetriaMobileRepository repository, ObjectMapper objectMapper) {
        // Relógio próprio: não depende do bean Clock de outro módulo.
        this(repository, objectMapper, Clock.systemUTC());
    }

    TelemetriaMobileUseCases(TelemetriaMobileRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public LoteResponse receber(LoteRequest lote) {
        OffsetDateTime agora = OffsetDateTime.now(clock);
        List<Linha> linhas = new ArrayList<>(lote.eventos().size());
        int descartados = 0;
        for (EventoRequest evento : lote.eventos()) {
            if (evento.em().isBefore(agora.minus(JANELA_PASSADO)) || evento.em().isAfter(agora.plus(JANELA_FUTURO))) {
                descartados++;
                continue;
            }
            linhas.add(new Linha(evento.em(), evento.tipo(), vazioParaNulo(lote.idAparelho()),
                    vazioParaNulo(lote.versaoApp()), serializar(sanitizar(evento.dados()))));
        }
        return new LoteResponse(repository.inserir(linhas), descartados);
    }

    /** Expurgo diário (03:15, horário do servidor): retenção de 90 dias. */
    @Scheduled(cron = "0 15 3 * * *")
    @Transactional
    public void expurgar() {
        int removidos = repository.expurgarAntesDe(OffsetDateTime.now(clock).minus(RETENCAO));
        if (removidos > 0) {
            log.info("Telemetria mobile: {} eventos expurgados (retenção de {} dias)", removidos, RETENCAO.toDays());
        }
    }

    static Map<String, Object> sanitizar(Map<String, Object> dados) {
        Map<String, Object> limpo = new LinkedHashMap<>();
        if (dados == null) {
            return limpo;
        }
        for (var entrada : dados.entrySet()) {
            if (limpo.size() >= MAX_CHAVES) {
                break;
            }
            String chave = entrada.getKey();
            Object valor = entrada.getValue();
            if (chave == null || !CHAVE_VALIDA.matcher(chave).matches()) {
                continue;
            }
            if (valor instanceof String texto) {
                limpo.put(chave, texto.length() > MAX_TEXTO ? texto.substring(0, MAX_TEXTO) : texto);
            } else if (valor instanceof Number || valor instanceof Boolean) {
                limpo.put(chave, valor);
            }
        }
        return limpo;
    }

    private String serializar(Map<String, Object> dados) {
        try {
            return objectMapper.writeValueAsString(dados);
        } catch (JsonProcessingException e) {
            // Mapa de primitivos já sanitizado: não deveria falhar. Grava vazio em vez de perder o evento.
            return "{}";
        }
    }

    private static String vazioParaNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor;
    }
}
