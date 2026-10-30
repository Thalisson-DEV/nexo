package com.nexo.app.modules.execucao;

import com.nexo.app.modules.execucao.application.dto.ExecucaoComercialResponse;
import com.nexo.app.modules.execucao.application.dto.LancamentoDoTurno;
import com.nexo.app.modules.execucao.application.dto.LancamentoResumido;
import com.nexo.app.modules.execucao.application.dto.ProducaoDoTurno;
import com.nexo.app.modules.execucao.application.dto.RegistrarExecucaoRequest;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ExecucaoComercialApi {
    ExecucaoComercialResponse registrar(RegistrarExecucaoRequest request, UUID idUsuarioOperador);

    ExecucaoComercialResponse buscarPorId(UUID id);

    List<ExecucaoComercialResponse> listarPorTurno(UUID idTurno);

    /** Lançamentos ativos de um turno em ordem cronológica, sem hidratação. */
    List<LancamentoDoTurno> listarLancamentosDoTurno(UUID idTurno);

    /** Notas concluídas e produção de cada turno informado; turnos sem nota não aparecem. */
    List<ProducaoDoTurno> resumirPorTurnos(Collection<UUID> idsTurnos);

    /**
     * Lançamentos concluídos das equipes informadas com data de lançamento em
     * {@code [inicio, fim)}, em ordem cronológica e sem hidratação. Equipes vazias
     * resultam em lista vazia.
     */
    List<LancamentoResumido> listarLancamentosResumidos(OffsetDateTime inicio, OffsetDateTime fim,
                                                       Collection<UUID> idsEquipes);

    /**
     * Notas concluídas das equipes informadas com data de lançamento em {@code [inicio, fim)},
     * hidratadas e paginadas — as mesmas que os dashboards somam. Opcionalmente restritas a um
     * tipo de serviço ou às notas sem tipo. Equipes vazias resultam em página vazia.
     */
    Page<ExecucaoComercialResponse> listarConcluidasDasEquipes(Collection<UUID> idsEquipes, OffsetDateTime inicio,
                                                              OffsetDateTime fim, UUID idTipoServico,
                                                              boolean semTipoServico, Pageable pageable);

    /** Referência e tipo de serviço das execuções informadas; ids inexistentes não aparecem. */
    List<NotaResumida> resumirNotas(Collection<UUID> idsExecucoes);
}
