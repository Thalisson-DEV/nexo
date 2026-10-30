package com.nexo.app.shared.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;

import java.util.concurrent.Executors;

@Configuration
public class AsyncConfig {

    /**
     * Define o executor de tarefas padrão como um executor de Virtual Threads.
     * Isso resolve a ambiguidade reportada no log devido à presença de múltiplos
     * executores (WebSocket, etc) e garante o uso de Loom para processamento assíncrono.
     */
    @Bean(name = "taskExecutor")
    @Primary
    public AsyncTaskExecutor taskExecutor() {
        return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
    }
}
