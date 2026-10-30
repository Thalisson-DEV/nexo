package com.nexo.app.modules.frota.application.usecases;

import com.nexo.app.modules.frota.domain.AlertaVencimento;
import com.nexo.app.modules.frota.domain.Condutor;
import com.nexo.app.modules.frota.domain.DocumentoFrota;
import com.nexo.app.modules.frota.domain.FichaVeiculo;
import com.nexo.app.modules.frota.domain.Implemento;
import com.nexo.app.modules.frota.domain.TipoAlvoAlerta;
import com.nexo.app.modules.frota.domain.TipoDocumentoFrota;
import com.nexo.app.modules.frota.infrastructure.persistence.AlertaVencimentoRepository;
import com.nexo.app.modules.frota.infrastructure.persistence.CondutorRepository;
import com.nexo.app.modules.frota.infrastructure.persistence.DocumentoFrotaRepository;
import com.nexo.app.modules.frota.infrastructure.persistence.FichaVeiculoRepository;
import com.nexo.app.modules.frota.infrastructure.persistence.ImplementoRepository;
import com.nexo.app.shared.domain.events.FrotaVencimentoProximoEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Cruza documentos e CNHs com as faixas de aviso e publica um evento por alvo e faixa.
 *
 * <p>Só a faixa mais urgente já alcançada é avisada: um documento cadastrado já vencendo
 * em 5 dias gera um aviso (faixa 7), não três (30, 15 e 7). A memória em
 * {@code frota_alertas_vencimento} impede repetir o aviso se o job rodar de novo.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertaVencimentoFrotaUseCases {

    private final AlertaVencimentoRepository alertaRepository;
    private final DocumentoFrotaRepository documentoRepository;
    private final CondutorRepository condutorRepository;
    private final FichaVeiculoRepository fichaRepository;
    private final ImplementoRepository implementoRepository;
    private final ParametrosFrotaUseCases parametros;
    private final ReferenciasFrota referencias;
    private final ApplicationEventPublisher eventPublisher;

    /** @return quantidade de avisos publicados */
    @Transactional
    public int processar(LocalDate hoje) {
        List<Integer> faixas = parametros.obter().faixasAlerta();
        if (faixas.isEmpty()) return 0;
        LocalDate limite = hoje.plusDays(faixas.get(0));
        int avisos = processarDocumentos(faixas, hoje, limite) + processarCnhs(faixas, hoje, limite);
        if (avisos > 0) {
            log.info("Frota: {} aviso(s) de vencimento publicados para {}", avisos, hoje);
        }
        return avisos;
    }

    private int processarDocumentos(List<Integer> faixas, LocalDate hoje, LocalDate limite) {
        List<DocumentoFrota> documentos = documentoRepository.findAllByAtivoTrueAndDataVencimentoLessThanEqual(limite);
        if (documentos.isEmpty()) return 0;
        Map<UUID, FichaVeiculo> fichas = fichaRepository.findAllByIdVeiculoIn(documentos.stream()
                        .map(DocumentoFrota::getIdVeiculo).filter(Objects::nonNull).toList()).stream()
                .collect(Collectors.toMap(FichaVeiculo::getIdVeiculo, Function.identity()));
        Map<UUID, Implemento> implementos = implementoRepository.findAllById(documentos.stream()
                        .map(DocumentoFrota::getIdImplemento).filter(Objects::nonNull).toList()).stream()
                .collect(Collectors.toMap(Implemento::getId, Function.identity()));
        int avisos = 0;
        for (DocumentoFrota documento : documentos) {
            String referencia;
            String link;
            if (documento.getIdVeiculo() != null) {
                FichaVeiculo ficha = fichas.get(documento.getIdVeiculo());
                if (ficha == null || !ficha.isAtivo()) continue;
                referencia = ficha.getPlaca();
                link = "/frota/veiculos/" + ficha.getIdVeiculo();
            } else {
                Implemento implemento = implementos.get(documento.getIdImplemento());
                if (implemento == null || !implemento.isAtivo()) continue;
                referencia = implemento.getIdentificacao();
                link = "/frota/implementos";
            }
            String descricao = documento.getTipo() == TipoDocumentoFrota.OUTRO && documento.getDescricao() != null
                    ? documento.getDescricao() : documento.getTipo().getRotulo();
            if (avisar(TipoAlvoAlerta.DOCUMENTO, documento.getId(), documento.getDataVencimento(), faixas, hoje,
                    descricao, referencia, link)) {
                avisos++;
            }
        }
        return avisos;
    }

    private int processarCnhs(List<Integer> faixas, LocalDate hoje, LocalDate limite) {
        List<Condutor> condutores = condutorRepository.findAllByAtivoTrueAndCnhValidadeLessThanEqual(limite);
        if (condutores.isEmpty()) return 0;
        Map<UUID, String> nomes = referencias.nomesColaboradores(condutores.stream().map(Condutor::getIdColaborador).toList());
        int avisos = 0;
        for (Condutor condutor : condutores) {
            String nome = nomes.getOrDefault(condutor.getIdColaborador(), "condutor");
            if (avisar(TipoAlvoAlerta.CNH, condutor.getId(), condutor.getCnhValidade(), faixas, hoje,
                    "CNH", nome, "/frota/condutores")) {
                avisos++;
            }
        }
        return avisos;
    }

    private boolean avisar(TipoAlvoAlerta tipo, UUID idAlvo, LocalDate vencimento, List<Integer> faixas,
                           LocalDate hoje, String descricao, String referencia, String link) {
        Integer faixa = AlertaVencimento.faixaAlcancada(faixas, vencimento, hoje);
        if (faixa == null) return false;
        if (alertaRepository.existsByTipoAlvoAndIdAlvoAndDataVencimentoAndDiasAntecedenciaLessThanEqual(
                tipo, idAlvo, vencimento, faixa)) {
            return false;
        }
        alertaRepository.save(new AlertaVencimento(tipo, idAlvo, faixa, vencimento, hoje));
        eventPublisher.publishEvent(new FrotaVencimentoProximoEvent(idAlvo, tipo.name(), descricao, referencia,
                vencimento, ChronoUnit.DAYS.between(hoje, vencimento), link));
        return true;
    }
}
