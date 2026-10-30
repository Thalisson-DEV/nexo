package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.AtivoInspecionavelSst;
import com.nexo.app.modules.sst.domain.HomologacaoInstituicaoSst;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class ReferenciaGovernancaSstDto {
    private ReferenciaGovernancaSstDto() {}

    public record ProcessoRequest(@NotBlank @Size(max=80) String codigo,
                                  @NotBlank @Size(max=180) String nome,
                                  @Size(max=255) String fonte) {}
    public record ProcessoResponse(UUID id,String codigo,String nome,int versaoReferencia,String fonte,
                                   boolean confirmado,LocalDate vigenteDesde,Long versao) {}

    public record InstituicaoRequest(@Size(max=20) String cnpj,@NotBlank @Size(max=200) String nome,
            @Size(max=240) String razaoSocial,@Size(max=240) String parceriaInstituicao,
            @Size(max=120) String inscricaoMunicipalLiteral,@Size(max=240) String local,
            @Size(max=4000) String telefones,@Size(max=4000) String contatos,
            boolean possuiDadoSuspeito) {}
    public record InstituicaoResponse(UUID id,String cnpj,String nome,String razaoSocial,String parceriaInstituicao,
            String inscricaoMunicipalLiteral,String local,String telefones,String contatos,
            String fonteReferencia,LocalDate dataReferencia,boolean possuiDadoSuspeito,
            List<HomologacaoResponse> homologacoes,Long versao) {}
    public record HomologacaoResponse(UUID id,HomologacaoInstituicaoSst.Status status,String statusFonte,
            String inicioLiteral,String fimLiteral,LocalDate vigenciaInicio,LocalDate vigenciaFim,String escopo,
            Boolean eadLiberado,String eadLiteral,Boolean competenciaLiberada,String competenciaLiteral,
            UUID idDocumentoEvidencia,OffsetDateTime confirmadaEm,Long versao) {}
    public record ConfirmarHomologacaoRequest(@NotNull HomologacaoInstituicaoSst.Status status,
            @NotNull LocalDate vigenciaInicio,LocalDate vigenciaFim,@Size(max=240) String escopo,
            Boolean eadLiberado,Boolean competenciaLiberada,UUID idDocumentoEvidencia) {}

    public record AtivoRequest(@NotNull UUID idBaseOperacional,@NotNull AtivoInspecionavelSst.Tipo tipo,
            @NotBlank @Size(max=80) String identificadorLocal,@NotBlank @Size(max=300) String localizacao,
            @Size(max=8000) String atributos,boolean possuiDadoSuspeito) {}
    public record AtivoResponse(UUID id,UUID idBaseOperacional,AtivoInspecionavelSst.Tipo tipo,
            String identificadorLocal,String localizacao,String atributos,String origem,
            AtivoInspecionavelSst.StatusConferencia statusConferencia,boolean possuiDadoSuspeito,
            LocalDate inicioVigencia,LocalDate fimVigencia,Long versao) {}
    public record ImportacaoResponse(String tipo,int quantidade,boolean jaExistia,String sha256Fonte) {}
}
