package com.nexo.app.modules.sincronizacao.application.usecases;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.app.modules.execucao.ExecucaoComercialApi;
import com.nexo.app.modules.execucao.TurnoApi;
import com.nexo.app.modules.execucao.application.dto.RegistrarExecucaoRequest;
import com.nexo.app.modules.execucao.application.dto.AbrirTurnoRequest;
import com.nexo.app.modules.estoque.MovimentacaoEstoqueApi;
import com.nexo.app.modules.estoque.SolicitacaoMaterialApi;
import com.nexo.app.modules.estoque.application.dto.MovimentacaoBulkRequest;
import com.nexo.app.modules.estoque.application.dto.SolicitacaoRequest;
import com.nexo.app.modules.sincronizacao.domain.TipoOperacaoSincronizacao;
import com.nexo.app.modules.sincronizacao.domain.AnexoSincronizacao;
import com.nexo.app.modules.sst.InspecaoSincronizacaoApi;
import com.nexo.app.modules.sst.domain.ContextoAnexo;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Executa somente portas tipadas, numa transação separada da fila administrativa. */
@Service @RequiredArgsConstructor
public class SincronizacaoReplayExecutor {
 private final ObjectMapper mapper; private final TurnoApi turnoApi; private final ExecucaoComercialApi execucaoApi;
 private final SolicitacaoMaterialApi solicitacaoApi; private final MovimentacaoEstoqueApi movimentacaoApi;
 private final InspecaoSincronizacaoApi inspecaoApi;
 @Transactional
 public void executar(TipoOperacaoSincronizacao tipo, Map<String,Object> original, UUID atorOrigem, List<AnexoSincronizacao> anexos){
  Map<String,Object> p=payloadReal(original);
  try { switch(tipo){
   case TURNO -> { if(p.get("idTurno")!=null) turnoApi.fechar(UUID.fromString(String.valueOf(p.get("idTurno")))); else turnoApi.abrir(mapper.convertValue(p,AbrirTurnoRequest.class)); }
   case EXECUCAO -> execucaoApi.registrar(mapper.convertValue(p,RegistrarExecucaoRequest.class),atorOrigem);
   case SOLICITACAO -> solicitacaoApi.criar(mapper.convertValue(p,SolicitacaoRequest.class),atorOrigem);
   case TRANSFERENCIA -> movimentacaoApi.transferenciaLote(mapper.convertValue(p,MovimentacaoBulkRequest.class),atorOrigem);
   case INSPECAO -> inspecaoApi.registrar(p,anexosEvidencia(original,anexos),atorOrigem);
   case ANEXO -> { if(anexos.size()!=1 || p.get("idInspecao")==null || p.get("idDesvio")==null || !(p.get("anexo") instanceof Map<?,?> dados)) throw new InvarianteVioladaException("Anexo requer arquivo, inspeção, desvio e contexto."); String contexto=String.valueOf(dados.get("contexto")); inspecaoApi.anexar(UUID.fromString(String.valueOf(p.get("idInspecao"))),UUID.fromString(String.valueOf(p.get("idDesvio"))),ContextoAnexo.valueOf(contexto),anexos.get(0),atorOrigem); }
  }} catch(IllegalArgumentException e){throw new InvarianteVioladaException("Payload incompatível com o adaptador de "+tipo+".");}
 }
 @SuppressWarnings("unchecked") private Map<String,Object> payloadReal(Map<String,Object> payload){
  Object body=payload.get("body"); if(body instanceof Map<?,?>) return (Map<String,Object>) body;
  Object request=payload.get("request"); if(request instanceof Map<?,?>) return (Map<String,Object>) request;
  var copy=new LinkedHashMap<>(payload); copy.remove("tempId"); copy.remove("erroOrigem"); return copy;
 }
 private List<AnexoSincronizacao> anexosEvidencia(Map<String,Object> original,List<AnexoSincronizacao> anexos){
  Object raw=original.get("evidencias");
  if(!(raw instanceof List<?> evidencias)) return anexos;
  Set<String> ids=new HashSet<>();
  for(Object item:evidencias) if(item instanceof Map<?,?> e && e.get("idLocal")!=null) ids.add(String.valueOf(e.get("idLocal")));
  return anexos.stream().filter(a->ids.contains(a.getIdAnexoLocal().toString())).toList();
 }
}
