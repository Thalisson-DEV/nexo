package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.domain.CondicaoMaterial;
import com.nexo.app.modules.estoque.domain.ItemSolicitacao;
import com.nexo.app.modules.estoque.domain.ReservaSerieSolicitacao;
import com.nexo.app.modules.estoque.domain.SaldoSerie;
import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial;
import com.nexo.app.modules.estoque.infrastructure.persistence.ReservaSerieSolicitacaoRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SaldoSerieRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SolicitacaoMaterialRepository;
import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static com.nexo.app.shared.infrastructure.config.CacheNames.SALDOS;

/**
 * O almoxarifado define as séries de um material antes da entrega; na entrega, só confirma.
 * A lista informada substitui a anterior: o que saiu dela é liberado, o que entrou fica reservado.
 */
@Service
@RequiredArgsConstructor
public class ReservaSerieSolicitacaoUseCase {

    private final SolicitacaoMaterialRepository solicitacaoRepository;
    private final SaldoSerieRepository serieRepository;
    private final ReservaSerieSolicitacaoRepository reservaRepository;

    @Transactional
    @CacheEvict(value = SALDOS, allEntries = true)
    public List<String> definir(UUID idSolicitacao, UUID idItem, List<String> series, UUID idUsuario) {
        SolicitacaoMaterial solicitacao = solicitacaoRepository.findById(idSolicitacao)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Solicitação não encontrada: " + idSolicitacao));
        if (!solicitacao.aceitaReservaDeSerie()) {
            throw new InvarianteVioladaException(
                    "Só dá para definir séries em solicitação comercial aprovada ou parcialmente entregue, com saldo aberto.");
        }
        ItemSolicitacao item = solicitacao.getItens().stream()
                .filter(i -> i.isAtivo() && i.getId().equals(idItem))
                .findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException("Item não encontrado nesta solicitação: " + idItem));
        if (!item.getMaterial().isDetalhaSerie()) {
            throw new InvarianteVioladaException("O material " + item.getMaterial().getCodigo() + " não tem controle de série.");
        }

        Set<String> desejadas = new LinkedHashSet<>();
        for (String serie : series) {
            if (serie != null && !serie.isBlank()) desejadas.add(serie.trim());
        }
        int pendente = item.getQuantidadePendente().intValue();
        if (desejadas.size() > pendente) {
            throw new InvarianteVioladaException("O item tem " + pendente + " a entregar; informe no máximo " + pendente + " séries.");
        }

        // Libera o que saiu da lista antes de reservar o que entrou (o índice aceita uma reserva ativa por série).
        List<ReservaSerieSolicitacao> liberadas = item.getReservasSerie().stream()
                .filter(BaseEntity::isAtivo)
                .filter(r -> !desejadas.contains(r.getSaldoSerie().getNumSerie()) || !r.isVigente())
                .toList();
        liberadas.forEach(BaseEntity::desativar);
        reservaRepository.saveAllAndFlush(liberadas);

        Set<String> jaReservadas = new LinkedHashSet<>(item.getSeriesReservadas());
        UUID idOrigem = solicitacao.getEstoqueOrigem().getId();
        for (String numSerie : desejadas) {
            if (jaReservadas.contains(numSerie)) continue;
            SaldoSerie serie = serieRepository.findByEstoqueIdAndMaterialIdAndNumSerieAndCondicaoAndAtivoTrue(
                            idOrigem, item.getMaterial().getId(), numSerie, CondicaoMaterial.NOVO)
                    .orElseThrow(() -> new InvarianteVioladaException("A série " + numSerie + " não está disponível como nova em "
                            + solicitacao.getEstoqueOrigem().getNomeIdentificador() + "."));
            serie.getReservaVigente().ifPresent(outra -> {
                throw new InvarianteVioladaException("A série " + numSerie + " já está reservada para a solicitação #"
                        + outra.getSolicitacao().getId().toString().substring(0, 8) + ".");
            });
            // Reserva que caducou (solicitação encerrada, item entregue) ainda ocupa o índice: sai antes.
            List<ReservaSerieSolicitacao> caducas = serie.getReservas().stream().filter(BaseEntity::isAtivo).toList();
            caducas.forEach(BaseEntity::desativar);
            reservaRepository.saveAllAndFlush(caducas);
            ReservaSerieSolicitacao reserva = reservaRepository.save(new ReservaSerieSolicitacao(serie, item, idUsuario));
            serie.getReservas().add(reserva);
            item.getReservasSerie().add(reserva);
        }
        return item.getSeriesReservadas().stream().filter(Objects::nonNull).toList();
    }
}
