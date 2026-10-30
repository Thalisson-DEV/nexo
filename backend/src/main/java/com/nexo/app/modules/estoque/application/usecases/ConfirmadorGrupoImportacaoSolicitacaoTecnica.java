package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.dto.ConfirmacaoImportacaoSolicitacaoTecnicaResponse;
import com.nexo.app.modules.estoque.application.dto.CriarSolicitacaoTecnicaRequest;
import com.nexo.app.modules.estoque.application.dto.PreviaImportacaoSolicitacaoTecnica;
import com.nexo.app.modules.estoque.application.dto.RegistrarReservaTecnicaRequest;
import com.nexo.app.modules.estoque.application.services.ContextoRegistroEventoSolicitacao;
import com.nexo.app.modules.estoque.application.services.GravadorEventoSolicitacaoService;
import com.nexo.app.modules.estoque.application.services.ObservabilidadeSolicitacaoTecnica;
import com.nexo.app.modules.estoque.domain.LoteImportacaoSolicitacao;
import com.nexo.app.modules.estoque.domain.OrigemComandoTecnico;
import com.nexo.app.modules.estoque.domain.PayloadEventoSolicitacao;
import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.TipoEventoSolicitacao;
import com.nexo.app.modules.estoque.infrastructure.persistence.LoteImportacaoSolicitacaoRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SolicitacaoMaterialRepository;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.PreenchimentoInvalidoException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Confirma exatamente uma chave em transacao independente e observa conflitos idempotentes. */
@Service
@RequiredArgsConstructor
public class ConfirmadorGrupoImportacaoSolicitacaoTecnica {

