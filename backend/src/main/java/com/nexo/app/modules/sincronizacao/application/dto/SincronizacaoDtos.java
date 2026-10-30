package com.nexo.app.modules.sincronizacao.application.dto;

import com.nexo.app.modules.sincronizacao.domain.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.*;

public final class SincronizacaoDtos {
 private SincronizacaoDtos(){}
 public record AnexoRequest(@NotNull UUID idLocal,@NotBlank @Size(max=255) String nome,@NotBlank @Size(max=150) String contentType,@NotBlank @Pattern(regexp="[0-9a-fA-F]{64}") String sha256,@NotBlank @Size(max=22_000_000) String conteudoBase64){}
 public record OperacaoRequest(@NotNull UUID idOperacaoLocal, UUID idRegistroLocal,@NotNull TipoOperacaoSincronizacao tipo,@NotNull OffsetDateTime instanteOrigem,@NotNull Map<String,Object> payload,@Valid List<AnexoRequest> anexos,String erroOrigem){
  public OperacaoRequest { anexos=anexos==null?List.of():List.copyOf(anexos); if(anexos.size()>20) throw new IllegalArgumentException("No máximo 20 anexos por operação."); }
 }
 public record OperacaoResponse(UUID idOperacaoLocal,UUID idRegistroLocal,TipoOperacaoSincronizacao tipo,StatusOperacaoSincronizacao status,boolean backofficeInformado,UUID idCaso,String hashOriginal,int tentativas){}
 public record AtribuicaoRequest(@NotNull UUID idResponsavel){}
 public record CorrecaoRequest(@NotNull Map<String,Object> payload,@NotBlank @Size(max=4000) String justificativa){}
 public record ReprocessamentoRequest(@NotBlank @Size(max=4000) String justificativa){}
 public record DescarteRequest(@NotBlank @Size(max=4000) String justificativa){}
 public record AnexoResponse(UUID id,UUID idLocal,String nome,String contentType,String sha256,long tamanhoBytes){}
 /** Próximo passo de quem trata o caso. */
 public enum AcaoRecomendada { REENVIAR, CORRIGIR, CONFERIR, AGUARDAR, DESCARTAR }
 public record DiagnosticoResponse(String codigo,String titulo,String explicacao,String orientacao,AcaoRecomendada acao){}
 /** {@code referencias} traz o nome de cada cadastro citado no registro, pela sua referência; só vem no detalhe. */
 public record CasoResponse(UUID id,UUID idOperacaoLocal,TipoOperacaoSincronizacao tipo,StatusCasoSincronizacao status,String motivo,String justificativaUltima,UUID idResponsavel,UUID idUsuarioOrigem,Map<String,Object> original,Map<String,Object> ultimaVersao,List<Map<String,Object>> versoes,OffsetDateTime recebidoEm,OffsetDateTime atualizadoEm,int tentativas,List<AnexoResponse> anexos,DiagnosticoResponse diagnostico,Map<String,String> referencias,OffsetDateTime resolvidoEm){}
}
