package com.nexo.app.modules.rh.infrastructure.persistence;

import com.nexo.app.modules.rh.domain.Colaborador;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Backfill idempotente da coluna {@code nome_busca} dos colaboradores existentes.
 *
 * <p>O {@code nome_completo} é cifrado em repouso, então o preenchimento não pode ser feito em
 * SQL puro (migration). Este runner carrega os colaboradores cujo {@code nomeBusca} ainda é nulo
 * (já descriptografados pelo {@code CryptoConverterImpl}), recalcula o nome de busca e salva.</p>
 *
 * <p>Como só atua sobre linhas com {@code nomeBusca == null}, após a primeira execução completa
 * ele se torna um no-op nos próximos startups e pode ser removido com segurança no futuro.</p>
 */
@Component
public class NomeBuscaBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(NomeBuscaBackfillRunner.class);
    private static final int BATCH_SIZE = 200;

    private final ColaboradorRepository repository;

    public NomeBuscaBackfillRunner(ColaboradorRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        long total = 0;
        // Sempre busca a primeira página de pendentes; ao salvar, elas deixam de ser nulas,
        // então a próxima leitura traz o lote seguinte até esgotar.
        Page<Colaborador> pendentes;
        do {
            pendentes = repository.findByNomeBuscaIsNull(PageRequest.of(0, BATCH_SIZE));
            pendentes.forEach(Colaborador::backfillNomeBusca);
            repository.saveAll(pendentes.getContent());
            repository.flush();
            total += pendentes.getNumberOfElements();
        } while (!pendentes.isEmpty());

        if (total > 0) {
            log.info("Backfill de nome_busca concluído para {} colaborador(es).", total);
        }
    }
}
