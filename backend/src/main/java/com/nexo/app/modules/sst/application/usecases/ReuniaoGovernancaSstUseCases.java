package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.sst.application.dto.ReuniaoGovernancaDto;
import com.nexo.app.modules.sst.domain.AlertaFaltaCipaSst;
import com.nexo.app.modules.sst.domain.ConvidadoReuniaoSst;
import com.nexo.app.modules.sst.domain.CorpoGovernancaSst;
import com.nexo.app.modules.sst.domain.MembroCorpoSst;
import com.nexo.app.modules.sst.domain.ModeloAtaSst;
import com.nexo.app.modules.sst.domain.ParticipacaoReuniaoSst;
import com.nexo.app.modules.sst.domain.ReuniaoGovernancaSst;
import com.nexo.app.modules.sst.domain.RevisaoTranscricaoSst;
import com.nexo.app.modules.sst.domain.StatusCorpoGovernancaSst;
import com.nexo.app.modules.sst.domain.StatusPresencaReuniaoSst;
import com.nexo.app.modules.sst.domain.StatusReuniaoGovernancaSst;
import com.nexo.app.modules.sst.domain.TipoCorpoGovernancaSst;
import com.nexo.app.modules.sst.domain.TipoSecretarioReuniaoSst;
import com.nexo.app.modules.sst.infrastructure.persistence.AlertaFaltaCipaSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.ConvidadoReuniaoSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.CorpoGovernancaSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.MembroCorpoSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.ModeloAtaSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.ParticipacaoReuniaoSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.ReuniaoGovernancaSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.ReuniaoGovernancaSstSpecification;
import com.nexo.app.modules.sst.infrastructure.persistence.RevisaoTranscricaoSstRepository;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReuniaoGovernancaSstUseCases {
    private static final EnumSet<StatusReuniaoGovernancaSst> STATUS_FALTA_COMPUTAVEL = EnumSet.of(
            StatusReuniaoGovernancaSst.AGUARDANDO_ATA,
            StatusReuniaoGovernancaSst.AGUARDANDO_ASSINATURAS,
            StatusReuniaoGovernancaSst.FINALIZADA);

    private final ReuniaoGovernancaSstRepository reuniaoRepository;
    private final CorpoGovernancaSstRepository corpoRepository;
    private final MembroCorpoSstRepository membroRepository;
    private final ModeloAtaSstRepository modeloRepository;
    private final ParticipacaoReuniaoSstRepository participacaoRepository;
    private final ConvidadoReuniaoSstRepository convidadoRepository;
    private final RevisaoTranscricaoSstRepository transcricaoRepository;
    private final AlertaFaltaCipaSstRepository alertaRepository;

    @Transactional
    public ReuniaoGovernancaDto.Response agendar(ReuniaoGovernancaDto.AgendarRequest request,
                                                  UUID idUsuario, boolean podeRegistrarHistorico) {
        exigirAtor(idUsuario);
        CorpoGovernancaSst corpo = buscarCorpo(request.idCorpo());
        LocalDate data = request.dataHora().toLocalDate();
        boolean dentroVigencia = !data.isBefore(corpo.getInicioPeriodo()) && !data.isAfter(corpo.getFimPeriodo());
        if (!dentroVigencia && (!request.registroHistorico() || !podeRegistrarHistorico)) {
            throw new InvarianteVioladaException(
                    "A reunião está fora da vigência. Use o registro histórico autorizado e informe a justificativa.");
        }
        if (dentroVigencia && corpo.getStatus() != StatusCorpoGovernancaSst.ATIVO
                && !request.registroHistorico()) {
            throw new InvarianteVioladaException("Somente uma composição ativa pode receber nova reunião.");
        }
        ModeloAtaSst modelo = buscarModeloAplicavel(request.idModeloAta(), corpo);
        ReuniaoGovernancaSst reuniao = ReuniaoGovernancaSst.agendar(
                corpo.getId(), corpo.getIdBaseOperacional(), corpo.getTipo(), request.tipo(),
                request.dataHora(), request.duracaoMinutos(), request.localOuMeio(), request.pauta(),
                request.idResponsavel() == null ? idUsuario : request.idResponsavel(),
                request.registroHistorico(), request.justificativaHistorico(), request.motivoExtraordinaria(),
                request.dataFatoGerador(), request.origemExtraordinaria());
        reuniao.selecionarModeloAta(modelo.getId(), modelo.getRevisao(), modelo.criarSnapshot());
        return toResponse(reuniaoRepository.save(reuniao));
    }

    @Transactional(readOnly = true)
    public PageResponse<ReuniaoGovernancaDto.Response> listar(ReuniaoGovernancaDto.Filter filtro,
                                                               Pageable pageable) {
        if (filtro != null && filtro.dataInicio() != null && filtro.dataFim() != null
                && filtro.dataFim().isBefore(filtro.dataInicio())) {
            throw new InvarianteVioladaException("O fim do período deve ser posterior ao início.");
        }
        return PageResponse.fromPage(reuniaoRepository.findAll(
                ReuniaoGovernancaSstSpecification.comFiltros(filtro), pageable).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public ReuniaoGovernancaDto.Response buscar(UUID idReuniao) {
        return toResponse(buscarReuniao(idReuniao));
    }

    @Transactional
    public ReuniaoGovernancaDto.Response selecionarModelo(UUID idReuniao, UUID idModelo) {
        ReuniaoGovernancaSst reuniao = buscarReuniaoParaAtualizar(idReuniao);
        ModeloAtaSst modelo = modeloRepository.findByIdAndAtivoTrue(idModelo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Modelo de ata não encontrado."));
        if (!modelo.aplicavelA(reuniao.getTipoCorpo(), reuniao.getIdBaseOperacional())) {
            throw new InvarianteVioladaException("O modelo não está publicado ou não se aplica à reunião.");
        }
        reuniao.selecionarModeloAta(modelo.getId(), modelo.getRevisao(), modelo.criarSnapshot());
        return toResponse(reuniaoRepository.save(reuniao));
    }

    @Transactional
    public ReuniaoGovernancaDto.Response iniciar(UUID idReuniao) {
        ReuniaoGovernancaSst reuniao = buscarReuniaoParaAtualizar(idReuniao);
        List<MembroCorpoSst> membros = membroRepository.buscarAtivosNoCorpoEm(
                reuniao.getIdCorpo(), reuniao.getDataHora().toLocalDate());
        if (membros.isEmpty()) {
            throw new InvarianteVioladaException("A composição não possui integrantes ativos na data da reunião.");
        }
        reuniao.iniciar();
        for (MembroCorpoSst membro : membros) {
            if (!participacaoRepository.existsByIdReuniaoAndIdMembroCorpoAndAtivoTrue(
                    reuniao.getId(), membro.getId())) {
                participacaoRepository.save(toParticipacao(reuniao, membro));
            }
        }
        return toResponse(reuniaoRepository.save(reuniao));
    }

    @Transactional(readOnly = true)
    public List<ReuniaoGovernancaDto.ParticipacaoResponse> listarParticipacoes(UUID idReuniao) {
        buscarReuniao(idReuniao);
        return participacaoRepository.findAllByIdReuniaoAndAtivoTrueOrderByNomeSnapshot(idReuniao)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public ReuniaoGovernancaDto.ParticipacaoResponse registrarPresenca(
            UUID idReuniao, UUID idParticipacao, ReuniaoGovernancaDto.PresencaRequest request,
            UUID idUsuario) {
        ReuniaoGovernancaSst reuniao = buscarReuniaoParaAtualizar(idReuniao);
        if (!reuniao.permiteAlterarParticipantes()) {
            throw new InvarianteVioladaException("A presença só pode ser alterada durante a reunião.");
        }
        ParticipacaoReuniaoSst participacao = participacaoRepository
                .findByIdAndIdReuniaoAndAtivoTrue(idParticipacao, idReuniao)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Participante não encontrado na reunião."));
        participacao.registrarPresenca(request.presenca(), request.justificativa(), idUsuario);
        return toResponse(participacaoRepository.save(participacao));
    }

    @Transactional
    public ReuniaoGovernancaDto.ConvidadoResponse adicionarConvidado(
            UUID idReuniao, ReuniaoGovernancaDto.ConvidadoRequest request) {
        ReuniaoGovernancaSst reuniao = buscarReuniaoParaAtualizar(idReuniao);
        if (!reuniao.permiteAlterarParticipantes()) {
            throw new InvarianteVioladaException("Convidados só podem ser incluídos durante a reunião.");
        }
        ConvidadoReuniaoSst convidado = new ConvidadoReuniaoSst(idReuniao, request.idColaborador(),
                request.nome(), request.funcao(), request.organizacao(), request.presente());
        return toResponse(convidadoRepository.save(convidado));
    }

    @Transactional(readOnly = true)
    public List<ReuniaoGovernancaDto.ConvidadoResponse> listarConvidados(UUID idReuniao) {
        buscarReuniao(idReuniao);
        return convidadoRepository.findAllByIdReuniaoAndAtivoTrueOrderByNomeSnapshot(idReuniao)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public ReuniaoGovernancaDto.Response designarSecretario(
            UUID idReuniao, ReuniaoGovernancaDto.SecretarioRequest request) {
        ReuniaoGovernancaSst reuniao = buscarReuniaoParaAtualizar(idReuniao);
        if (request.tipo() == TipoSecretarioReuniaoSst.PARTICIPANTE) {
            participacaoRepository.findByIdAndIdReuniaoAndAtivoTrue(request.idPessoaNaReuniao(), idReuniao)
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Participante não encontrado na reunião."));
            reuniao.designarSecretarioParticipante(request.idPessoaNaReuniao());
        } else {
            convidadoRepository.findByIdAndIdReuniaoAndAtivoTrue(request.idPessoaNaReuniao(), idReuniao)
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Convidado não encontrado na reunião."));
            reuniao.designarSecretarioConvidado(request.idPessoaNaReuniao());
        }
        return toResponse(reuniaoRepository.save(reuniao));
    }

    @Transactional
    public ReuniaoGovernancaDto.Response concluirPresencas(UUID idReuniao) {
        ReuniaoGovernancaSst reuniao = buscarReuniaoParaAtualizar(idReuniao);
        long pendentes = participacaoRepository.countByIdReuniaoAndAtivoTrueAndPresenca(
                idReuniao, StatusPresencaReuniaoSst.NAO_REGISTRADA);
        validarSecretarioPresente(reuniao);
        reuniao.concluirPresencas(pendentes);
        reuniaoRepository.saveAndFlush(reuniao);
        emitirAlertasQuintaFalta(reuniao);
        return toResponse(reuniao);
    }

    @Transactional
    public ReuniaoGovernancaDto.TranscricaoResponse salvarTranscricao(
            UUID idReuniao, ReuniaoGovernancaDto.TranscricaoRequest request) {
        ReuniaoGovernancaSst reuniao = buscarReuniaoParaAtualizar(idReuniao);
        int numero = reuniao.reservarNumeroRevisaoTranscricao();
        RevisaoTranscricaoSst revisao = new RevisaoTranscricaoSst(idReuniao, numero, request.origem(),
                request.conteudo(), ArquivoPrivadoReuniao.sha256(request.conteudo().trim()));
        reuniaoRepository.save(reuniao);
        return toResponse(transcricaoRepository.save(revisao));
    }

    @Transactional
    public ReuniaoGovernancaDto.TranscricaoResponse confirmarTranscricao(
            UUID idReuniao, UUID idRevisao, UUID idUsuario) {
        ReuniaoGovernancaSst reuniao = buscarReuniaoParaAtualizar(idReuniao);
        if (!reuniao.getStatus().permiteConteudo()) {
            throw new InvarianteVioladaException("O estado da reunião não permite confirmar a transcrição.");
        }
        RevisaoTranscricaoSst revisao = transcricaoRepository
                .findByIdAndIdReuniaoAndAtivoTrue(idRevisao, idReuniao)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Revisão de transcrição não encontrada."));
        revisao.confirmar(idUsuario);
        return toResponse(transcricaoRepository.save(revisao));
    }

    @Transactional(readOnly = true)
    public List<ReuniaoGovernancaDto.TranscricaoResponse> listarTranscricoes(UUID idReuniao) {
        buscarReuniao(idReuniao);
        return transcricaoRepository.findAllByIdReuniaoAndAtivoTrueOrderByNumeroDesc(idReuniao)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public ReuniaoGovernancaDto.Response cancelar(UUID idReuniao, String motivo, UUID idUsuario) {
        ReuniaoGovernancaSst reuniao = buscarReuniaoParaAtualizar(idReuniao);
        reuniao.cancelar(motivo, idUsuario);
        return toResponse(reuniaoRepository.save(reuniao));
    }

    @Transactional(readOnly = true)
    public List<ReuniaoGovernancaDto.AlertaFaltaResponse> listarAlertas(UUID idCorpo) {
        buscarCorpo(idCorpo);
        return alertaRepository.findAllByIdCorpoAndAtivoTrueOrderByEmitidoEmDesc(idCorpo).stream()
                .map(a -> new ReuniaoGovernancaDto.AlertaFaltaResponse(a.getId(), a.getIdMembroCorpo(),
                        a.getQuantidadeFaltas(), participacaoRepository.findIdsReunioesFaltasCipaComputaveis(
                                idCorpo, a.getIdMembroCorpo(), STATUS_FALTA_COMPUTAVEL),
                        a.getEmitidoEm(), a.getConferidoEm())).toList();
    }

    private void emitirAlertasQuintaFalta(ReuniaoGovernancaSst reuniao) {
        if (reuniao.getTipoCorpo() != TipoCorpoGovernancaSst.CIPA) return;
        for (ParticipacaoReuniaoSst p : participacaoRepository
                .findAllByIdReuniaoAndAtivoTrueOrderByNomeSnapshot(reuniao.getId())) {
            if (!p.faltaOrdinariaInjustificadaComputavel(reuniao.getTipoCorpo(), reuniao.getTipo())) continue;
            long total = participacaoRepository.countFaltasCipaComputaveis(
                    reuniao.getIdCorpo(), p.getIdMembroCorpo(), STATUS_FALTA_COMPUTAVEL);
            if (total >= 5) {
                AlertaFaltaCipaSst alerta = alertaRepository
                        .findByIdCorpoAndIdMembroCorpoAndAtivoTrue(reuniao.getIdCorpo(), p.getIdMembroCorpo())
                        .orElseGet(() -> new AlertaFaltaCipaSst(reuniao.getIdCorpo(), p.getIdMembroCorpo(),
                                reuniao.getId(), Math.toIntExact(total)));
                alerta.atualizarContagem(Math.toIntExact(total));
                alertaRepository.save(alerta);
            }
        }
    }

    private void validarSecretarioPresente(ReuniaoGovernancaSst reuniao) {
        if (!reuniao.exigeSecretario() || reuniao.getTipoSecretario() == null) return;
        if (reuniao.getTipoSecretario() == TipoSecretarioReuniaoSst.PARTICIPANTE) {
            ParticipacaoReuniaoSst p = participacaoRepository.findByIdAndIdReuniaoAndAtivoTrue(
                    reuniao.getIdSecretarioParticipacao(), reuniao.getId())
                    .orElseThrow(() -> new InvarianteVioladaException("O secretário não pertence à reunião."));
            if (p.getPresenca() != StatusPresencaReuniaoSst.PRESENTE)
                throw new InvarianteVioladaException("O secretário designado deve estar presente.");
        } else {
            ConvidadoReuniaoSst c = convidadoRepository.findByIdAndIdReuniaoAndAtivoTrue(
                    reuniao.getIdSecretarioConvidado(), reuniao.getId())
                    .orElseThrow(() -> new InvarianteVioladaException("O secretário não pertence à reunião."));
            if (!c.isPresente()) throw new InvarianteVioladaException("O secretário designado deve estar presente.");
        }
    }

    private ModeloAtaSst buscarModeloAplicavel(UUID id, CorpoGovernancaSst corpo) {
        ModeloAtaSst modelo = modeloRepository.findByIdAndAtivoTrue(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Modelo de ata não encontrado."));
        if (!modelo.aplicavelA(corpo.getTipo(), corpo.getIdBaseOperacional())) {
            throw new InvarianteVioladaException("O modelo não está publicado ou não se aplica à composição.");
        }
        return modelo;
    }

    private CorpoGovernancaSst buscarCorpo(UUID id) {
        return corpoRepository.findByIdAndAtivoTrue(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Composição de governança não encontrada."));
    }

    private ReuniaoGovernancaSst buscarReuniao(UUID id) {
        return reuniaoRepository.findByIdAndAtivoTrue(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Reunião de governança não encontrada."));
    }

    private ReuniaoGovernancaSst buscarReuniaoParaAtualizar(UUID id) {
        return reuniaoRepository.findAtivaByIdForUpdate(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Reunião de governança não encontrada."));
    }

    private ParticipacaoReuniaoSst toParticipacao(ReuniaoGovernancaSst r, MembroCorpoSst m) {
        return new ParticipacaoReuniaoSst(r.getId(), m.getId(), m.getIdColaborador(), m.getNomeSnapshot(),
                m.getFuncaoSnapshot(), m.getPapel().name(),
                m.getRepresentacao() == null ? null : m.getRepresentacao().name(),
                m.getTitularidade() == null ? null : m.getTitularidade().name(), m.getTurnoSnapshot());
    }

    private ReuniaoGovernancaDto.Response toResponse(ReuniaoGovernancaSst r) {
        return new ReuniaoGovernancaDto.Response(r.getId(), r.getIdCorpo(), r.getIdBaseOperacional(),
                r.getTipoCorpo(), r.getTipo(), r.getStatus(), r.getDataHora(), r.getDuracaoMinutos(),
                r.getLocalOuMeio(), r.getPauta(),
                r.getIdResponsavel(), r.getIdModeloAta(), r.getRevisaoModeloAta(), r.getSnapshotModeloAta(),
                r.isRegistroHistorico(), r.getJustificativaHistorico(), r.getMotivoExtraordinaria(),
                r.getDataFatoGerador(), r.getOrigemExtraordinaria(), r.getTipoSecretario(),
                r.getIdSecretarioParticipacao(), r.getIdSecretarioConvidado(), r.getIdVersaoAtaAprovada(),
                r.getIniciadaEm(), r.getPresencasConcluidasEm(), r.getFinalizadaEm(), r.getVersao(), r.getDataCriacao());
    }

    private ReuniaoGovernancaDto.ParticipacaoResponse toResponse(ParticipacaoReuniaoSst p) {
        return new ReuniaoGovernancaDto.ParticipacaoResponse(p.getId(), p.getIdMembroCorpo(), p.getIdColaborador(),
                p.getNomeSnapshot(), p.getFuncaoSnapshot(), p.getPapelSnapshot(), p.getRepresentacaoCipaSnapshot(),
                p.getTitularidadeCipaSnapshot(), p.getTurnoSnapshot(), p.getPresenca(),
                p.getJustificativaAusencia(), p.getClassificadaEm());
    }

    private ReuniaoGovernancaDto.ConvidadoResponse toResponse(ConvidadoReuniaoSst c) {
        return new ReuniaoGovernancaDto.ConvidadoResponse(c.getId(), c.getIdColaborador(), c.getNomeSnapshot(),
                c.getFuncaoSnapshot(), c.getOrganizacaoSnapshot(), c.isPresente());
    }

    private ReuniaoGovernancaDto.TranscricaoResponse toResponse(RevisaoTranscricaoSst t) {
        return new ReuniaoGovernancaDto.TranscricaoResponse(t.getId(), t.getNumero(), t.getOrigem(), t.getConteudo(),
                t.getHashConteudo(), t.confirmada(), t.getConfirmadaEm(), t.getDataCriacao());
    }

    private static void exigirAtor(UUID idUsuario) {
        if (idUsuario == null) throw new InvarianteVioladaException("Não foi possível identificar o usuário autenticado.");
    }
}
