import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { signal } from '@angular/core';
import { EstoqueListComponent } from './estoque-list.component';
import { DbService } from '../../../core/db/db.service';
import { TokenService } from '../../../core/auth/token.service';
import { SyncService } from '../../../core/sync/sync.service';
import { SessaoService } from '../../../core/sessao/sessao.service';

const diasAtras = (n: number) => new Date(Date.now() - n * 86_400_000).toISOString();

describe('EstoqueListComponent - tempo no saldo', () => {
  async function abrir() {
    const db = jasmine.createSpyObj('DbService', ['getSaldosComMateriais', 'getSeriaisPorMaterial']);
    db.getSaldosComMateriais.and.resolveTo([
      { id: 's1', idMaterial: 'm-cabo', quantidade: 40, condicao: 'NOVO', codigo: 'CABO', descricao: 'Cabo', unidadeMedida: 'MT', detalhaSerie: false, ultimaMovimentacao: diasAtras(45) },
      { id: 's2', idMaterial: 'm-med', quantidade: 2, condicao: 'NOVO', codigo: 'MED', descricao: 'Medidor', unidadeMedida: 'UN', detalhaSerie: true, ultimaMovimentacao: diasAtras(1), serieMaisAntiga: diasAtras(70) },
      { id: 's3', idMaterial: 'm-fita', quantidade: 3, condicao: 'NOVO', codigo: 'FITA', descricao: 'Fita', unidadeMedida: 'UN', detalhaSerie: false, ultimaMovimentacao: diasAtras(0) },
    ]);
    db.getSeriaisPorMaterial.and.resolveTo([
      { id: 'a', idEstoque: 'e', idMaterial: 'm-med', numSerie: 'MED1', ativo: true, condicao: 'NOVO', noSaldoDesde: diasAtras(70) },
      { id: 'b', idEstoque: 'e', idMaterial: 'm-med', numSerie: 'MED2', ativo: true, condicao: 'NOVO', noSaldoDesde: diasAtras(2) },
    ]);
    await TestBed.configureTestingModule({
      imports: [EstoqueListComponent],
      providers: [
        { provide: DbService, useValue: db },
        { provide: TokenService, useValue: { estoqueId: () => 'e' } },
        { provide: SyncService, useValue: { lastSyncAt: signal(null) } },
        { provide: SessaoService, useValue: { estadoTela: () => ({}), definirEstadoTela: () => undefined, rotaAnterior: () => null } },
        { provide: Router, useValue: jasmine.createSpyObj('Router', ['navigateByUrl']) },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(EstoqueListComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    await new Promise(r => setTimeout(r, 50));
    fixture.detectChanges();
    return fixture;
  }

  it('mostra há quanto tempo cada material está parado e conta os parados', async () => {
    const fixture = await abrir();
    const texto = (fixture.nativeElement as HTMLElement).textContent!.replace(/\s+/g, ' ');
    expect(texto).toContain('Parado há 45 dias');
    expect(texto).toContain('A mais antiga está com você há 70 dias');
    expect(texto).toContain('Movimentado hoje');
    expect(texto).toContain('2 materiais parados há 30 dias ou mais');
  });

  it('filtra só os parados, do mais antigo para o mais novo', async () => {
    const fixture = await abrir();
    const c = fixture.componentInstance;
    c.alternarParados();
    expect(c.filtrados().map(m => m.descricao)).toEqual(['Medidor', 'Cabo']);
  });

  it('na folha de séries, cada série diz há quanto tempo está com a equipe', async () => {
    const fixture = await abrir();
    const c = fixture.componentInstance;
    await c.abrirSeries(c.materiais()!.find(m => m.detalhaSerie)!);
    fixture.detectChanges();
    expect(c.descricaoSeries()).toBe('2 séries no caminhão · a mais antiga chegou há 70 dias');
    expect(c.rotuloSerie(diasAtras(70))).toBe('Com você há 70 dias');
  });
});
