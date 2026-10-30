package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.sst.domain.EvidenciaInspecao;
import com.nexo.app.modules.sst.infrastructure.persistence.EvidenciaInspecaoRepository;
import com.nexo.app.shared.infrastructure.storage.ObjectStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Recolhe as fotos que subiram mas nunca viraram inspeção.
 *
 * <p>Como a evidência é enviada antes do lançamento, todo rascunho abandonado deixa um
 * objeto pago no bucket e uma linha inútil no banco. A janela é generosa de propósito:
 * o inspetor pode subir as fotos, perder o sinal e só concluir o lançamento horas
 * depois. Foto já vinculada a uma inspeção nunca é tocada aqui.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExpurgoEvidenciasPendentesService {

    private final EvidenciaInspecaoRepository repository;
    private final ObjectStorageService objectStorage;

    @Value("${ces.sst.evidencias.retencao-pendentes-horas:72}")
    private int retencaoHoras;

    @Scheduled(cron = "${ces.sst.evidencias.expurgo-cron:0 45 3 * * *}")
    @Transactional
    public void expurgar() {
        OffsetDateTime corte = OffsetDateTime.now().minusHours(retencaoHoras);
        List<EvidenciaInspecao> pendentes = repository.findByInspecaoIsNullAndDataCriacaoBefore(corte);
        if (pendentes.isEmpty()) {
            return;
        }

        List<String> chaves = pendentes.stream().map(EvidenciaInspecao::getObjectKey).toList();
        repository.deleteAll(pendentes);

        int removidos = 0;
        for (String chave : chaves) {
            try {
                objectStorage.remover(chave);
                removidos++;
            } catch (RuntimeException e) {
                log.warn("Falha ao expurgar o objeto '{}' no R2: {}", chave, e.getMessage());
            }
        }

        log.info("Expurgo de evidências pendentes: {} registros com mais de {}h descartados ({} objetos removidos do R2).",
                pendentes.size(), retencaoHoras, removidos);
    }
}
