import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ConfirmationService } from 'primeng/api';
import { describe, expect, it } from 'vitest';

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
});
