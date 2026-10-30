package com.nexo.app.modules.sincronizacao.application.usecases;

import com.nexo.app.modules.contrato.ContratoApi;
import com.nexo.app.modules.estoque.EstoqueApi;
import com.nexo.app.modules.execucao.TipoServicoApi;
import com.nexo.app.modules.execucao.TurnoApi;
import com.nexo.app.modules.execucao.application.dto.TurnoResponse;
import com.nexo.app.modules.rh.EquipeApi;
import com.nexo.app.modules.rh.VeiculoApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;

import static com.nexo.app.modules.sincronizacao.application.usecases.DiagnosticoCasoSincronizacao.uuid;

/**
 * Nome de cada cadastro citado no registro (equipe, turno, tipo de serviço, ações,
 * materiais e veículo), para a tela de tratamento nunca mostrar identificador interno.
 * Referência que não resolve fica de fora; a tela mostra "não localizado".
 */
@Component @RequiredArgsConstructor
public class ReferenciasCasoSincronizacao {
 private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
 private static final DateTimeFormatter DIA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");
 private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

 private final EquipeApi equipeApi;
 private final TurnoApi turnoApi;
 private final TipoServicoApi tipoServicoApi;
 private final ContratoApi contratoApi;
 private final EstoqueApi estoqueApi;
 private final VeiculoApi veiculoApi;

 public Map<String, String> resolver(Collection<Map<String, Object>> payloads) {
  var nomes = new LinkedHashMap<String, String>();
  var tiposServico = new HashMap<UUID, String>();
  for (var p : payloads) {
   if (p == null) continue;
   resolverUm(nomes, p.get("idEquipe"), id -> equipeApi.buscarPublicaPorId(id).map(e -> "Equipe " + e.codigo()).orElse(null));
   resolverUm(nomes, p.get("idTurno"), id -> rotuloTurno(turnoApi.buscarPorId(id)));
   resolverUm(nomes, p.get("idVeiculo"), id -> veiculoApi.buscarPorIdResponse(id).map(v -> v.placa() + (v.modelo() == null ? "" : " · " + v.modelo())).orElse(null));
   resolverUm(nomes, p.get("idTipoServico"), id -> {
    if (tiposServico.isEmpty()) tipoServicoApi.listarTodos().forEach(t -> tiposServico.put(t.id(), t.codigo() + " · " + t.descricao()));
    return tiposServico.get(id);
   });
   if (p.get("acoes") instanceof List<?> acoes) acoes.forEach(a -> resolverUm(nomes, a, id -> contratoApi.buscarAcaoPorIdResponse(id).map(x -> x.codigo() + " · " + x.nome()).orElse(null)));
   for (String chave : List.of("materiais", "itens")) {
    if (p.get(chave) instanceof List<?> itens) for (Object item : itens) if (item instanceof Map<?, ?> m)
     resolverUm(nomes, m.get("idMaterial"), id -> estoqueApi.buscarMaterialPorIdResponse(id).map(x -> x.codigo() + " · " + x.descricao()).orElse(null));
   }
  }
  return nomes;
 }

 static String rotuloTurno(TurnoResponse t) {
  if (t == null || t.dataInicio() == null) return null;
  var abertura = t.dataInicio().atZoneSameInstant(ZONA);
  String fim = t.dataFim() == null ? "em aberto"
          : t.encerradoAutomaticamente() ? "encerrado pelo sistema" : "fechado às " + t.dataFim().atZoneSameInstant(ZONA).format(HORA);
  return "Turno aberto em " + abertura.format(DIA_HORA) + " (" + fim + ")";
 }

 private static void resolverUm(Map<String, String> nomes, Object valor, Function<UUID, String> busca) {
  UUID id = uuid(valor);
  if (id == null || nomes.containsKey(id.toString())) return;
  try {
   String nome = busca.apply(id);
   if (nome != null) nomes.put(id.toString(), nome);
  } catch (RuntimeException ignorada) {
   // Cadastro removido ou inacessível: a tela mostra a referência como não localizada.
  }
 }
}
