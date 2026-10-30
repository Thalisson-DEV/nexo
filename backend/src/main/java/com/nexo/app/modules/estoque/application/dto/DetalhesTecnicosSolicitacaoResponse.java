package com.nexo.app.modules.estoque.application.dto;

import com.nexo.app.modules.estoque.domain.AcaoPermitidaSolicitacao;
import com.nexo.app.modules.estoque.domain.CondicaoMaterial;
import com.nexo.app.modules.estoque.domain.EtapaOperacionalSolicitacao;
import com.nexo.app.modules.estoque.domain.OperacaoSolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.ReservaSolicitacaoTecnica;
import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.StatusSeparacao;
import com.nexo.app.modules.estoque.domain.TipoObraSnapshot;
import com.nexo.app.modules.estoque.domain.TipoOperacaoSolicitacao;
import com.nexo.app.modules.estoque.domain.TipoSolicitacaoTecnica;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Complemento aditivo do detalhe legado para filhos de uma solicitacao tecnica. */
public record DetalhesTecnicosSolicitacaoResponse(
        TipoSolicitacaoTecnica tipoTecnico,
        TipoObraSnapshot tipoObra,
        UUID idObra,
        OcorrenciaResponse ocorrencia,
        UUID idAlmoxarifadoAtendimento,
        UUID idEquipe,
        UUID idColaboradorSaque,
        boolean saqueForaEquipe,
        LocalDate dataPrevista,
        String observacao,
        StatusSeparacao statusSeparacao,
        ReservaResponse reserva,
        TotaisResponse totais,
        List<OperacaoResponse> operacoes,
        long versao,
        Set<AcaoPermitidaSolicitacao> acoesPermitidas,
        boolean atrasada,
        boolean foraPrazoSaque,
        boolean encerrada,
        OffsetDateTime encerradaEm,
        UUID idUsuarioEncerramento,
        String motivoEncerramento,
        EtapaOperacionalSolicitacao etapaOperacional
) {

    public static DetalhesTecnicosSolicitacaoResponse de(
            SolicitacaoMaterial solicitacao,
            List<SolicitacaoMaterial> relacionadas,
            List<OperacaoSolicitacaoMaterial> operacoes,
            Set<AcaoPermitidaSolicitacao> acoesPermitidas
    ) {
        return new DetalhesTecnicosSolicitacaoResponse(
                solicitacao.getTipoTecnico(), solicitacao.getTipoObraSnapshot(), solicitacao.getIdObra(),
                solicitacao.getIdOcorrencia() == null ? null
                        : new OcorrenciaResponse(solicitacao.getIdOcorrencia(), solicitacao.getOcorrencia()),
                solicitacao.getAlmoxarifadoAtendimento().getId(),
                solicitacao.getIdEquipeSaque(),
                solicitacao.getIdColaboradorSaque(),
                solicitacao.isSaqueForaEquipe(),
                solicitacao.getDataPrevistaRetirada(),
                solicitacao.getObservacao(), solicitacao.getStatusSeparacao(), ReservaResponse.de(solicitacao.getReserva()),
                TotaisResponse.de(solicitacao), operacoes.stream().map(OperacaoResponse::de).toList(),
                solicitacao.getVersao() == null ? 0 : solicitacao.getVersao(), acoesPermitidas,
                estaAtrasada(solicitacao), solicitacao.isForaPrazoSaque(), solicitacao.estaEncerrada(), solicitacao.getEncerradaEm(), solicitacao.getIdUsuarioEncerramento(),
                solicitacao.getMotivoEncerramento(), EtapaOperacionalSolicitacao.calcular(solicitacao));
    }

    private static boolean estaAtrasada(SolicitacaoMaterial solicitacao) {
        return solicitacao.getDataPrevistaRetirada() != null
                && solicitacao.getDataPrevistaRetirada().isBefore(LocalDate.now())
                && !solicitacao.estaEncerrada()
                && solicitacao.getItens().stream().anyMatch(
                        item -> item.getQuantidadePendente().signum() > 0);
    }

    public record ReservaResponse(
            UUID id, String numero, LocalDate dataCriacao, UUID idAgenteResponsavel,
            String nomeAgente, String empresaAgente, UUID idUsuarioRegistro, OffsetDateTime registradaEm,
            OffsetDateTime atualizadaEm, UUID idUsuarioAtualizacao, long versao
    ) {
        static ReservaResponse de(ReservaSolicitacaoTecnica reserva) {
            return reserva == null ? null : new ReservaResponse(
                    reserva.getId(), reserva.getNumeroApi(), reserva.getDataCriacao(), reserva.getIdAgenteResponsavel(),
                    reserva.getNomeAgenteSnapshot(), reserva.getEmpresaAgenteSnapshot(), reserva.getIdUsuarioRegistro(),
                    reserva.getRegistradaEm(), reserva.getAtualizadaEm(), reserva.getIdUsuarioAtualizacao(),
                    reserva.getVersao());
        }
    }

    public record OcorrenciaResponse(UUID id, String codigo) {
    }

    public record TotaisResponse(
            BigDecimal solicitada, BigDecimal entregaBruta, BigDecimal entregaEstornada,
            BigDecimal entregaLiquida, BigDecimal devolucaoBruta, BigDecimal devolucaoEstornada,
            BigDecimal devolucaoLiquida, BigDecimal pendente
    ) {
        static TotaisResponse de(SolicitacaoMaterial solicitacao) {
            return new TotaisResponse(
                    somar(solicitacao, item -> item.getQuantidade()),
                    somar(solicitacao, item -> item.getQuantidadeEntregueBruta()),
                    somar(solicitacao, item -> item.getQuantidadeEntregaEstornada()),
                    somar(solicitacao, item -> item.getQuantidadeEntregaLiquida()),
                    somar(solicitacao, item -> item.getQuantidadeDevolvidaBruta()),
                    somar(solicitacao, item -> item.getQuantidadeDevolucaoEstornada()),
                    somar(solicitacao, item -> item.getQuantidadeDevolucaoLiquida()),
                    somar(solicitacao, item -> item.getQuantidadePendente()));
        }

        private static BigDecimal somar(
                SolicitacaoMaterial solicitacao,
                java.util.function.Function<com.nexo.app.modules.estoque.domain.ItemSolicitacao, BigDecimal> valor
        ) {
            return solicitacao.getItens().stream().map(valor).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    public record OperacaoResponse(
            UUID id, TipoOperacaoSolicitacao tipo, UUID idEquipe, UUID idColaboradorSaque, boolean saqueForaEquipe,
            UUID idUsuarioOperador, OffsetDateTime realizadaEm, String motivo, UUID idOperacaoOriginal,
            List<ItemOperacaoResponse> itens
    ) {
        static OperacaoResponse de(OperacaoSolicitacaoMaterial operacao) {
            return new OperacaoResponse(
                    operacao.getId(), operacao.getTipo(), operacao.getIdEquipe(), operacao.getIdColaboradorSaque(),
                    operacao.isSaqueForaEquipe(), operacao.getIdUsuarioOperador(),
                    operacao.getRealizadaEm(), operacao.getMotivo(),
                    operacao.getOperacaoOriginal() == null ? null : operacao.getOperacaoOriginal().getId(),
                    operacao.getItens().stream().map(ItemOperacaoResponse::de).toList());
        }
    }

    public record ItemOperacaoResponse(
            UUID id, UUID idItemSolicitacao, UUID idMaterial, String codigoMaterial, String descricaoMaterial,
            BigDecimal quantidade, String numeroSerie, CondicaoMaterial condicao, UUID idItemOperacaoOriginal
    ) {
        static ItemOperacaoResponse de(com.nexo.app.modules.estoque.domain.ItemOperacaoSolicitacao item) {
            return new ItemOperacaoResponse(
                    item.getId(), item.getItemSolicitacao().getId(), item.getMaterial().getId(),
                    item.getCodigoMaterialSnapshot(), item.getDescricaoMaterialSnapshot(),
                    item.getQuantidade(), item.getNumeroSerie(), item.getCondicao(),
                    item.getItemOperacaoOriginal() == null ? null : item.getItemOperacaoOriginal().getId());
        }
    }
}
