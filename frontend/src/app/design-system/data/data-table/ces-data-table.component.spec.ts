import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { CesDataTableComponent } from './ces-data-table.component';
import { CesCellDirective, CesRowActionsDirective, CesRowDetailDirective } from './ces-data-table.directives';
import { CesDataTableColumn } from './ces-data-table.utils';

interface Linha {
  id: string;
  nome: string;
  realizado: number;
}

function gerarLinhas(total: number): Linha[] {
  return Array.from({ length: total }, (_, i) => ({ id: `id-${i}`, nome: `Pessoa ${String(i + 1).padStart(2, '0')}`, realizado: i }));
}

@Component({
  standalone: true,
  imports: [CesDataTableComponent, CesCellDirective, CesRowDetailDirective],
  template: `
    <ces-data-table
      title="Inspetores"
      storageKey="teste-inspetores"
      exportFileName="inspetores"
      rowActionLabel="Ver histórico"
      [columns]="colunas"
      [rows]="linhas()"
      [rowExpandable]="expansivel()"
      (rowActivate)="ativada = $event"
    >
      <ng-template cesCell="realizado" let-row>
        <strong class="custom-cell">{{ row.realizado }} feitas</strong>
      </ng-template>
      <ng-template cesRowDetail let-row>
        <p class="detalhe">Histórico de {{ row.nome }}</p>
      </ng-template>
    </ces-data-table>
  `,
})
class HostComponent {
  readonly colunas: CesDataTableColumn<Linha>[] = [
    { key: 'nome', header: 'Inspetor' },
    { key: 'realizado', header: 'Realizado', type: 'integer' },
  ];
  readonly linhas = signal<Linha[]>(gerarLinhas(23));
  readonly expansivel = signal<((linha: Linha) => boolean) | undefined>(undefined);
  ativada: Linha | null = null;
}

async function montar() {
  await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
  const fixture = TestBed.createComponent(HostComponent);
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();
  return fixture;
}

function nomesVisiveis(el: HTMLElement): string[] {
  return Array.from(el.querySelectorAll('tbody tr:not(.ces-data-table__detail-row) td:nth-child(2)')).map(
    (td) => td.textContent!.trim(),
  );
}

