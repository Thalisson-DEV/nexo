import { describe, it, expect } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { DatePicker } from 'primeng/datepicker';
import { MultiSelect } from 'primeng/multiselect';
import { Select } from 'primeng/select';
import { AutoComplete } from 'primeng/autocomplete';

import { CesAutocompleteComponent } from './ces-autocomplete.component';
import { CesBooleanControlComponent } from './ces-boolean-control.component';
import { CesDatepickerComponent } from './ces-datepicker.component';
import { CesFileInputComponent } from './ces-file-input.component';
import { CesMultiSelectComponent } from './ces-multiselect.component';
import { CesNumberInputComponent } from './ces-number-input.component';
import { CesPasswordInputComponent } from './ces-password-input.component';
import { CesSelectComponent } from './ces-select.component';
import { CesTextInputComponent } from './ces-text-input.component';
import { CesTextareaComponent } from './ces-textarea.component';

const configure = async (...imports: unknown[]) => {
  await TestBed.configureTestingModule({ imports }).compileComponents();
};

describe('Nexo form controls', () => {
  it('associa texto, descrição e erro ao input nativo', async () => {
    await configure(CesTextInputComponent);
    const fixture = TestBed.createComponent(CesTextInputComponent);
    fixture.componentRef.setInput('label', 'Nome da base');
    fixture.componentRef.setInput('inputId', 'base-name');
    fixture.componentRef.setInput('description', 'Use o nome oficial.');
    fixture.detectChanges();

    const label = fixture.nativeElement.querySelector('label') as HTMLLabelElement;
    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;

    expect(label.id).toBe('base-name-label');
    expect(label.htmlFor).toBe('base-name');
    expect(input.getAttribute('aria-describedby')).toBe('base-name-description');
    expect(input.getAttribute('aria-invalid')).toBe('false');

    fixture.componentRef.setInput('error', 'Informe o nome da base.');
    fixture.detectChanges();

    expect(input.getAttribute('aria-describedby')).toBe('base-name-error');
    expect(input.getAttribute('aria-invalid')).toBe('true');
  });

  it('distingue readonly de disabled no input de texto', async () => {
    await configure(CesTextInputComponent);
    const fixture = TestBed.createComponent(CesTextInputComponent);
    fixture.componentRef.setInput('label', 'Código');
    fixture.componentRef.setInput('readonly', true);
    fixture.detectChanges();

    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    expect(input.readOnly).toBe(true);
    expect(input.disabled).toBe(false);

    fixture.componentRef.setInput('disabled', true);
    fixture.detectChanges();
    expect(input.disabled).toBe(true);
  });

  it('usa os padrões compactos e appendTo body nos controles de overlay', async () => {
    await configure(
      CesSelectComponent,
      CesMultiSelectComponent,
      CesDatepickerComponent,
      CesAutocompleteComponent,
    );

    const selectFixture = TestBed.createComponent(CesSelectComponent);
    selectFixture.componentRef.setInput('label', 'Município');
    selectFixture.detectChanges();
    const select = selectFixture.debugElement.query(By.directive(Select))
      .componentInstance as Select;

    const multiFixture = TestBed.createComponent(CesMultiSelectComponent);
    multiFixture.componentRef.setInput('label', 'Bases');
    multiFixture.detectChanges();
    const multiselect = multiFixture.debugElement.query(By.directive(MultiSelect))
      .componentInstance as MultiSelect;

    const dateFixture = TestBed.createComponent(CesDatepickerComponent);
    dateFixture.componentRef.setInput('label', 'Vencimento');
    dateFixture.componentRef.setInput('labelVisible', false);
    dateFixture.detectChanges();
    const datepicker = dateFixture.debugElement.query(By.directive(DatePicker))
      .componentInstance as DatePicker;
    expect(dateFixture.nativeElement.querySelector('.ces-sr-only')?.textContent).toContain('Vencimento');

    const autocompleteFixture = TestBed.createComponent(CesAutocompleteComponent);
    autocompleteFixture.componentRef.setInput('label', 'Colaborador');
    autocompleteFixture.detectChanges();
    const autocomplete = autocompleteFixture.debugElement.query(By.directive(AutoComplete))
      .componentInstance as AutoComplete;

    expect(select.size()).toBe('small');
    expect(select.appendTo()).toBe('body');
    expect(multiselect.size()).toBe('small');
    expect(multiselect.appendTo()).toBe('body');
    expect(datepicker.size()).toBe('small');
    expect(datepicker.appendTo()).toBe('body');
    expect(autocomplete.size()).toBe('small');
    expect(autocomplete.appendTo()).toBe('body');
    expect(autocomplete.minQueryLength).toBe(2);
    expect(autocomplete.virtualScroll).toBe(true);
    expect(autocomplete.forceSelection).toBe(true);
  });

  it('mantem valor primitivo no ControlValueAccessor e encaminha busca/paginacao', async () => {
    await configure(CesAutocompleteComponent);
    const fixture = TestBed.createComponent(CesAutocompleteComponent<string>);
    fixture.componentRef.setInput('label', 'Colaborador');
    fixture.componentRef.setInput('inputId', 'collaborator');
    fixture.componentRef.setInput('description', 'Pesquise por nome.');
    fixture.detectChanges();

    const component = fixture.componentInstance;
    const changes: Array<string | null> = [];
    const searches: string[] = [];
    let loadMoreCount = 0;
    let touched = false;
    component.registerOnChange((value) => changes.push(value));
    component.registerOnTouched(() => (touched = true));
    component.search.subscribe((query) => searches.push(query));
    component.loadMore.subscribe(() => loadMoreCount++);
    component.writeValue('collaborator-1');
    fixture.detectChanges();

    expect(component.value()).toBe('collaborator-1');
    const autocompleteElement = fixture.debugElement.query(By.directive(AutoComplete));
    // PrimeNG emits the primitive option.value through ngModelChange.
    autocompleteElement.triggerEventHandler('ngModelChange', 'collaborator-2');
    autocompleteElement.triggerEventHandler('completeMethod', {
      query: 'br',
      originalEvent: new Event('input'),
    });
    autocompleteElement.triggerEventHandler('onLazyLoad', { first: 10, last: 20 });
    autocompleteElement.triggerEventHandler('onBlur');

    expect(component.value()).toBe('collaborator-2');
    expect(changes).toEqual(['collaborator-2']);
    expect(searches).toEqual(['br']);
    expect(loadMoreCount).toBe(1);
    expect(touched).toBe(true);
  });

  it('explica antes da busca que o usuario precisa digitar', async () => {
    await configure(CesAutocompleteComponent);
    const fixture = TestBed.createComponent(CesAutocompleteComponent);
    fixture.componentRef.setInput('label', 'Material');
    fixture.componentRef.setInput('inputId', 'material-search');
    fixture.detectChanges();

    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    const description = fixture.nativeElement.querySelector(
      '#material-search-description',
    ) as HTMLParagraphElement;
    expect(description.textContent?.trim()).toBe(
      'Digite pelo menos 2 caracteres para ver as opções.',
    );
    expect(input.getAttribute('aria-describedby')).toBe('material-search-description');
  });

  it('mostra o rotulo da opcao para um valor primitivo hidratado', async () => {
    await configure(CesAutocompleteComponent);
    const fixture = TestBed.createComponent(CesAutocompleteComponent<string>);
    fixture.componentRef.setInput('label', 'Colaborador');
    fixture.componentRef.setInput('options', [{ value: 'colab-1', label: 'Maria Souza' }]);
    fixture.detectChanges();
    fixture.componentInstance.writeValue('colab-1');
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    expect(input.value).toBe('Maria Souza');
  });

  it('mantem rotulo acessivel sem ocupar espaco quando a tela preserva seu label externo', async () => {
    await configure(CesAutocompleteComponent);
    const fixture = TestBed.createComponent(CesAutocompleteComponent);
    fixture.componentRef.setInput('label', 'Colaborador');
    fixture.componentRef.setInput('labelVisible', false);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.ces-field__heading')).toBeNull();
    const input = fixture.nativeElement.querySelector('[role="combobox"]') as HTMLElement;
    const labelId = input.getAttribute('aria-labelledby');
    const label = fixture.nativeElement.querySelector(`#${labelId}`) as HTMLLabelElement;
    expect(label.classList).toContain('ces-sr-only');
    expect(label.textContent?.trim()).toBe('Colaborador');
  });

  it('associa estado de erro ao combobox e a regiao anunciavel', async () => {
    await configure(CesAutocompleteComponent);
    const fixture = TestBed.createComponent(CesAutocompleteComponent);
    fixture.componentRef.setInput('label', 'Centro de custo');
    fixture.componentRef.setInput('inputId', 'cost-center');
    fixture.componentRef.setInput('error', 'Selecione um centro valido.');
    fixture.componentRef.setInput('status', 'error');
    fixture.detectChanges();

    const component = fixture.componentInstance;
    let retries = 0;
    component.retry.subscribe(() => retries++);
    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    expect(input.getAttribute('aria-labelledby')).toBe('cost-center-label');
    expect(input.getAttribute('aria-describedby')).toBe('cost-center-error');
    expect(input.getAttribute('aria-invalid')).toBe('true');
    expect(fixture.nativeElement.querySelector('[aria-live="polite"]')?.textContent).toContain(
      'Não foi possível carregar',
    );

    component.requestRetry();
    expect(retries).toBe(1);
  });

  it('mapeia o erro do select para o combobox acessível', async () => {
    await configure(CesSelectComponent);
    const fixture = TestBed.createComponent(CesSelectComponent);
    fixture.componentRef.setInput('label', 'Centro de custo');
    fixture.componentRef.setInput('inputId', 'cost-center');
    fixture.componentRef.setInput('error', 'Selecione um centro de custo.');
    fixture.detectChanges();

    const combobox = fixture.nativeElement.querySelector('[role="combobox"]') as HTMLElement;
    expect(combobox.getAttribute('aria-labelledby')).toBe('cost-center-label');
    expect(combobox.getAttribute('aria-describedby')).toBe('cost-center-error');
    expect(combobox.getAttribute('aria-invalid')).toBe('true');
  });

  it('expõe modelos coerentes para número e booleano', async () => {
    await configure(CesNumberInputComponent, CesBooleanControlComponent);

    const numberFixture = TestBed.createComponent(CesNumberInputComponent);
    numberFixture.componentRef.setInput('label', 'Quantidade');
    numberFixture.componentInstance.value.set(12.5);
    numberFixture.detectChanges();
    expect(numberFixture.componentInstance.value()).toBe(12.5);

    const booleanFixture = TestBed.createComponent(CesBooleanControlComponent);
    booleanFixture.componentRef.setInput('label', 'Ativo');
    booleanFixture.componentRef.setInput('kind', 'toggle');
    booleanFixture.componentInstance.value.set(true);
    booleanFixture.detectChanges();

    const input = booleanFixture.nativeElement.querySelector('input') as HTMLInputElement;
    expect(input.id).toBe(booleanFixture.componentInstance.controlId());
    expect(input.getAttribute('role')).toBe('switch');
    expect(booleanFixture.componentInstance.value()).toBe(true);
  });

  it('mantém rótulo, controle e descrição do booleano inline em uma única linha', async () => {
    await configure(CesBooleanControlComponent);
    const fixture = TestBed.createComponent(CesBooleanControlComponent);
    fixture.componentRef.setInput('label', 'Saque fora da equipe');
    fixture.componentRef.setInput('inputId', 'outside-team');
    fixture.componentRef.setInput('description', 'Permite selecionar um colaborador externo.');
    fixture.componentRef.setInput('kind', 'toggle');
    fixture.componentRef.setInput('inline', true);
    fixture.detectChanges();

    const field = fixture.nativeElement.querySelector('ces-field') as HTMLElement;
    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;

    expect(field.classList.contains('ces-field--inline')).toBe(true);
    expect(input.getAttribute('aria-describedby')).toBe('outside-team-description');
  });

  it('habilita virtualScroll automaticamente em listas grandes ou quando explicitado', async () => {
    await configure(CesSelectComponent, CesMultiSelectComponent);

    const selectFixture = TestBed.createComponent(CesSelectComponent);
    selectFixture.componentRef.setInput('label', 'Colaborador');
    const largeOptions = Array.from({ length: 50 }, (_, i) => ({
      label: `Colab ${i}`,
      value: `${i}`,
    }));
    selectFixture.componentRef.setInput('options', largeOptions);
    selectFixture.detectChanges();

    const select = selectFixture.debugElement.query(By.directive(Select))
      .componentInstance as Select;
    expect(select.virtualScroll).toBe(true);

    const multiFixture = TestBed.createComponent(CesMultiSelectComponent);
    multiFixture.componentRef.setInput('label', 'Materiais');
    multiFixture.componentRef.setInput('options', largeOptions);
    multiFixture.detectChanges();

    const multiselect = multiFixture.debugElement.query(By.directive(MultiSelect))
      .componentInstance as MultiSelect;
    expect(multiselect.virtualScroll).toBe(true);
  });

  it('valida o limite do arquivo antes de expor a seleção', async () => {
    await configure(CesFileInputComponent);
    const fixture = TestBed.createComponent(CesFileInputComponent);
    fixture.componentRef.setInput('label', 'Planilha');
    fixture.componentRef.setInput('maxBytes', 4);
    fixture.detectChanges();

    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    const file = new File(['conteudo'], 'solicitacoes.csv', { type: 'text/csv' });
    Object.defineProperty(input, 'files', { value: [file], configurable: true });
    input.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    expect(fixture.componentInstance.file()).toBeNull();
    expect(fixture.componentInstance.effectiveError()).toContain('excede o limite');
  });

  it('permite rótulo apenas acessível, altura máxima e foco programático no textarea', async () => {
    await configure(CesTextareaComponent);
    const fixture = TestBed.createComponent(CesTextareaComponent);
    fixture.componentRef.setInput('label', 'Mensagem para o Copiloto');
    fixture.componentRef.setInput('inputId', 'mensagem');
    fixture.componentRef.setInput('labelVisible', false);
    fixture.componentRef.setInput('maxHeight', '12rem');
    fixture.detectChanges();

    const label = fixture.nativeElement.querySelector('label') as HTMLLabelElement;
    const textarea = fixture.nativeElement.querySelector('textarea') as HTMLTextAreaElement;
    expect(label.classList).toContain('ces-sr-only');
    expect(label.htmlFor).toBe('mensagem');
    expect(textarea.style.maxHeight).toBe('12rem');
    expect(textarea.style.overflowY).toBe('auto');

    fixture.componentInstance.focus();
    expect(document.activeElement).toBe(textarea);
  });
  it('mascara a senha, revela sob demanda e propaga o valor', async () => {
    await configure(CesPasswordInputComponent);
    const fixture = TestBed.createComponent(CesPasswordInputComponent);
    fixture.componentRef.setInput('label', 'Senha inicial');
    fixture.componentRef.setInput('inputId', 'senha');
    fixture.componentRef.setInput('minlength', 6);
    fixture.detectChanges();

    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    const revelar = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    expect(input.type).toBe('password');
    expect(input.autocomplete).toBe('new-password');
    expect(input.getAttribute('minlength')).toBe('6');
    expect(revelar.getAttribute('aria-label')).toBe('Mostrar senha');
    expect(revelar.getAttribute('aria-controls')).toBe('senha');

    revelar.click();
    fixture.detectChanges();
    expect(input.type).toBe('text');
    expect(revelar.getAttribute('aria-pressed')).toBe('true');
    expect(revelar.getAttribute('aria-label')).toBe('Ocultar senha');

    input.value = 'segredo1';
    input.dispatchEvent(new Event('input'));
    expect(fixture.componentInstance.value()).toBe('segredo1');
  });
  it('datepicker com hora: placeholder, showTime e máscara dd/mm/aaaa hh:mm', async () => {
    await configure(CesDatepickerComponent);
    const fixture = TestBed.createComponent(CesDatepickerComponent);
    fixture.componentRef.setInput('label', 'Início da vigência');
    fixture.componentRef.setInput('showTime', true);
    fixture.detectChanges();
    const datepicker = fixture.debugElement.query(By.directive(DatePicker)).componentInstance as DatePicker;
    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    expect(datepicker.showTime).toBe(true);
    expect(input.placeholder).toBe('dd/mm/aaaa hh:mm');

    input.value = '101020261430';
    input.dispatchEvent(new Event('input', { bubbles: true }));
    expect(input.value).toBe('10/10/2026 14:30');

    fixture.componentRef.setInput('showTime', false);
    fixture.detectChanges();
    input.value = '101020261430';
    input.dispatchEvent(new Event('input', { bubbles: true }));
    expect(input.value).toBe('10/10/2026');
  });

  it('datepicker não reemite o mesmo instante (evita laço com Date recriado a cada leitura)', async () => {
    await configure(CesDatepickerComponent);
    const fixture = TestBed.createComponent(CesDatepickerComponent);
    fixture.componentRef.setInput('label', 'Data');
    fixture.componentRef.setInput('value', new Date(2026, 9, 10));
    fixture.detectChanges();
    const emitidos: unknown[] = [];
    fixture.componentInstance.value.subscribe((v) => emitidos.push(v));
    const datepicker = fixture.debugElement.query(By.directive(DatePicker));
    datepicker.triggerEventHandler('ngModelChange', new Date(2026, 9, 10));
    expect(emitidos).toEqual([]);
    datepicker.triggerEventHandler('ngModelChange', new Date(2026, 9, 11));
    expect(emitidos).toEqual([new Date(2026, 9, 11)]);
  });

  it('controles do PrimeNG não saem com autofocus', async () => {
    for (const tipo of [CesDatepickerComponent, CesSelectComponent]) {
      await configure(tipo);
      const fixture = TestBed.createComponent(tipo as never);
      (fixture.componentRef as { setInput(n: string, v: unknown): void }).setInput('label', 'Campo');
      fixture.detectChanges();
      expect((fixture.nativeElement as HTMLElement).querySelector('[autofocus]')).toBeNull();
      TestBed.resetTestingModule();
    }
  });
});
