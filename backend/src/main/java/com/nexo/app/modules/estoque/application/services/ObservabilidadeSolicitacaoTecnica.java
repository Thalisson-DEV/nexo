package com.nexo.app.modules.estoque.application.services;

import com.nexo.app.modules.estoque.domain.EventoSolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.OrigemComandoTecnico;
import com.nexo.app.modules.estoque.domain.StatusIntegridadeHistorico;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.LockTimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Métricas operacionais do fluxo técnico.
 *
 * <p>Todas as tags pertencem a enumerações fechadas. Identificadores, ocorrência,
 * reserva, chave de idempotência, payload, hashes e assinaturas nunca viram tags.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ObservabilidadeSolicitacaoTecnica {

    static final String METRICA_CONFLITOS = "ces.estoque.solicitacao.tecnica.conflitos";
    static final String METRICA_FILHOS = "ces.estoque.solicitacao.tecnica.filhos";
    static final String METRICA_RESERVAS_INVALIDAS = "ces.estoque.solicitacao.tecnica.reservas.invalidas";
    static final String METRICA_CUSTODIA_INSUFICIENTE = "ces.estoque.solicitacao.tecnica.custodia.insuficiente";
    static final String METRICA_CONCORRENCIA = "ces.estoque.solicitacao.tecnica.concorrencia";
    static final String METRICA_IMPORTACAO = "ces.estoque.solicitacao.tecnica.importacao";
    static final String METRICA_INTEGRIDADE = "ces.estoque.solicitacao.tecnica.integridade";

    private final MeterRegistry meterRegistry;

    public void registrarConflito(Operacao operacao, TipoConflito tipo) {
        contador(METRICA_CONFLITOS, "Conflitos rejeitados no fluxo técnico",
                "operacao", operacao.name(), "tipo", tipo.name()).increment();
    }

    public void registrarFilhosCriados(OrigemComandoTecnico origem, int quantidade) {
        incrementar(contador(METRICA_FILHOS, "Filhos internos criados por submissão técnica",
                "acao", AcaoFilho.CRIADO.name(), "origem", origemSegura(origem)), quantidade);
    }

    public void registrarReservaInvalida(Operacao operacao, MotivoReservaInvalida motivo) {
        contador(METRICA_RESERVAS_INVALIDAS, "Reservas ausentes ou inválidas no fluxo técnico",
                "operacao", operacao.name(), "motivo", motivo.name()).increment();
    }

    public void registrarCustodiaInsuficiente(Operacao operacao, RecursoCustodia recurso) {
        contador(METRICA_CUSTODIA_INSUFICIENTE, "Rejeições por custódia insuficiente na obra",
                "operacao", operacao.name(), "recurso", recurso.name()).increment();
    }

    /**
     * Registra apenas falhas reconhecidas de aquisição de lock.
     *
     * @return {@code true} quando a exceção representa deadlock ou timeout de lock.
     */
    public boolean registrarFalhaConcorrencia(Operacao operacao, RuntimeException excecao) {
        TipoFalhaConcorrencia tipo = classificarFalhaConcorrencia(excecao);
        if (tipo == null) {
            return false;
        }
        contador(METRICA_CONCORRENCIA, "Falhas de lock no fluxo técnico",
                "operacao", operacao.name(), "tipo", tipo.name()).increment();
        return true;
    }

    public void registrarImportacao(EtapaImportacao etapa, ResultadoImportacao resultado, int quantidade) {
        incrementar(contador(METRICA_IMPORTACAO, "Resultados da importação de solicitações técnicas",
                "etapa", etapa.name(), "resultado", resultado.name()), quantidade);
    }

    public void registrarIntegridade(
            StatusIntegridadeHistorico status,
            List<EventoSolicitacaoMaterial> eventos
    ) {
        contador(METRICA_INTEGRIDADE, "Resultados de verificação da cadeia de auditoria técnica",
                "status", status.name()).increment();
        if (status != StatusIntegridadeHistorico.VIOLADO || eventos == null || eventos.isEmpty()) {
            return;
        }
        List<EventoSolicitacaoMaterial> ordenados = eventos.stream()
                .sorted(Comparator.comparingLong(EventoSolicitacaoMaterial::getSequencia))
                .toList();
        EventoSolicitacaoMaterial primeiro = ordenados.getFirst();
        EventoSolicitacaoMaterial ultimo = ordenados.getLast();
        UUID idCadeia = primeiro.getIdSolicitacao();
        log.error(
                "Integridade do historico tecnico violada: idSolicitacao={}, eventos={}, sequenciaInicial={}, sequenciaFinal={}",
                idCadeia, ordenados.size(), primeiro.getSequencia(), ultimo.getSequencia());
    }

    private Counter contador(String nome, String descricao, String... tags) {
        return Counter.builder(nome).description(descricao).tags(tags).register(meterRegistry);
    }

    private static void incrementar(Counter counter, int quantidade) {
        if (quantidade > 0) {
            counter.increment(quantidade);
        }
    }

    private static String origemSegura(OrigemComandoTecnico origem) {
        return origem == null ? OrigemComandoTecnico.SYSTEM.name() : origem.name();
    }

    private static TipoFalhaConcorrencia classificarFalhaConcorrencia(RuntimeException excecao) {
        Throwable atual = excecao;
        while (atual != null) {
            if (atual instanceof SQLException sqlException) {
                if ("40P01".equals(sqlException.getSQLState())) {
                    return TipoFalhaConcorrencia.DEADLOCK;
                }
                if ("55P03".equals(sqlException.getSQLState())
                        || "57014".equals(sqlException.getSQLState())) {
                    return TipoFalhaConcorrencia.LOCK_TIMEOUT;
                }
            }
            if (atual.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT).contains("deadlock")) {
                return TipoFalhaConcorrencia.DEADLOCK;
            }
            atual = atual.getCause();
        }
        if (excecao instanceof CannotAcquireLockException
                || excecao instanceof PessimisticLockingFailureException
                || excecao instanceof QueryTimeoutException
                || excecao instanceof LockTimeoutException) {
            return TipoFalhaConcorrencia.LOCK_TIMEOUT;
        }
        return null;
    }

    public enum Operacao {
        CRIACAO,
        PLANEJAMENTO,
        RESERVA,
        SEPARACAO,
        ENTREGA,
        DEVOLUCAO,
        ESTORNO,
        ENCERRAMENTO,
        IMPORTACAO
    }

    public enum TipoConflito {
        VERSAO,
        IDEMPOTENCIA
    }

    public enum AcaoFilho {
        CRIADO
    }

    public enum MotivoReservaInvalida {
        AUSENTE,
        FORMATO,
        AGENTE_INVALIDO,
        ALTERACAO_BLOQUEADA
    }

    public enum RecursoCustodia {
        SALDO,
        SERIE
    }

    public enum TipoFalhaConcorrencia {
        DEADLOCK,
        LOCK_TIMEOUT
    }

    public enum EtapaImportacao {
        VALIDACAO,
        CONFIRMACAO
    }

    public enum ResultadoImportacao {
        SUCESSO,
        COM_ERROS,
        FALHA
    }
}
