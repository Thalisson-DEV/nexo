import { Component, OnInit } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';
import { provideLocationMocks } from '@angular/common/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createFormDraft } from './form-draft';
import { ViewStateStore } from './view-state.store';

@Component({ template: '', standalone: true })
class FormHost implements OnInit {
  readonly form = new FormGroup({
    nome: new FormControl(''),
    cpf: new FormControl(''),
  });
  readonly draft = createFormDraft({
    read: () => this.form.getRawValue(),
    write: (value) => this.form.patchValue(value),
    changes: this.form.valueChanges,
    persist: false,
  });
  restaurado = false;

  ngOnInit(): void {
    this.restaurado = this.draft.restore();
  }
}

@Component({ template: '', standalone: true })
class OutraTela {}

describe('createFormDraft', () => {
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'obras/nova', component: FormHost },
          { path: 'seletor', component: OutraTela },
        ]),
        provideLocationMocks(),
      ],
    });
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => {
    vi.useRealTimers();
    TestBed.resetTestingModule();
  });

  it('preserva o preenchimento ao ir a uma tela auxiliar e voltar', async () => {
    const form = await harness.navigateByUrl('/obras/nova', FormHost);
    expect(form.restaurado).toBe(false);
    form.form.patchValue({ nome: 'Obra Centro' });

    await harness.navigateByUrl('/seletor');
    const devolta = await harness.navigateByUrl('/obras/nova', FormHost);
    expect(devolta.restaurado).toBe(true);
    expect(devolta.form.value.nome).toBe('Obra Centro');
  });

  it('descarta após salvar/cancelar explícito', async () => {
    const form = await harness.navigateByUrl('/obras/nova', FormHost);
    form.form.patchValue({ nome: 'Obra' });
    form.draft.clear();

    await harness.navigateByUrl('/seletor');
    const devolta = await harness.navigateByUrl('/obras/nova', FormHost);
    expect(devolta.restaurado).toBe(false);
    expect(devolta.form.value.nome).toBe('');
  });

  it('formulário aberto e não alterado não gera rascunho', async () => {
    await harness.navigateByUrl('/obras/nova', FormHost);
    await harness.navigateByUrl('/seletor');
    expect(TestBed.inject(ViewStateStore).get(ViewStateStore.key('/obras/nova', 'rascunho'))).toBeUndefined();
  });

  it('salva com debounce enquanto o usuário digita', async () => {
    vi.useFakeTimers();
    const form = await harness.navigateByUrl('/obras/nova', FormHost);
    form.form.patchValue({ nome: 'A' });
    expect(form.draft.peek()).toBeUndefined();
    vi.advanceTimersByTime(400);
    expect(form.draft.peek()).toEqual({ nome: 'A', cpf: '' });
  });

  it('persist:false: dado pessoal não vai para o sessionStorage', async () => {
    const form = await harness.navigateByUrl('/obras/nova', FormHost);
    form.form.patchValue({ cpf: '12345678900' });
    await harness.navigateByUrl('/seletor');
    expect(JSON.stringify(sessionStorage)).not.toContain('12345678900');
  });

  it('logout (store.clear) impede restaurar o rascunho do usuário anterior', async () => {
    const form = await harness.navigateByUrl('/obras/nova', FormHost);
    form.form.patchValue({ nome: 'X' });
    await harness.navigateByUrl('/seletor');
    TestBed.inject(ViewStateStore).clear();
    const devolta = await harness.navigateByUrl('/obras/nova', FormHost);
    expect(devolta.restaurado).toBe(false);
  });
});
