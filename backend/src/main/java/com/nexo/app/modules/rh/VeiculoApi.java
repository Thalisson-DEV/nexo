package com.nexo.app.modules.rh;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface VeiculoApi {
    
    record VeiculoBaseData(UUID veiculoId, Double latBase, Double lngBase, Integer raioBase) {}
    
    record VeiculoEquipeData(UUID veiculoId, UUID equipeId, String equipeCodigo) {}

    /**
     * Retorna os dados da base operacional vinculada ao veículo.
     */
    VeiculoBaseData buscarDadosBase(UUID veiculoId);
    
    /**
     * Retorna um mapa de todos os veículos ativos e os dados de suas bases.
     */
    Map<UUID, VeiculoBaseData> listarDadosBaseAtivos();
record VeiculoCompletoData(UUID id, String placa, UUID baseId, String baseNome, Double baseLat, Double baseLng, Integer baseRaio) {}

/**
 * Retorna a lista de veículos ativos com dados da base operacional.
 */
List<VeiculoCompletoData> listarVeiculosCompletosAtivos(java.util.List<UUID> baseIds);

/**
 * Retorna um mapa de Veículo ID -> Dados da Equipe vinculada.
...

     */
    Map<UUID, VeiculoEquipeData> listarVinculosEquipeAtivos();

    /**
     * Retorna a lista de IDs de veículos ativos, opcionalmente filtrada por bases.
     */
    List<UUID> listarIdsVeiculosAtivos(java.util.List<UUID> baseIds);

    /**
     * Retorna o conjunto de todas as placas de veículos ativos no Nexo.
     */
    Set<String> listarPlacasAtivas();

    /**
     * Retorna um mapa de placa -> ID de todos os veículos ativos.
     */
    Map<String, UUID> mapaPlacasIds();

    /**
     * Verifica se uma placa está cadastrada e ativa no Nexo.
     */
    boolean isVeiculoCadastrado(String placa);

    /**
     * Verifica se um veículo existe e está ativo (por ID).
     */
    boolean existeVeiculo(UUID id);

    List<UUID> buscarIdsPorTrecho(String trecho);

    /**
     * Busca os dados de um veículo por ID para exibição em outros módulos.
     */
    java.util.Optional<VeiculoResponse> buscarPorIdResponse(UUID id);

    /** Resolve veículos em lote (ativos e inativos); IDs inexistentes ficam de fora. */
    Map<UUID, VeiculoResponse> buscarPorIdsResponse(java.util.Collection<UUID> ids);

    /**
     * Dados cadastrais que pertencem ao RH. A ficha de frota (tipo, chassi, regime...)
     * fica no módulo de frota, que chama estes métodos para manter um único id de veículo.
     */
    record DadosCadastroVeiculo(String placa, String modelo, String fabricante, UUID idBaseOperacional) {}

    /** Cadastra o veículo com as mesmas regras da tela do RH (placa única, base existente). */
    VeiculoResponse cadastrar(DadosCadastroVeiculo dados);

    /** Altera placa, modelo, fabricante e base com as mesmas regras da tela do RH. */
    VeiculoResponse alterarCadastro(UUID id, DadosCadastroVeiculo dados);
}
