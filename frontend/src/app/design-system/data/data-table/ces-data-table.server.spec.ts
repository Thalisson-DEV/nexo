import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { CesContentState } from '../../core';
import { CesConfirmationService } from '../../overlays/confirmation';
import { CesDataTableComponent } from './ces-data-table.component';
import { CesDataTableColumn, CesLazyLoadEvent, CesSortRule } from './ces-data-table.utils';

interface Veiculo {
  id: string;
  placa: string;
  pendencias: number;
}

function pagina(inicio: number, tamanho: number): Veiculo[] {
  return Array.from({ length: tamanho }, (_, i) => ({
    id: `v-${inicio + i}`,
    placa: `PLC${String(inicio + i).padStart(4, '0')}`,
    pendencias: i,
  }));
}

@Component({
  standalone: true,
  imports: [CesDataTableComponent],
  template: `
    <ces-data-table
      title="Veículos"
      mode="server"
      exportFileName="veiculos"
      [columns]="colunas"
      [rows]="linhas()"
      [totalRecords]="total()"
      [state]="estado()"
      [filtered]="filtrado()"
      [exportRows]="exportar()"
      [exportLimit]="limite"
      [(page)]="pagina"
      [(pageSize)]="tamanho"
      [(sort)]="ordem"
      [(query)]="busca"
      (lazyLoad)="eventos.push($event)"
      (adjustFilters)="ajustes = ajustes + 1"
      (clearFilters)="limpezas = limpezas + 1"
    ></ces-data-table>
  `,
})
class HostComponent {
  readonly colunas: CesDataTableColumn<Veiculo>[] = [
    { key: 'placa', header: 'Placa', sortField: 'placa' },
    { key: 'pendencias', header: 'Pendências', type: 'integer' },
  ];
  readonly linhas = signal<Veiculo[]>(pagina(26, 25));
  readonly total = signal(1240);
  readonly estado = signal<CesContentState>('ready');
  readonly filtrado = signal(false);
  readonly exportar = signal<((e: CesLazyLoadEvent, limite: number) => Observable<Veiculo[]>) | undefined>(undefined);
  limite = 10000;
  readonly pagina = signal(1);
  readonly tamanho = signal(25);
  readonly ordem = signal<CesSortRule[]>([{ key: 'placa', direction: 'desc' }]);
  readonly busca = signal('');
  readonly eventos: CesLazyLoadEvent[] = [];
  ajustes = 0;
  limpezas = 0;
}

async function montar(configurar?: (host: HostComponent) => void, providers: unknown[] = []) {
  await TestBed.configureTestingModule({ imports: [HostComponent], providers: providers as never[] }).compileComponents();
  const fixture = TestBed.createComponent(HostComponent);
  configurar?.(fixture.componentInstance);
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();
  return fixture;
}

function placas(el: HTMLElement): string[] {
  return Array.from(el.querySelectorAll('tbody tr td:first-child')).map((td) => td.textContent!.trim());
}

