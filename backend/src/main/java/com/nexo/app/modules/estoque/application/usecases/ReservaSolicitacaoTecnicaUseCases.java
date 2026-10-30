package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.dto.RegistrarReservaTecnicaRequest;
import com.nexo.app.modules.estoque.application.dto.ReservaSolicitacaoTecnicaResponse;
import com.nexo.app.modules.estoque.application.services.ContextoRegistroEventoSolicitacao;
import com.nexo.app.modules.estoque.application.services.GravadorEventoSolicitacaoService;
import com.nexo.app.modules.estoque.application.services.IdempotenciaComandoSolicitacaoService;
import com.nexo.app.modules.estoque.application.services.ObservabilidadeSolicitacaoTecnica;
import com.nexo.app.modules.estoque.application.services.PublicadorEventoSolicitacaoTecnica;
import com.nexo.app.modules.estoque.domain.ContextoSolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.ReservaSolicitacaoTecnica;
import com.nexo.app.modules.estoque.domain.AlteracaoEventoSolicitacao;
import com.nexo.app.modules.estoque.domain.OrigemComandoTecnico;
import com.nexo.app.modules.estoque.domain.PayloadEventoSolicitacao;
import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.TipoEventoSolicitacao;
import com.nexo.app.modules.estoque.infrastructure.persistence.ReservaSolicitacaoTecnicaRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SolicitacaoMaterialRepository;
import com.nexo.app.modules.obras.AgenteResponsavelApi.AgenteResponsavelPublico;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.ConflitoVersaoException;
import com.nexo.app.shared.domain.exception.PreenchimentoInvalidoException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import com.nexo.app.shared.domain.exception.BaseBusinessException;
import com.nexo.app.shared.domain.events.SolicitacaoMaterialTecnicaEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReservaSolicitacaoTecnicaUseCases {

    private static final String PREFIXO_ESCOPO = "RESERVA_TECNICA:";

    private final SolicitacaoMaterialRepository solicitacaoRepository;
    private final ReservaSolicitacaoTecnicaRepository reservaRepository;
    private final ReferenciaTecnicaValidator referenciaValidator;
    private final GravadorEventoSolicitacaoService gravadorEvento;
    private final PublicadorEventoSolicitacaoTecnica publicadorEventos;
    private final ObservabilidadeSolicitacaoTecnica observabilidade;
    private final IdempotenciaComandoSolicitacaoService idempotencia;

    @Transactional
    public ReservaSolicitacaoTecnicaResponse registrarOuAlterar(
            UUID idSolicitacao,
            RegistrarReservaTecnicaRequest request,
            UUID idUsuario
    ) {
        return registrarOuAlterar(idSolicitacao, request, idUsuario, OrigemComandoTecnico.UI);
    }

    @Transactional
    public ReservaSolicitacaoTecnicaResponse registrarOuAlterar(
            UUID idSolicitacao,
            RegistrarReservaTecnicaRequest request,
            UUID idUsuario,
            OrigemComandoTecnico origem
    ) {
        return registrarOuAlterarInterno(idSolicitacao, request, idUsuario, origem, null);
    }

    @Transactional
    public ReservaSolicitacaoTecnicaResponse registrarOuAlterar(
            UUID idSolicitacao,
            RegistrarReservaTecnicaRequest request,
            UUID idUsuario,
            OrigemComandoTecnico origem,
            String chaveIdempotencia
    ) {
        Objects.requireNonNull(request, "Os dados da reserva sao obrigatorios");
        Objects.requireNonNull(idUsuario, "O usuario de registro e obrigatorio");
        IdempotenciaComandoSolicitacaoService.Consulta<ReservaSolicitacaoTecnicaResponse> consulta =
                idempotencia.consultar(
                        PREFIXO_ESCOPO + idSolicitacao, idUsuario, chaveIdempotencia, request,
                        ReservaSolicitacaoTecnicaResponse.class,
                        ObservabilidadeSolicitacaoTecnica.Operacao.RESERVA);
        if (consulta.repetida()) {
            return consulta.resposta();
        }
        return registrarOuAlterarInterno(idSolicitacao, request, idUsuario, origem, consulta);
    }

    private ReservaSolicitacaoTecnicaResponse registrarOuAlterarInterno(
            UUID idSolicitacao,
            RegistrarReservaTecnicaRequest request,
            UUID idUsuario,
            OrigemComandoTecnico origem,
            IdempotenciaComandoSolicitacaoService.Consulta<ReservaSolicitacaoTecnicaResponse> consulta
    ) {
        Objects.requireNonNull(request, "Os dados da reserva sao obrigatorios");
        Objects.requireNonNull(idUsuario, "O usuario de registro e obrigatorio");
        SolicitacaoMaterial solicitacao = solicitacaoRepository.findById(idSolicitacao)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Solicitacao material nao encontrada"));
        if (solicitacao.getContexto() != ContextoSolicitacaoMaterial.TECNICO) {
            throw new InvarianteVioladaException("Reserva externa se aplica somente a solicitacao tecnica.");
        }
        if (!Objects.equals(solicitacao.getVersao(), request.versaoEsperada())) {
            observabilidade.registrarConflito(
                    ObservabilidadeSolicitacaoTecnica.Operacao.RESERVA,
                    ObservabilidadeSolicitacaoTecnica.TipoConflito.VERSAO);
            throw new ConflitoVersaoException(
                    ConflitoVersaoException.Recurso.SOLICITACAO,
                    request.versaoEsperada(), solicitacao.getVersao());
        }

        BigInteger numero = converterNumero(request.numero());
        AgenteResponsavelPublico agente;
        try {
            agente = referenciaValidator.validarAgente(request.idAgenteResponsavel());
        } catch (BaseBusinessException ex) {
            observabilidade.registrarReservaInvalida(
                    ObservabilidadeSolicitacaoTecnica.Operacao.RESERVA,
                    ObservabilidadeSolicitacaoTecnica.MotivoReservaInvalida.AGENTE_INVALIDO);
            throw ex;
        }
        ReservaSolicitacaoTecnica reserva = reservaRepository.findBySolicitacaoId(idSolicitacao).orElse(null);
        boolean criada = reserva == null;
        boolean alterada = false;
        String numeroAnterior = reserva == null ? null : reserva.getNumeroApi();
        java.time.LocalDate dataAnterior = reserva == null ? null : reserva.getDataCriacao();
        UUID agenteAnterior = reserva == null ? null : reserva.getIdAgenteResponsavel();
        if (criada) {
            reserva = new ReservaSolicitacaoTecnica(
                    solicitacao, numero, request.dataCriacao(),
                    agente == null ? null : agente.id(),
                    agente == null ? null : agente.nome(),
                    agente == null ? null : agente.empresaOrgao(), idUsuario);
            reserva = reservaRepository.save(reserva);
        } else if (!reserva.possuiMesmosDados(numero, request.dataCriacao(), agente.id())) {
            if (request.justificativa() == null || request.justificativa().isBlank()) {
                throw new PreenchimentoInvalidoException(Map.of(
                        "justificativa", "A justificativa e obrigatoria para alterar a reserva."));
            }
            if (solicitacao.possuiFatoOperacional()) {
                observabilidade.registrarReservaInvalida(
                        ObservabilidadeSolicitacaoTecnica.Operacao.RESERVA,
                        ObservabilidadeSolicitacaoTecnica.MotivoReservaInvalida.ALTERACAO_BLOQUEADA);
            }
            reserva.atualizar(
                    numero, request.dataCriacao(),
                    agente == null ? null : agente.id(),
                    agente == null ? null : agente.nome(),
                    agente == null ? null : agente.empresaOrgao(), idUsuario);
            reserva = reservaRepository.save(reserva);
            alterada = true;
        }
        if (criada || alterada) {
            List<AlteracaoEventoSolicitacao> alteracoes = List.of(
                    new AlteracaoEventoSolicitacao("reserva.numero", numeroAnterior, reserva.getNumeroApi()),
                    new AlteracaoEventoSolicitacao("reserva.dataCriacao", dataAnterior, reserva.getDataCriacao()),
                    new AlteracaoEventoSolicitacao("reserva.idAgenteResponsavel", agenteAnterior,
                            reserva.getIdAgenteResponsavel())).stream()
                    .filter(item -> !Objects.equals(item.valorAnterior(), item.valorNovo()))
                    .toList();
            gravadorEvento.registrarFilho(
                    solicitacao,
                    criada ? TipoEventoSolicitacao.RESERVA_REGISTRADA
                            : TipoEventoSolicitacao.RESERVA_ALTERADA,
                    new PayloadEventoSolicitacao("REGISTRAR_RESERVA", alteracoes, Map.of()),
                    new ContextoRegistroEventoSolicitacao(
                            idUsuario, "USUARIO " + idUsuario,
                            Objects.requireNonNullElse(origem, OrigemComandoTecnico.UI),
                            request.justificativa()));
            publicadorEventos.publicarFilho(
                    criada ? SolicitacaoMaterialTecnicaEvent.Tipo.RESERVA_REGISTRADA
                            : SolicitacaoMaterialTecnicaEvent.Tipo.RESERVA_ALTERADA,
                    solicitacao);
            if (criada && !solicitacao.getTipoTecnico().isAdicao()
                    && solicitacao.getDataPrevistaRetirada() != null
                    && solicitacao.getIdEquipeSaque() != null) {
                publicadorEventos.publicarFilho(
                        SolicitacaoMaterialTecnicaEvent.Tipo.DEVOLUCAO_PRONTA, solicitacao);
            }
        }
        ReservaSolicitacaoTecnicaResponse resposta = ReservaSolicitacaoTecnicaResponse.de(reserva);
        if (consulta != null) {
            idempotencia.registrar(
                    consulta, solicitacao.getId(), null, resposta);
        }
        return resposta;
    }

    private BigInteger converterNumero(String valor) {
        if (valor == null || !valor.matches("[0-9]{1,20}")) {
            observabilidade.registrarReservaInvalida(
                    ObservabilidadeSolicitacaoTecnica.Operacao.RESERVA,
                    ObservabilidadeSolicitacaoTecnica.MotivoReservaInvalida.FORMATO);
            throw new PreenchimentoInvalidoException(Map.of(
                    "numero", "O numero da reserva deve conter de 1 a 20 digitos."));
        }
        return new BigInteger(valor);
    }
}
