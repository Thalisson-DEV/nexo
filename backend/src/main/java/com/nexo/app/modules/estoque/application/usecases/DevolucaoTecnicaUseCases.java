package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.dto.DevolucaoTecnicaRequest;
import com.nexo.app.modules.estoque.application.services.DebitoSaldoService;
import com.nexo.app.modules.estoque.domain.CondicaoMaterial;
import com.nexo.app.modules.estoque.domain.Estoque;
import com.nexo.app.modules.estoque.domain.Material;
import com.nexo.app.modules.estoque.domain.MovimentacaoEstoque;
import com.nexo.app.modules.estoque.domain.Saldo;
import com.nexo.app.modules.estoque.domain.SaldoSerie;
import com.nexo.app.modules.estoque.domain.TipoObraSnapshot;
import com.nexo.app.modules.estoque.infrastructure.persistence.EstoqueRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.MaterialRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.MovimentacaoEstoqueRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SaldoRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SaldoSerieRepository;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static com.nexo.app.shared.infrastructure.config.CacheNames.SALDOS;

/**
 * Devolução técnica direta: o material sai da obra e entra no almoxarifado na hora, sem passar pelo
 * fluxo de solicitação. Pode vir material que o sistema não sabia estar na obra; a diferença entra
 * antes na obra como entrada avulsa, para o saldo não ficar negativo e o histórico continuar fechando.
 */
@Service
@RequiredArgsConstructor
public class DevolucaoTecnicaUseCases {

    private final MovimentacaoEstoqueRepository repository;
    private final EstoqueRepository estoqueRepository;
    private final MaterialRepository materialRepository;
    private final SaldoRepository saldoRepository;
    private final SaldoSerieRepository serieRepository;
    private final DebitoSaldoService debitoSaldoService;
    private final ReferenciaTecnicaValidator referenciaValidator;
    private final EstoqueObraProvisioningService estoqueObraService;

    @Transactional
    @CacheEvict(value = SALDOS, allEntries = true)
    public UUID devolver(DevolucaoTecnicaRequest request, UUID idOperador) {
        var contexto = referenciaValidator.validar(request.idObra(), request.ocorrencia(), request.idEquipe(), null);
        Estoque estoqueObra = estoqueObraService.resolverOuProvisionar(contexto.obra());
        Estoque destino = resolverDestino(request.idEstoqueDestino());
        validarSeriesUnicas(request.itens());

        UUID idReferencia = UUID.randomUUID();
        // Referências próprias: a listagem agrupa por referência e juntaria operações diferentes.
        UUID idReferenciaEntrada = UUID.randomUUID();
        UUID idReferenciaBaixa = UUID.randomUUID();
        OffsetDateTime dataHora = OffsetDateTime.now();

        for (DevolucaoTecnicaRequest.ItemDevolucaoTecnicaRequest item : request.itens()) {
            if (!item.tipo().isDevolucao()) {
                throw new InvarianteVioladaException("A devolucao tecnica aceita somente os tipos 222 e 922.");
            }
            Material material = materialRepository.findById(item.idMaterial())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Material não encontrado: " + item.idMaterial()));
            if (material.isDetalhaSerie() && item.quantidade().compareTo(BigDecimal.ONE) != 0) {
                throw new InvarianteVioladaException(
                        "O material " + material.getCodigo() + " controla série: devolva uma série por item, com quantidade 1.");
            }
            CondicaoMaterial condicao = Objects.requireNonNullElse(item.condicao(), CondicaoMaterial.NOVO);
            String numSerie = StringUtils.hasText(item.numSerie()) ? item.numSerie().trim() : null;

            var dados = new MovimentacaoEstoque.ContextoDevolucaoTecnica(
                    item.tipo(), contexto.obra().id(), TipoObraSnapshot.valueOf(contexto.obra().tipoObra().name()),
                    contexto.ocorrencia() != null ? contexto.ocorrencia().id() : null,
                    contexto.ocorrencia() != null ? contexto.ocorrencia().codigo() : null,
                    request.idEquipe());

            completarSaldoDaObra(estoqueObra, material, condicao, item, numSerie, dados, idOperador, idReferenciaEntrada);
            debitoSaldoService.debitar(estoqueObra, material, condicao, item.quantidade());

            if (item.deveGuardar()) {
                Saldo saldoDestino = saldoRepository.findByEstoqueIdAndMaterialIdAndCondicao(destino.getId(), material.getId(), condicao)
                        .orElseGet(() -> new Saldo(destino, material, condicao));
                saldoDestino.adicionarQuantidade(item.quantidade());
                saldoRepository.save(saldoDestino);
            }
            if (material.isDetalhaSerie()) {
                transferirSerie(estoqueObra, destino, material, numSerie, condicao, item.deveGuardar());
            }

            repository.save(MovimentacaoEstoque.criarDevolucaoTecnica(
                    estoqueObra, destino, material, item.quantidade(), condicao, dados, idOperador, idReferencia, numSerie));
            if (!item.deveGuardar()) {
                repository.save(new MovimentacaoEstoque(
                        destino, null, material, item.quantidade(),
                        MovimentacaoEstoque.TipoMovimentacao.BAIXA_AVULSA, condicao, idOperador,
                        idReferenciaBaixa, numSerie, dataHora));
            }
        }
        return idReferencia;
    }