describe('CesDataTableComponent (modo servidor)', () => {
  afterEach(() => {
    vi.useRealTimers();
    localStorage.clear();
  });

  it('emite uma única carga na entrada com o estado restaurado e o sortField', async () => {
    const fixture = await montar();
    expect(fixture.componentInstance.eventos).toEqual([
      { page: 1, size: 25, sort: [{ key: 'placa', direction: 'desc' }], query: '', filters: [] },
    ]);
  });

  it('mostra a página recebida na ordem do servidor e o intervalo com o total', async () => {
    const fixture = await montar();
    const el: HTMLElement = fixture.nativeElement;
    expect(placas(el)).toHaveLength(25);
    expect(placas(el)[0]).toBe('PLC0026');
    expect(el.querySelector('.ces-data-table__range')!.textContent).toContain('26–50 de 1.240');
    expect(el.querySelector('.ces-data-table__page-label')!.textContent).toContain('Página 2 de 50');
  });

  it('busca com espera de 300 ms, uma única emissão e volta à primeira página', async () => {
    vi.useFakeTimers();
    const fixture = await montar();
    const host = fixture.componentInstance;
    host.eventos.length = 0;
    const input = fixture.nativeElement.querySelector('input[type="search"]') as HTMLInputElement;
    for (const texto of ['A', 'AB', 'ABC', 'ABC1']) {
      input.value = texto;
      input.dispatchEvent(new Event('input'));
      vi.advanceTimersByTime(50);
    }
    expect(host.eventos).toHaveLength(0);
    vi.advanceTimersByTime(300);
    expect(host.eventos).toHaveLength(1);
    expect(host.eventos[0]).toMatchObject({ query: 'ABC1', page: 0 });
    expect(host.pagina()).toBe(0);
  });

  it('só ordena colunas com sortField e reinicia a página ao ordenar', async () => {
    const fixture = await montar();
    const host = fixture.componentInstance;
    const el: HTMLElement = fixture.nativeElement;
    const [thPlaca, thPendencias] = Array.from(el.querySelectorAll('thead th')) as HTMLElement[];
    expect(thPendencias.querySelector('button')).toBeNull();

    host.eventos.length = 0;
    thPlaca.querySelector('button')!.click();
    expect(host.eventos).toEqual([{ page: 0, size: 25, sort: [], query: '', filters: [] }]);
    thPlaca.querySelector('button')!.click();
    expect(host.eventos[1].sort).toEqual([{ key: 'placa', direction: 'asc' }]);
  });

  it('pede a próxima página e o novo tamanho ao servidor', async () => {
    const fixture = await montar();
    const host = fixture.componentInstance;
    const el: HTMLElement = fixture.nativeElement;
    host.eventos.length = 0;
    (el.querySelector('button[aria-label="Próxima página"]') as HTMLButtonElement).click();
    expect(host.eventos[0]).toMatchObject({ page: 2, size: 25 });

    const select = el.querySelector('.ces-data-table__page-size select') as HTMLSelectElement;
    select.value = '50';
    select.dispatchEvent(new Event('change'));
    expect(host.eventos[1]).toMatchObject({ page: 0, size: 50 });
  });

  it('mostra páginas numeradas com a atual marcada e navega pelo número', async () => {
    const fixture = await montar();
    const host = fixture.componentInstance;
    const el: HTMLElement = fixture.nativeElement;
    const numeros = () => Array.from(el.querySelectorAll('.ces-data-table__pages li')).map((li) => li.textContent!.trim());
    expect(numeros()).toEqual(['1', '2', '3', '4', '…', '50']);
    expect(el.querySelector('.ces-data-table__page[aria-current="page"]')!.textContent!.trim()).toBe('2');

    host.eventos.length = 0;
    (el.querySelector('button[aria-label="Página 50"]') as HTMLButtonElement).click();
    expect(host.eventos[0]).toMatchObject({ page: 49 });
  });

  it('mantém o paginador visível mesmo com uma única página', async () => {
    const fixture = await montar((host) => {
      host.pagina.set(0);
      host.linhas.set(pagina(1, 3));
      host.total.set(3);
    });
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('nav.ces-data-table__pager')).not.toBeNull();
    expect((el.querySelector('button[aria-label="Próxima página"]') as HTMLButtonElement).disabled).toBe(true);
  });

  it('mantém as linhas visíveis durante a recarga e mostra o skeleton só na primeira carga', async () => {
    const fixture = await montar((host) => host.estado.set('loading'));
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('table')).toBeNull();

    fixture.componentInstance.estado.set('ready');
    fixture.detectChanges();
    fixture.componentInstance.estado.set('loading');
    fixture.detectChanges();
    expect(placas(el)).toHaveLength(25);
    expect(el.querySelector('table')!.getAttribute('aria-busy')).toBe('true');
    expect(el.querySelector('[role="progressbar"]')).not.toBeNull();
  });

  it('em erro oferece "Tentar novamente", que repete a última carga', async () => {
    const fixture = await montar((host) => host.estado.set('error'));
    const host = fixture.componentInstance;
    host.eventos.length = 0;
    const botao = Array.from(fixture.nativeElement.querySelectorAll('button')).find((b) =>
      (b as HTMLButtonElement).textContent!.includes('Tentar novamente'),
    ) as HTMLButtonElement;
    botao.click();
    expect(host.eventos).toEqual([{ page: 1, size: 25, sort: [{ key: 'placa', direction: 'desc' }], query: '', filters: [] }]);
  });

  it('no vazio por filtro oferece "Ajustar filtros" e "Limpar filtros"', async () => {
    const fixture = await montar((host) => {
      host.linhas.set([]);
      host.total.set(0);
      host.filtrado.set(true);
    });
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('.ces-data-table__no-results')!.textContent).toContain('Nada corresponde aos filtros aplicados');
    const botoes = Array.from(el.querySelectorAll('.ces-data-table__no-results button')) as HTMLButtonElement[];
    botoes.find((b) => b.textContent!.includes('Ajustar filtros'))!.click();
    botoes.find((b) => b.textContent!.includes('Limpar filtros'))!.click();
    expect(fixture.componentInstance.ajustes).toBe(1);
    expect(fixture.componentInstance.limpezas).toBe(1);
  });

  it('sem provedor exporta só a página exibida', async () => {
    const fixture = await montar();
    expect(fixture.nativeElement.querySelector('button[aria-label="Exportar os 25 registros desta página em CSV"]')).not.toBeNull();
  });

  it('avisa quando o recorte passa do limite de exportação e usa o provedor', async () => {
    const confirm = vi.fn(async () => true);
    const provedor = vi.fn((_e: CesLazyLoadEvent, limite: number) => of(pagina(0, limite)));
    const fixture = await montar(
      (host) => {
        host.limite = 100;
        host.exportar.set(provedor);
      },
      [{ provide: CesConfirmationService, useValue: { confirm } }],
    );
    Object.assign(URL, { createObjectURL: vi.fn(() => 'blob:x'), revokeObjectURL: vi.fn() });
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
    (fixture.nativeElement.querySelector('button[aria-label="Exportar 1240 registros em CSV"]') as HTMLButtonElement).click();
    await fixture.whenStable();
    await Promise.resolve();
    expect(confirm).toHaveBeenCalledOnce();
    expect((confirm.mock.calls[0] as unknown as [{ message: string }])[0].message).toContain('1.240');
    expect(provedor).toHaveBeenCalledWith(expect.objectContaining({ page: 1 }), 100);
    expect(click).toHaveBeenCalled();
    click.mockRestore();
  });

  it('no mobile, "Carregar mais" pede a página seguinte e acumula os cards', async () => {
    const original = window.matchMedia;
    window.matchMedia = ((query: string) =>
      ({ matches: true, media: query, addEventListener: () => undefined, removeEventListener: () => undefined }) as unknown as MediaQueryList) as typeof window.matchMedia;
    try {
      const fixture = await montar((host) => {
        host.pagina.set(0);
        host.linhas.set(pagina(1, 25));
      });
      const host = fixture.componentInstance;
      const el: HTMLElement = fixture.nativeElement;
      expect(el.querySelectorAll('.ces-data-table__card')).toHaveLength(25);
      host.eventos.length = 0;
      (Array.from(el.querySelectorAll('button')).find((b) => b.textContent!.includes('Carregar mais')) as HTMLButtonElement).click();
      expect(host.eventos[0]).toMatchObject({ page: 1 });
      host.linhas.set(pagina(26, 25));
      fixture.detectChanges();
      expect(el.querySelectorAll('.ces-data-table__card')).toHaveLength(50);
      expect(el.querySelector('.ces-data-table__range')!.textContent).toContain('50 de 1.240');
    } finally {
      window.matchMedia = original;
    }
  });
});
