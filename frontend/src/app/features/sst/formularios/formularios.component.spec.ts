import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { CesConfirmationService } from '@ces/ui';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { SstService } from '../../../core/services/sst.service';
import { abrirListagem, chipsDoPainel, paginaTeste } from '../../../shared/listagem/listagem-teste';
import { FormulariosComponent } from './formularios.component';

const formulario = { id: 'f1', titulo: 'Checklist de veículo', descricao: 'Inspeção diária', totalPerguntas: 12, ativo: true };

describe('FormulariosComponent (SST)', () => {
  let sst: Record<string, ReturnType<typeof vi.fn>>;
  const abrir = (url = '/lista') => abrirListagem(FormulariosComponent, 'lista', url, [{ provide: SstService, useValue: sst }]);

  beforeEach(() => {
    TestBed.resetTestingModule();
    sst = {
      listarFormularios: vi.fn(() => paginaTeste([formulario])),
      duplicarFormulario: vi.fn(() => of({ id: 'f2' })),
      ativarFormulario: vi.fn(() => of(null)),
      desativarFormulario: vi.fn(() => of(null))
    };
  });

  afterEach(() => localStorage.clear());

  it('abre já nos dados, ordenado pelo título', async () => {
    const { el } = await abrir('/lista?status=true');
    expect(sst['listarFormularios']).toHaveBeenLastCalledWith({ ativo: true, buscaGlobal: undefined, page: 0, size: 25, sort: 'titulo,asc' });
    expect(el.textContent).toContain('Checklist de veículo');
    expect(chipsDoPainel(el)).toEqual(['Status: Ativo']);
  });

  it('duplicar abre o construtor na cópia', async () => {
    const { componente } = await abrir();
    vi.spyOn(TestBed.inject(CesConfirmationService), 'confirm').mockResolvedValue(true);
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    await (componente as unknown as { duplicar(f: unknown): Promise<void> }).duplicar(formulario);
    expect(sst['duplicarFormulario']).toHaveBeenCalledWith('f1');
    expect(navegar).toHaveBeenCalledWith(['/sst/formularios', 'f2']);
  });
});
