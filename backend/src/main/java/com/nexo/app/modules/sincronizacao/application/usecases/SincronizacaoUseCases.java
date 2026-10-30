package com.nexo.app.modules.sincronizacao.application.usecases;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.app.modules.iam.infrastructure.persistence.UsuarioRepository;
import com.nexo.app.modules.execucao.TurnoApi;
import com.nexo.app.modules.execucao.ExecucaoComercialApi;
import com.nexo.app.modules.execucao.application.dto.RegistrarExecucaoRequest;
import com.nexo.app.modules.estoque.SolicitacaoMaterialApi;
import com.nexo.app.modules.estoque.MovimentacaoEstoqueApi;
import com.nexo.app.modules.estoque.application.dto.SolicitacaoRequest;
import com.nexo.app.modules.estoque.application.dto.MovimentacaoBulkRequest;
import com.nexo.app.modules.notificacao.application.dto.NovaNotificacao;
import com.nexo.app.modules.notificacao.application.usecases.PublicadorNotificacoes;
import com.nexo.app.modules.notificacao.domain.*;
import com.nexo.app.modules.sincronizacao.application.dto.SincronizacaoDtos.*;
import com.nexo.app.modules.sincronizacao.domain.*;
import com.nexo.app.modules.sincronizacao.infrastructure.persistence.*;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class SincronizacaoUseCases {
 private final OperacaoSincronizacaoRepository operacoes;
 private final CasoSincronizacaoRepository casos;
 private final VersaoCasoSincronizacaoRepository versoes;
 private final AnexoSincronizacaoRepository anexos;
 private final ObjectMapper mapper;
 private final UsuarioRepository usuarios;
 private final PublicadorNotificacoes publicador;
 private final SincronizacaoReplayExecutor replayExecutor;
 private final SincronizacaoFalhaService falhaService;
 private final DiagnosticoCasoSincronizacao diagnostico;
 private final ReferenciasCasoSincronizacao referencias;

 @Transactional
 public OperacaoResponse receber(OperacaoRequest r, UUID usuario) {
  exigirUsuario(usuario);
  rejeitarSegredos(r.payload());
  var anterior=operacoes.findByIdOperacaoLocalAndIdUsuarioOrigem(r.idOperacaoLocal(),usuario);
  String hash=hashEnvelope(r);
  if(anterior.isPresent()) { if(!hash.equalsIgnoreCase(anterior.get().getHashOriginal())) throw new InvarianteVioladaException("O UUID local já foi recebido com outro conteúdo."); return resposta(anterior.get(),casos.findByOperacaoId(anterior.get().getId()).orElse(null)); }
  OperacaoSincronizacao op=new OperacaoSincronizacao(r.idOperacaoLocal(),r.idRegistroLocal(),r.tipo(),usuario,r.instanteOrigem(),r.payload(),hash);
  operacoes.save(op);
  long totalAnexos=0;
  for(var a:r.anexos()){
   byte[] bytes;
   try{bytes=Base64.getDecoder().decode(a.conteudoBase64());}catch(IllegalArgumentException e){throw new InvarianteVioladaException("Anexo base64 inválido.");}
   if(bytes.length>15_000_000) throw new InvarianteVioladaException("Anexo excede o limite de 15 MB.");
   totalAnexos += bytes.length; if(totalAnexos>30_000_000) throw new InvarianteVioladaException("A soma dos anexos excede o limite de 30 MB.");
   if(!hash(bytes).equalsIgnoreCase(a.sha256())) throw new InvarianteVioladaException("Hash do anexo não confere.");
   anexos.save(new AnexoSincronizacao(op,a.idLocal(),a.nome(),a.contentType(),a.sha256().toLowerCase(),bytes));
  }
  // Até os adaptadores tipados estarem ligados, a fila segura cada escrita para tratamento humano.
  op.emTratamento();
  String motivo=(r.erroOrigem()==null||r.erroOrigem().isBlank())
          ? "Registro recebido e preservado; aguardando tratamento administrativo."
          : "Erro informado pelo aplicativo: "+r.erroOrigem().substring(0, Math.min(r.erroOrigem().length(), 1800));
  var caso=casos.save(new CasoSincronizacao(op,motivo));
  notificarBackoffice(caso);
  return resposta(op,caso);
 }

 @Transactional(readOnly=true) public OperacaoResponse consultar(UUID local,UUID usuario){
  var op=operacoes.findByIdOperacaoLocalAndIdUsuarioOrigem(local,usuario).orElseThrow(()->new RecursoNaoEncontradoException("Operação não encontrada."));
  return resposta(op,casos.findByOperacaoId(op.getId()).orElse(null));
 }
 @Transactional(readOnly=true) public PageResponse<CasoResponse> listar(Pageable p,StatusCasoSincronizacao status,TipoOperacaoSincronizacao tipo,String busca,LocalDate receivedFrom,LocalDate receivedTo){
  var zone=ZoneId.of("America/Sao_Paulo");
  var inicio=receivedFrom==null?null:receivedFrom.atStartOfDay(zone).toOffsetDateTime();
  var fim=receivedTo==null?null:receivedTo.plusDays(1).atStartOfDay(zone).toOffsetDateTime();
  Specification<CasoSincronizacao> filtro=(root,query,cb)->{
   var predicados=new ArrayList<jakarta.persistence.criteria.Predicate>();
   if(status!=null) predicados.add(cb.equal(root.get("status"),status));
   if(tipo!=null) predicados.add(cb.equal(root.join("operacao").get("tipo"),tipo));
   if(inicio!=null) predicados.add(cb.greaterThanOrEqualTo(root.get("dataCriacao"),inicio));
   if(fim!=null) predicados.add(cb.lessThan(root.get("dataCriacao"),fim));
   if(busca!=null && !busca.isBlank()){
    String termo=busca.trim();
    var opcoes=new ArrayList<jakarta.persistence.criteria.Predicate>();
    opcoes.add(cb.like(cb.lower(root.get("motivo")),"%"+termo.toLowerCase(Locale.ROOT)+"%"));
    try { opcoes.add(cb.equal(root.join("operacao").get("idOperacaoLocal"),UUID.fromString(termo))); }
    catch(IllegalArgumentException ignored) { }
    try { opcoes.add(cb.equal(root.join("operacao").get("tipo"),TipoOperacaoSincronizacao.valueOf(termo.toUpperCase(Locale.ROOT)))); }
    catch(IllegalArgumentException ignored) { }
    predicados.add(cb.or(opcoes.toArray(jakarta.persistence.criteria.Predicate[]::new)));
   }
   return cb.and(predicados.toArray(jakarta.persistence.criteria.Predicate[]::new));
  };
  var page=casos.findAll(filtro,normalizarOrdenacao(p));
  return PageResponse.fromPage(page.map(c->casoResponse(c,false)));
 }
 static Pageable normalizarOrdenacao(Pageable pageable) {
  if (pageable.isUnpaged()) return Pageable.unpaged(Sort.by("dataCriacao").ascending());
  var ordens = new ArrayList<Sort.Order>();
  for (var ordem : pageable.getSort()) {
   String propriedade = switch (ordem.getProperty()) {
    case "receivedAt", "dataCriacao" -> "dataCriacao";
    case "tipo", "operationType" -> "operacao.tipo";
    case "status" -> "status";
    default -> null;
   };
   if (propriedade != null) ordens.add(new Sort.Order(ordem.getDirection(), propriedade));
  }
  if (ordens.isEmpty()) ordens.add(Sort.Order.asc("dataCriacao"));
  ordens.add(Sort.Order.asc("id"));
  return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(ordens));
 }
 @Transactional(readOnly=true) public CasoResponse detalhar(UUID id){return casoResponse(casos.findById(id).orElseThrow(()->new RecursoNaoEncontradoException("Caso não encontrado.")),true);}
 @Transactional(readOnly=true) public AnexoSincronizacao baixarAnexo(UUID idCaso,UUID idAnexo){var c=buscar(idCaso);return anexos.findById(idAnexo).filter(a->a.getOperacao().getId().equals(c.getOperacao().getId())).orElseThrow(()->new RecursoNaoEncontradoException("Anexo não encontrado."));}
 @Transactional public CasoResponse atribuir(UUID id,UUID responsavel){var c=buscar(id);if(!usuarios.buscarIdsAtivosComPermissao("sincronizacao:manage").contains(responsavel)) throw new InvarianteVioladaException("O responsável precisa ser um usuário ativo com permissão de gestão.");c.atribuir(responsavel);return casoResponse(casos.save(c),true);}
 @Transactional public CasoResponse corrigir(UUID id,CorrecaoRequest r,UUID usuario){var c=buscar(id);c.corrigir(r.justificativa());rejeitarSegredos(r.payload());int n=versoes.findByCaso_IdOrderByNumeroAsc(c.getId()).size()+1;versoes.save(new VersaoCasoSincronizacao(c,n,r.payload(),r.justificativa(),usuario,hash(r.payload())));c.getOperacao().emTratamento();return casoResponse(casos.save(c),true);}
 @Transactional public CasoResponse descartar(UUID id,DescarteRequest r){var c=buscar(id);c.rejeitar(r.justificativa());c.getOperacao().rejeitada();return casoResponse(casos.save(c),true);}
 /** Reenvia a última versão (ou o original, quando o caso não precisa de correção) pelo adaptador tipado. */
 @Transactional public CasoResponse reprocessar(UUID id,ReprocessamentoRequest r){
  var c=buscar(id);if(c.isEncerrado())throw new InvarianteVioladaException("Este caso ja foi encerrado.");
  var payload=versoes.findTopByCaso_IdOrderByNumeroDesc(c.getId()).map(VersaoCasoSincronizacao::getPayload).orElse(c.getOperacao().getPayloadOriginal());
  try { replayExecutor.executar(c.getOperacao().getTipo(),payload,c.getOperacao().getIdUsuarioOrigem(),anexos.findByOperacao_Id(c.getOperacao().getId())); }
  catch(RuntimeException e){ falhaService.registrar(c.getId(),e.getMessage()); throw e; }
  c.getOperacao().tentativa();
  c.getOperacao().emTratamento();
  c.resolver(r.justificativa());c.getOperacao().resolvida();return casoResponse(casos.save(c),true);
 }
 /** Reenvio disparado pelo sistema, em transação própria: a falha de um caso não desfaz os demais. */
 @Transactional(propagation=Propagation.REQUIRES_NEW) public CasoResponse reenviarAutomaticamente(UUID id,String justificativa){return reprocessar(id,new ReprocessamentoRequest(justificativa));}
 private CasoSincronizacao buscar(UUID id){return casos.findById(id).orElseThrow(()->new RecursoNaoEncontradoException("Caso não encontrado."));}
 /** Casos da equipe recusados por falta de turno, com nota do mesmo dia da abertura do turno. */
 @Transactional(readOnly=true) public List<UUID> aguardandoTurnoDaEquipe(UUID idEquipe,LocalDate dia){
  Specification<CasoSincronizacao> abertos=(root,query,cb)->cb.and(
   root.get("status").in(StatusCasoSincronizacao.ABERTO,StatusCasoSincronizacao.EM_ANALISE,StatusCasoSincronizacao.AGUARDANDO_REPROCESSAMENTO),
   cb.equal(root.join("operacao").get("tipo"),TipoOperacaoSincronizacao.EXECUCAO));
  var zona=ZoneId.of("America/Sao_Paulo");
  return casos.findAll(abertos,Sort.by("dataCriacao")).stream().filter(DiagnosticoCasoSincronizacao::semTurnoAberto).filter(c->{
   var p=payloadAtual(c);
   var lancamento=DiagnosticoCasoSincronizacao.data(p.get("dataLancamento")!=null?p.get("dataLancamento"):p.get("dataInicio"));
   return idEquipe.equals(DiagnosticoCasoSincronizacao.uuid(p.get("idEquipe"))) && lancamento!=null && lancamento.atZoneSameInstant(zona).toLocalDate().equals(dia);
  }).map(CasoSincronizacao::getId).toList();
 }
 /** Payload que o próximo reenvio usaria: a última correção ou o original, sem o envelope do aparelho. */
 private Map<String,Object> payloadAtual(CasoSincronizacao c){
  return payloadSemEnvelope(versoes.findTopByCaso_IdOrderByNumeroDesc(c.getId()).map(VersaoCasoSincronizacao::getPayload).orElse(c.getOperacao().getPayloadOriginal()));
 }
 private CasoResponse casoResponse(CasoSincronizacao c,boolean completo){
  var vs=versoes.findByCaso_IdOrderByNumeroAsc(c.getId());
  var op=c.getOperacao();
  var atual=payloadSemEnvelope(vs.isEmpty()?op.getPayloadOriginal():vs.get(vs.size()-1).getPayload());
  var diag=completo?diagnostico.completo(c,atual):diagnostico.resumo(c,atual);
  Map<String,String> nomes=Map.of();
  if(completo){var payloads=new ArrayList<Map<String,Object>>();payloads.add(payloadSemEnvelope(op.getPayloadOriginal()));vs.forEach(v->payloads.add(payloadSemEnvelope(v.getPayload())));nomes=referencias.resolver(payloads);}
  var ats=anexos.findByOperacao_Id(op.getId()).stream().map(a->new AnexoResponse(a.getId(),a.getIdAnexoLocal(),a.getNome(),a.getContentType(),a.getSha256(),a.getTamanhoBytes())).toList();
  var historico=vs.stream().map(v->Map.<String,Object>of("numero",v.getNumero(),"payload",v.getPayload(),"justificativa",v.getJustificativa(),"hash",v.getHash(),"idUsuario",v.getIdUsuario(),"criadaEm",v.getDataCriacao())).toList();
  return new CasoResponse(c.getId(),op.getIdOperacaoLocal(),op.getTipo(),c.getStatus(),c.getMotivo(),c.getJustificativaUltima(),c.getIdResponsavel(),op.getIdUsuarioOrigem(),op.getPayloadOriginal(),vs.isEmpty()?null:vs.get(vs.size()-1).getPayload(),historico,c.getDataCriacao(),c.getDataUltimaAlteracao(),op.getTentativas(),ats,diag,nomes,c.getDataResolucao());
 }
 @SuppressWarnings("unchecked") private static Map<String,Object> payloadSemEnvelope(Map<String,Object> p){
  if(p==null) return Map.of();
  if(p.get("body") instanceof Map<?,?> body) return (Map<String,Object>) body;
  if(p.get("request") instanceof Map<?,?> request) return (Map<String,Object>) request;
  return p;
 }
 private OperacaoResponse resposta(OperacaoSincronizacao o,CasoSincronizacao c){return new OperacaoResponse(o.getIdOperacaoLocal(),o.getIdRegistroLocal(),o.getTipo(),o.getStatus(),c!=null,c==null?null:c.getId(),o.getHashOriginal(),o.getTentativas());}
 private void notificarBackoffice(CasoSincronizacao c){var ids=usuarios.buscarIdsAtivosComPermissao("sincronizacao:manage");if(ids.isEmpty())return;publicador.publicar(NovaNotificacao.builder().destinatarios(new HashSet<>(ids)).origem(OrigemNotificacao.SISTEMA).categoria(CategoriaNotificacao.SISTEMA).prioridade(PrioridadeNotificacao.ALTA).titulo("Registro de campo recebido para tratamento").corpo("Um registro foi preservado e aguarda análise do backoffice.").deeplink("/sincronizacao/casos/"+c.getId()).contexto(Map.of("idCaso",c.getId().toString())).chaveIdempotencia("sincronizacao:caso:"+c.getId()).email(false).build());}
 private String hash(Object value){try{return hash(mapper.writeValueAsBytes(value));}catch(Exception e){throw new InvarianteVioladaException("Não foi possível calcular a integridade do registro.");}}
 private String hashEnvelope(OperacaoRequest r){var envelope=new LinkedHashMap<String,Object>();envelope.put("idOperacaoLocal",r.idOperacaoLocal());envelope.put("idRegistroLocal",r.idRegistroLocal());envelope.put("tipo",r.tipo().name());envelope.put("instanteOrigem",r.instanteOrigem().toString());envelope.put("payload",r.payload());envelope.put("anexos",r.anexos().stream().map(a->Map.of("idLocal",a.idLocal(),"nome",a.nome(),"contentType",a.contentType(),"sha256",a.sha256().toLowerCase(Locale.ROOT),"tamanhoBase64",a.conteudoBase64().length())).toList());return hash(envelope);}
 private String hash(byte[] b){try{var d=MessageDigest.getInstance("SHA-256");var out=d.digest(b);return java.util.HexFormat.of().formatHex(out);}catch(Exception e){throw new IllegalStateException(e);}}
 private void exigirUsuario(UUID id){if(id==null)throw new InvarianteVioladaException("Usuário autenticado não identificado.");}
 private void rejeitarSegredos(Object value){
  if(value instanceof Map<?,?> map){
   for(var e:map.entrySet()){
    String key=String.valueOf(e.getKey()).toLowerCase(Locale.ROOT);
    if(key.contains("authorization")||key.contains("bearer")||key.contains("token")||key.contains("senha")||key.contains("password")||key.contains("secret")) throw new InvarianteVioladaException("O snapshot não pode conter credenciais ou tokens.");
    rejeitarSegredos(e.getValue());
   }
  } else if(value instanceof Collection<?> collection) collection.forEach(this::rejeitarSegredos);
 }
}