    private final LoteImportacaoSolicitacaoRepository loteRepository;
    private final SolicitacaoTecnicaUseCases solicitacaoTecnicaUseCases;
    private final ReservaSolicitacaoTecnicaUseCases reservaUseCases;
    private final SolicitacaoMaterialRepository solicitacaoRepository;
    private final GravadorEventoSolicitacaoService gravadorEvento;
    private final ObservabilidadeSolicitacaoTecnica observabilidade;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ConfirmacaoImportacaoSolicitacaoTecnicaResponse.Resultado confirmar(
            String tokenHash,
            String chave,
            String tipo,
            boolean reconhecerAvisos,
            UUID idUsuario,
            String idempotencyKeyHash,
            OffsetDateTime agora
    ) {
        LoteImportacaoSolicitacao lote = loteRepository.buscarParaConfirmacao(tokenHash, idUsuario)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "A previa de importacao nao foi encontrada para o usuario atual."));
        if (lote.expiradaEm(agora) || lote.getStatus() == LoteImportacaoSolicitacao.Status.EXPIRADO) {
            lote.marcarExpirado();
            throw new InvarianteVioladaException(
                    "A previa de importacao expirou. Valide o arquivo novamente.");
        }
        PreviaImportacaoSolicitacaoTecnica.SolicitacaoPrevista grupo = lote.getPrevia().solicitacoesPrevistas().stream()
                .filter(item -> {
                    boolean subgrupoBate = tipo == null || item.subgrupos().stream().anyMatch(s -> s.tipo().equals(tipo));
                    return subgrupoBate && item.chave().equals(chave);
                })
                .findFirst()
                .orElseGet(() -> lote.getPrevia().solicitacoesPrevistas().stream()
                        .filter(item -> {
                            boolean subgrupoBate = tipo == null || item.subgrupos().stream().anyMatch(s -> s.tipo().equals(tipo));
                            if (!subgrupoBate) {
                                return false;
                            }
                            return item.chave().equals(chave)
                                    || Objects.equals(item.numeroObra(), chave)
                                    || item.chave().startsWith(chave + " - ")
                                    || item.chave().contains(chave);
                        })
                        .findFirst()
                        .orElseGet(() -> lote.getPrevia().solicitacoesPrevistas().stream()
                                .filter(item -> item.chave().equals(chave)
                                        || Objects.equals(item.numeroObra(), chave)
                                        || item.chave().startsWith(chave + " - "))
                                .findFirst()
                                .orElseThrow(() -> new PreenchimentoInvalidoException(Map.of(
                                        "chaves", "A chave " + chave + " nao pertence a previa informada.")))));
        PreviaImportacaoSolicitacaoTecnica.Subgrupo subgrupo = grupo.subgrupos().stream()
                .filter(item -> tipo == null || item.tipo().equals(tipo))
                .findFirst()
                .orElseThrow(() -> new PreenchimentoInvalidoException(Map.of(
                        "tipo", "O tipo informado nao pertence a chave da previa.")));
        if (subgrupo.resultado() != null) {
            if (!Objects.equals(subgrupo.resultado().idempotencyKeyHash(), idempotencyKeyHash)) {
                observabilidade.registrarConflito(
                        ObservabilidadeSolicitacaoTecnica.Operacao.IMPORTACAO,
                        ObservabilidadeSolicitacaoTecnica.TipoConflito.IDEMPOTENCIA);
                throw new InvarianteVioladaException(
                        "A chave de importacao ja foi confirmada com outra chave de idempotencia.");
            }
            return sucesso(chave, subgrupo.resultado());
        }
        if (!subgrupoConfirmavel(grupo, subgrupo)) {
            throw new InvarianteVioladaException(
                    "A solicitacao " + chave + " possui erros e nao pode ser confirmada.");
        }
        if (subgrupoPossuiAvisos(grupo, subgrupo) && !reconhecerAvisos) {
            throw new PreenchimentoInvalidoException(Map.of(
                    "reconhecerAvisos", "Reconheca os avisos antes de confirmar a solicitacao " + chave + "."));
        }

        List<CriarSolicitacaoTecnicaRequest.Item> itens = subgrupo.itens().stream()
                        .map(item -> new CriarSolicitacaoTecnicaRequest.Item(
                                item.idMaterial(),
                                com.nexo.app.modules.estoque.domain.TipoSolicitacaoTecnica
                                        .porCodigo(subgrupo.tipo()),
                                item.quantidade()))
                .toList();
        var criacao = solicitacaoTecnicaUseCases.criar(
                new CriarSolicitacaoTecnicaRequest(
                        grupo.idObra(), grupo.ocorrencia(), grupo.idAlmoxarifadoAtendimento(),
                        grupo.idEquipe(), grupo.dataPrevista(), grupo.observacao(), itens),
                idUsuario,
                OrigemComandoTecnico.IMPORT);

        UUID idSolicitacao = criacao.solicitacoesCriadas().getFirst().id();
        if (subgrupo.reserva() != null) {
            SolicitacaoMaterial solicitacao = solicitacaoRepository.findById(idSolicitacao)
                    .orElseThrow(() -> new IllegalStateException("Filho tecnico recem-criado nao encontrado."));
            reservaUseCases.registrarOuAlterar(
                    idSolicitacao,
                    new RegistrarReservaTecnicaRequest(
                            solicitacao.getVersao(), subgrupo.reserva().numero(),
                            subgrupo.reserva().dataCriacao(), subgrupo.reserva().idAgenteResponsavel(),
                            "Reserva registrada pela importacao " + lote.getId()),
                    idUsuario,
                    OrigemComandoTecnico.IMPORT);
        }

        List<PreviaImportacaoSolicitacaoTecnica.SolicitacaoCriada> solicitacoes = List.of(
                new PreviaImportacaoSolicitacaoTecnica.SolicitacaoCriada(
                        criacao.solicitacoesCriadas().getFirst().tipo().getCodigo(), idSolicitacao));
        List<Integer> linhasOrigem = subgrupo.linhasOrigem();
        List<Map<String, Object>> solicitacoesPayload = solicitacoes.stream()
                .map(item -> Map.<String, Object>of(
                        "tipo", item.tipo(),
                        "id", item.id().toString()))
                .toList();
        Map<String, Object> snapshots = new LinkedHashMap<>();
        snapshots.put("idLote", lote.getId());
        snapshots.put("hashArquivo", lote.getHashArquivo());
        snapshots.put("chaveSolicitacao", grupo.chave());
        snapshots.put("linhasOrigem", linhasOrigem);
        snapshots.put("solicitacoes", solicitacoesPayload);
        var contextoEvento = new ContextoRegistroEventoSolicitacao(
                idUsuario, "USUARIO " + idUsuario, OrigemComandoTecnico.IMPORT, null);
        solicitacoes.forEach(item -> {
            SolicitacaoMaterial filho = solicitacaoRepository.findById(item.id())
                    .orElseThrow(() -> new IllegalStateException("Filho tecnico recem-criado nao encontrado."));
            gravadorEvento.registrarFilho(
                    filho, TipoEventoSolicitacao.IMPORTACAO_CONFIRMADA,
                    PayloadEventoSolicitacao.semAlteracoes("CONFIRMAR_IMPORTACAO", snapshots), contextoEvento);
        });

        PreviaImportacaoSolicitacaoTecnica.ResultadoConfirmacao resultado =
                new PreviaImportacaoSolicitacaoTecnica.ResultadoConfirmacao(
                        solicitacoes, idempotencyKeyHash, agora,
                        subgrupo.tipo());
        lote.registrarResultado(grupo.chave(), subgrupo.tipo(), resultado, idUsuario, agora);
        loteRepository.save(lote);
        return sucesso(chave, resultado);
    }

    private boolean subgrupoConfirmavel(PreviaImportacaoSolicitacaoTecnica.SolicitacaoPrevista grupo,
                                        PreviaImportacaoSolicitacaoTecnica.Subgrupo subgrupo) {
        return grupo.mensagens().stream()
                .filter(mensagem -> mensagem.severidade() == PreviaImportacaoSolicitacaoTecnica.Severidade.ERRO)
                .noneMatch(mensagem -> mensagemAplicaAoSubgrupo(mensagem, subgrupo));
    }

    private boolean subgrupoPossuiAvisos(PreviaImportacaoSolicitacaoTecnica.SolicitacaoPrevista grupo,
                                          PreviaImportacaoSolicitacaoTecnica.Subgrupo subgrupo) {
        return grupo.mensagens().stream()
                .filter(mensagem -> mensagem.severidade() == PreviaImportacaoSolicitacaoTecnica.Severidade.AVISO)
                .anyMatch(mensagem -> mensagemAplicaAoSubgrupo(mensagem, subgrupo));
    }

    private boolean mensagemAplicaAoSubgrupo(PreviaImportacaoSolicitacaoTecnica.Mensagem mensagem,
                                              PreviaImportacaoSolicitacaoTecnica.Subgrupo subgrupo) {
        if (mensagem.linha() != null) {
            return subgrupo.linhasOrigem().contains(mensagem.linha());
        }
        if ("MATERIAL_DUPLICADO_CONSOLIDADO".equals(mensagem.codigo())) {
            return subgrupo.itens().stream()
                    .anyMatch(item -> Objects.equals(item.codigoMaterial(), mensagem.valorOriginal()));
        }
        return true;
    }

    private ConfirmacaoImportacaoSolicitacaoTecnicaResponse.Resultado sucesso(
            String chave,
            PreviaImportacaoSolicitacaoTecnica.ResultadoConfirmacao resultado
    ) {
        return new ConfirmacaoImportacaoSolicitacaoTecnicaResponse.Resultado(
                chave, resultado.tipo(), resultado.solicitacoes(), null);
    }
}
