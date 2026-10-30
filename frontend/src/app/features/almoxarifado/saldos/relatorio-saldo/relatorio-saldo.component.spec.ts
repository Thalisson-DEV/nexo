import '@angular/compiler';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { MessageService } from 'primeng/api';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AlmoxarifadoService } from '../../../../core/services/almoxarifado.service';
import { RhService } from '../../../../core/services/rh.service';
import { NavigationHistoryService } from '../../../../core/navigation/navigation-history.service';
import { RelatorioSaldoComponent } from './relatorio-saldo.component';

const pagina = (content: unknown[], totalElements: number) => ({
  content,
  page: { number: 0, size: content.length || 1, totalElements, totalPages: 1 },
});

const saldo = (id: string, detalhaSerie: boolean, quantidade = 2) => ({
  id,
  quantidade,
  negativo: quantidade < 0,
  condicao: 'NOVO',
  ativo: true,
  estoque: { id: 'stock-1' },
  material: { id: `mat-${id}`, codigo: `C-${id}`, descricao: `Material ${id}`, unidadeMedida: 'UN', detalhaSerie },
});

describe('RelatorioSaldoComponent', () => {
  const listarSaldos = vi.fn();
  const listarSaldosSeriais = vi.fn();

  async function montar() {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [RelatorioSaldoComponent],
      providers: [
        {
          provide: ActivatedRoute,
          useValue: {
            queryParams: of({ idEstoque: 'stock-1' }),
            snapshot: { queryParamMap: convertToParamMap({ idEstoque: 'stock-1' }) },
          },
        },
        // O estado da tabela vai para a URL (replace); aqui só importa que a tela continue consultando.
        { provide: Router, useValue: { navigate: vi.fn(() => Promise.resolve(true)) } },
        { provide: NavigationHistoryService, useValue: { back: vi.fn() } },
        {
          provide: AlmoxarifadoService,
          useValue: {
            buscarEstoquePorId: () =>
              of({ id: 'stock-1-abcdef', nomeIdentificador: 'Central', ativo: true, tipoEstoque: { nome: 'Fixo' } }),
            listarSaldos,
            listarSaldosSeriais,
          },
        },
        { provide: RhService, useValue: { buscarEquipePorId: vi.fn() } },
        MessageService,
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(RelatorioSaldoComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    listarSaldos.mockReset();
    listarSaldosSeriais.mockReset();
    listarSaldos.mockImplementation((params: Record<string, unknown>) => {
      if (params['size'] === 1) {
        if (params['negativo']) return of(pagina([], 3));
        if (params['condicao'] === 'SUCATA') return of(pagina([], 1));
        return of(pagina([], 40));
      }
      return of(pagina([saldo('a', true), saldo('b', false)], 2));
    });
    listarSaldosSeriais.mockImplementation((params: Record<string, unknown>) =>
      params['size'] === 1
        ? of(pagina([], 2))
        : of(pagina([{ id: 's1', numSerie: 'SN-001', ativo: true }, { id: 's2', numSerie: 'SN-002', ativo: false }], 2)),
    );
  });

  it('resume o estoque inteiro: itens, negativos, sucata e séries disponíveis', async () => {
    const fixture = await montar();
    expect(listarSaldos).toHaveBeenCalledWith({ idEstoque: 'stock-1', negativo: true, page: 0, size: 1 });
    expect(listarSaldos).toHaveBeenCalledWith({ idEstoque: 'stock-1', condicao: 'SUCATA', page: 0, size: 1 });
    expect(listarSaldosSeriais).toHaveBeenCalledWith({ idEstoque: 'stock-1', ativo: true, page: 0, size: 1 });
    expect((fixture.componentInstance as any).resumo()).toEqual({ itens: 40, negativos: 3, sucata: 1, series: 2 });
    expect(fixture.nativeElement.textContent).toContain('3 saldos negativos');
  });

  it('aplica o recorte de negativos e de sucata na consulta da tabela', async () => {
    const fixture = await montar();
    const tela = fixture.componentInstance as any;

    listarSaldos.mockClear();
    tela.aplicarRecorte('negativos');
    expect(listarSaldos).toHaveBeenLastCalledWith(expect.objectContaining({ idEstoque: 'stock-1', negativo: true, page: 0 }));

    tela.aplicarRecorte('sucata');
    expect(listarSaldos).toHaveBeenLastCalledWith(expect.objectContaining({ condicao: 'SUCATA', negativo: undefined }));

    // Clicar de novo no recorte ativo volta para todos.
    tela.aplicarRecorte('sucata');
    expect(tela.recorte()).toBe('todos');
  });

  it('expande na própria linha só os materiais seriados, com os números de série', async () => {
    const fixture = await montar();
    const el: HTMLElement = fixture.nativeElement;

    const expansores = el.querySelectorAll<HTMLButtonElement>('.ces-data-table__expander');
    expect(expansores).toHaveLength(1);
    expect(expansores[0].getAttribute('aria-label')).toBe('Expandir C-a Material a');

    expansores[0].click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(listarSaldosSeriais).toHaveBeenCalledWith(
      expect.objectContaining({ idEstoque: 'stock-1', idMaterial: 'mat-a', condicao: 'NOVO', page: 0, sort: 'numSerie,asc' }),
    );
    const painel = el.querySelector('app-saldo-seriais-painel')!;
    expect(painel.textContent).toContain('SN-001');
    expect(painel.textContent).toContain('Indisponível');
  });
});