    private Estoque resolverDestino(UUID idEstoqueDestino) {
        Estoque destino = estoqueRepository.findById(idEstoqueDestino)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Estoque de destino não encontrado"));
        if (!destino.isAtivo() || destino.getTipoEstoque() == null
                || !"FISICO".equalsIgnoreCase(destino.getTipoEstoque().getNome())) {
            throw new InvarianteVioladaException("O estoque de destino da devolução deve estar ativo e ser do tipo FISICO.");
        }
        return destino;
    }

    /** Lança na obra o que falta para cobrir a devolução, quando o sistema não sabia do material. */
    private void completarSaldoDaObra(
            Estoque estoqueObra,
            Material material,
            CondicaoMaterial condicao,
            DevolucaoTecnicaRequest.ItemDevolucaoTecnicaRequest item,
            String numSerie,
            MovimentacaoEstoque.ContextoDevolucaoTecnica dados,
            UUID idOperador,
            UUID idReferenciaEntrada
    ) {
        Saldo saldo = saldoRepository.findWithLockByEstoqueIdAndMaterialIdAndCondicao(estoqueObra.getId(), material.getId(), condicao)
                .orElseGet(() -> new Saldo(estoqueObra, material, condicao));
        BigDecimal disponivel = saldo.getQuantidade().max(BigDecimal.ZERO);
        BigDecimal falta = item.quantidade().subtract(disponivel);
        if (falta.signum() <= 0) {
            return;
        }
        saldo.adicionarQuantidade(falta);
        saldoRepository.saveAndFlush(saldo);
        repository.save(MovimentacaoEstoque.criarEntradaPorDevolucaoTecnica(
                estoqueObra, material, falta, condicao, dados, idOperador, idReferenciaEntrada, numSerie));
    }

    private void transferirSerie(
            Estoque estoqueObra,
            Estoque destino,
            Material material,
            String numSerie,
            CondicaoMaterial condicao,
            boolean guardar
    ) {
        SaldoSerie serie = serieRepository
                .findByEstoqueIdAndMaterialIdAndNumSerieAndCondicaoAndAtivoTrue(estoqueObra.getId(), material.getId(), numSerie, condicao)
                .orElseGet(() -> novaSerieNaObra(estoqueObra, material, numSerie, condicao));
        serie.transferirPara(destino);
        if (!guardar) {
            serie.desativar();
        }
        serieRepository.save(serie);
    }

    private SaldoSerie novaSerieNaObra(Estoque estoqueObra, Material material, String numSerie, CondicaoMaterial condicao) {
        if (numSerie == null) {
            throw new InvarianteVioladaException("O material '" + material.getCodigo() + "' exige número de série.");
        }
        // O número de série é único entre as séries ativas de todos os materiais.
        if (!serieRepository.findAllByNumSerieInAndAtivoTrue(List.of(numSerie)).isEmpty()) {
            throw new InvarianteVioladaException("A série " + numSerie + " já está ativa em outro estoque ou condição.");
        }
        return new SaldoSerie(estoqueObra, material, numSerie, condicao);
    }

    private void validarSeriesUnicas(List<DevolucaoTecnicaRequest.ItemDevolucaoTecnicaRequest> itens) {
        Set<String> series = new HashSet<>();
        for (DevolucaoTecnicaRequest.ItemDevolucaoTecnicaRequest item : itens) {
            if (StringUtils.hasText(item.numSerie()) && !series.add(item.idMaterial() + "|" + item.numSerie().trim())) {
                throw new InvarianteVioladaException(
                        "O número de série " + item.numSerie() + " foi informado mais de uma vez na devolução.");
            }
        }
    }
}
