package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.obras.ObraApi;
import com.nexo.app.modules.obras.application.dto.ProjetoRelacionadoResponse;
import com.nexo.app.modules.obras.domain.AgenteResponsavel;
import com.nexo.app.modules.obras.domain.Obra;
import com.nexo.app.modules.obras.domain.OcorrenciaObra;
import com.nexo.app.modules.obras.infrastructure.persistence.ObraRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.OcorrenciaObraRepository;
import com.nexo.app.modules.rh.ProcessoNegocioApi;
import com.nexo.app.modules.rh.ProcessoNegocioResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ObraApiAdapter implements ObraApi {

    private static final String PROCESSO_TECNICO = "OBRAS";

    private final ObraRepository repository;
    private final OcorrenciaObraRepository ocorrenciaRepository;
    private final RelacionamentoProjetoUseCases relacionamentoProjetoUseCases;
    private final ProcessoNegocioApi processoNegocioApi;

    @Override
    @Transactional(readOnly = true)
    public Optional<ObraPublica> buscarPorId(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return repository.findById(id).map(this::mapear);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ObraPublica> buscarPorNumero(String numero) {
        if (numero == null || numero.isBlank()) {
            return Optional.empty();
        }
        return repository.findByNumeroIgnoreCase(numero.trim()).map(this::mapear);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ObraPublica> buscarTecnicaAtivaPorId(UUID id) {
        return buscarPorId(id)
                .filter(ObraPublica::ativa)
                .filter(ObraPublica::processoTecnico);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OcorrenciaPublica> buscarOcorrenciaPorId(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return ocorrenciaRepository.findById(id).map(this::mapearOcorrencia);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OcorrenciaPublica> buscarOcorrencia(UUID idObra, String codigo) {
        if (idObra == null || codigo == null) {
            return Optional.empty();
        }
        String normalizado = OcorrenciaObra.normalizarCodigo(codigo);
        return ocorrenciaRepository.findByObraIdAndCodigoNormalizado(idObra, normalizado)
                .map(this::mapearOcorrencia);
    }

    @Override
    @Transactional
    public OcorrenciaPublica resolverOuCriarOcorrencia(UUID idObra, String codigo) {
        Obra obra = repository.findById(idObra)
                .orElseThrow(() -> new com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException(
                        "Obra nao encontrada"));
        if (!obra.isAtivo()) {
            throw new com.nexo.app.shared.domain.exception.InvarianteVioladaException(
                    "A ocorrencia nao pode ser criada em obra inativa.");
        }
        String normalizado = OcorrenciaObra.normalizarCodigo(codigo);
        ocorrenciaRepository.inserirSeAusente(idObra, normalizado);
        return ocorrenciaRepository.findByObraIdAndCodigoNormalizado(idObra, normalizado)
                .map(this::mapearOcorrencia)
                .orElseThrow(() -> new IllegalStateException("Ocorrencia recem-resolvida nao encontrada."));
    }

    @Override
    @Transactional
    public void vincularEquipeSeAusente(UUID idObra, UUID idEquipe) {
        if (idObra == null || idEquipe == null) {
            return;
        }
        Obra obra = repository.findById(idObra)
                .orElseThrow(() -> new com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException(
                        "Obra nao encontrada"));
        if (!obra.isAtivo()) {
            throw new com.nexo.app.shared.domain.exception.InvarianteVioladaException(
                    "A equipe nao pode ser vinculada a obra inativa.");
        }
        repository.vincularEquipeSeAusente(idObra, idEquipe);
    }

    private OcorrenciaPublica mapearOcorrencia(OcorrenciaObra ocorrencia) {
        return new OcorrenciaPublica(
                ocorrencia.getId(), ocorrencia.getObra().getId(), ocorrencia.getCodigoNormalizado());
    }

    private ObraPublica mapear(Obra obra) {
        ProcessoNegocioResponse processo = processoNegocioApi.buscarPorIdResponse(obra.getIdProcesso())
                .orElse(null);
        String nomeProcesso = processo != null ? processo.nome() : null;
        boolean processoTecnico = processo != null
                && processo.ativo()
                && PROCESSO_TECNICO.equalsIgnoreCase(processo.nome());

        return new ObraPublica(
                obra.getId(),
                obra.getNumero(),
                obra.getTitulo(),
                TipoObraPublico.valueOf(obra.getTipoObra().name()),
                obra.isAtivo(),
                obra.getIdProcesso(),
                nomeProcesso,
                processoTecnico,
                obra.getIdBaseOperacional(),
                obra.getEquipes(),
                mapearAgente(obra.getAgenteResponsavel()),
                relacionamentoProjetoUseCases.listarSemValidarExistencia(obra.getId()).stream()
                        .map(this::mapearProjetoRelacionado)
                        .toList()
        );
    }

    private AgenteResponsavelPublico mapearAgente(AgenteResponsavel agente) {
        if (agente == null) {
            return null;
        }
        return new AgenteResponsavelPublico(
                agente.getId(), agente.getNome(), agente.getEmail(), agente.isAtivo());
    }

    private ProjetoRelacionadoPublico mapearProjetoRelacionado(ProjetoRelacionadoResponse projeto) {
        return new ProjetoRelacionadoPublico(
                projeto.idProjeto(),
                projeto.numero(),
                projeto.titulo(),
                TipoObraPublico.valueOf(projeto.tipoObra().name()),
                TipoVinculoProjetoPublico.valueOf(projeto.tipoVinculo().name()),
                projeto.ativo()
        );
    }
}
