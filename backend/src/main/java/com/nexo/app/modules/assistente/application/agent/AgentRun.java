package com.nexo.app.modules.assistente.application.agent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * Execucao do agente desacoplada da conexao HTTP (mesmo desenho do Onyx): o loop roda em thread propria
 * e publica pacotes num buffer; conexoes leem a partir de um cursor e podem retomar apos queda. Se o
 * cliente desconectar, a execucao continua e persiste o resultado.
 */
public final class AgentRun {

    private final UUID id = UUID.randomUUID();
    private final UUID conversaId;
    private final UUID usuarioId;
    private final UUID mensagemUsuarioId;
    private final UUID mensagemAssistenteId;
    private final UUID clientMessageId;
    private final Instant iniciadoEm = Instant.now();
    private final Function<Packet, String> serializer;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition changed = lock.newCondition();
    private final List<Packet> packets = new ArrayList<>();
    private final List<String> lines = new ArrayList<>();
    private final AtomicBoolean cancelRequested = new AtomicBoolean(false);
    private volatile boolean finished;
    private volatile Instant finalizadoEm;

    public AgentRun(UUID conversaId, UUID usuarioId, UUID mensagemUsuarioId, UUID mensagemAssistenteId,
            UUID clientMessageId, Function<Packet, String> serializer) {
        this.conversaId = Objects.requireNonNull(conversaId);
        this.usuarioId = Objects.requireNonNull(usuarioId);
        this.mensagemUsuarioId = mensagemUsuarioId;
        this.mensagemAssistenteId = Objects.requireNonNull(mensagemAssistenteId);
        this.clientMessageId = clientMessageId;
        this.serializer = Objects.requireNonNull(serializer);
    }

    public void emit(Packet packet) {
        String line = serializer.apply(packet);
        lock.lock();
        try {
            if (finished) return;
            packets.add(packet);
            lines.add(line);
            changed.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Retorna as linhas NDJSON a partir do cursor, aguardando ate {@code waitMillis} por novidades.
     * Lista vazia com {@link #isFinished()} verdadeiro indica fim do stream.
     */
    public List<String> readFrom(int cursor, long waitMillis) throws InterruptedException {
        lock.lock();
        try {
            long remaining = TimeUnit.MILLISECONDS.toNanos(waitMillis);
            while (lines.size() <= cursor && !finished && remaining > 0) {
                remaining = changed.awaitNanos(remaining);
            }
            if (lines.size() <= cursor) return List.of();
            return List.copyOf(lines.subList(Math.max(0, cursor), lines.size()));
        } finally {
            lock.unlock();
        }
    }

    public void finish() {
        lock.lock();
        try {
            finished = true;
            finalizadoEm = Instant.now();
            changed.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public List<Packet> snapshotPackets() {
        lock.lock();
        try {
            return List.copyOf(packets);
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return lines.size();
        } finally {
            lock.unlock();
        }
    }

    public boolean requestCancel() {
        return cancelRequested.compareAndSet(false, true);
    }

    public boolean isCancelRequested() {
        return cancelRequested.get();
    }

    public boolean isFinished() {
        return finished;
    }

    public UUID id() {
        return id;
    }

    public UUID conversaId() {
        return conversaId;
    }

    public UUID usuarioId() {
        return usuarioId;
    }

    public UUID mensagemUsuarioId() {
        return mensagemUsuarioId;
    }

    public UUID mensagemAssistenteId() {
        return mensagemAssistenteId;
    }

    public UUID clientMessageId() {
        return clientMessageId;
    }

    public Instant iniciadoEm() {
        return iniciadoEm;
    }

    public Instant finalizadoEm() {
        return finalizadoEm;
    }
}
