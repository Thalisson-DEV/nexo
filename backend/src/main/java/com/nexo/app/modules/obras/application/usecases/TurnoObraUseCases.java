package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.execucao.TurnoApi;
import com.nexo.app.modules.execucao.application.dto.TurnoResponse;
import com.nexo.app.modules.obras.application.dto.DownloadAnexoObraDTO;
import com.nexo.app.modules.obras.application.dto.TurnoObraRequests;
import com.nexo.app.modules.obras.application.dto.TurnoObraResponses;
import com.nexo.app.modules.obras.domain.AnexoTurno;
import com.nexo.app.modules.obras.domain.AprTurno;
import com.nexo.app.modules.obras.domain.Programacao;
import com.nexo.app.modules.obras.domain.AtividadeTurno;
import com.nexo.app.modules.obras.domain.OcorrenciaTurno;
import com.nexo.app.modules.obras.domain.RelatorioTurno;
import com.nexo.app.modules.obras.infrastructure.persistence.AnexoTurnoRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.AprTurnoRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.ProgramacaoRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.AtividadeTurnoRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.ObraRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.OcorrenciaTurnoRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.RelatorioTurnoRepository;
import com.nexo.app.modules.rh.EquipeApi;
import com.nexo.app.modules.rh.EquipeResponse;
import com.nexo.app.shared.domain.exception.ArmazenamentoException;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import com.nexo.app.shared.infrastructure.storage.ObjectStorageService;
import com.nexo.app.shared.infrastructure.storage.StorageObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * Registros da equipe no turno de Obras: atividade, relatório diário, ocorrências, APRs e arquivos. Quem lança é a
 * equipe, pelo app; o web só consulta. Só atende equipes dos processos que têm obras.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TurnoObraUseCases {

    private final TurnoApi turnoApi;
    private final EquipeApi equipeApi;
    private final ObraRepository obraRepository;
    private final AtividadeTurnoRepository atividadeRepository;
    private final RelatorioTurnoRepository relatorioRepository;
    private final OcorrenciaTurnoRepository ocorrenciaRepository;
    private final AprTurnoRepository aprRepository;
    private final ProgramacaoRepository programacaoRepository;
    private final AnexoTurnoRepository anexoRepository;
    private final ObjectStorageService objectStorage;

    @Transactional
    public void registrarAtividade(UUID id, TurnoObraRequests.Atividade request) {
        buscarTurnoDeObras(id);
        exigirObra(request.idObra());
        if (atividadeRepository.existsByIdTurnoAndIdObraAndIdProgramacaoIsNullAndAtivoTrue(id, request.idObra())) {
            throw new InvarianteVioladaException("Esta obra não programada já está nas atividades do turno.");
        }
        atividadeRepository.save(new AtividadeTurno(id, request.idObra(), null, request.observacao()));
    }

    @Transactional
    public void removerAtividade(UUID id, UUID idAtividade) {
        AtividadeTurno atividade = atividadeRepository.findByIdAndIdTurnoAndAtivoTrue(idAtividade, id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Atividade não encontrada neste turno."));
        if (atividade.isProgramada()) {
            throw new InvarianteVioladaException("A atividade programada vem dos apontamentos: estorne o apontamento para retirá-la.");
        }
        atividade.desativar();
        atividadeRepository.save(atividade);
    }

    @Transactional
    public void salvarRelatorio(UUID id, TurnoObraRequests.Relatorio request) {
        buscarTurnoDeObras(id);
        RelatorioTurno relatorio = relatorioRepository.findByIdTurno(id)
                .map(existente -> {
                    existente.informar(request.observacao());
                    return existente;
                })
                .orElseGet(() -> new RelatorioTurno(id, request.observacao()));
        relatorioRepository.save(relatorio);
    }

    @Transactional
    public void registrarOcorrencia(UUID id, TurnoObraRequests.Ocorrencia request) {
        buscarTurnoDeObras(id);
        ocorrenciaRepository.save(new OcorrenciaTurno(id, request.idObra(), request.tipo(), request.descricao(), request.impedeExecucao()));
    }

    @Transactional
    public void alterarOcorrencia(UUID id, UUID idOcorrencia, TurnoObraRequests.Ocorrencia request) {
        OcorrenciaTurno ocorrencia = buscarOcorrencia(id, idOcorrencia);
        ocorrencia.informar(request.idObra(), request.tipo(), request.descricao(), request.impedeExecucao());
        ocorrenciaRepository.save(ocorrencia);
    }

    @Transactional
    public void removerOcorrencia(UUID id, UUID idOcorrencia) {
        OcorrenciaTurno ocorrencia = buscarOcorrencia(id, idOcorrencia);
        ocorrencia.desativar();
        ocorrenciaRepository.save(ocorrencia);
    }

    @Transactional
    public void registrarApr(UUID id, TurnoObraRequests.Apr request) {
        exigirServicoDaEquipe(buscarTurnoDeObras(id), request);
        aprRepository.save(new AprTurno(id, request.idObra(), request.idProgramacao(), request.atividade(), request.idResponsavel(), request.formulario()));
    }

    @Transactional
    public void alterarApr(UUID id, UUID idApr, TurnoObraRequests.Apr request) {
        AprTurno apr = buscarApr(id, idApr);
        exigirServicoDaEquipe(buscarTurnoDeObras(id), request);
        apr.informar(request.idObra(), request.idProgramacao(), request.atividade(), request.idResponsavel(), request.formulario());
        aprRepository.save(apr);
    }

    @Transactional
    public void concluirApr(UUID id, UUID idApr) {
        AprTurno apr = buscarApr(id, idApr);
        apr.concluir();
        aprRepository.save(apr);
    }

    @Transactional
    public void removerApr(UUID id, UUID idApr) {
        AprTurno apr = buscarApr(id, idApr);
        apr.excluir();
        aprRepository.save(apr);
    }

    /** Sem {@code idApr}, é foto do relatório diário e precisa ser imagem. A APR aceita também PDF. */
    @Transactional
    public void anexar(UUID id, UUID idApr, MultipartFile arquivo) {
        buscarTurnoDeObras(id);
        if (arquivo == null || arquivo.isEmpty()) {
            throw new InvarianteVioladaException("O arquivo para upload não pode estar vazio.");
        }
        String contentType = arquivo.getContentType() != null ? arquivo.getContentType() : "application/octet-stream";
        boolean imagem = contentType.startsWith("image/");
        if (idApr != null) {
            buscarApr(id, idApr);
            if (!imagem && !"application/pdf".equals(contentType)) {
                throw new InvarianteVioladaException("Anexe à APR um PDF ou uma imagem.");
            }
        } else if (!imagem) {
            throw new InvarianteVioladaException("As fotos do relatório devem ser imagens.");
        }
        String nome = sanitizar(arquivo.getOriginalFilename());
        String chave = "obras/turnos/" + id + "/" + UUID.randomUUID() + "_" + nome;
        try (InputStream stream = arquivo.getInputStream()) {
            objectStorage.enviar(chave, stream, arquivo.getSize(), contentType);
        } catch (IOException e) {
            log.error("Erro ao ler o arquivo '{}' do turno {}", nome, id, e);
            throw new ArmazenamentoException("Falha ao processar arquivo para envio ao armazenamento.", e);
        }
        anexoRepository.save(new AnexoTurno(id, idApr, nome, contentType, arquivo.getSize(), chave));
    }

    @Transactional(readOnly = true)
    public DownloadAnexoObraDTO baixarAnexo(UUID id, UUID idAnexo) {
        AnexoTurno anexo = buscarAnexo(id, idAnexo);
        StorageObject objeto = objectStorage.baixar(anexo.getChaveStorage());
        return new DownloadAnexoObraDTO(objeto.conteudo(), anexo.getNomeArquivo(), anexo.getContentType(), anexo.getTamanhoBytes());
    }

    @Transactional
    public void removerAnexo(UUID id, UUID idAnexo) {
        AnexoTurno anexo = buscarAnexo(id, idAnexo);
        anexo.desativar();
        anexoRepository.save(anexo);
    }

    /** Quem não é titular da equipe entra como substituto. Sem líder informado, vale o responsável da equipe. */
    private void exigirServicoDaEquipe(TurnoResponse turno, TurnoObraRequests.Apr request) {
        if (request.idProgramacao() == null) {
            return;
        }
        Programacao programacao = programacaoRepository.findById(request.idProgramacao())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Programação não encontrada."));
        if (!programacao.getIdObra().equals(request.idObra()) || !programacao.getIdEquipe().equals(turno.idEquipe())) {
            throw new InvarianteVioladaException("A programação #" + programacao.getNumero() + " não é desta obra e desta equipe.");
        }
    }

    private TurnoResponse buscarTurnoDeObras(UUID id) {
        TurnoResponse turno = turnoApi.buscarPorId(id);
        exigirEquipeDeObras(turno.idEquipe());
        return turno;
    }

    private void exigirEquipeDeObras(UUID idEquipe) {
        EquipeResponse equipe = equipeApi.buscarPorIdResponse(idEquipe)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Equipe não encontrada."));
        if (equipe.processo() == null || !obraRepository.listarProcessosComObras().contains(equipe.processo().id())) {
            throw new InvarianteVioladaException("A equipe " + equipe.codigo() + " não é de um processo com obras.");
        }
    }

    private void exigirObra(UUID idObra) {
        if (!obraRepository.existsById(idObra)) {
            throw new RecursoNaoEncontradoException("Obra não encontrada com o ID: " + idObra);
        }
    }

    private OcorrenciaTurno buscarOcorrencia(UUID id, UUID idOcorrencia) {
        return ocorrenciaRepository.findByIdAndIdTurnoAndAtivoTrue(idOcorrencia, id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ocorrência não encontrada neste turno."));
    }

    private AprTurno buscarApr(UUID id, UUID idApr) {
        return aprRepository.findByIdAndIdTurnoAndAtivoTrue(idApr, id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("APR não encontrada neste turno."));
    }

    private AnexoTurno buscarAnexo(UUID id, UUID idAnexo) {
        return anexoRepository.findByIdAndIdTurnoAndAtivoTrue(idAnexo, id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Arquivo não encontrado neste turno."));
    }

    private static String sanitizar(String nome) {
        return nome == null || nome.isBlank() ? "arquivo_sem_nome" : nome.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
