import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ConfirmationService } from 'primeng/api';
import { describe, expect, it } from 'vitest';

import { CesDataTableComponent } from '../../data/data-table/ces-data-table.component';
import { CesImportDrawerComponent, cesImportStatus } from './ces-import-drawer.component';

const linhas = [
  { linha: 2, status: 'VALIDADO' },
  { linha: 3, status: 'COM_ERRO', mensagemErro: 'Perfil inexistente' }
];

describe('CesImportDrawerComponent', () => {
  const abrir = async (bloquear = false) => {
    await TestBed.configureTestingModule({ imports: [CesImportDrawerComponent], providers: [provideNoopAnimations(), ConfirmationService] }).compileComponents();
    const fixture = TestBed.createComponent(CesImportDrawerComponent);
    fixture.componentRef.setInput('visible', true);
    fixture.componentRef.setInput('rows', linhas);
    fixture.componentRef.setInput('blockOnErrors', bloquear);
    fixture.detectChanges();
    await fixture.whenStable();
    const processar = () =>
      Array.from(document.querySelectorAll<HTMLButtonElement>('button')).find((b) => b.textContent?.includes('Processar importação'))!;
    return { fixture, processar };
  };

  it('resume as linhas e libera o processamento mesmo com erros', async () => {
    const { processar } = await abrir();
    expect(document.body.textContent).toContain('2 linhas · 1 com erro');
    expect(processar().disabled).toBe(false);
  });

  it('com blockOnErrors, só processa sem linhas com erro', async () => {
    const { fixture, processar } = await abrir(true);
    expect(processar().disabled).toBe(true);
    expect(document.body.textContent).toContain('remova as linhas com erro para processar');

    fixture.componentRef.setInput('rows', [linhas[0]]);
    fixture.detectChanges();
    expect(processar().disabled).toBe(false);
  });

  it('traduz a situação de cada linha', () => {
    expect(cesImportStatus('COM_ERRO').tone).toBe('danger');
    expect(cesImportStatus('VALIDADO').label).toBeTruthy();
  });

  const arrastar = (tipo: string, arquivos: File[] = []) => {
    const evento = new Event(tipo, { bubbles: true, cancelable: true });
    Object.defineProperty(evento, 'dataTransfer', { value: { types: ['Files'], files: arquivos, dropEffect: 'none' } });
    document.body.dispatchEvent(evento);
    return evento;
  };

  it('aceita o arquivo arrastado e solto na tela', async () => {
    const { fixture } = await abrir();
    const recebidos: File[] = [];
    fixture.componentInstance.fileSelected.subscribe((arquivo) => recebidos.push(arquivo));

    arrastar('dragenter');
    fixture.detectChanges();
    expect(document.body.textContent).toContain('Solte o arquivo para importar');
    expect(arrastar('dragover').defaultPrevented).toBe(true);

    const planilha = new File(['a;b'], 'solicitacoes.CSV', { type: 'text/csv' });
    expect(arrastar('drop', [planilha]).defaultPrevented).toBe(true);
    fixture.detectChanges();

    expect(recebidos).toEqual([planilha]);
    expect(document.body.textContent).not.toContain('Solte o arquivo para importar');
  });

  it('recusa extensão fora do accept e mais de um arquivo, com aviso', async () => {
    const { fixture } = await abrir();
    const recebidos: File[] = [];
    fixture.componentInstance.fileSelected.subscribe((arquivo) => recebidos.push(arquivo));

    arrastar('drop', [new File(['x'], 'foto.png')]);
    fixture.detectChanges();
    expect(document.querySelector('[role="alert"]')?.textContent).toContain('"foto.png" não é um arquivo aceito');

    arrastar('drop', [new File(['a'], 'a.csv'), new File(['b'], 'b.csv')]);
    fixture.detectChanges();
    expect(document.querySelector('[role="alert"]')?.textContent).toContain('um arquivo por vez');
    expect(recebidos).toEqual([]);
  });

  it('ignora arquivo solto enquanto processa', async () => {
    const { fixture } = await abrir();
    fixture.componentRef.setInput('processing', true);
    fixture.detectChanges();
    const recebidos: File[] = [];
    fixture.componentInstance.fileSelected.subscribe((arquivo) => recebidos.push(arquivo));

    expect(arrastar('drop', [new File(['a'], 'a.csv')]).defaultPrevented).toBe(false);
    expect(recebidos).toEqual([]);
  });

  it('abre na largura máxima e a alça reduz até o tamanho xl', async () => {
    const { fixture } = await abrir();
    const alca = document.querySelector<HTMLElement>('.ces-form-drawer__resize')!;
    const maximo = Math.round(window.innerWidth * 0.9);
    expect(alca.getAttribute('role')).toBe('separator');
    expect(Number(alca.getAttribute('aria-valuenow'))).toBe(maximo);

    alca.dispatchEvent(new KeyboardEvent('keydown', { key: 'Home', bubbles: true }));
    fixture.detectChanges();
    const minimo = Number(alca.getAttribute('aria-valuenow'));
    expect(minimo).toBe(Math.min(56 * 16, maximo));

    alca.dispatchEvent(new KeyboardEvent('keydown', { key: 'End', bubbles: true }));
    fixture.detectChanges();
    expect(Number(alca.getAttribute('aria-valuenow'))).toBe(maximo);
  });

  it('a pré-visualização ganha largura mínima por coluna e texto em até 3 linhas', async () => {
    @Component({
      standalone: true,
      imports: [CesImportDrawerComponent, CesDataTableComponent],
      template: `
        <ces-import-drawer [visible]="true" [rows]="linhas">
          <ces-data-table caption="Prévia" [searchable]="false" [columns]="colunas" [rows]="linhas"></ces-data-table>
        </ces-import-drawer>
      `,
    })
    class HostComponent {
      readonly linhas = [{ linha: 2, status: 'COM_ERRO', mensagemErro: 'Material inexistente no catálogo do almoxarifado' }];
      readonly colunas = [
        { key: 'linha', header: 'Linha', type: 'number' as const },
        { key: 'mensagemErro', header: 'Erro' },
        { key: 'status', header: 'Situação', width: '6rem' },
      ];
    }
    await TestBed.configureTestingModule({ imports: [HostComponent], providers: [provideNoopAnimations(), ConfirmationService] }).compileComponents();
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    await fixture.whenStable();

    const tabela = document.querySelector<HTMLElement>('ces-data-table')!;
    expect(tabela.hasAttribute('data-fit')).toBe(true);
    expect(tabela.style.getPropertyValue('--ces-data-table-lines')).toBe('3');
    const [linha, erro, situacao] = Array.from(tabela.querySelectorAll<HTMLElement>('thead th'));
    expect(erro.style.minWidth).toBe('9rem');
    expect(linha.style.minWidth).toContain('4.5rem');
    expect(situacao.style.minWidth).toBe('');
    const celula = tabela.querySelector<HTMLElement>('.ces-data-table__clamp[title^="Material inexistente"]');
    expect(celula?.textContent).toContain('Material inexistente no catálogo');
  });

  it('mostra os parâmetros da importação acima da escolha do arquivo', async () => {
    @Component({
      standalone: true,
      imports: [CesImportDrawerComponent],
      template: `
        <ces-import-drawer [visible]="true">
          <label import-params class="parametro">Contrato <select><option>CT-01</option></select></label>
          <p class="previa">Prévia</p>
        </ces-import-drawer>
      `,
    })
    class HostComponent {}
    await TestBed.configureTestingModule({ imports: [HostComponent], providers: [provideNoopAnimations(), ConfirmationService] }).compileComponents();
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    await fixture.whenStable();

    const parametro = document.querySelector('.ces-import-drawer__params .parametro');
    const escolha = document.querySelector('.ces-import-drawer__pick');
    expect(parametro).not.toBeNull();
    expect(parametro!.compareDocumentPosition(escolha!) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(document.querySelector('.ces-import-drawer__preview .previa')).not.toBeNull();
    expect(document.querySelector('.ces-import-drawer__preview .parametro')).toBeNull();
  });
});
