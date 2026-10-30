import { TestBed } from '@angular/core/testing';
import { provideLocationMocks } from '@angular/common/testing';
import { Router, provideRouter } from '@angular/router';
import { ConfirmationService, MessageService } from 'primeng/api';
import { of } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { CesConfirmationService } from '@ces/ui/overlays';
import { ContratoService } from '../../../core/services/contrato.service';
import { FavoritosService } from '../../../core/services/favoritos.service';
import { ImportacaoService } from '../../../core/services/importacao.service';
import { NavigationHistoryService } from '../../../core/navigation/navigation-history.service';
import { openRoute, settleNavigation } from '../../../core/navigation/testing';
import { ContratosComponent } from './contratos.component';

describe('ContratosComponent — cadastro em drawer (tipo A)', () => {
  const contrato = {
    id: 'c-1',
    numeroContrato: 'CT-001',
    cliente: 'Concessionária',
    dataVigencia: '2026-01-10',
    dataEncerramento: '2026-12-31',
    coeficienteFaturamento: 1.5,
    ativo: true,
  };
  let service: Record<string, ReturnType<typeof vi.fn>>;
  let confirm: ReturnType<typeof vi.fn>;

  const abrir = async (url: string) => {
    vi.stubGlobal('matchMedia', (query: string) => ({
      matches: false,
      media: query,
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
    }));
    service = {
      listarContratos: vi.fn(() => of({ content: [contrato], page: { size: 10, number: 0, totalElements: 1, totalPages: 1 } })),
      buscarContratoPorId: vi.fn(() => of(contrato)),
      criarContrato: vi.fn(() => of(contrato)),
      atualizarContrato: vi.fn(() => of(contrato)),
    };
    confirm = vi.fn(async () => true);
    TestBed.configureTestingModule({
      imports: [ContratosComponent],
      providers: [
        provideRouter([{ path: '**', component: ContratosComponent }]),
        provideLocationMocks(),
        ConfirmationService,
        MessageService,
        { provide: CesConfirmationService, useValue: { confirm } },
        { provide: ContratoService, useValue: service },
        { provide: ImportacaoService, useValue: { baixarModeloCsv: vi.fn() } },
        { provide: FavoritosService, useValue: { isCurrentPageFavorite: () => false, toggleFavorite: vi.fn() } },
      ],
    });
    const fixture = await openRoute(url, ContratosComponent);
    fixture.detectChanges();
    return { fixture, tela: fixture.componentInstance as any };
  };

  const preencher = (tela: any) =>
    tela.contratoForm.setValue({
      numeroContrato: 'CT-002',
      cliente: 'Concessionária',
      dataVigencia: new Date(2026, 1, 1),
      dataEncerramento: new Date(2026, 10, 30),
      coeficienteFaturamento: 1.2,
    });

  afterEach(() => {
    vi.unstubAllGlobals();
    document.body.querySelectorAll('.p-drawer, .p-drawer-mask').forEach((el) => el.remove());
    TestBed.resetTestingModule();
  });

  it('lista paginada no servidor, ordenada pelo número, com o coeficiente em 4 casas', async () => {
    const { fixture } = await abrir('/admin/cadastros/contratos?cliente=neo');
    await settleNavigation();
    fixture.detectChanges();
    expect(service['listarContratos']).toHaveBeenLastCalledWith(
      expect.objectContaining({ cliente: 'neo', ativo: null, page: 0, size: 25, sort: 'numeroContrato,asc' }),
    );
    expect(fixture.nativeElement.textContent).toContain('1,5000');
    expect(Array.from(fixture.nativeElement.querySelectorAll('.ces-filter-panel__chip')).map((c: any) => c.textContent.trim())).toEqual(['Cliente: neo']);
  });

  it('Novo abre o drawer em ?novo=1 com formulário único (sem etapas)', async () => {
    const { tela, fixture } = await abrir('/admin/cadastros/contratos');
    tela.showAddDialog();
    await settleNavigation();
    fixture.detectChanges();

    expect(TestBed.inject(Router).url).toBe('/admin/cadastros/contratos?novo=1');
    const drawer = document.body.querySelector('.p-drawer') as HTMLElement;
    expect(drawer.querySelector('h2')!.textContent).toContain('Novo contrato');
    expect(drawer.querySelector('p-stepper')).toBeNull();
    expect(Array.from(drawer.querySelectorAll('.ces-form-section__title')).map((t) => t.textContent!.trim())).toEqual([
      'Identificação',
      'Vigência',
      'Faturamento',
    ]);
  });

  it('salvar vazio não chama a API e marca os 5 campos obrigatórios', async () => {
    const { tela, fixture } = await abrir('/admin/cadastros/contratos?novo=1');
    await tela.onSave();
    fixture.detectChanges();

    expect(service['criarContrato']).not.toHaveBeenCalled();
    expect(document.body.querySelectorAll('.p-drawer .ces-form-field--invalid')).toHaveLength(5);
  });

  it('mantém as regras do coeficiente (maior que 0 e até 1000)', async () => {
    const { tela } = await abrir('/admin/cadastros/contratos?novo=1');
    preencher(tela);
    tela.contratoForm.patchValue({ coeficienteFaturamento: 0 });
    expect(tela.contratoForm.valid).toBe(false);
    tela.contratoForm.patchValue({ coeficienteFaturamento: 1001 });
    expect(tela.contratoForm.valid).toBe(false);
  });

  it('"Salvar e novo" cria, limpa o formulário e mantém o drawer aberto', async () => {
    const { tela } = await abrir('/admin/cadastros/contratos?novo=1');
    preencher(tela);
    await tela.onSave(true);
    await settleNavigation();

    expect(service['criarContrato']).toHaveBeenCalledWith(
      expect.objectContaining({ numeroContrato: 'CT-002', dataVigencia: '2026-02-01', dataEncerramento: '2026-11-30' }),
    );
    expect(TestBed.inject(Router).url).toBe('/admin/cadastros/contratos?novo=1');
    expect(tela.contratoForm.value.numeroContrato).toBeNull();
  });

  it('link com ?editar=<id> (F5) busca o contrato e salva com atualizar; fechar volta à lista', async () => {
    const { tela } = await abrir('/admin/cadastros/contratos?editar=c-1');
    expect(service['buscarContratoPorId']).toHaveBeenCalledWith('c-1');
    expect(tela.contratoForm.value.numeroContrato).toBe('CT-001');

    await tela.onSave();
    await settleNavigation();
    expect(service['atualizarContrato']).toHaveBeenCalledWith('c-1', expect.objectContaining({ cliente: 'Concessionária' }));
    expect(TestBed.inject(Router).url).toBe('/admin/cadastros/contratos');
  });

  it('editar pela lista empilha ?editar e o voltar do navegador fecha', async () => {
    const { tela } = await abrir('/admin/cadastros/contratos');
    tela.editContrato(contrato);
    await settleNavigation();
    expect(TestBed.inject(Router).url).toBe('/admin/cadastros/contratos?editar=c-1');
    expect(service['buscarContratoPorId']).not.toHaveBeenCalled();

    TestBed.inject(NavigationHistoryService).back();
    await settleNavigation();
    expect(TestBed.inject(Router).url).toBe('/admin/cadastros/contratos');
    expect(tela.drawerUrl.open()).toBe(false);
  });
});
