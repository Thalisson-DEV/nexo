package com.nexo.app.modules.obras.application.dto;

import com.nexo.app.shared.application.dto.ImportacaoStatus;

/**
 * Linha da planilha da carteira: uma obra. Obra existente usa só as colunas de carteira preenchidas;
 * obra nova exige também os dados do cadastro (título, processo, base, município, centro de custo,
 * supervisor, coordenador, início e prazo).
 */
public record CarteiraImportacaoLinha(
        Integer linha,
        String numero,
        String titulo,
        String tensao,
        String prazo,
        String prazoRegulado,
        String prioridade,
        String lotePrevisto,
        String valorProjeto,
        String valorMaterialPrevisto,
        String valorServicoPrevisto,
        String latitude,
        String longitude,
        String processoNegocio,
        String baseOperacional,
        String municipio,
        String centroCusto,
        String supervisor,
        String coordenador,
        String statusObra,
        String dataInicio,
        String tipoObra,
        Boolean obraNova,
        ImportacaoStatus status,
        String mensagemErro
) {
    public CarteiraImportacaoLinha withStatus(Boolean nova, ImportacaoStatus novoStatus, String novaMensagem) {
        return new CarteiraImportacaoLinha(linha, numero, titulo, tensao, prazo, prazoRegulado, prioridade, lotePrevisto,
                valorProjeto, valorMaterialPrevisto, valorServicoPrevisto, latitude, longitude,
                processoNegocio, baseOperacional, municipio, centroCusto, supervisor, coordenador, statusObra,
                dataInicio, tipoObra, nova, novoStatus, novaMensagem);
    }
}
