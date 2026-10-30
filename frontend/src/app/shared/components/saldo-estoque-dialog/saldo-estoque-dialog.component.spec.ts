import '@angular/compiler';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { AlmoxarifadoService } from '../../../core/services/almoxarifado.service';
import { SaldoEstoqueDialogComponent } from './saldo-estoque-dialog.component';

describe('SaldoEstoqueDialogComponent', () => {
  const montar = async (listarSaldos: ReturnType<typeof vi.fn> = vi.fn(() => of({ content: [] as unknown[], page: { totalElements: 0 } }))) => {
    TestBed.resetTestingModule();
    TestBed.overrideComponent(SaldoEstoqueDialogComponent, {
      set: { template: '<span>{{ negativeCount() }}</span>', imports: [] },
    });
    await TestBed.configureTestingModule({
      imports: [SaldoEstoqueDialogComponent],
      providers: [provideRouter([]), { provide: AlmoxarifadoService, useValue: { listarSaldos } }],
    }).compileComponents();
    const fixture = TestBed.createComponent(SaldoEstoqueDialogComponent);
    return { fixture, tela: fixture.componentInstance as any, listarSaldos };
  };

  it('destaca a quantidade de divergencias negativas da pagina', async () => {
    const { fixture } = await montar();
    fixture.componentInstance.saldos.set([{ negativo: true }, { negativo: false }, { negativo: true }] as any);
    fixture.detectChanges();

    expect(fixture.componentInstance.negativeCount()).toBe(2);
    expect(fixture.nativeElement.textContent).toContain('2');
  });

  it('busca e pagina no servidor, pelo estoque aberto', async () => {
    const saldo = { id: 's1', negativo: false, quantidade: 3, material: { id: 'm1', codigo: '802149', descricao: 'MEDIDOR', detalhaSerie: true } };
    const { tela, listarSaldos } = await montar(vi.fn(() => of({ content: [saldo], page: { totalElements: 1 } })));

    tela.show('est-1', 'ALMOXARIFADO');
    tela.onLazyLoad({ page: 1, size: 25, sort: [], query: ' medidor ', filters: [] });

    expect(listarSaldos).toHaveBeenCalledWith({
      idEstoque: 'est-1',
      page: 1,
      size: 25,
      sort: 'material.descricao,asc',
      buscaGlobal: 'medidor',
    });
    expect(tela.saldos()).toHaveLength(1);
    expect(tela.estado()).toBe('ready');
    // o material com série abre as séries na própria linha
    expect(tela.ehSeriado(saldo)).toBe(true);
  });
});
