/**
 * Nome do header que carrega a chave de idempotência até o backend.
 * Espelha `IdempotenciaFilter.HEADER_CHAVE` no ces-backend.
 */
export const HEADER_IDEMPOTENCIA = 'Idempotency-Key';

/**
 * Gera uma chave nova para uma intenção de escrita.
 *
 * <p>Uma chave = uma intenção, não uma requisição. Duas retentativas do mesmo salvamento
 * compartilham a chave (e o backend executa uma vez); dois salvamentos que o usuário
 * realmente quis fazer têm chaves diferentes (e o backend executa os dois).</p>
 *
 * `crypto.randomUUID` exige contexto seguro (https ou localhost). O fallback existe para
 * não derrubar o salvamento em um ambiente servido por http puro — a chave só precisa ser
 * única, não imprevisível, porque o backend já a escopa por usuário autenticado.
 */
export function novaChaveIdempotencia(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return `k-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`;
}

/**
 * Impressão digital de uma mutação, usada para reconhecer o duplo clique: mesma rota,
 * mesmo corpo, tudo ainda em voo.
 *
 * <p>`FormData` é resumido por nome/tamanho dos campos em vez de conteúdo — ler 15 MB de
 * anexo para comparar bytes custaria mais que o próprio envio, e nome + tamanho já
 * distinguem o segundo clique de um segundo arquivo.</p>
 *
 * Retorna `null` quando o corpo não é resumível com segurança; nesse caso não há
 * coalescência, apenas a chave de idempotência protege.
 */
export function digitalDaMutacao(metodo: string, url: string, corpo: unknown): string | null {
  const resumoCorpo = resumirCorpo(corpo);
  return resumoCorpo === null ? null : `${metodo} ${url} ${resumoCorpo}`;
}

function resumirCorpo(corpo: unknown): string | null {
  if (corpo === null || corpo === undefined) return '';

  if (corpo instanceof FormData) {
    const partes: string[] = [];
    corpo.forEach((valor, chave) => {
      partes.push(
        valor instanceof File
          ? `${chave}:${valor.name}:${valor.size}:${valor.lastModified}`
          : `${chave}:${String(valor)}`
      );
    });
    return partes.sort().join('|');
  }

  if (corpo instanceof Blob || corpo instanceof ArrayBuffer) return null;

  try {
    return JSON.stringify(corpo);
  } catch {
    // Estrutura cíclica: sem resumo confiável, melhor não coalescer do que coalescer errado.
    return null;
  }
}
