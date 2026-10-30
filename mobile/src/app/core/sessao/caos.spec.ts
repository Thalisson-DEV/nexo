import { Injector } from '@angular/core';
import { RascunhoStore, MARCA_CONFLITO } from './rascunho.store';
import { SessaoDb, SESSAO_DB, ID_ABA } from './sessao-db';

/**
 * Caos com sementes reproduzíveis: duas abas gravando o mesmo rascunho, falhas
 * de gravação injetadas (processo morto, quota), leituras intercaladas.
 *
 * Invariantes, para qualquer sequência:
 * 1. nenhuma operação lança para quem chamou;
 * 2. o que se lê é sempre um valor que alguma aba gravou com sucesso (nunca um meio-termo);
 * 3. o valor principal é o da última gravação bem-sucedida;
 * 4. todo valor bem-sucedido continua recuperável (principal ou cópia de conflito)
 *    — nenhuma digitação some.
 *
 * Para reproduzir uma falha: rode só a semente que aparece no contexto do erro.
 */

/** mulberry32: PRNG pequeno e determinístico. */
function prng(semente: number) {
  let a = semente >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function loja(db: SessaoDb, aba: string) {
  return Injector.create({
    providers: [
      { provide: SESSAO_DB, useValue: db },
      { provide: ID_ABA, useValue: aba },
      { provide: RascunhoStore, useClass: RascunhoStore },
    ],
  }).get(RascunhoStore);
}

const SEMENTES = [1, 7, 42, 1337, 2026, 31337, 65537, 99991];
const PASSOS = 60;

describe('caos: rascunho com duas abas e falhas injetadas', () => {
  let conflitosExercitados = 0;
  afterAll(() => {
    // Sem isto a invariante 4 poderia passar sem nunca ter sido exercida.
    expect(conflitosExercitados).toBeGreaterThan(SEMENTES.length);
  });

  for (const semente of SEMENTES) {
    it(`semente ${semente}`, async () => {
      const rnd = prng(semente);
      const db = new SessaoDb(`teste-caos-${semente}-${crypto.randomUUID()}`);
      await db.open();
      const abas = [loja(db, 'A'), loja(db, 'B')];
      const versaoVista = [0, 0];
      const gravados = new Set<string>();
      let ultimo: string | null = null;
      let abaDoUltimo = -1;
      let versaoGravada = 0;
      const devemSobreviver = new Set<string>();
      let relogio = 1_000;

      const putOriginal = db.rascunhos.put.bind(db.rascunhos);
      let falharProxima: 'nada' | 'morte' | 'quota' = 'nada';
      spyOn(db.rascunhos, 'put').and.callFake(((...args: Parameters<typeof putOriginal>) => {
        const registro = args[0] as { chave: string };
        if (!registro.chave.includes(MARCA_CONFLITO) && falharProxima !== 'nada') {
          const tipo = falharProxima;
          falharProxima = 'nada';
          return Promise.reject(tipo === 'quota'
            ? Object.assign(new Error('q'), { name: 'QuotaExceededError' })
            : new Error('processo morto'));
        }
        return putOriginal(...args);
      }) as typeof putOriginal);

      try {
        for (let passo = 0; passo < PASSOS; passo++) {
          const i = rnd() < 0.5 ? 0 : 1;
          const r = rnd();
          relogio += 1 + Math.floor(rnd() * 1000);
          const contexto = `semente ${semente}, passo ${passo}`;

          if (r < 0.15) {
            falharProxima = rnd() < 0.5 ? 'morte' : 'quota';
          } else if (r < 0.35) {
            const lido = await abas[i].ler<{ v: string }>('u', 'k');
            if (lido) {
              versaoVista[i] = lido.versao;
              expect(gravados.has(lido.valor.v)).withContext(contexto).toBeTrue();
              expect(lido.valor.v).withContext(contexto).toBe(ultimo!);
            }
          } else {
            const v = `${'AB'[i]}-${passo}`;
            const base = versaoVista[i];
            const versao = await abas[i].salvar('u', 'k', { v }, { versaoBase: base }, relogio);
            if (versao !== null) {
              // Sobrescreveu sem ter visto a versão da outra aba: ela tem de ser preservada.
              if (ultimo !== null && abaDoUltimo !== i && base !== versaoGravada) devemSobreviver.add(ultimo);
              gravados.add(v);
              ultimo = v;
              abaDoUltimo = i;
              versaoGravada = versao;
              versaoVista[i] = versao;
            }
          }
        }

        const todos = await db.rascunhos.toArray();
        const recuperaveis = new Set(todos.map(r => (r.valor as { v: string }).v));
        conflitosExercitados += devemSobreviver.size;
        for (const v of devemSobreviver) {
          expect(recuperaveis.has(v)).withContext(`semente ${semente}: ${v} perdido`).toBeTrue();
        }
        const principal = todos.find(r => r.chave === 'k');
        expect(principal ? (principal.valor as { v: string }).v : null).withContext(`semente ${semente}: final`).toBe(ultimo);
        for (const r of todos) {
          expect(gravados.has((r.valor as { v: string }).v)).withContext(`semente ${semente}: ${r.chave}`).toBeTrue();
        }
      } finally {
        db.close();
        await db.delete();
      }
    });
  }
});
