import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { providePrimeNG } from 'primeng/config';
import { of, throwError } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { DecisaoEmail } from '../../../core/models/financeiro.models';
import { FinanceiroService } from '../../../core/services/financeiro.service';
import { DecisaoEmailComponent } from './decisao-email.component';

const disponivel: DecisaoEmail = {
  situacao: 'DISPONIVEL',
  mensagem: 'Confira os dados e confirme a sua decisão.',
  titulo: 'Compra nº 8',
  situacaoSolicitacao: 'Aguardando aprovação',
  valorSolicitado: 123,
  decisor: 'Carla Diretora',
  expiraEm: '2026-10-30T16:00:00Z',
  ficha: [
    { rotulo: 'Fornecedor', valor: 'HC PNEUS' },
    { rotulo: 'Base operacional', valor: 'JUAZEIRO' }
  ]
};

describe('DecisaoEmailComponent', () => {
  let service: { consultarDecisaoEmail: ReturnType<typeof vi.fn>; decidirPorEmail: ReturnType<typeof vi.fn> };

  const montar = async (resposta = of(disponivel), acao: string | null = 'aprovar') => {
    service = {
      consultarDecisaoEmail: vi.fn(() => resposta),
      decidirPorEmail: vi.fn(() =>
        of({ ...disponivel, situacao: 'DECIDIDA_POR_ESTE_LINK', mensagem: 'Aprovação registrada por este link.' })
      )
    };
    await TestBed.configureTestingModule({
      imports: [DecisaoEmailComponent],
      providers: [
        provideNoopAnimations(),
        providePrimeNG(),
        { provide: FinanceiroService, useValue: service },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: convertToParamMap({ token: 'tok-123' }),
              queryParamMap: convertToParamMap(acao ? { acao } : {})
            }
          }
        }
      ]
    }).compileComponents();
    const fixture = TestBed.createComponent(DecisaoEmailComponent);
    fixture.detectChanges();
    return { fixture, component: fixture.componentInstance as any, texto: () => fixture.nativeElement.textContent as string };
  };

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.clearAllMocks();
  });

  it('mostra a solicitação inteira e já vem com a decisão escolhida no e-mail', async () => {
    const { component, texto } = await montar();

    expect(service.consultarDecisaoEmail).toHaveBeenCalledWith('tok-123');
    expect(texto()).toContain('Compra nº 8');
    expect(texto()).toContain('HC PNEUS');
    expect(texto()).toContain('em nome de Carla Diretora');
    expect(component.acao()).toBe('APROVAR');
    expect(component.valor()).toBe(123);
  });

  it('aprova com o valor e a observação informados', async () => {
    const { fixture, component, texto } = await montar();
    component.valor.set(110);
    component.texto.set('Com desconto');

    component.confirmar();
    fixture.detectChanges();

    expect(service.decidirPorEmail).toHaveBeenCalledWith('tok-123', {
      acao: 'APROVAR',
      valorAprovado: 110,
      texto: 'Com desconto'
    });
    expect(texto()).toContain('Aprovação registrada por este link.');
    expect(texto()).not.toContain('Confirmar aprovação');
  });

  it('exige motivo para rejeitar e não autoriza acima do pedido', async () => {
    const { component } = await montar(of(disponivel), 'rejeitar');

    component.confirmar();
    expect(component.erroTexto()).toBe('Informe o motivo da rejeição.');

    component.escolher('APROVAR');
    component.valor.set(200);
    component.confirmar();
    expect(component.erroValor()).toBe('O valor autorizado não pode passar do solicitado.');
    expect(service.decidirPorEmail).not.toHaveBeenCalled();
  });

  it('não oferece decisão quando o link expirou', async () => {
    const { texto } = await montar(
      of({ ...disponivel, situacao: 'EXPIRADO', mensagem: 'Este link expirou. Para decidir, entre no Nexo.' })
    );

    expect(texto()).toContain('Este link expirou');
    expect(texto()).not.toContain('Sua decisão');
  });

  it('informa link inválido', async () => {
    const { texto } = await montar(throwError(() => new Error('Link de decisão inválido.')), null);

    expect(texto()).toContain('Link indisponível');
  });
});
