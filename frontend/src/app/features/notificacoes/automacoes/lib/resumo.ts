import { Catalogo, Definicao, FlowNode, NoEspec, Params } from '../../../../core/models/automacao.models';
import { lista, objetos, texto } from './arvore';
import { descreverExpressao, segmentos } from './dinamico';

const DIAS = ['segunda', 'terça', 'quarta', 'quinta', 'sexta', 'sábado', 'domingo'];

/** Troca {{ expr }} pelo nome amigável, para caber numa linha do cartão. */
export function legivel(valor: string, d: Definicao, c: Catalogo): string {
  return segmentos(valor)
    .map((s) => (s.expressao !== undefined ? `[${descreverExpressao(s.expressao, d, c).split(' › ').pop()}]` : s.texto))
    .join('')
    .replace(/\s+/g, ' ')
    .trim();
}

export function descreverAgenda(p: Params): string {
  const f = texto(p['frequency']) || 'week';
  const intervalo = Number(p['interval'] ?? 1) || 1;
  const hora = texto(p['time']) || '08:00';
  switch (f) {
    case 'minute':
      return `A cada ${intervalo} minutos`;
    case 'hour':
      return intervalo === 1 ? 'A cada hora' : `A cada ${intervalo} horas`;
    case 'day':
      return intervalo === 1 ? `Todo dia às ${hora}` : `A cada ${intervalo} dias às ${hora}`;
    case 'month':
      return `Todo dia ${texto(p['day_of_month']) || '1'} do mês às ${hora}`;
    default: {
      const dias = lista(p['weekdays']).map(Number).sort();
      if (!dias.length) return 'Escolha os dias';
      const nomes = dias.join(',') === '0,1,2,3,4' ? 'dia útil' : dias.map((x) => DIAS[x] ?? '?').join(', ');
      return `Toda ${nomes} às ${hora}`;
    }
  }
}

function contarRegras(valor: unknown): number {
  if (!valor || typeof valor !== 'object') return 0;
  const regras = (valor as { rules?: unknown[] }).rules ?? [];
  return regras.reduce<number>((s, r) => s + (r && typeof r === 'object' && 'rules' in (r as object) ? contarRegras(r) : 1), 0);
}

function destinatarios(valor: unknown): string {
  const lista = objetos(valor as never);
  if (!lista.length) return 'ninguém ainda';
  const nomes = lista.map((d) => texto(d['rotulo']) || (texto(d['tipo']) === 'expressao' ? 'conteúdo dinâmico' : texto(d['valor'])));
  return nomes.length > 2 ? `${nomes.slice(0, 2).join(', ')} +${nomes.length - 2}` : nomes.join(', ');
}

export function resumirNo(no: FlowNode | null, espec: NoEspec | undefined, d: Definicao, c: Catalogo): string {
  if (!espec) return 'Tipo desconhecido';
  const p = no ? no.params : d.trigger.params;
  switch (espec.tipo) {
    case 'trigger.schedule':
      return descreverAgenda(p);
    case 'trigger.manual': {
      const n = objetos(p['inputs']).length;
      return n ? `Pede ${n} informação(ões) ao executar` : 'Quando alguém clicar em Executar';
    }
    case 'trigger.automation_failed':
      return lista(p['automations']).length ? 'Quando as automações escolhidas falharem' : 'Quando qualquer automação falhar';
    case 'control.condition': {
      const n = contarRegras(p['condition']);
      return n ? `${n} regra(s)` : 'Sem regras ainda';
    }
    case 'control.switch':
      return texto(p['on']) ? `Conforme ${legivel(texto(p['on']), d, c)}` : 'Escolha o valor';
    case 'control.foreach':
      return texto(p['items']) ? `Cada item de ${legivel(texto(p['items']), d, c)}` : 'Escolha a lista';
    case 'notify.people':
      return `${legivel(texto(p['title']), d, c) || 'Sem título'} → ${destinatarios(p['recipients'])}`;
    case 'email.send':
      return `${legivel(texto(p['subject']), d, c) || 'Sem assunto'} → ${destinatarios(p['to'])}`;
    case 'people.route': {
      const rotas = objetos(p['routes']);
      return rotas.length ? `${rotas.map((r) => texto(r['when']) || '…').join(' · ')} · senão` : 'Monte a tabela de quem recebe';
    }
    case 'ai.copiloto':
    case 'ai.prompt': {
      const t = legivel(texto(p['prompt'] ?? p['instructions']), d, c);
      return t ? (t.length > 70 ? `${t.slice(0, 68)}…` : t) : 'Escreva o pedido';
    }
    case 'ces.query':
      return texto(p['source']) ? `Fonte: ${texto(p['source'])}` : 'Escolha a fonte de dados';
    case 'approval.request':
      return `${legivel(texto(p['title']), d, c) || 'O que aprovar?'} → ${destinatarios(p['approvers'])}`;
    case 'control.wait': {
      const modo = texto(p['mode']) || 'duration';
      if (modo === 'weekday') return `Até ${DIAS[Number(p['weekday'] ?? 4)] ?? ''} às ${texto(p['time']) || '17:00'}`;
      if (modo === 'datetime') return `Até ${texto(p['until']) || '…'}`;
      const partes = [['days', 'dia(s)'], ['hours', 'hora(s)'], ['minutes', 'min']]
        .filter(([k]) => Number(p[k!] ?? 0) > 0).map(([k, r]) => `${texto(p[k!])} ${r}`);
      return partes.length ? `Por ${partes.join(' e ')}` : 'Informe o tempo';
    }
    case 'http.request':
      return `${texto(p['method']) || 'POST'} ${texto(p['url'])}`.trim();
    default:
      if (espec.gatilho && espec.chaveEvento) return espec.descricao;
      if (espec.tipo.startsWith('threshold.')) return `Quando chegar a ${texto(p['limiar']) || '1'}`;
      return espec.descricao;
  }
}
