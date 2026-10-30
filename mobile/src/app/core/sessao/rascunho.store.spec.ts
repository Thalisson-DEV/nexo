import { Injector } from '@angular/core';
import { RascunhoStore, MARCA_CONFLITO, RASCUNHO_ANTIGO_MS, ANEXO_ORFAO_MS } from './rascunho.store';
import { SessaoDb, SESSAO_DB, ID_ABA } from './sessao-db';

function novaStore(db: SessaoDb, aba: string): RascunhoStore {
  return Injector.create({
    providers: [
      { provide: SESSAO_DB, useValue: db },
      { provide: ID_ABA, useValue: aba },
      { provide: RascunhoStore, useClass: RascunhoStore },
    ],
  }).get(RascunhoStore);
}

function erroQuota(): Error {
  return Object.assign(new Error('The quota has been exceeded.'), { name: 'QuotaExceededError' });
}

describe('RascunhoStore (IndexedDB real)', () => {
  let db: SessaoDb;
  let store: RascunhoStore;
  const U = 'u-1';

  beforeEach(async () => {
    db = new SessaoDb(`teste-rascunho-${crypto.randomUUID()}`);
    await db.open();
    store = novaStore(db, 'aba-A');
  });

  afterEach(async () => {
    db.close();
    await db.delete();
  });

  it('grava e lê de volta o valor, a versão e os metadados', async () => {
    const v = await store.salvar(U, 'execucao:nova', { nota: '7004512', passo: 2 }, { titulo: 'Nota 7004512', rota: '/campo/nova' });
    expect(v).toBe(1);
    const lido = await store.ler<{ nota: string; passo: number }>(U, 'execucao:nova');
    expect(lido?.valor).toEqual({ nota: '7004512', passo: 2 });
    expect(lido?.versao).toBe(1);
    expect(lido?.rota).toBe('/campo/nova');
    expect(await store.salvar(U, 'execucao:nova', { nota: '7004512', passo: 3 })).toBe(2);
  });

  it('não entrega rascunho de outra conta', async () => {
    await store.salvar(U, 'execucao:nova', { nota: '1' });
    expect(await store.ler('u-2', 'execucao:nova')).toBeNull();
  });

  it('rascunho corrompido não é aplicado e também não é apagado', async () => {
    await store.salvar(U, 'execucao:nova', { nota: '1' });
    await db.rascunhos.update('execucao:nova', { valor: { nota: '2' } as unknown });
    expect(await store.ler(U, 'execucao:nova')).toBeNull();
    expect(await db.rascunhos.get('execucao:nova')).toBeDefined();
  });

  it('armazenamento cheio: não lança, sinaliza e mantém a versão anterior', async () => {
    await store.salvar(U, 'execucao:nova', { nota: '1' });
    spyOn(db.rascunhos, 'put').and.rejectWith(erroQuota());
    const v = await store.salvar(U, 'execucao:nova', { nota: '2' });
    expect(v).toBeNull();
    expect(store.estado()).toBe('cheio');
    expect((await db.rascunhos.get('execucao:nova'))?.valor).toEqual({ nota: '1' });
  });

  it('volta a "ok" quando uma gravação seguinte funciona', async () => {
    const put = spyOn(db.rascunhos, 'put').and.rejectWith(erroQuota());
    await store.salvar(U, 'k', { a: 1 });
    expect(store.estado()).toBe('cheio');
    put.and.callThrough();
    await store.salvar(U, 'k', { a: 2 });
    expect(store.estado()).toBe('ok');
  });

  it('conflito entre abas: a versão da outra aba é preservada antes de sobrescrever', async () => {
    const outraAba = novaStore(db, 'aba-B');
    await store.salvar(U, 'execucao:nova', { nota: 'A1' }, {}, 1000);
    const lidoA = await store.ler(U, 'execucao:nova');
    await outraAba.salvar(U, 'execucao:nova', { nota: 'B1' }, { versaoBase: lidoA!.versao }, 2000);
    // A aba A ainda acha que está na versão 1 e grava por cima.
    await store.salvar(U, 'execucao:nova', { nota: 'A2' }, { versaoBase: 1 }, 3000);

    expect((await store.ler(U, 'execucao:nova'))?.valor).toEqual({ nota: 'A2' });
    const copia = await db.rascunhos.get(`execucao:nova${MARCA_CONFLITO}2000`);
    expect(copia?.valor).toEqual({ nota: 'B1' });
    expect(store.conflitos()).toBe(1);
  });

  it('mesma aba gravando em sequência não gera conflito', async () => {
    await store.salvar(U, 'k', { a: 1 }, { versaoBase: 0 });
    await store.salvar(U, 'k', { a: 2 }, { versaoBase: 1 });
    expect(await db.rascunhos.count()).toBe(1);
    expect(store.conflitos()).toBe(0);
  });

  it('transação abortada no meio (cópia de conflito gravada, principal falha) não deixa nada pela metade', async () => {
    const outraAba = novaStore(db, 'aba-B');
    await outraAba.salvar(U, 'k', { v: 'B' }, {}, 1000);
    const putOriginal = db.rascunhos.put.bind(db.rascunhos);
    let chamadas = 0;
    spyOn(db.rascunhos, 'put').and.callFake(((...args: Parameters<typeof putOriginal>) => {
      chamadas++;
      // 1ª chamada: cópia de conflito (passa). 2ª: gravação principal (o processo "morre").
      if (chamadas === 2) return Promise.reject(new Error('processo morto no meio da gravação'));
      return putOriginal(...args);
    }) as typeof putOriginal);

    expect(await store.salvar(U, 'k', { v: 'A' }, { versaoBase: 0 }, 2000)).toBeNull();
    expect(store.estado()).toBe('erro');
    expect(await db.rascunhos.count()).toBe(1);
    expect((await db.rascunhos.get('k'))?.valor).toEqual({ v: 'B' });
  });

  it('anexo é gravado na hora e entra na lista do rascunho', async () => {
    await store.salvar(U, 'inspecao:nova', { respostas: [] });
    const foto = new Blob([new Uint8Array([1, 2, 3])], { type: 'image/jpeg' });
    const id = await store.anexar(U, 'inspecao:nova', foto, 'foto.jpg');
    expect(id).toBeTruthy();
    const lido = await store.ler(U, 'inspecao:nova');
    expect(lido?.anexos).toEqual([id!]);
    const anexo = await store.lerAnexo(id!);
    expect(anexo?.tipo).toBe('image/jpeg');
    expect(new Uint8Array(await anexo!.blob.arrayBuffer())).toEqual(new Uint8Array([1, 2, 3]));
  });

  it('navegador que recusa Blob no IndexedDB: grava os bytes e devolve a foto igual', async () => {
    const putOriginal = db.anexos.put.bind(db.anexos);
    spyOn(db.anexos, 'put').and.callFake(((registro: { blob?: unknown }, ...resto: unknown[]) => {
      if (registro.blob instanceof Blob) return Promise.reject(new Error('DataCloneError'));
      return (putOriginal as any)(registro, ...resto);
    }) as typeof putOriginal);
    const id = await store.anexar(U, 'inspecao:nova', new Blob([new Uint8Array([7, 8, 9])], { type: 'image/png' }), 'f.png');
    expect(id).toBeTruthy();
    const lido = await store.lerAnexo(id!);
    expect(lido?.blob.type).toBe('image/png');
    expect(new Uint8Array(await lido!.blob.arrayBuffer())).toEqual(new Uint8Array([7, 8, 9]));
    expect((await store.anexosDoRascunho(U, 'inspecao:nova'))[0].blob instanceof Blob).toBe(true);
  });

  it('falha ao vincular o anexo desfaz também o arquivo (sem órfão)', async () => {
    await store.salvar(U, 'inspecao:nova', { respostas: [] });
    spyOn(db.rascunhos, 'put').and.rejectWith(new Error('abortado'));
    const id = await store.anexar(U, 'inspecao:nova', new Blob(['x']), 'x.jpg');
    expect(id).toBeNull();
    expect(await db.anexos.count()).toBe(0);
  });

  it('descartar remove rascunho e arquivos juntos', async () => {
    await store.salvar(U, 'k', { a: 1 });
    await store.anexar(U, 'k', new Blob(['x']), 'x');
    await store.descartar('k');
    expect(await db.rascunhos.count()).toBe(0);
    expect(await db.anexos.count()).toBe(0);
  });

  it('limpeza apaga só anexos órfãos antigos; rascunhos antigos ficam', async () => {
    const agora = 10 * RASCUNHO_ANTIGO_MS;
    await store.salvar(U, 'antigo', { a: 1 }, {}, agora - 2 * RASCUNHO_ANTIGO_MS);
    await db.anexos.bulkPut([
      { id: 'orfao-velho', chaveRascunho: 'sumiu', idUsuario: U, blob: new Blob(['1']), nome: '', tipo: '', criadoEm: agora - ANEXO_ORFAO_MS - 1 },
      { id: 'orfao-novo', chaveRascunho: 'sumiu', idUsuario: U, blob: new Blob(['2']), nome: '', tipo: '', criadoEm: agora - 1000 },
      { id: 'do-antigo', chaveRascunho: 'antigo', idUsuario: U, blob: new Blob(['3']), nome: '', tipo: '', criadoEm: agora - 3 * ANEXO_ORFAO_MS },
    ]);
    expect(await store.limparOrfaos(agora)).toBe(1);
    expect((await db.anexos.toCollection().primaryKeys()).sort()).toEqual(['do-antigo', 'orfao-novo']);
    expect(await db.rascunhos.get('antigo')).toBeDefined();
  });

  it('lista os rascunhos da conta, mais recentes primeiro, marcando os antigos', async () => {
    const agora = 100 * RASCUNHO_ANTIGO_MS;
    await store.salvar(U, 'velho', { a: 1 }, { titulo: 'Velho' }, agora - RASCUNHO_ANTIGO_MS - 1);
    await store.salvar(U, 'novo', { a: 1 }, { titulo: 'Novo' }, agora - 1);
    await store.salvar('u-2', 'alheio', { a: 1 }, {}, agora);
    const lista = await store.listar(U, agora);
    expect(lista.map(r => [r.chave, r.antigo])).toEqual([['novo', false], ['velho', true]]);
  });

  it('limparConta apaga só os dados daquela conta', async () => {
    await store.salvar(U, 'a', { a: 1 });
    await store.anexar(U, 'a', new Blob(['x']), 'x');
    await store.salvar('u-2', 'b', { b: 1 });
    await store.limparConta(U);
    expect(await db.rascunhos.toCollection().primaryKeys()).toEqual(['b']);
    expect(await db.anexos.count()).toBe(0);
  });

  it('milhares de rascunhos e anexos: listar e limpar continuam corretos', async () => {
    const agora = Date.now();
    await db.rascunhos.bulkPut(Array.from({ length: 2000 }, (_, i) => ({
      chave: `r-${i}`, idUsuario: U, valor: { i }, versao: 1, idAba: 'x', criadoEm: agora, atualizadoEm: agora - i,
      anexos: [`a-${i}`], checksum: '',
    })));
    await db.anexos.bulkPut(Array.from({ length: 2000 }, (_, i) => ({
      id: `a-${i}`, chaveRascunho: `r-${i}`, idUsuario: U, blob: new Blob([String(i)]), nome: '', tipo: '', criadoEm: 0,
    })));
    const lista = await store.listar(U, agora);
    expect(lista.length).toBe(2000);
    expect(lista[0].chave).toBe('r-0');
    expect(await store.limparOrfaos(agora)).toBe(0);
  });
});
