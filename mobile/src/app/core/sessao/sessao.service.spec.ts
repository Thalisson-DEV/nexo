import { TestBed } from '@angular/core/testing';
import { Component, signal } from '@angular/core';
import { Router, provideRouter } from '@angular/router';
import { Location } from '@angular/common';
import { provideLocationMocks } from '@angular/common/testing';
import { SessaoService, DEBOUNCE_SESSAO_MS } from './sessao.service';
import { SessaoDb, SESSAO_DB, ID_ABA } from './sessao-db';
import { selar, SNAPSHOT_SCHEMA } from './snapshot';
import { TokenService } from '../auth/token.service';
import { retomadaGuard } from './retomada.guard';

@Component({ template: '' })
class Vazio {}

const espera = (ms: number) => new Promise(r => setTimeout(r, ms));

describe('SessaoService (IndexedDB real)', () => {
  let db: SessaoDb;
  let sessao: SessaoService;
  let router: Router;
  const dono = signal<string | null>('u-1');
  const token = signal<string | null>('jwt');
  let expirado = false;

  function configurar() {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: '', pathMatch: 'full', canActivate: [retomadaGuard], component: Vazio },
          { path: 'login', component: Vazio },
          { path: 'hoje', component: Vazio },
          { path: 'campo', component: Vazio },
          { path: 'campo/nova', component: Vazio },
          { path: 'campo/:id', component: Vazio },
          { path: 'perfil', component: Vazio },
        ]),
        provideLocationMocks(),
        { provide: SESSAO_DB, useValue: db },
        { provide: ID_ABA, useValue: 'aba-teste' },
        { provide: TokenService, useValue: { ownerId: dono, token, isTokenExpired: () => expirado } },
      ],
    });
    sessao = TestBed.inject(SessaoService);
    router = TestBed.inject(Router);
  }

  beforeEach(async () => {
    db = new SessaoDb(`teste-sessao-${crypto.randomUUID()}`);
    await db.open();
    dono.set('u-1');
    token.set('jwt');
    expirado = false;
    configurar();
  });

  afterEach(async () => {
    db.close();
    await db.delete();
  });

  /** Simula reabrir o app: novo injetor (memória zerada), mesmo armazenamento. */
  function reabrir() {
    TestBed.resetTestingModule();
    configurar();
  }

  it('voltar pula o formulário salvo e a própria tela: detalhe e edição não se reabrem em loop', async () => {
    sessao.iniciar();
    await router.navigateByUrl('/campo');
    await router.navigateByUrl('/campo/n-1');
    await router.navigateByUrl('/campo/nova?alterarId=n-1');
    // Salvou a alteração: volta ao detalhe; o formulário fica na pilha.
    await router.navigateByUrl('/campo/n-1', { replaceUrl: true });

    expect(sessao.rotaAnterior()).toBe('/campo/nova?alterarId=n-1');
    expect(sessao.rotaAnteriorFora('/campo/nova', '/campo/n-1')).toBe('/campo');
    expect(sessao.rotaAnteriorFora('/campo/nova', '/campo/n-1', '/campo')).toBeNull();
  });

  it('grava a rota alguns instantes depois de navegar (sem depender de evento de saída)', async () => {
    sessao.iniciar();
    await router.navigateByUrl('/campo/nova');
    expect(await db.snapshots.get('atual')).toBeUndefined();
    await espera(DEBOUNCE_SESSAO_MS + 150);
    expect((await db.snapshots.get('atual'))?.dados.rota).toBe('/campo/nova');
  });

  it('visibilitychange para hidden grava na hora', async () => {
    sessao.iniciar();
    await router.navigateByUrl('/campo');
    spyOnProperty(document, 'visibilityState', 'get').and.returnValue('hidden');
    document.dispatchEvent(new Event('visibilitychange'));
    await espera(30);
    expect((await db.snapshots.get('atual'))?.dados.rota).toBe('/campo');
  });

  it('processo morto e reaberto pela raiz: volta para a mesma rota com o estado de tela', async () => {
    sessao.iniciar();
    await router.navigateByUrl('/campo');
    sessao.definirEstadoTela({ rolagem: 840, filtro: 'pendentes' });
    await router.navigateByUrl('/campo/nova');
    await sessao.gravarAgora();

    reabrir();
    sessao.iniciar();
    await router.navigateByUrl('/');
    expect(router.url).toBe('/campo/nova');
    expect(sessao.estadoTela('/campo')).toEqual({ rolagem: 840, filtro: 'pendentes' });
    expect(sessao.rotaAnterior()).toBe('/campo');
  });

  it('token vencido na volta (campo sem sinal): retoma onde parou, sem pedir login', async () => {
    sessao.iniciar();
    await router.navigateByUrl('/campo/nova');
    await sessao.gravarAgora();
    expirado = true;
    reabrir();
    await router.navigateByUrl('/');
    expect(router.url).toBe('/campo/nova');
  });

  it('a raiz só retoma na primeira navegação; depois é só o início', async () => {
    sessao.iniciar();
    await router.navigateByUrl('/campo/nova');
    await sessao.gravarAgora();
    reabrir();
    await router.navigateByUrl('/');
    expect(router.url).toBe('/campo/nova');
    await router.navigateByUrl('/');
    expect(router.url).toBe('/hoje');
  });

  it('snapshot atual corrompido: usa a reserva anterior', async () => {
    sessao.iniciar();
    await router.navigateByUrl('/campo');
    await sessao.gravarAgora();
    await router.navigateByUrl('/perfil');
    await sessao.gravarAgora();
    const atual = (await db.snapshots.get('atual'))!;
    await db.snapshots.put({ chave: 'atual', dados: { ...atual.dados, rota: '/adulterada' } });

    reabrir();
    await router.navigateByUrl('/');
    expect(router.url).toBe('/campo');
  });

  it('atual e reserva corrompidos: abre no início, sem lançar', async () => {
    await db.snapshots.bulkPut([
      { chave: 'atual', dados: { lixo: true } as any },
      { chave: 'anterior', dados: 'texto' as any },
    ]);
    await router.navigateByUrl('/');
    expect(router.url).toBe('/hoje');
  });

  it('snapshot de outra conta não é restaurado', async () => {
    await db.snapshots.put({ chave: 'atual', dados: selar({
      schema: SNAPSHOT_SCHEMA, salvoEm: Date.now(), idUsuario: 'u-2', rota: '/campo', pilha: [], telas: {}, idAba: 'x', seq: 1,
    }) });
    await router.navigateByUrl('/');
    expect(router.url).toBe('/hoje');
  });

  it('snapshot de schema antigo (v1) é migrado e restaurado', async () => {
    await db.snapshots.put({ chave: 'atual', dados: { versao: 1, ts: Date.now() - 1000, usuario: 'u-1', url: '/perfil', ui: {} } as any });
    await router.navigateByUrl('/');
    expect(router.url).toBe('/perfil');
  });

  it('nunca grava a tela de login como destino', async () => {
    sessao.iniciar();
    await router.navigateByUrl('/login');
    expect(await sessao.gravarAgora()).toBeFalse();
    expect(await db.snapshots.count()).toBe(0);
  });

  it('processo morto durante a gravação: o retrato anterior continua íntegro', async () => {
    sessao.iniciar();
    await router.navigateByUrl('/campo');
    await sessao.gravarAgora();
    const putOriginal = db.snapshots.put.bind(db.snapshots);
    let n = 0;
    spyOn(db.snapshots, 'put').and.callFake(((...a: Parameters<typeof putOriginal>) => {
      n++;
      return n === 2 ? Promise.reject(new Error('morto')) : putOriginal(...a);
    }) as typeof putOriginal);
    await router.navigateByUrl('/perfil');
    expect(await sessao.gravarAgora()).toBeFalse();

    reabrir();
    await router.navigateByUrl('/');
    expect(router.url).toBe('/campo');
  });

  it('armazenamento cheio: não lança e o app segue', async () => {
    sessao.iniciar();
    await router.navigateByUrl('/campo');
    spyOn(db.snapshots, 'put').and.rejectWith(Object.assign(new Error('q'), { name: 'QuotaExceededError' }));
    expect(await sessao.gravarAgora()).toBeFalse();
    expect((await db.eventos.toArray()).some(e => e.tipo === 'armazenamento.cheio')).toBeTrue();
  });

  it('pilha acompanha ida e volta (popstate)', async () => {
    router.setUpLocationChangeListener();
    sessao.iniciar();
    await router.navigateByUrl('/hoje');
    await router.navigateByUrl('/campo');
    await router.navigateByUrl('/campo/nova');
    expect(sessao.rotaAnterior()).toBe('/campo');
    TestBed.inject(Location).back();
    await espera(50);
    expect(router.url).toBe('/campo');
    expect(sessao.rotaAnterior()).toBe('/hoje');
  });

  it('esquecer (logout): a próxima abertura vai ao início', async () => {
    sessao.iniciar();
    await router.navigateByUrl('/campo');
    await sessao.gravarAgora();
    await sessao.esquecer();
    reabrir();
    await router.navigateByUrl('/');
    expect(router.url).toBe('/hoje');
  });
});
