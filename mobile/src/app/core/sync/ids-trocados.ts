/**
 * Quando um registro criado no aparelho é aceito pelo servidor, o id temporário é
 * trocado pelo id real. Quem estava com a tela aberta (ou volta pelo histórico, ou
 * reabre o app no detalhe) ainda tem o id antigo na URL: este mapa deixa a tela
 * seguir o registro em vez de mostrar "não encontrado".
 *
 * Guarda só os últimos 100 pares; perder o mapa só traz de volta o "não encontrado".
 */
const CHAVE = 'ces_ids_trocados';
const LIMITE = 100;

function ler(): Record<string, string> {
  try {
    return JSON.parse(localStorage.getItem(CHAVE) || '{}') as Record<string, string>;
  } catch {
    return {};
  }
}

export function registrarIdTrocado(temporario: string, real: string): void {
  if (!temporario || !real || temporario === real) return;
  try {
    const mapa = ler();
    delete mapa[temporario];
    mapa[temporario] = real;
    const chaves = Object.keys(mapa);
    for (const k of chaves.slice(0, Math.max(0, chaves.length - LIMITE))) delete mapa[k];
    localStorage.setItem(CHAVE, JSON.stringify(mapa));
  } catch { /* armazenamento indisponível: segue sem o atalho */ }
}

export function idAtual(id: string): string | null {
  const real = ler()[id];
  return real && real !== id ? real : null;
}
