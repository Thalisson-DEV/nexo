import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { ConfirmationService, MessageService } from 'primeng/api';
import { of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { LookupStore } from '../../../core/lookup';
import { VeiculoFrotaResumo } from '../../../core/models/frota.models';
import { AuthService } from '../../../core/services/auth.service';
import { FrotaService } from '../../../core/services/frota.service';
import { FrotaVeiculosComponent } from './frota-veiculos.component';

const veiculo: VeiculoFrotaResumo = {
  idVeiculo: 'v1',
  placa: 'ABC1234',
  modelo: 'CONSTELLATION',
  fabricante: 'VOLKSWAGEN',
  idBaseOperacional: 'b1',
  baseOperacional: 'BASE SALVADOR',
  idTipoVeiculo: null,
  tipoVeiculo: null,
  situacao: 'EM_MANUTENCAO',
  regime: 'LOCADO',
  combustivel: 'DIESEL_S10',
  anoModelo: 2025,
  kmAtual: 12400,
  kmAtualEm: '2026-10-08T10:00:00-03:00',
  idCondutorAtual: 'c1',
  condutorAtual: 'MARIA SOUZA',
  implementosAcoplados: ['CESTO-01'],
  documentosVencidos: 1,
  documentosAVencer: 0,
  leiturasInconsistentes: 2,
  ativo: true
};

describe('FrotaVeiculosComponent', () => {
  let frota: Record<string, ReturnType<typeof vi.fn>>;
  let permissoes: string[];

  async function abrir(url = '/frota/veiculos') {
    TestBed.configureTestingModule({
      providers: [
        provideNoopAnimations(),
        provideRouter([{ path: 'frota/veiculos', component: FrotaVeiculosComponent }]),
        MessageService,
        ConfirmationService,
        { provide: FrotaService, useValue: frota },
        {
          provide: LookupStore,
          useValue: { operationalBases: signal([{ value: 'b1', label: 'BASE SALVADOR' }]), loadBootstrap: () => of({}) }
        },
        { provide: AuthService, useValue: { currentUser: () => ({ permissoes }) } }
      ]
    });
    const harness = await RouterTestingHarness.create();
    const componente = await harness.navigateByUrl(url, FrotaVeiculosComponent);
    harness.detectChanges();
    await harness.fixture.whenStable();
    harness.detectChanges();
    return { harness, componente, el: harness.routeNativeElement as HTMLElement };
  }

  function chips(el: HTMLElement): string[] {
    return Array.from(el.querySelectorAll('.ces-filter-panel__chip')).map((c) => c.textContent!.trim());
  }

  beforeEach(() => {
    TestBed.resetTestingModule();
    permissoes = ['frota:veiculo:read'];
    frota = {
      opcoes: vi.fn(() => of({ situacoes: [], regimes: [], combustiveis: [], provedoresTelemetria: [], categoriasCnh: [], tiposDocumento: [], origensOdometro: [] })),
      tiposVeiculo: vi.fn(() => of({ content: [], page: { size: 500, number: 0, totalElements: 0, totalPages: 0 } })),
      listarVeiculos: vi.fn(() => of({ content: [veiculo], page: { size: 25, number: 0, totalElements: 1, totalPages: 1 } }))
    };
  });

  afterEach(() => localStorage.clear());

  it('abre já nos dados com os filtros padrão, paginado no servidor', async () => {
    const { el } = await abrir();
    expect(frota['listarVeiculos']).toHaveBeenCalledOnce();
    expect(frota['listarVeiculos']).toHaveBeenCalledWith(
      expect.objectContaining({ ativo: true, idsBase: [], busca: null }),
      { page: 0, size: 25, sort: ['placa,asc'] }
    );
    const texto = el.textContent as string;
    expect(texto).toContain('ABC-1234');
    expect(texto).toContain('Em manutenção');
    expect(texto).toContain('MARIA SOUZA');
    expect(texto).toContain('1 doc. vencido');
    expect(texto).toContain('2 KM a conferir');
    // Painel recolhido, com o padrão como chip não removível e sem contar como filtro ativo.
    expect(el.querySelector('.ces-filter-panel__content')).toBeNull();
    expect(chips(el)).toEqual(['Cadastro: Somente ativos']);
    expect(el.textContent).not.toContain('filtro ativo');
    expect(el.textContent).not.toContain('Pesquisar');
  });

  it('restaura o recorte da URL, remove filtro pelo chip e grava a URL', async () => {
    const { el, harness } = await abrir('/frota/veiculos?base=b1&pagina=2&ativo=null');
    expect(frota['listarVeiculos']).toHaveBeenLastCalledWith(
      expect.objectContaining({ idsBase: ['b1'], ativo: null }),
      expect.objectContaining({ page: 1 })
    );
    expect(chips(el)).toEqual(['Base: BASE SALVADOR', 'Cadastro: Todos']);
    expect(el.textContent).toContain('2 filtros ativos');

    (el.querySelector('button[aria-label="Remover filtro Base: BASE SALVADOR"]') as HTMLButtonElement).click();
    harness.detectChanges();
    await harness.fixture.whenStable();
    expect(frota['listarVeiculos']).toHaveBeenLastCalledWith(
      expect.objectContaining({ idsBase: [], ativo: null }),
      expect.objectContaining({ page: 0 })
    );
    expect(TestBed.inject(Router).url).toBe('/frota/veiculos?ativo=null');
  });

  it('a busca da tabela vai para o backend como `busca`', async () => {
    const { componente } = await abrir();
    (componente as unknown as { lista: { load(e: object): void } }).lista.load({ page: 0, size: 25, sort: [], query: 'ABC', filters: [] });
    expect(frota['listarVeiculos']).toHaveBeenLastCalledWith(expect.objectContaining({ busca: 'ABC' }), expect.anything());
  });

  it('só oferece cadastro a quem pode escrever, e "n" abre o drawer', async () => {
    let tela = await abrir();
    expect(tela.el.textContent).not.toContain('Novo veículo');
    document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'n', bubbles: true }));
    expect((tela.componente as unknown as { formularioAberto(): boolean }).formularioAberto()).toBe(false);

    TestBed.resetTestingModule();
    permissoes = ['frota:veiculo:read', 'frota:veiculo:write'];
    tela = await abrir();
    expect(tela.el.textContent).toContain('Novo veículo');
    document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'n', bubbles: true }));
    expect((tela.componente as unknown as { formularioAberto(): boolean }).formularioAberto()).toBe(true);
  });

  it('mostra o erro do servidor quando a listagem falha', async () => {
    frota['listarVeiculos'] = vi.fn(() => throwError(() => ({ error: { detail: 'Falha simulada' } })));
    const { el } = await abrir();
    expect(el.textContent).toContain('Não foi possível carregar os veículos');
    expect(el.textContent).toContain('Tentar novamente');
  });

  it('abre a ficha ao ativar a linha', async () => {
    const { el } = await abrir();
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    (el.querySelector('button[aria-label="Abrir ficha: Veículo ABC1234"]') as HTMLButtonElement).click();
    expect(navegar).toHaveBeenCalledWith(['/frota/veiculos', 'v1']);
  });
});
