import { TestBed } from '@angular/core/testing';
import { EnvironmentInjector, createEnvironmentInjector, signal } from '@angular/core';
import { RascunhoService, DEBOUNCE_RASCUNHO_MS } from './rascunho.service';
import { RascunhoStore } from './rascunho.store';
import { SessaoDb, SESSAO_DB, ID_ABA } from './sessao-db';
import { TokenService } from '../auth/token.service';

const espera = (ms: number) => new Promise(r => setTimeout(r, ms));

interface Form { passo: number; nota: string; series: string[] }

describe('RascunhoService (formulário ↔ rascunho)', () => {
  let db: SessaoDb;
  let parent: EnvironmentInjector;

  beforeEach(async () => {
    db = new SessaoDb(`teste-vinculo-${crypto.randomUUID()}`);
    await db.open();
    TestBed.configureTestingModule({
      providers: [
        { provide: SESSAO_DB, useValue: db },
        { provide: ID_ABA, useValue: 'aba-teste' },
        { provide: TokenService, useValue: { ownerId: signal('u-1') } },
      ],
    });
    parent = TestBed.inject(EnvironmentInjector);
  });

  afterEach(async () => {
    db.close();
    await db.delete();
  });

  /** Uma "tela": injetor próprio (destruído ao sair) e signals do formulário. */
  function abrirTela() {
    const injector = createEnvironmentInjector([], parent);
    const passo = signal(1);
    const nota = signal('');
    const series = signal<string[]>([]);
    const vinculo = TestBed.inject(RascunhoService).vincular<Form>({
      chave: 'execucao:nova',
      injector,
      ler: () => ({ passo: passo(), nota: nota(), series: series() }),
      aplicar: v => { passo.set(v.passo); nota.set(v.nota); series.set(v.series); },
      titulo: () => (nota() ? `Nota ${nota()} · passo ${passo()} de 4` : undefined),
      rota: () => '/campo/nova',
      vazio: v => v.passo === 1 && !v.nota && v.series.length === 0,
    });
    return { injector, passo, nota, series, vinculo };
  }

  async function assentar() {
    TestBed.flushEffects();
    await espera(DEBOUNCE_RASCUNHO_MS + 120);
  }

  it('abrir e sair sem digitar não cria rascunho fantasma', async () => {
    const t = abrirTela();
    expect(await t.vinculo.restaurado).toBeFalse();
    await assentar();
    t.injector.destroy();
    await espera(50);
    expect(await db.rascunhos.count()).toBe(0);
  });

  it('cada alteração é gravada ~300 ms depois, com título para "continuar"', async () => {
    const t = abrirTela();
    await t.vinculo.restaurado;
    t.nota.set('7004512');
    t.passo.set(2);
    await assentar();
    const r = await db.rascunhos.get('execucao:nova');
    expect(r?.valor).toEqual({ passo: 2, nota: '7004512', series: [] });
    expect(r?.titulo).toBe('Nota 7004512 · passo 2 de 4');
  });

  it('processo morto: reabrir a tela reaplica tudo, inclusive o passo', async () => {
    const a = abrirTela();
    await a.vinculo.restaurado;
    a.nota.set('7004512');
    a.passo.set(3);
    a.series.set(['SN-0001']);
    await assentar();
    // Sem destroy: o processo simplesmente sumiu.

    const b = abrirTela();
    expect(await b.vinculo.restaurado).toBeTrue();
    expect([b.passo(), b.nota(), b.series()]).toEqual([3, '7004512', ['SN-0001']]);
  });

  it('sair da tela antes do debounce grava na hora (destroy)', async () => {
    const t = abrirTela();
    await t.vinculo.restaurado;
    t.nota.set('123');
    TestBed.flushEffects();
    t.injector.destroy();
    await espera(60);
    expect((await db.rascunhos.get('execucao:nova'))?.valor).toEqual({ passo: 1, nota: '123', series: [] });
  });

  it('app indo para segundo plano grava na hora', async () => {
    const t = abrirTela();
    await t.vinculo.restaurado;
    t.nota.set('999');
    TestBed.flushEffects();
    await TestBed.inject(RascunhoService).gravarTodos();
    expect(((await db.rascunhos.get('execucao:nova'))?.valor as Form | undefined)?.nota).toBe('999');
  });

  it('concluir (envio enfileirado) apaga o rascunho e os arquivos', async () => {
    const t = abrirTela();
    await t.vinculo.restaurado;
    t.nota.set('1');
    await assentar();
    await t.vinculo.anexar(new Blob(['foto']), 'foto.jpg');
    await t.vinculo.concluir();
    t.nota.set('2');
    await assentar();
    expect(await db.rascunhos.count()).toBe(0);
    expect(await db.anexos.count()).toBe(0);
  });

  it('armazenamento cheio: segue em memória e grava quando houver espaço', async () => {
    const store = TestBed.inject(RascunhoStore);
    const put = spyOn(db.rascunhos, 'put').and.rejectWith(Object.assign(new Error('q'), { name: 'QuotaExceededError' }));
    const t = abrirTela();
    await t.vinculo.restaurado;
    t.nota.set('5');
    await assentar();
    expect(store.estado()).toBe('cheio');
    put.and.callThrough();
    await t.vinculo.gravarAgora();
    expect(((await db.rascunhos.get('execucao:nova'))?.valor as Form | undefined)?.nota).toBe('5');
    expect(store.estado()).toBe('ok');
  });
});
