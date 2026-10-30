import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { CesFilterChip, CesFilterPanelComponent } from './ces-filter-panel.component';

@Component({
  standalone: true,
  imports: [CesFilterPanelComponent],
  template: `
    <ces-filter-panel
      [storageKey]="chave"
      [activeCount]="ativos()"
      [summary]="chips()"
      (applied)="aplicados = aplicados + 1"
      (cleared)="limpos = limpos + 1"
      (chipRemoved)="remover($event)"
    >
      <label>Placa <input class="campo" type="text" /></label>
      <textarea class="nota"></textarea>
    </ces-filter-panel>
  `,
})
class HostComponent {
  chave: string | undefined = 'teste-painel';
  readonly ativos = signal(0);
  readonly chips = signal<CesFilterChip[]>([
    { key: 'situacao', label: 'Situação: Vencidos e a vencer', removable: false },
    { key: 'base', label: 'Base: Feira de Santana' },
    { key: 'tipo', label: 'Tipo: Caminhão' },
  ]);
  aplicados = 0;
  limpos = 0;
  readonly removidos: string[] = [];

  remover(chave: string): void {
    this.removidos.push(chave);
    this.chips.update((atuais) => atuais.filter((chip) => chip.key !== chave));
  }
}

async function montar(configurar?: (host: HostComponent) => void) {
  await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
  const fixture = TestBed.createComponent(HostComponent);
  configurar?.(fixture.componentInstance);
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();
  return fixture;
}

describe('CesFilterPanelComponent', () => {
  afterEach(() => localStorage.clear());

  it('abre recolhido e mostra o resumo de filtros com padrões não removíveis', async () => {
    const fixture = await montar();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('.ces-filter-panel__content')).toBeNull();
    const toggle = el.querySelector('.ces-filter-panel__toggle') as HTMLButtonElement;
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    expect(toggle.getAttribute('aria-label')).toBe('Expandir filtros');

    const chips = Array.from(el.querySelectorAll('.ces-filter-panel__chip')).map((chip) => chip.textContent!.trim());
    expect(chips).toEqual(['Situação: Vencidos e a vencer', 'Base: Feira de Santana', 'Tipo: Caminhão']);
    expect(el.querySelector('button[aria-label="Remover filtro Situação: Vencidos e a vencer"]')).toBeNull();
    expect(el.textContent).not.toContain('filtro ativo');
  });

  it('remove um chip e leva o foco ao próximo chip ou ao botão do painel', async () => {
    const fixture = await montar();
    const el: HTMLElement = fixture.nativeElement;
    (el.querySelector('button[aria-label="Remover filtro Base: Feira de Santana"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(fixture.componentInstance.removidos).toEqual(['base']);
    expect(document.activeElement?.getAttribute('aria-label')).toBe('Remover filtro Tipo: Caminhão');

    (el.querySelector('button[aria-label="Remover filtro Tipo: Caminhão"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(document.activeElement?.classList.contains('ces-filter-panel__toggle')).toBe(true);
  });

  it('lembra a expansão por tela e aplica com Enter num campo de texto', async () => {
    const fixture = await montar();
    const el: HTMLElement = fixture.nativeElement;
    (el.querySelector('.ces-filter-panel__toggle') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(localStorage.getItem('ces.filters.teste-painel.expanded')).toBe('true');

    const campo = el.querySelector('.campo') as HTMLInputElement;
    campo.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    expect(fixture.componentInstance.aplicados).toBe(1);

    const nota = el.querySelector('.nota') as HTMLTextAreaElement;
    nota.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    expect(fixture.componentInstance.aplicados).toBe(1);

    TestBed.resetTestingModule();
    const outra = await montar();
    expect(outra.nativeElement.querySelector('.ces-filter-panel__content')).not.toBeNull();
  });

  it('desabilita "Limpar filtros" quando tudo está no padrão e emite aplicar e limpar', async () => {
    const fixture = await montar((host) => (host.chave = undefined));
    const el: HTMLElement = fixture.nativeElement;
    (el.querySelector('.ces-filter-panel__toggle') as HTMLButtonElement).click();
    fixture.detectChanges();
    const [limpar, aplicar] = Array.from(el.querySelectorAll('footer button')) as HTMLButtonElement[];
    expect(limpar.disabled).toBe(true);

    fixture.componentInstance.ativos.set(2);
    fixture.detectChanges();
    expect(el.textContent).toContain('2 filtros ativos');
    expect(limpar.disabled).toBe(false);
    limpar.click();
    aplicar.click();
    expect(fixture.componentInstance.limpos).toBe(1);
    expect(fixture.componentInstance.aplicados).toBe(1);
  });

  it('funciona sem localStorage', async () => {
    const getItem = vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('bloqueado');
    });
    const setItem = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('bloqueado');
    });
    const fixture = await montar();
    (fixture.nativeElement.querySelector('.ces-filter-panel__toggle') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.ces-filter-panel__content')).not.toBeNull();
    getItem.mockRestore();
    setItem.mockRestore();
  });
});
