package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.obras.AgenteResponsavelApi;
import com.nexo.app.modules.estoque.domain.NormalizadorOcorrenciaTecnica;
import com.nexo.app.modules.obras.AgenteResponsavelApi.AgenteResponsavelPublico;
import com.nexo.app.modules.obras.ObraApi;
import com.nexo.app.modules.obras.ObraApi.OcorrenciaPublica;
import com.nexo.app.modules.obras.ObraApi.ObraPublica;
import com.nexo.app.modules.obras.ObraApi.TipoObraPublico;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.ColaboradorResponse;
import com.nexo.app.modules.rh.EquipeApi;
import com.nexo.app.modules.rh.EquipeApi.EquipePublica;
import com.nexo.app.shared.domain.exception.PreenchimentoInvalidoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Valida referencias publicas usadas por comandos tecnicos antes de qualquer escrita. */
@Service
@RequiredArgsConstructor
public class ReferenciaTecnicaValidator {

    private final ObraApi obraApi;
    private final EquipeApi equipeApi;
    private final AgenteResponsavelApi agenteResponsavelApi;
    private final ColaboradorApi colaboradorApi;

    @Transactional
    public ContextoTecnicoValidado validar(
            UUID idObra,
            String ocorrencia,
            UUID idEquipe,
            UUID idAgenteResponsavel
    ) {
        return validar(idObra, ocorrencia, idEquipe, idAgenteResponsavel, null, false);
    }

    @Transactional
    public ContextoTecnicoValidado validar(
            UUID idObra,
            String ocorrencia,
            UUID idEquipe,
            UUID idAgenteResponsavel,
            UUID idColaboradorSaque,
            boolean saqueForaEquipe
    ) {
        Map<String, String> erros = new LinkedHashMap<>();
        ObraPublica obra = resolverObra(idObra, erros);
        String ocorrenciaNormalizada = validarOcorrencia(obra, ocorrencia, erros);
        EquipePublica equipe = resolverEquipe(obra, idEquipe, erros);
        AgenteResponsavelPublico agente = resolverAgente(idAgenteResponsavel, erros);
        ColaboradorResponse colaboradorSaque = resolverColaboradorSaque(equipe, idColaboradorSaque, saqueForaEquipe, erros);

        if (!erros.isEmpty()) {
            throw new PreenchimentoInvalidoException(erros);
        }
        OcorrenciaPublica ocorrenciaResolvida = ocorrenciaNormalizada == null ? null
                : obraApi.resolverOuCriarOcorrencia(obra.id(), ocorrenciaNormalizada);
        return new ContextoTecnicoValidado(obra, ocorrenciaResolvida, equipe, agente, colaboradorSaque, saqueForaEquipe);
    }

    @Transactional(readOnly = true)
    public AgenteResponsavelPublico validarAgente(UUID idAgenteResponsavel) {
        Map<String, String> erros = new LinkedHashMap<>();
        AgenteResponsavelPublico agente = resolverAgente(idAgenteResponsavel, erros);
        if (!erros.isEmpty()) {
            throw new PreenchimentoInvalidoException(erros);
        }
        return agente;
    }

    public void vincularEquipeSeNecessario(ContextoTecnicoValidado contexto) {
        if (contexto != null && contexto.equipe() != null) {
            obraApi.vincularEquipeSeAusente(contexto.obra().id(), contexto.equipe().id());
        }
    }

    private ObraPublica resolverObra(UUID idObra, Map<String, String> erros) {
        if (idObra == null) {
            erros.put("idObra", "A obra e obrigatoria.");
            return null;
        }
        ObraPublica obra = obraApi.buscarPorId(idObra).orElse(null);
        if (obra == null) {
            erros.put("idObra", "A obra informada nao foi encontrada.");
        } else if (!obra.ativa()) {
            erros.put("idObra", "A obra informada esta inativa.");
        } else if (!obra.processoTecnico()) {
            erros.put("idObra", "A obra informada nao pertence a um processo tecnico elegivel.");
        }
        return obra;
    }

    private String validarOcorrencia(ObraPublica obra, String ocorrencia, Map<String, String> erros) {
        String normalizada;
        try {
            normalizada = NormalizadorOcorrenciaTecnica.normalizar(ocorrencia);
        } catch (IllegalArgumentException ex) {
            erros.put("ocorrencia", ex.getMessage());
            return null;
        }
        if (obra == null) {
            return normalizada;
        }
        boolean informada = normalizada != null;
        boolean obrigatoria = obra.tipoObra() == TipoObraPublico.CORRETIVA
                || obra.tipoObra() == TipoObraPublico.PREVENTIVA;
        if (obrigatoria && !informada) {
            erros.put("ocorrencia", "A ocorrencia e obrigatoria para obra corretiva ou preventiva.");
        } else if (!obrigatoria && informada) {
            erros.put("ocorrencia", "A ocorrencia nao se aplica a obra do tipo " + obra.tipoObra() + ".");
        }
        return normalizada;
    }

    private EquipePublica resolverEquipe(
            ObraPublica obra,
            UUID idEquipe,
            Map<String, String> erros
    ) {
        if (idEquipe == null) {
            return null;
        }
        return equipeApi.buscarAtivaPorId(idEquipe)
                .orElseGet(() -> {
                    erros.put("idEquipe", "A equipe deve existir e estar ativa.");
                    return null;
                });
    }

    private AgenteResponsavelPublico resolverAgente(
            UUID idAgenteResponsavel,
            Map<String, String> erros
    ) {
        if (idAgenteResponsavel == null) {
            return null;
        }
        return agenteResponsavelApi.buscarAtivoPorId(idAgenteResponsavel)
                .orElseGet(() -> {
                    erros.put("idAgenteResponsavel", "O agente responsavel deve existir e estar ativo.");
                    return null;
                });
    }

    private ColaboradorResponse resolverColaboradorSaque(
            EquipePublica equipe,
            UUID idColaboradorSaque,
            boolean saqueForaEquipe,
            Map<String, String> erros
    ) {
        if (idColaboradorSaque == null) {
            return null;
        }
        ColaboradorResponse colaborador = colaboradorApi.buscarPorIdResponse(idColaboradorSaque)
                .filter(ColaboradorResponse::ativo)
                .orElse(null);
        if (colaborador == null) {
            erros.put("idColaboradorSaque", "O colaborador responsavel pelo saque deve existir e estar ativo.");
            return null;
        }
        if (!saqueForaEquipe) {
            if (equipe == null) {
                erros.put("idColaboradorSaque", "Informe a equipe ou habilite o saque fora da equipe.");
                return null;
            }
            boolean membro = equipeApi.isMembroAtivo(equipe.id(), idColaboradorSaque);
            if (!membro) {
                erros.put("idColaboradorSaque", "O colaborador informado nao e um membro ativo da equipe informada.");
                return null;
            }
        }
        return colaborador;
    }

    public record ContextoTecnicoValidado(
            ObraPublica obra,
            OcorrenciaPublica ocorrencia,
            EquipePublica equipe,
            AgenteResponsavelPublico agenteResponsavel,
            ColaboradorResponse colaboradorSaque,
            boolean saqueForaEquipe
    ) {
        public ContextoTecnicoValidado(
                ObraPublica obra,
                OcorrenciaPublica ocorrencia,
                EquipePublica equipe,
                AgenteResponsavelPublico agenteResponsavel
        ) {
            this(obra, ocorrencia, equipe, agenteResponsavel, null, false);
        }
    }
}
