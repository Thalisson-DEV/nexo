import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { afterEach, describe, expect, it } from 'vitest';
import { CesShortcutsPreference } from '../../core/shortcuts';
import { CesDataTableComponent } from '../../data/data-table/ces-data-table.component';
import { CesButtonComponent } from '../../primitives/button';
import { CesFilterPanelComponent } from '../filter-panel/ces-filter-panel.component';
import { CesListShortcutsDirective } from './ces-list-shortcuts.directive';

@Component({
  standalone: true,
  imports: [CesListShortcutsDirective, CesFilterPanelComponent, CesDataTableComponent, CesButtonComponent],
  template: `
    <div cesListShortcuts #atalhos="cesListShortcuts" [newAction]="podeCriar()" (newRequested)="novos = novos + 1">
      <ces-button class="novo" label="Novo veículo" [keyShortcut]="atalhos.newShortcut()"></ces-button>
      <ces-filter-panel><input class="campo" /></ces-filter-panel>
      <ces-data-table [columns]="colunas" [rows]="linhas"></ces-data-table>
    </div>
  `,
})
class HostComponent {
  readonly podeCriar = signal(true);
  readonly colunas = [{ key: 'placa', header: 'Placa' }];
  readonly linhas = [{ id: '1', placa: 'ABC1D23' }];
  novos = 0;
}

async function montar() {
  await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
  const fixture = TestBed.createComponent(HostComponent);
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();
  return fixture;
}

function tecla(key: string, alvo: EventTarget = document.body, extra: KeyboardEventInit = {}): KeyboardEvent {
  const evento = new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true, ...extra });
  alvo.dispatchEvent(evento);
  return evento;
}

describe('CesListShortcutsDirective', () => {
  afterEach(() => {
    localStorage.clear();
    TestBed.inject(CesShortcutsPreference).setEnabled(true);
  });

  it('"/" foca a busca, "f" expande os filtros e "n" pede novo registro', async () => {
    const fixture = await montar();
    const el: HTMLElement = fixture.nativeElement;

    expect(tecla('/').defaultPrevented).toBe(true);
    expect(document.activeElement).toBe(el.querySelector('input[type="search"]'));
    (document.activeElement as HTMLElement).blur();

    tecla('f');
    fixture.detectChanges();
    await fixture.whenStable();
    expect(el.querySelector('.ces-filter-panel__content')).not.toBeNull();

    tecla('n');
    expect(fixture.componentInstance.novos).toBe(1);
  });

  it('anuncia os atalhos com aria-keyshortcuts', async () => {
    const fixture = await montar();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('input[type="search"]')!.getAttribute('aria-keyshortcuts')).toBe('/');
    expect(el.querySelector('.ces-filter-panel__toggle')!.getAttribute('aria-keyshortcuts')).toBe('F');
    expect(el.querySelector('.novo button')!.getAttribute('aria-keyshortcuts')).toBe('N');
  });

  it('ignora teclas digitadas em campos, com modificadores e sem permissão de criar', async () => {
    const fixture = await montar();
    const el: HTMLElement = fixture.nativeElement;
    const busca = el.querySelector('input[type="search"]') as HTMLInputElement;
    expect(tecla('n', busca).defaultPrevented).toBe(false);
    tecla('n', document.body, { ctrlKey: true });
    expect(fixture.componentInstance.novos).toBe(0);

    fixture.componentInstance.podeCriar.set(false);
    fixture.detectChanges();
    tecla('n');
    expect(fixture.componentInstance.novos).toBe(0);
    expect(el.querySelector('.novo button')!.hasAttribute('aria-keyshortcuts')).toBe(false);
  });

  it('ignora atalhos com um overlay aberto', async () => {
    const fixture = await montar();
    const dialogo = document.createElement('div');
    dialogo.setAttribute('aria-modal', 'true');
    document.body.appendChild(dialogo);
    try {
      tecla('n');
      expect(fixture.componentInstance.novos).toBe(0);
    } finally {
      dialogo.remove();
    }
  });

  it('desligados pela preferência, não têm efeito nem são anunciados', async () => {
    const fixture = await montar();
    TestBed.inject(CesShortcutsPreference).setEnabled(false);
    fixture.detectChanges();
    await fixture.whenStable();
    tecla('n');
    expect(fixture.componentInstance.novos).toBe(0);
    expect(fixture.nativeElement.querySelector('input[type="search"]')!.hasAttribute('aria-keyshortcuts')).toBe(false);
    expect(localStorage.getItem('ces_single_key_shortcuts')).toBe('off');
  });
});
