package com.nexo.app.modules.contrato;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * API Pública do módulo de Contrato para consultas externas.
 */
public interface ContratoApi {

    /**
     * Calcula o valor total faturado de uma execução, considerando o coeficiente
     * do contrato e o valor base de cada ação vigente na data de execução.
     *
     * @param idAcoes Lista de IDs das ações executadas.
     * @param idContrato ID do contrato vinculado à equipe.
     * @param dataExecucao Data de referência para vigência de valores.
     * @return Valor total calculado.
     */
    BigDecimal calcularValorFaturado(List<UUID> idAcoes, UUID idContrato, LocalDate dataExecucao);

    record AcaoValorItem(UUID idAcao, String codigo, String nome, BigDecimal valor) {}

    /**
     * Lista todas as ações ativas com seus respectivos valores calculados (valorBase * coeficiente)
     * para o contrato informado na data de referência.
     */
    List<AcaoValorItem> listarAcoesComValor(UUID idContrato, LocalDate dataReferencia);

    /**
     * Verifica se o contrato existe, está ativo e tem vigência na data informada.
     */
    boolean existeContratoAtivoNaData(UUID idContrato, LocalDate data);

    /**
     * Busca os dados de uma ação por ID para exibição.
     */
    Optional<AcaoResponse> buscarAcaoPorIdResponse(UUID id);

    /**
     * Busca várias ações de uma vez, para quem monta listas. Ids sem ação ficam fora do resultado.
     */
    List<AcaoResponse> buscarAcoesPorIdsResponse(Collection<UUID> ids);

    /**
     * Verifica se uma ação existe e está ativa.
     */
    boolean existeAcao(UUID id);

    /**
     * Dados mínimos de um contrato para exibição em outros módulos (ex.: o campo
     * "OBRA" das solicitações de compra de obras civis).
     */
    record ContratoResumo(UUID id, String numeroContrato, String cliente, boolean ativo) {}

    /**
     * Verifica se um contrato existe e está ativo, sem considerar vigência.
     */
    boolean existeContrato(UUID id);

    /**
     * Busca os dados resumidos de um contrato por ID.
     */
    Optional<ContratoResumo> buscarContratoResumo(UUID id);

    List<UUID> buscarIdsPorTrecho(String trecho);
}
