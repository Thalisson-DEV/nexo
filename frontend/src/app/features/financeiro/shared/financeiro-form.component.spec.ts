import { describe, it, expect, beforeEach, vi } from 'vitest';
import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { FormGroup, ReactiveFormsModule } from '@angular/forms';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { providePrimeNG } from 'primeng/config';
import { of } from 'rxjs';

import { CampoFormulario, FormularioSchema } from '../../../core/models/financeiro.models';
import { LookupStore } from '../../../core/lookup/store/lookup.store';
import { FinanceiroFormComponent } from './financeiro-form.component';

/**
 * O campo booleano é a correção do bug que motivou a refatoração: renderizado como
 * caixa de marcação, ele nascia desmarcado e reportava "não" sem que ninguém
 * tivesse respondido — e os campos condicionados a ele nunca apareciam.
 */
@Component({
  standalone: true,
  imports: [ReactiveFormsModule, FinanceiroFormComponent],
  template: `
    <form [formGroup]="form">
      <app-financeiro-form [schema]="schema()" [form]="form" />
    </form>
  `
})
class HospedeiroTeste {
  // Mesmo tipo do componente real: os controles nascem do schema, em tempo de execução.
  readonly form: FormGroup<any> = new FormGroup({});
  readonly schema = signal<FormularioSchema>(esquema());
}

function campo(parcial: Partial<CampoFormulario> & Pick<CampoFormulario, 'chave'>): CampoFormulario {
  return {
    id: parcial.chave,
    rotulo: parcial.chave,
    tipoCampo: 'TEXTO',
    campoCanonico: parcial.chave.toUpperCase(),
    obrigatorio: false,
    ordem: 0,
    grupo: 'NOTA_FISCAL',
    grupoRotulo: 'Nota fiscal',
    opcoes: [],
    ...parcial
  } as CampoFormulario;
}

function esquema(): FormularioSchema {
  return {
    id: 'schema-1',
    tipoSolicitacao: 'COMPRA',
    codigo: 'PADRAO',
    titulo: 'Compra',
    padrao: true,
    ativo: true,
    campos: [
      campo({ chave: 'temNf', rotulo: 'Tem NF', tipoCampo: 'BOOLEANO', obrigatorio: true, ordem: 0 }),
      campo({
        chave: 'justificativaSemNf',
        rotulo: 'Justificativa por não haver NF',
        tipoCampo: 'TEXTO_LONGO',
        obrigatorio: true,
        ordem: 1,
        exibirQuando: { campoChave: 'temNf', valores: ['false'] }
      })
    ]
  };
}

describe('FinanceiroFormComponent — campo booleano', () => {
  let fixture: ComponentFixture<HospedeiroTeste>;
  let hospedeiro: HospedeiroTeste;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HospedeiroTeste],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        providePrimeNG(),
        {
          provide: LookupStore,
          useValue: {
            searchCollaborators: vi.fn(() =>
              of({
                items: [{ value: 'colab-1', label: 'João Silva' }],
                nextCursor: null,
                hasMore: false
              })
            )
          }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(HospedeiroTeste);
    hospedeiro = fixture.componentInstance;
    fixture.detectChanges();
  });

  function botoes(): HTMLElement[] {
    return Array.from(fixture.nativeElement.querySelectorAll('p-selectbutton p-togglebutton'));
  }

  function rotuloSelecionado(): string | null {
    const marcado = botoes().find((botao) => botao.getAttribute('aria-pressed') === 'true');
    return marcado?.textContent?.trim() ?? null;
  }

  function clicar(rotulo: string): void {
    const alvo = botoes().find((botao) => botao.textContent?.trim() === rotulo);
    if (!alvo) throw new Error(`Botão "${rotulo}" não encontrado`);
    alvo.click();
    fixture.detectChanges();
  }

  it('renderiza um par Sim/Não, e não uma caixa de marcação', () => {
    expect(fixture.nativeElement.querySelector('p-checkbox')).toBeNull();
    expect(botoes().map((botao) => botao.textContent?.trim())).toEqual(['Sim', 'Não']);
  });

  it('nasce sem resposta, deixando o campo condicionado oculto', () => {
    expect(hospedeiro.form.get('temNf')!.value).toBeNull();
    expect(rotuloSelecionado()).toBeNull();
    expect(fixture.nativeElement.querySelector('#justificativaSemNf')).toBeNull();
  });

  it('responder "Não" grava false e revela o campo dependente', () => {
    clicar('Não');

    expect(hospedeiro.form.get('temNf')!.value).toBe(false);
    expect(rotuloSelecionado()).toBe('Não');
    expect(fixture.nativeElement.querySelector('#justificativaSemNf')).not.toBeNull();
  });

  it('responder "Sim" grava true e mantém o dependente oculto', () => {
    clicar('Sim');

    expect(hospedeiro.form.get('temNf')!.value).toBe(true);
    expect(fixture.nativeElement.querySelector('#justificativaSemNf')).toBeNull();
  });

  it('trocar a resposta limpa o que havia sido escrito no campo que sumiu', () => {
    clicar('Não');
    hospedeiro.form.get('justificativaSemNf')!.setValue('Fornecedor não emite nota.');
    fixture.detectChanges();

    clicar('Sim');

    expect(hospedeiro.form.get('justificativaSemNf')!.value).toBeNull();
  });

  it('não permite voltar ao estado "sem resposta" depois de responder', () => {
    clicar('Não');
    clicar('Não');

    expect(hospedeiro.form.get('temNf')!.value).toBe(false);
  });
});