describe('CesDataTableComponent', () => {
  afterEach(() => localStorage.clear());

  it('pagina em 10 por padrão, navega e anuncia o intervalo', async () => {
    const fixture = await montar();
    const el: HTMLElement = fixture.nativeElement;
    expect(nomesVisiveis(el)).toHaveLength(10);
    expect(el.querySelector('.ces-data-table__range')!.textContent).toContain('1–10 de 23');

    (el.querySelector('[aria-label="Última página"] button, button[aria-label="Última página"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(nomesVisiveis(el)).toEqual(['Pessoa 21', 'Pessoa 22', 'Pessoa 23']);
    expect(el.querySelector('.ces-data-table__page-label')!.textContent).toContain('Página 3 de 3');
  });

  it('lembra o tamanho de página por tabela', async () => {
    const fixture = await montar();
    const select = fixture.nativeElement.querySelector('.ces-data-table__page-size select') as HTMLSelectElement;
    select.value = '25';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(nomesVisiveis(fixture.nativeElement)).toHaveLength(23);
    expect(localStorage.getItem('ces.table.teste-inspetores.rows')).toBe('25');
  });

  it('ordena pelo cabeçalho com aria-sort e acumula critérios com Shift', async () => {
    const fixture = await montar();
    const el: HTMLElement = fixture.nativeElement;
    const [thNome, thRealizado] = Array.from(el.querySelectorAll('thead th')).slice(1) as HTMLElement[];
    const botao = thRealizado.querySelector('button')!;
    botao.click();
    botao.click();
    fixture.detectChanges();
    expect(thRealizado.getAttribute('aria-sort')).toBe('descending');
    expect(nomesVisiveis(el)[0]).toBe('Pessoa 23');

    thNome.querySelector('button')!.dispatchEvent(new MouseEvent('click', { shiftKey: true }));
    fixture.detectChanges();
    expect(thNome.getAttribute('aria-sort')).toBe('ascending');
    expect(thRealizado.querySelector('.ces-data-table__sort-order')!.textContent).toBe('1');
    expect(thNome.querySelector('.ces-data-table__sort-order')!.textContent).toBe('2');
  });

  it('filtra pela busca rápida e oferece limpar quando nada corresponde', async () => {
    const fixture = await montar();
    const el: HTMLElement = fixture.nativeElement;
    const input = el.querySelector('input[type="search"]') as HTMLInputElement;
    input.value = 'pessoa 07';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(nomesVisiveis(el)).toEqual(['Pessoa 07']);

    input.value = 'ninguém';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(el.querySelector('.ces-data-table__no-results')!.textContent).toContain('ninguém');
  });

  it('renderiza célula customizada, expande a linha e emite a ação da linha', async () => {
    const fixture = await montar();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('.custom-cell')!.textContent).toContain('0 feitas');

    const expandir = el.querySelector('.ces-data-table__expander') as HTMLButtonElement;
    expect(expandir.getAttribute('aria-label')).toBe('Expandir Pessoa 01');
    expandir.click();
    fixture.detectChanges();
    expect(expandir.getAttribute('aria-expanded')).toBe('true');
    expect(el.querySelector('.detalhe')!.textContent).toContain('Histórico de Pessoa 01');

    (el.querySelector('button[aria-label="Ver histórico: Pessoa 01"]') as HTMLButtonElement).click();
    expect(fixture.componentInstance.ativada?.id).toBe('id-0');
  });

  it('expande só as linhas aceitas por rowExpandable, mantendo a coluna do expansor', async () => {
    const fixture = await montar();
    fixture.componentInstance.expansivel.set((linha) => linha.realizado % 2 === 1);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;

    const linhas = Array.from(el.querySelectorAll<HTMLTableRowElement>('tbody tr:not(.ces-data-table__detail-row)'));
    expect(linhas).toHaveLength(10);
    expect(linhas[0].querySelector('.ces-data-table__expander')).toBeNull();
    expect(linhas[0].querySelector('.ces-data-table__expander-col')).not.toBeNull();

    const expandir = linhas[1].querySelector<HTMLButtonElement>('.ces-data-table__expander')!;
    expect(expandir.getAttribute('aria-label')).toBe('Expandir Pessoa 02');
    expandir.click();
    fixture.detectChanges();
    expect(el.querySelectorAll('.detalhe')).toHaveLength(1);
    expect(el.querySelector('.detalhe')!.textContent).toContain('Histórico de Pessoa 02');
  });

  it('mantém busca e exportação quando a tabela não tem título (caption apenas)', async () => {
    await TestBed.configureTestingModule({ imports: [CesDataTableComponent] }).compileComponents();
    const fixture = TestBed.createComponent(CesDataTableComponent<Linha>);
    fixture.componentRef.setInput('columns', [{ key: 'nome', header: 'Inspetor' }]);
    fixture.componentRef.setInput('rows', gerarLinhas(3));
    fixture.componentRef.setInput('caption', 'Inspetores');
    fixture.componentRef.setInput('exportFileName', 'x');
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('input[type="search"]')).not.toBeNull();
    expect(el.querySelector('button[aria-label="Exportar 3 registros em CSV"]')).not.toBeNull();
    expect(el.querySelector('caption')!.textContent).toContain('Inspetores');
  });

  it('sem tamanho lembrado, começa na primeira opção de pageSizeOptions', async () => {
    await TestBed.configureTestingModule({ imports: [CesDataTableComponent] }).compileComponents();
    const fixture = TestBed.createComponent(CesDataTableComponent<Linha>);
    fixture.componentRef.setInput('columns', [{ key: 'nome', header: 'Inspetor' }]);
    fixture.componentRef.setInput('rows', gerarLinhas(80));
    fixture.componentRef.setInput('caption', 'Permissões');
    fixture.componentRef.setInput('pageSizeOptions', [50, 100]);
    fixture.detectChanges();
    expect(fixture.componentInstance.pageSize()).toBe(50);
    expect((fixture.nativeElement as HTMLElement).querySelectorAll('tbody tr')).toHaveLength(50);
  });

  it('aplica filtros por coluna como chips removíveis', async () => {
    await TestBed.configureTestingModule({ imports: [CesDataTableComponent] }).compileComponents();
    const fixture = TestBed.createComponent(CesDataTableComponent<Linha>);
    fixture.componentRef.setInput('columns', [
      { key: 'nome', header: 'Inspetor' },
      { key: 'realizado', header: 'Realizado', type: 'integer' },
    ]);
    fixture.componentRef.setInput('rows', gerarLinhas(23));
    fixture.componentInstance.filters.set([{ key: 'realizado', operator: 'gte', value: 20 }]);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('.ces-data-table__range')!.textContent).toContain('1–3 de 3');
    const remover = el.querySelector('button[aria-label="Remover filtro Realizado ≥ 20"]') as HTMLButtonElement;
    expect(remover).not.toBeNull();
    remover.click();
    fixture.detectChanges();
    expect(fixture.componentInstance.filters()).toEqual([]);
    expect(el.querySelector('.ces-data-table__range')!.textContent).toContain('1–10 de 23');
  });

  it('mostra o estado vazio quando não há linhas', async () => {
    const fixture = await montar();
    fixture.componentInstance.linhas.set([]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('table')).toBeNull();
    expect(fixture.nativeElement.querySelector('ces-async-state')).not.toBeNull();
  });

  it('exporta a visão inteira em CSV', async () => {
    const fixture = await montar();
    const createObjectURL = vi.fn(() => 'blob:x');
    const revokeObjectURL = vi.fn();
    Object.assign(URL, { createObjectURL, revokeObjectURL });
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
    (fixture.nativeElement.querySelector('button[aria-label="Exportar 23 registros em CSV"]') as HTMLButtonElement).click();
    expect(createObjectURL).toHaveBeenCalledOnce();
    const blob = (createObjectURL.mock.calls[0] as unknown as [Blob])[0];
    const texto = await new Promise<string>((resolve) => {
      const leitor = new FileReader();
      leitor.onload = () => resolve(String(leitor.result));
      leitor.readAsText(blob);
    });
    expect(texto.split('\r\n')).toHaveLength(24);
    expect(click).toHaveBeenCalled();
    click.mockRestore();
  });

  it('clique na linha ativa a ação principal, mas não ao clicar em controles da linha', async () => {
    const fixture = await montar();
    const el: HTMLElement = fixture.nativeElement;
    const linhas = el.querySelectorAll<HTMLTableRowElement>('tbody tr:not(.ces-data-table__detail-row)');
    expect(linhas[1].classList).toContain('is-clickable');

    linhas[1].querySelector<HTMLElement>('.custom-cell')!.click();
    expect(fixture.componentInstance.ativada?.id).toBe('id-1');

    fixture.componentInstance.ativada = null;
    linhas[2].querySelector<HTMLButtonElement>('.ces-data-table__expander')!.click();
    fixture.detectChanges();
    expect(fixture.componentInstance.ativada).toBeNull();
  });

  it('sem ação principal, a linha só é clicável com rowClickable', async () => {
    @Component({
      standalone: true,
      imports: [CesDataTableComponent],
      template: `
        <ces-data-table [columns]="colunas" [rows]="linhas" [rowClickable]="clicavel()" (rowActivate)="ativada = $event"></ces-data-table>
      `,
    })
    class SemAcao {
      readonly colunas: CesDataTableColumn<Linha>[] = [{ key: 'nome', header: 'Inspetor' }];
      readonly linhas = gerarLinhas(2);
      readonly clicavel = signal<boolean | undefined>(undefined);
      ativada: Linha | null = null;
    }
    await TestBed.configureTestingModule({ imports: [SemAcao] }).compileComponents();
    const fixture = TestBed.createComponent(SemAcao);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    const celula = () => el.querySelector<HTMLElement>('tbody tr td')!;

    celula().click();
    expect(fixture.componentInstance.ativada).toBeNull();
    expect(el.querySelector('tbody tr')!.classList).not.toContain('is-clickable');

    fixture.componentInstance.clicavel.set(true);
    fixture.detectChanges();
    celula().click();
    expect(fixture.componentInstance.ativada?.id).toBe('id-0');
  });

  it('reúne ações secundárias e principal numa única coluna "Ações"', async () => {
    @Component({
      standalone: true,
      imports: [CesDataTableComponent, CesRowActionsDirective],
      template: `
        <ces-data-table [columns]="colunas" [rows]="linhas" rowActionLabel="Editar">
          <ng-template cesRowActions let-row><button type="button" class="secundaria">Logs {{ row.nome }}</button></ng-template>
        </ces-data-table>
      `,
    })
    class ComAcoes {
      readonly colunas: CesDataTableColumn<Linha>[] = [{ key: 'nome', header: 'Inspetor' }];
      readonly linhas = gerarLinhas(2);
    }
    await TestBed.configureTestingModule({ imports: [ComAcoes] }).compileComponents();
    const fixture = TestBed.createComponent(ComAcoes);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    const cabecalhos = Array.from(el.querySelectorAll('thead th')).map((th) => th.textContent!.trim());
    expect(cabecalhos).toEqual(['Inspetor', 'Ações']);
    const celula = el.querySelector('tbody tr td.ces-data-table__action-col')!;
    expect(celula.querySelector('.secundaria')!.textContent).toContain('Logs Pessoa 01');
    expect(celula.querySelector('button[aria-label="Editar: Pessoa 01"]')).not.toBeNull();
  });
  it('seleciona linhas, a página inteira e respeita o limite', async () => {
    @Component({
      standalone: true,
      imports: [CesDataTableComponent],
      template: `
        <ces-data-table [columns]="colunas" [rows]="linhas" [selectable]="true" [selectionLimit]="12" [(selection)]="selecao">
          <button selection-actions type="button" class="lote">Tratar em lote</button>
        </ces-data-table>
      `,
    })
    class ComSelecao {
      readonly colunas: CesDataTableColumn<Linha>[] = [{ key: 'nome', header: 'Inspetor' }];
      readonly linhas = gerarLinhas(23);
      selecao: readonly Linha[] = [];
    }
    await TestBed.configureTestingModule({ imports: [ComSelecao] }).compileComponents();
    const fixture = TestBed.createComponent(ComSelecao);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    const marcar = (rotulo: string) => {
      (el.querySelector(`input[aria-label="${rotulo}"]`) as HTMLInputElement).click();
      fixture.detectChanges();
    };

    marcar('Selecionar Pessoa 02');
    expect(fixture.componentInstance.selecao.map((l) => l.id)).toEqual(['id-1']);
    expect(el.querySelector('.ces-data-table__selection')!.textContent).toContain('1 selecionado');
    expect(el.querySelector('.ces-data-table__selection .lote')).not.toBeNull();
    const todos = el.querySelector('input[aria-label="Selecionar todos desta página"]') as HTMLInputElement;
    expect(todos.indeterminate).toBe(true);

    marcar('Selecionar todos desta página');
    expect(fixture.componentInstance.selecao).toHaveLength(10);
    expect(todos.checked).toBe(true);

    (el.querySelector('button[aria-label="Próxima página"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    marcar('Selecionar todos desta página');
    expect(fixture.componentInstance.selecao).toHaveLength(12);
    expect(el.querySelector('.ces-data-table__selection')!.textContent).toContain('limite de 12');
    expect((el.querySelector('input[aria-label="Selecionar Pessoa 20"]') as HTMLInputElement).disabled).toBe(true);

    const limpar = Array.from(el.querySelectorAll('button')).find((b) => b.textContent!.includes('Limpar seleção'))!;
    limpar.click();
    fixture.detectChanges();
    expect(fixture.componentInstance.selecao).toEqual([]);
    expect(el.querySelector('.ces-data-table__selection')).toBeNull();
  });
});
