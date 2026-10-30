package com.nexo.app.modules.estoque.application.dto;

import io.swagger.v3.oas.annotations.Parameter;
import org.springdoc.core.annotations.ParameterObject;

/** O que exportar do painel de solicitações; o recorte vem de {@link PainelSolicitacoesFilter}. */
@ParameterObject
public record ExportarPainelSolicitacoesRequest(
    @Parameter(description = "SOLICITACOES (padrão), MATERIAIS, EQUIPES ou ALMOXARIFADOS")
    Conteudo conteudo,

    @Parameter(description = "RESUMO (padrão) ou DETALHADO. Solicitações detalhadas saem um item por linha; "
            + "materiais detalhados saem por processo e sentido")
    Nivel nivel,

    @Parameter(description = "Situação atual: ABERTA, ATRASADA, CONCLUIDA ou RECUSADA; vazio traz todas")
    Situacao situacao,

    @Parameter(description = "Inclui as solicitações ainda abertas criadas antes do período (padrão: sim)")
    Boolean incluirBacklog,

    @Parameter(description = "csv (padrão) ou xlsx")
    String formato
) {

    public enum Conteudo { SOLICITACOES, MATERIAIS, EQUIPES, ALMOXARIFADOS }

    public enum Nivel { RESUMO, DETALHADO }

    public enum Situacao { ABERTA, ATRASADA, CONCLUIDA, RECUSADA }

    public Conteudo conteudoOuPadrao() {
        return conteudo == null ? Conteudo.SOLICITACOES : conteudo;
    }

    public Nivel nivelOuPadrao() {
        return nivel == null ? Nivel.RESUMO : nivel;
    }

    public boolean incluirBacklogOuPadrao() {
        return incluirBacklog == null || incluirBacklog;
    }

    public boolean planilha() {
        return "xlsx".equalsIgnoreCase(formato);
    }
}
