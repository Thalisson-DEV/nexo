package com.nexo.app.shared.infrastructure.observability;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ClassLoadingMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;

/**
 * Registra no log, a cada 30 minutos, o uso de memória da JVM.
 *
 * <p>O Actuator só é acessível pela rede privada, e o backend já caiu por
 * {@code OutOfMemoryError: Metaspace}. Com esta linha nos logs do Railway dá para ver se o
 * metaspace e o total de classes estabilizam depois do aquecimento ou continuam crescendo.</p>
 */
@Slf4j
@Component
public class MemoriaJvmLogger {

    private static final long MB = 1024 * 1024;

    @Scheduled(fixedDelay = 1_800_000, initialDelay = 300_000)
    public void registrar() {
        MemoryMXBean memoria = ManagementFactory.getMemoryMXBean();
        ClassLoadingMXBean classes = ManagementFactory.getClassLoadingMXBean();
        long metaspace = ManagementFactory.getMemoryPoolMXBeans().stream()
                .filter(pool -> "Metaspace".equals(pool.getName()))
                .mapToLong(pool -> pool.getUsage().getUsed())
                .sum();

        log.info("Memoria JVM: heap {} MB, metaspace {} MB, classes carregadas {} (total {}, descarregadas {}), threads {}",
                memoria.getHeapMemoryUsage().getUsed() / MB,
                metaspace / MB,
                classes.getLoadedClassCount(),
                classes.getTotalLoadedClassCount(),
                classes.getUnloadedClassCount(),
                ManagementFactory.getThreadMXBean().getThreadCount());
    }
}
