package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.dto.*;
import com.nexo.app.modules.estoque.application.mappers.SolicitacaoMapper;
import com.nexo.app.modules.estoque.application.services.PoliticaAcoesPermitidasSolicitacao;
import com.nexo.app.modules.estoque.domain.*;
import com.nexo.app.modules.estoque.infrastructure.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KanbanSolicitacaoTecnicaUseCases {

    private static final int MAX_CARDS_POR_SUB_COLUNA = 30;

    private static final List<MacroEtapaKanban> ORDEM_MACRO_COLUNAS_TECNICO = List.of(
            MacroEtapaKanban.PLANEJAMENTO,
            MacroEtapaKanban.SEPARACAO,
            MacroEtapaKanban.ENTREGA,
            MacroEtapaKanban.RECUSADO
    );

    private static final List<MacroEtapaKanban> ORDEM_MACRO_COLUNAS_COMERCIAL = List.of(
            MacroEtapaKanban.PLANEJAMENTO,
            MacroEtapaKanban.ENTREGA,
            MacroEtapaKanban.RECUSADO
    );

    private final SolicitacaoMaterialRepository repository;
    private final SolicitacaoMapper mapper;
    private final PoliticaAcoesPermitidasSolicitacao politicaAcoesPermitidas;
    private final OperacaoSolicitacaoMaterialRepository operacaoSolicitacaoRepository;

    public KanbanSolicitacaoTecnicaResponse obter(SolicitacaoFilter filtroBase) {
        ContextoSolicitacaoMaterial contexto = filtroBase.contexto() != null
                ? filtroBase.contexto()
                : ContextoSolicitacaoMaterial.TECNICO;
        SolicitacaoFilter filtro = filtroBase.comContexto(contexto);

        // Busca todas as solicitações que atendem ao filtro base
        List<SolicitacaoMaterial> todas = repository.findAll(
                SolicitacaoSpecification.filterBy(filtro),
                org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Order.asc("dataPrevistaRetirada").nullsLast(),
                        org.springframework.data.domain.Sort.Order.asc("dataSolicitacao")
                )
        );

        List<MacroEtapaKanban> ordemMacro = contexto == ContextoSolicitacaoMaterial.COMERCIAL
                ? ORDEM_MACRO_COLUNAS_COMERCIAL
                : ORDEM_MACRO_COLUNAS_TECNICO;

        List<KanbanColunaResponse> colunas = ordemMacro.stream()
                .map(macro -> construirMacroColuna(macro, todas, contexto))
                .toList();

        return new KanbanSolicitacaoTecnicaResponse(colunas);
    }

    private KanbanColunaResponse construirMacroColuna(
            MacroEtapaKanban macro,
            List<SolicitacaoMaterial> todas,
            ContextoSolicitacaoMaterial contexto
    ) {
        // Sub-etapas desta macro etapa apropriadas ao contexto
        List<SubEtapaKanban> subEtapasDaMacro = Arrays.stream(SubEtapaKanban.values())
                .filter(sub -> sub.getMacroEtapa() == macro && pertenceAoContexto(sub, contexto))
                .toList();

        List<KanbanSubColunaResponse> subColunas = subEtapasDaMacro.stream()
                .map(sub -> construirSubColuna(sub, todas, contexto))
                .toList();

        // Todos os itens desta macro etapa para cálculo de métricas agregadas
        List<SolicitacaoMaterial> itensDaMacro = todas.stream()
                .filter(s -> {
                    SubEtapaKanban sub = SubEtapaKanban.calcular(s);
                    return sub != null && sub.getMacroEtapa() == macro;
                })
                .toList();

        LocalDate hoje = LocalDate.now();

        long total = itensDaMacro.size();
        long atrasadas = itensDaMacro.stream()
                .filter(s -> s.getDataPrevistaRetirada() != null
                        && s.getDataPrevistaRetirada().isBefore(hoje)
                        && !s.estaEncerrada())
                .count();
        long foraPrazo = itensDaMacro.stream().filter(SolicitacaoMaterial::isForaPrazoSaque).count();
        Long diasMaisAntiga = itensDaMacro.stream()
                .map(s -> s.getDataSolicitacao() != null ? s.getDataSolicitacao().toLocalDate() : null)
                .filter(d -> d != null)
                .min(Comparator.naturalOrder())
                .map(d -> ChronoUnit.DAYS.between(d, hoje))
                .orElse(null);

        return new KanbanColunaResponse(
                macro,
                macro.getLabel(),
                macro.getIcon(),
                subColunas,
                new KanbanMetricasColuna(total, atrasadas, foraPrazo, diasMaisAntiga)
        );
    }

    private KanbanSubColunaResponse construirSubColuna(
            SubEtapaKanban subEtapa,
            List<SolicitacaoMaterial> todas,
            ContextoSolicitacaoMaterial contexto
    ) {
        List<SolicitacaoMaterial> itensDoSub = todas.stream()
                .filter(s -> SubEtapaKanban.calcular(s) == subEtapa)
                .toList();

        List<SolicitacaoSummaryResponse> cards = itensDoSub.stream()
                .limit(MAX_CARDS_POR_SUB_COLUNA)
                .map(s -> {
                    var acoes = politicaAcoesPermitidas.calcular(s);
                    SolicitacaoSummaryResponse resumo = mapper.toSummaryResponse(s);
                    resumo.setAcoesPermitidas(acoes);
                    if (contexto == ContextoSolicitacaoMaterial.TECNICO) {
                        resumo.setTecnico(DetalhesTecnicosSolicitacaoResponse.de(
                                s, List.of(s),
                                operacaoSolicitacaoRepository
                                        .findAllBySolicitacaoIdOrderByRealizadaEmAscIdAsc(s.getId()),
                                acoes
                        ));
                    }
                    return resumo;
                })
                .toList();

        return new KanbanSubColunaResponse(
                subEtapa,
                subEtapa.getLabel(),
                subEtapa.getCorPadrao(),
                cards,
                itensDoSub.size()
        );
    }

    private boolean pertenceAoContexto(SubEtapaKanban sub, ContextoSolicitacaoMaterial contexto) {
        if (contexto == ContextoSolicitacaoMaterial.COMERCIAL) {
            return sub != SubEtapaKanban.PENDENTE_PLANEJAMENTO
                    && sub != SubEtapaKanban.PLANEJADO
                    && sub != SubEtapaKanban.PRONTO_PARA_SEPARACAO
                    && sub != SubEtapaKanban.EM_SEPARACAO
                    && sub != SubEtapaKanban.SEPARADO
                    && sub != SubEtapaKanban.PENDENTE_DEVOLUCAO;
        } else {
            return sub != SubEtapaKanban.PENDENTE_APROVACAO;
        }
    }
}
