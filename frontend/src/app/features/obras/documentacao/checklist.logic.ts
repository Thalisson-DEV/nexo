import {
  EstruturaChecklist,
  FinalidadeChecklist,
  GrupoChecklist,
  ItemEvidenciaModelo,
  ItemResposta,
  PerguntaChecklist,
  PontoDocumentacao,
  RespostaInformada,
  TipoPerguntaChecklist
} from '../../../core/models/obras-documentacao.models';
import { dataIso } from '../../../shared/listagem/listagem';

export const FINALIDADES: { label: string; value: FinalidadeChecklist }[] = [
  { label: 'Visita prévia', value: 'VISITA_PREVIA' },
  { label: 'Ficha As Built', value: 'AS_BUILT' }
];

export const TIPOS_PERGUNTA: { label: string; value: TipoPerguntaChecklist }[] = [
  { label: 'Escolha única', value: 'ESCOLHA_UNICA' },
  { label: 'Múltipla escolha', value: 'MULTIPLA_ESCOLHA' },
  { label: 'Texto', value: 'TEXTO' },
  { label: 'Número', value: 'NUMERO' },
  { label: 'Data', value: 'DATA' }
];

export function temOpcoes(tipo: TipoPerguntaChecklist): boolean {
  return tipo === 'ESCOLHA_UNICA' || tipo === 'MULTIPLA_ESCOLHA';
}

/** Na ficha As Built, campo sem ocorrência é marcado como "Não se aplica"; por isso já vem ligado. */
export function novaPergunta(finalidade: FinalidadeChecklist): PerguntaChecklist {
  return { id: crypto.randomUUID(), enunciado: '', tipo: 'ESCOLHA_UNICA', obrigatoria: true, aceitaNaoSeAplica: finalidade === 'AS_BUILT', opcoes: ['Sim', 'Não'] };
}

export function novoGrupo(finalidade: FinalidadeChecklist): GrupoChecklist {
  return { titulo: '', perguntas: [novaPergunta(finalidade)] };
}

export function novoItemEvidencia(): ItemEvidenciaModelo {
  return { id: crypto.randomUUID(), titulo: '', obrigatoria: true };
}

/** Itens obrigatórios do modelo que o ponto ainda não fotografou. */
export function itensFaltando(ponto: PontoDocumentacao): ItemEvidenciaModelo[] {
  const respondidos = new Set(ponto.evidencias.map((e) => e.idItem));
  return ponto.itensEvidencia.filter((item) => item.obrigatoria && !respondidos.has(item.id));
}

/** Primeiro problema do modelo, com a mesma regra do backend; `null` quando pode salvar. */
export function problemaDaEstrutura(estrutura: EstruturaChecklist): string | null {
  if ((estrutura.evidencias ?? []).some((item) => !item.titulo.trim())) return 'Informe o título de todas as fotos pedidas.';
  if (!estrutura.grupos.length) return 'Inclua ao menos um grupo.';
  for (const grupo of estrutura.grupos) {
    if (!grupo.titulo.trim()) return 'Informe o título de todos os grupos.';
    if (!grupo.perguntas.length) return `O grupo "${grupo.titulo}" não tem perguntas.`;
    for (const pergunta of grupo.perguntas) {
      if (!pergunta.enunciado.trim()) return `Há pergunta sem enunciado no grupo "${grupo.titulo}".`;
      if (temOpcoes(pergunta.tipo)) {
        const opcoes = pergunta.opcoes ?? [];
        if (opcoes.length < 2 || new Set(opcoes).size !== opcoes.length) return `"${pergunta.enunciado}" precisa de ao menos duas opções diferentes.`;
      }
    }
  }
  return null;
}

/** Texto digitado (uma opção por linha) para a lista de opções. */
export function opcoesDoTexto(texto: string): string[] {
  return texto.split('\n').map((linha) => linha.trim()).filter(Boolean);
}

/** Resposta enquanto é preenchida: cada tipo usa o seu campo. */
export interface RespostaEmEdicao {
  texto: string;
  numero: number | null;
  data: Date | null;
  selecionadas: string[];
  naoSeAplica: boolean;
}

export type RespostasEmEdicao = Record<string, RespostaEmEdicao>;

export function respostaVazia(): RespostaEmEdicao {
  return { texto: '', numero: null, data: null, selecionadas: [], naoSeAplica: false };
}

/** Começa em branco ou a partir do que já foi respondido; pergunta que não existe mais no modelo é ignorada. */
export function respostasIniciais(estrutura: EstruturaChecklist, itens: ItemResposta[] = []): RespostasEmEdicao {
  const respostas: RespostasEmEdicao = {};
  for (const pergunta of estrutura.grupos.flatMap((g) => g.perguntas)) {
    const item = itens.find((i) => i.idPergunta === pergunta.id);
    respostas[pergunta.id] = {
      texto: pergunta.tipo === 'TEXTO' ? item?.valor ?? '' : '',
      numero: pergunta.tipo === 'NUMERO' && item?.valor != null ? Number(item.valor) : null,
      data: pergunta.tipo === 'DATA' && item?.valor ? new Date(`${item.valor}T00:00:00`) : null,
      selecionadas: item?.selecionadas ?? [],
      naoSeAplica: item?.naoSeAplica ?? false
    };
  }
  return respostas;
}

export function paraInformadas(estrutura: EstruturaChecklist, respostas: RespostasEmEdicao): RespostaInformada[] {
  return estrutura.grupos.flatMap((g) => g.perguntas).map((pergunta) => {
    const r = respostas[pergunta.id] ?? respostaVazia();
    return {
      idPergunta: pergunta.id,
      valor: valorDe(pergunta.tipo, r),
      selecionadas: temOpcoes(pergunta.tipo) ? r.selecionadas : [],
      naoSeAplica: r.naoSeAplica
    };
  });
}

function valorDe(tipo: TipoPerguntaChecklist, r: RespostaEmEdicao): string | null {
  if (tipo === 'TEXTO') return r.texto.trim() || null;
  if (tipo === 'NUMERO') return r.numero?.toString() ?? null;
  return tipo === 'DATA' ? dataIso(r.data) : null;
}

export function respondida(pergunta: PerguntaChecklist, r: RespostaEmEdicao | undefined): boolean {
  if (!r) return false;
  if (r.naoSeAplica) return true;
  if (temOpcoes(pergunta.tipo)) return r.selecionadas.length > 0;
  if (pergunta.tipo === 'NUMERO') return r.numero != null;
  if (pergunta.tipo === 'DATA') return r.data != null;
  return !!r.texto.trim();
}

/** Perguntas obrigatórias sem resposta. */
export function pendentes(estrutura: EstruturaChecklist, respostas: RespostasEmEdicao): PerguntaChecklist[] {
  return estrutura.grupos.flatMap((g) => g.perguntas).filter((p) => p.obrigatoria && !respondida(p, respostas[p.id]));
}

/** Clicar na opção marcada desmarca; na escolha única, a nova substitui a anterior. */
export function alternarOpcao(pergunta: PerguntaChecklist, atuais: string[], opcao: string): string[] {
  if (atuais.includes(opcao)) return atuais.filter((o) => o !== opcao);
  return pergunta.tipo === 'ESCOLHA_UNICA' ? [opcao] : [...atuais, opcao];
}
