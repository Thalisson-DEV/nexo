package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.obras.application.port.RotaRodoviariaPort;
import com.nexo.app.modules.obras.application.port.RotaRodoviariaPort.Coordenada;
import com.nexo.app.modules.obras.domain.Obra;
import com.nexo.app.modules.obras.infrastructure.persistence.ObraRepository;
import com.nexo.app.modules.rh.BaseOperacionalApi;
import com.nexo.app.modules.rh.BaseOperacionalResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Calcula o deslocamento rodoviário da base operacional da obra até as coordenadas da obra.
 *
 * <p>Usa o motor de rotas quando configurado; sem ele (ou em falha) estima pela distância em linha reta
 * multiplicada pelo fator de sinuosidade e por uma velocidade média, marcando o resultado como estimado.
 * Um agendamento recalcula as obras cuja base ou coordenadas mudaram desde o último cálculo.</p>
 */
@Slf4j
@Service
public class DeslocamentoObraService {

    private static final double RAIO_TERRA_KM = 6371.0088;
    private static final int LOTE = 100;

    private final ObraRepository obraRepository;
    private final BaseOperacionalApi baseOperacionalApi;
    private final RotaRodoviariaPort rotas;
    private final TransactionTemplate transacao;
    private final double fatorSinuosidade;
    private final double velocidadeMediaKmh;

    public DeslocamentoObraService(ObraRepository obraRepository,
                                   BaseOperacionalApi baseOperacionalApi,
                                   RotaRodoviariaPort rotas,
                                   PlatformTransactionManager transacoes,
                                   @Value("${ces.obras.rotas.fator-sinuosidade:1.3}") double fatorSinuosidade,
                                   @Value("${ces.obras.rotas.velocidade-media-kmh:45}") double velocidadeMediaKmh) {
        this.obraRepository = obraRepository;
        this.baseOperacionalApi = baseOperacionalApi;
        this.rotas = rotas;
        this.transacao = new TransactionTemplate(transacoes);
        this.fatorSinuosidade = fatorSinuosidade;
        this.velocidadeMediaKmh = velocidadeMediaKmh;
    }

    /**
     * Recalcula o deslocamento da obra (sem verificar se está desatualizado). Obra sem coordenadas ou base
     * sem coordenadas fica com deslocamento não calculado.
     */
    public void recalcular(Obra obra) {
        if (!obra.possuiCoordenadas()) {
            obra.limparDeslocamento();
            return;
        }
        Optional<BaseOperacionalResponse> base = baseOperacionalApi.buscarPorIdResponse(obra.getIdBaseOperacional())
                .filter(b -> b.latitude() != null && b.longitude() != null);
        if (base.isEmpty()) {
            obra.limparDeslocamento();
            return;
        }
        Coordenada origem = new Coordenada(base.get().latitude(), base.get().longitude());
        Coordenada destino = new Coordenada(obra.getLatitude(), obra.getLongitude());
        Optional<RotaRodoviariaPort.Rota> rota = rotas.calcular(origem, destino);
        if (rota.isPresent()) {
            obra.registrarDeslocamento(rota.get().distanciaKm(), rota.get().tempoMinutos(), false);
            return;
        }
        double km = distanciaKm(origem, destino) * fatorSinuosidade;
        int minutos = (int) Math.ceil(km / velocidadeMediaKmh * 60d);
        obra.registrarDeslocamento(BigDecimal.valueOf(km).setScale(2, RoundingMode.HALF_UP), minutos, true);
    }

    @Scheduled(fixedDelayString = "${ces.obras.rotas.recalculo-intervalo:PT10M}",
            initialDelayString = "${ces.obras.rotas.recalculo-atraso-inicial:PT2M}")
    public void recalcularDesatualizados() {
        List<UUID> ids = obraRepository.buscarIdsComDeslocamentoDesatualizado(PageRequest.of(0, LOTE));
        for (UUID id : ids) {
            try {
                transacao.executeWithoutResult(status -> obraRepository.findById(id).ifPresent(obra -> {
                    recalcular(obra);
                    obraRepository.save(obra);
                }));
            } catch (RuntimeException e) {
                log.warn("Não foi possível recalcular o deslocamento da obra {}: {}", id, e.getMessage());
            }
        }
        if (!ids.isEmpty()) {
            log.info("Deslocamento recalculado para {} obra(s)", ids.size());
        }
    }

    /** Distância em linha reta (haversine), em km. */
    public static double distanciaKm(Coordenada a, Coordenada b) {
        double dLat = Math.toRadians(b.latitude() - a.latitude());
        double dLng = Math.toRadians(b.longitude() - a.longitude());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(a.latitude())) * Math.cos(Math.toRadians(b.latitude()))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * RAIO_TERRA_KM * Math.asin(Math.min(1, Math.sqrt(h)));
    }
}