function esquemaComColaborador(): FormularioSchema {
  return {
    ...esquema(),
    id: 'schema-colaborador',
    campos: [
      campo({
        chave: 'solicitanteColaboradorId',
        rotulo: 'Nome do solicitante',
        tipoCampo: 'REFERENCIA',
        campoCanonico: 'SOLICITANTE_COLABORADOR',
        grupo: 'IDENTIFICACAO',
        grupoRotulo: 'Identificação',
        referencia: {
          fonte: 'COLABORADOR',
          endpointOpcoes: '/v1/rh/colaboradores',
          descricao: 'Colaborador que solicitou a compra'
        }
      })
    ]
  };
}

describe('FinanceiroFormComponent — referência de colaborador', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HospedeiroTeste],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        providePrimeNG(),
        {
          provide: LookupStore,
          useValue: {
            searchCollaborators: vi.fn(() =>
              of({
                items: [{ value: 'colab-1', label: 'João Silva' }],
                nextCursor: null,
                hasMore: false
              })
            )
          }
        }
      ]
    }).compileComponents();
  });

  it('usa autocomplete e só consulta após a digitação', async () => {
    const lookupStore = TestBed.inject(LookupStore) as unknown as {
      searchCollaborators: ReturnType<typeof vi.fn>;
    };
    const host = TestBed.createComponent(HospedeiroTeste);
    host.componentInstance.schema.set(esquemaComColaborador());
    host.detectChanges();

    expect(host.nativeElement.querySelector('ces-autocomplete')).not.toBeNull();
    expect(host.nativeElement.querySelector('p-select')).toBeNull();
    expect(lookupStore.searchCollaborators).not.toHaveBeenCalled();

    const componente = host.debugElement.query(By.directive(FinanceiroFormComponent))
      .componentInstance as FinanceiroFormComponent & {
        pesquisarColaboradores: (query: string) => void;
        opcoesColaborador: () => readonly { value: string; label: string }[];
      };
    componente.pesquisarColaboradores('jo');
    await new Promise((resolve) => setTimeout(resolve, 350));

    expect(lookupStore.searchCollaborators).toHaveBeenCalledWith({ query: 'jo', cursor: null });
    componente.pesquisarColaboradores('jo');
    await new Promise((resolve) => setTimeout(resolve, 350));
    expect(lookupStore.searchCollaborators).toHaveBeenCalledTimes(1);
    expect(componente.opcoesColaborador()).toEqual([{ value: 'colab-1', label: 'João Silva' }]);

    let exibicoes: Record<string, { valor: unknown; exibicao?: string | null }> = {};
    componente.exibicoesResolvidas.subscribe((value) => (exibicoes = value));
    host.componentInstance.form.get('solicitanteColaboradorId')!.setValue('colab-1');
    expect(exibicoes['solicitanteColaboradorId']).toEqual({
      valor: 'colab-1',
      exibicao: 'João Silva'
    });
  });
});
