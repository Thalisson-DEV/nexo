package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.dto.SaldoObraResponse;
import com.nexo.app.modules.estoque.domain.CondicaoMaterial;
import com.nexo.app.modules.estoque.domain.Estoque;
import com.nexo.app.modules.estoque.domain.SaldoSerie;
import com.nexo.app.modules.estoque.infrastructure.persistence.EstoqueRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SaldoRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SaldoSerieRepository;
import com.nexo.app.modules.obras.ObraApi;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Saldo atual do estoque da obra. Saldo negativo aparece, porque é divergência que precisa ser vista. */
@Service
@RequiredArgsConstructor
public class SaldoObraUseCases {

    private final EstoqueRepository estoqueRepository;
    private final SaldoRepository saldoRepository;
    private final SaldoSerieRepository saldoSerieRepository;
    private final ObraApi obraApi;

    private record ChaveSerie(UUID idMaterial, CondicaoMaterial condicao) {
    }

    @Transactional(readOnly = true)
    public SaldoObraResponse buscar(UUID idObra) {
        obraApi.buscarPorId(idObra)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Obra nao encontrada."));
        Estoque estoque = estoqueRepository.findByIdObra(idObra).orElse(null);
        if (estoque == null) {
            return new SaldoObraResponse(null, List.of());
        }
        Map<ChaveSerie, List<String>> series = saldoSerieRepository.findByEstoqueIdAndAtivoTrue(estoque.getId()).stream()
                .collect(Collectors.groupingBy(
                        serie -> new ChaveSerie(serie.getMaterial().getId(), serie.getCondicao()),
                        Collectors.mapping(SaldoSerie::getNumSerie, Collectors.toList())));
        List<SaldoObraResponse.ItemSaldo> itens = saldoRepository.findDiferentesDeZeroByEstoqueId(estoque.getId()).stream()
                .map(saldo -> new SaldoObraResponse.ItemSaldo(
                        saldo.getMaterial().getCodigo(),
                        saldo.getMaterial().getDescricao(),
                        saldo.getMaterial().getUnidadeMedida(),
                        saldo.getCondicao(),
                        saldo.getQuantidade(),
                        series.getOrDefault(new ChaveSerie(saldo.getMaterial().getId(), saldo.getCondicao()), List.of())
                                .stream().sorted().toList()))
                .toList();
        return new SaldoObraResponse(estoque.getId(), itens);
    }
}
