package com.nexo.app.modules.estoque.application.services;

import com.nexo.app.modules.estoque.domain.EscopoEventoSolicitacao;
import com.nexo.app.modules.estoque.domain.EventoSolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.ItemSolicitacao;
import com.nexo.app.modules.estoque.domain.PayloadEventoSolicitacao;
import com.nexo.app.modules.estoque.domain.ReferenciaEventoSolicitacao;
import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.TipoEventoSolicitacao;
import com.nexo.app.modules.estoque.infrastructure.persistence.EventoSolicitacaoMaterialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GravadorEventoSolicitacaoService {

    private final EventoSolicitacaoMaterialRepository repository;
    private final SerializadorPayloadEventoSolicitacao serializador;
    private final IntegridadeHistoricoSolicitacaoService integridade;
    private final ResolvedorAutorEventoSolicitacao resolvedorAutor;
    private final Clock clock;

    @Transactional
    public EventoSolicitacaoMaterial registrarFilho(
            SolicitacaoMaterial solicitacao,
            TipoEventoSolicitacao tipo,
            PayloadEventoSolicitacao payload,
            ContextoRegistroEventoSolicitacao contexto
    ) {
        String autorSnapshot = resolvedorAutor.resolver(contexto.idAutor(), contexto.autorSnapshot());
        ContextoRegistroEventoSolicitacao contextoResolvido = new ContextoRegistroEventoSolicitacao(
                contexto.idAutor(), autorSnapshot, contexto.origem(), contexto.justificativa());
        EventoSolicitacaoMaterial anterior = repository
                .findTopByEscopoAndIdSolicitacaoOrderBySequenciaDesc(
                        EscopoEventoSolicitacao.SOLICITACAO, solicitacao.getId())
                .orElse(null);
        long sequencia = anterior == null ? 1 : anterior.getSequencia() + 1;
        EventoSolicitacaoMaterial evento = new EventoSolicitacaoMaterial(
                solicitacao.getId(), sequencia, tipo, solicitacao.getTipoTecnico(),
                solicitacao.getTipoObraSnapshot(), solicitacao.getIdOcorrencia(), solicitacao.getOcorrencia(),
                contextoResolvido.idAutor(), contextoResolvido.autorSnapshot(), contextoResolvido.origem(),
                contexto.justificativa(), versao(solicitacao.getVersao()),
                serializador.serializar(enriquecer(solicitacao, payload, contextoResolvido)),
                OffsetDateTime.now(clock).withOffsetSameInstant(java.time.ZoneOffset.UTC)
                        .truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        integridade.selar(evento, anterior == null ? null : anterior.getHashAtual());
        return repository.save(evento);
    }

    private PayloadEventoSolicitacao enriquecer(
            SolicitacaoMaterial solicitacao,
            PayloadEventoSolicitacao payload,
            ContextoRegistroEventoSolicitacao contexto
    ) {
        Map<String, Object> snapshots = new LinkedHashMap<>(payload.snapshots());
        snapshots.put("obra", new ReferenciaEventoSolicitacao(solicitacao.getIdObra(), null));
        snapshots.put("tipoObra", solicitacao.getTipoObraSnapshot());
        snapshots.put("ocorrencia", solicitacao.getIdOcorrencia() == null ? null
                : new ReferenciaEventoSolicitacao(solicitacao.getIdOcorrencia(), solicitacao.getOcorrencia()));
        snapshots.put("tipoTecnico", solicitacao.getTipoTecnico());
        snapshots.put("almoxarifado", solicitacao.getAlmoxarifadoAtendimento() == null ? null
                : new ReferenciaEventoSolicitacao(
                        solicitacao.getAlmoxarifadoAtendimento().getId(),
                        solicitacao.getAlmoxarifadoAtendimento().getNomeIdentificador()));
        snapshots.put("equipe", solicitacao.getIdEquipeSaque() == null ? null
                : new ReferenciaEventoSolicitacao(solicitacao.getIdEquipeSaque(), null));
        snapshots.put("dataPrevista", solicitacao.getDataPrevistaRetirada());
        snapshots.put("observacao", solicitacao.getObservacao());
        snapshots.put("status", solicitacao.getStatus());
        snapshots.put("statusSeparacao", solicitacao.getStatusSeparacao());
        snapshots.put("encerradaEm", solicitacao.getEncerradaEm());
        snapshots.put("idUsuarioEncerramento", solicitacao.getIdUsuarioEncerramento());
        snapshots.put("motivoEncerramento", solicitacao.getMotivoEncerramento());
        snapshots.put("itens", snapshotsItens(solicitacao.getItens()));
        if (solicitacao.getReserva() != null) {
            snapshots.put("reserva", Map.of(
                    "numero", solicitacao.getReserva().getNumeroApi(),
                    "agente", new ReferenciaEventoSolicitacao(
                            solicitacao.getReserva().getIdAgenteResponsavel(),
                            solicitacao.getReserva().getNomeAgenteSnapshot())));
        }
        snapshots.put("usuario", new ReferenciaEventoSolicitacao(
                contexto.idAutor(), contexto.autorSnapshot()));
        return new PayloadEventoSolicitacao(payload.comando(), payload.alteracoes(), snapshots);
    }

    private List<Map<String, Object>> snapshotsItens(List<ItemSolicitacao> itens) {
        List<Map<String, Object>> resultado = new ArrayList<>(itens.size());
        itens.stream().sorted(java.util.Comparator.comparing(item -> item.getId().toString()))
                .forEach(item -> {
                    Map<String, Object> snapshot = new LinkedHashMap<>();
                    snapshot.put("id", item.getId());
                    snapshot.put("material", new ReferenciaEventoSolicitacao(
                            item.getMaterial().getId(),
                            item.getMaterial().getCodigo() + " - " + item.getMaterial().getDescricao()));
                    snapshot.put("tipoTecnico", item.getSolicitacao().getTipoTecnico());
                    snapshot.put("quantidade", item.getQuantidade());
                    snapshot.put("quantidadeEntregaLiquida", item.getQuantidadeEntregaLiquida());
                    snapshot.put("quantidadeDevolucaoLiquida", item.getQuantidadeDevolucaoLiquida());
                    snapshot.put("quantidadePendente", item.getQuantidadePendente());
                    resultado.add(snapshot);
                });
        return resultado;
    }

    private long versao(Long valor) {
        return valor == null ? 0 : valor;
    }
}
