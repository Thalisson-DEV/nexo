package com.nexo.app.modules.obras.application.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nexo.app.modules.obras.domain.ClasseTensao;
import com.nexo.app.modules.obras.domain.OperacaoItemKit;
import com.nexo.app.modules.obras.domain.OrigemPlanejamento;
import com.nexo.app.modules.obras.domain.SituacaoProgramacao;
import com.nexo.app.modules.obras.domain.StatusItemPlanejado;
import com.nexo.app.modules.obras.domain.TipoExecucaoPonto;
import com.nexo.app.modules.obras.domain.TipoItemKit;
import com.nexo.app.modules.obras.domain.TipoPontoPlanejamento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Planejamento completo da obra numa só leitura, para o editor montar a folha e a árvore sem várias chamadas.
 * Os valores são só dos serviços: o congelado na programação ou, sem programação, o vigente hoje no contrato.
 */
public record PlanejamentoObraResponse(
        UUID idObra,
        String numeroObra,
        String tituloObra,
        UUID idContrato,
        ClasseTensao classeTensao,
        ProjetoMestre projetoMestre,
        Map<String, Integer> rotacoes,
        List<Ponto> pontos,
        List<Vao> vaos,
        List<Kit> kits,
        List<Anotacao> anotacoes,
        Indicadores indicadores,
        List<AvancoKit> avancoKits,
        List<ProgramacaoDoPlano> programacoes
) {

    /** Programação aberta da obra, para os selos da árvore e o estado atrasado na folha. */
    public record ProgramacaoDoPlano(UUID id, long numero, UUID idEquipe, String codigoEquipe,
                                     @JsonFormat(pattern = "dd/MM/yyyy") LocalDate inicio,
                                     @JsonFormat(pattern = "dd/MM/yyyy") LocalDate fim,
                                     SituacaoProgramacao situacao) {
    }

    public record ProjetoMestre(UUID idAnexo, String nomeArquivo, long tamanhoBytes) {
    }

    public record Ponto(UUID id, String numero, int pagina, BigDecimal x, BigDecimal y, String barramento,
                        TipoPontoPlanejamento tipo, TipoExecucaoPonto tipoExecucao, boolean temPoda,
                        boolean temAterramento, OrigemPlanejamento origem, String levantador,
                        OffsetDateTime dataCriacao, Long versao) {
    }

    public record Vao(UUID id, UUID idPontoA, UUID idPontoB, BigDecimal metragem, OrigemPlanejamento origem,
                      Long versao) {
    }

    public record Kit(UUID id, UUID idPonto, UUID idVao, UUID idKit, String codigo, String descricao, String grupo,
                      OperacaoItemKit operacao, BigDecimal multiplicador, boolean executadoAntes, Long versao,
                      List<Item> itens) {
    }

    public record Item(UUID id, TipoItemKit tipo, String codigo, String descricao, OperacaoItemKit operacao,
                       String unidade, BigDecimal quantidadePrevista, BigDecimal quantidadeExecutada,
                       StatusItemPlanejado status, BigDecimal valorUnitario, UUID idProgramacao, Long versao) {
    }

    public record Anotacao(UUID id, int pagina, BigDecimal x, BigDecimal y, String texto, String autor,
                           OffsetDateTime data, Long versao) {
    }

    public record Indicadores(BigDecimal percentualExecutado, BigDecimal valorExecutado, BigDecimal valorProgramado,
                              BigDecimal valorPendente, BigDecimal valorTotal, int servicosSemValor,
                              int pontosSemKit, int vaosSemKit) {
    }

    public record AvancoKit(String codigo, String descricao, BigDecimal percentualExecutado,
                            BigDecimal valorExecutado, BigDecimal valorTotal) {
    }
}
