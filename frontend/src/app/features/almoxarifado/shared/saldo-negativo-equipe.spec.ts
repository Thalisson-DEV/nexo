import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BrowserTestingModule, platformBrowserTesting } from '@angular/platform-browser/testing';
import { describe, expect, it } from 'vitest';
import { SaldoResponse } from '../../../core/models/almoxarifado.models';
import { AvisoSaldoNegativoEquipeComponent, agruparSaldosNegativos } from './saldo-negativo-equipe';

try {
  TestBed.initTestEnvironment(BrowserTestingModule, platformBrowserTesting());
} catch {}

const saldo = (idMaterial: string, quantidade: number, descricao = 'MEDIDOR MONOFÁSICO') =>
  ({
    id: `${idMaterial}-${quantidade}`,
    material: { id: idMaterial, codigo: '410001', descricao, unidadeMedida: 'UN' },
    quantidade,
  }) as unknown as SaldoResponse;

describe('agruparSaldosNegativos', () => {
  it('soma por material apenas os saldos negativos', () => {
    const mapa = agruparSaldosNegativos([saldo('m1', -2), saldo('m1', -1), saldo('m1', 5), saldo('m2', 0)]);

    expect([...mapa.keys()]).toEqual(['m1']);
    expect(mapa.get('m1')).toEqual(expect.objectContaining({ quantidade: -3, unidadeMedida: 'UN' }));
  });
});

describe('AvisoSaldoNegativoEquipeComponent', () => {
  let fixture: ComponentFixture<AvisoSaldoNegativoEquipeComponent>;

  const criar = (idsMateriais: string[]) => {
    fixture = TestBed.createComponent(AvisoSaldoNegativoEquipeComponent);
    fixture.componentRef.setInput('saldosNegativos', agruparSaldosNegativos([saldo('m1', -2)]));
    fixture.componentRef.setInput('idsMateriais', idsMateriais);
    fixture.componentRef.setInput('equipe', 'EQ-SSA-04');
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  };

  it('mostra o material negativo desta entrega e orienta informar o gestor', () => {
    const el = criar(['m1', 'm1', 'm3']);

    expect(el.textContent).toContain('Saldo negativo na equipe');
    expect(el.textContent).toContain('EQ-SSA-04');
    expect(el.textContent).toContain('MEDIDOR MONOFÁSICO');
    expect(el.textContent).toContain('informar ao gestor');
    expect(el.textContent).toContain('A entrega pode seguir normalmente');
    expect(el.querySelectorAll('li').length).toBe(1);
  });

  it('não aparece quando nenhum material da entrega está negativo', () => {
    const el = criar(['m3']);

    expect(el.textContent?.trim()).toBe('');
  });
});
