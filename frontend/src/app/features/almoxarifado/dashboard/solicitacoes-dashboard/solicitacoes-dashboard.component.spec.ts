import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { ConfirmationService } from 'primeng/api';
import { NotificationService } from '@ces/ui';
import { PainelSolicitacoesFiltros, PainelSolicitacoesResponse, SolicitacaoDoPainel } from '../../../../core/models/painel-solicitacoes.models';
import { AlmoxarifadoService } from '../../../../core/services/almoxarifado.service';
import { AssistenteService } from '../../../../core/services/assistente.service';
import { AuthService } from '../../../../core/services/auth.service';
import { RecorteDashboardService } from '../../../execucao/dashboards/recorte-dashboard.service';
import { SolicitacoesDashboardComponent } from './solicitacoes-dashboard.component';

const recorteFalso = {
  carregando: signal(false),
  carregandoEquipes: signal(false),
  opcoesBases: signal([]),
  opcoesProcessos: signal([]),
  opcoesEquipes: signal([{ label: 'EQ-01', value: 'eq-1' }]),
  carregar: vi.fn(),
  carregarEquipes: vi.fn(),
  nomeDaBase: () => null,
  nomeDoProcesso: () => null
};

const agora = new Date();
const diasAtras = (n: number) => new Date(agora.getTime() - n * 86_400_000).toISOString();

function sol(p: Partial<SolicitacaoDoPainel>): SolicitacaoDoPainel {
  return {
    id: Math.random().toString(36).slice(2),
    contexto: 'COMERCIAL',
    status: 'PENDENTE',
    etapa: 'PENDENTE_APROVACAO',
    macroEtapa: 'PLANEJAMENTO',
    tipoTecnico: null,
    tipoObra: null,
    ocorrencia: null,
    idObra: null,
    numeroObra: null,
    statusSeparacao: null,
    dataSolicitacao: diasAtras(1),
    dataVerificacao: null,
    dataEntrega: null,
    dataPrevista: null,
    separacaoIniciadaEm: null,
    separacaoConcluidaEm: null,
    encerradaEm: null,
    estoqueOrigem: 'Almox Central',
    estoqueDestino: 'Estoque EQ-01',
    idAlmoxarifado: 'almox-1',
    almoxarifado: 'Almox Central',
    idEquipe: 'eq-1',
    equipe: 'EQ-01',
    baseOperacional: 'Salvador',
    processo: 'Comercial',
    motivoRejeicao: null,
    possuiPendencia: false,
    motivosPendencia: null,
    foraPrazoSaque: false,
    saqueForaEquipe: false,
    temReserva: false,
    itens: 3,
    quantidadeSolicitada: 10,
    quantidadeAtendida: 0,
    anteriorAoPeriodo: false,
    ...p
  };
}

const resposta: PainelSolicitacoesResponse = {
  geradoEm: agora.toISOString(),
  inicio: diasAtras(10),
  fim: agora.toISOString(),
  limite: 5000,
  truncado: false,
  solicitacoes: [
    sol({ etapa: 'PENDENTE_RETIRADA', dataPrevista: '2020-01-01', possuiPendencia: true, motivosPendencia: 'Falta de saldo' }),
    sol({ etapa: 'CONCLUIDO', status: 'ENTREGUE', dataSolicitacao: diasAtras(3), dataEntrega: diasAtras(2), quantidadeAtendida: 10 }),
    sol({ etapa: 'REJEITADO', status: 'REJEITADO', motivoRejeicao: 'Sem saldo' }),
    sol({ contexto: 'TECNICO', tipoTecnico: '221', tipoObra: 'OBRA', idObra: 'o1', numeroObra: '123', etapa: 'EM_SEPARACAO', idEquipe: 'eq-2', equipe: 'EQ-02' }),
    sol({ contexto: 'TECNICO', tipoTecnico: '222', etapa: 'PENDENTE_DEVOLUCAO', anteriorAoPeriodo: true, dataSolicitacao: diasAtras(40) })
  ],
  materiais: [
    { contexto: 'COMERCIAL', sentido: 'SAIDA', codigo: 'M1', descricao: 'Cabo multiplex', unidade: 'M', solicitacoes: 2, quantidadeSolicitada: 20, quantidadeAtendida: 10 }
  ],
  periodoAnterior: [{ contexto: 'COMERCIAL', total: 2, concluidas: 2, rejeitadas: 0, cicloMedioHoras: 12, aprovacaoMediaHoras: 1 }]
};

function montar() {
  const obterPainelSolicitacoes = vi.fn((_filtros: PainelSolicitacoesFiltros) => of(resposta));
  const exportarPainelSolicitacoes = vi.fn((_filtros: PainelSolicitacoesFiltros, _opcoes: unknown) => of(new Blob(['x'])));
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      ConfirmationService,
      {
        provide: AlmoxarifadoService,
        useValue: {
          obterPainelSolicitacoes,
          exportarPainelSolicitacoes,
          listarEstoquesAtivos: () => of({ content: [{ id: 'almox-1', nomeIdentificador: 'Almox Central' }], page: {} })
        }
      },
      { provide: RecorteDashboardService, useValue: recorteFalso },
      { provide: AuthService, useValue: { currentUser: signal({ id: 'u1', permissoes: [] }), token: () => null } },
      { provide: AssistenteService, useValue: {} },
      { provide: NotificationService, useValue: { show: vi.fn(), success: vi.fn(), error: vi.fn() } }
    ]
  });
  const fixture = TestBed.createComponent(SolicitacoesDashboardComponent);
  fixture.detectChanges();
  return { fixture, el: fixture.nativeElement as HTMLElement, obterPainelSolicitacoes, exportarPainelSolicitacoes };
}

function texto(el: Element | null): string {
  return (el?.textContent ?? '').replace(/\s+/g, ' ').trim();
}

describe('Painel de solicitações (visão geral)', () => {
  it('carrega o mês corrente com os dois processos e mostra veredito e indicadores', () => {
    const { el, obterPainelSolicitacoes } = montar();

    expect(obterPainelSolicitacoes).toHaveBeenCalledTimes(1);
    const filtros = obterPainelSolicitacoes.mock.calls[0][0];
    expect(filtros.contexto).toBeUndefined();
    expect(filtros.dataInicio).toBeTruthy();

    expect(texto(el.querySelector('.vg'))).toContain('1 solicitação atrasada');
    const metricas = [...el.querySelectorAll('.dash-metric')].map(texto);
    expect(metricas[0]).toContain('Solicitações no período');
    expect(metricas[0]).toContain('4');
    expect(metricas.some((m) => m.includes('Atrasadas') && m.includes('1'))).toBe(true);
  });

  it('compara os processos e lista fluxo, fila de atenção, técnicos e motivos', () => {
    const { el } = montar();

    expect(el.querySelectorAll('.sd-processo')).toHaveLength(2);
    const etapas = [...el.querySelectorAll('.sd-fluxo__etapa')].map(texto);
    expect(etapas.some((e) => e.includes('Pendente de retirada') && e.includes('1 atrasada'))).toBe(true);
    const atencao = el.querySelectorAll('.sd-atencao__item');
    expect(atencao.length).toBe(3);
    expect(atencao[0].getAttribute('data-tom')).toBe('danger');
    expect(atencao[0].querySelector('a.sd-link')?.getAttribute('href')).toMatch(/^\/almoxarifado\/solicitacoes\//);
    expect(texto(el.querySelector('.sd-ranking'))).toBeTruthy();
    expect(texto(el)).toContain('Obra 123');
    expect(texto(el)).toContain('Falta de saldo');
    expect(texto(el)).toContain('Sem saldo');
    expect(texto(el)).toContain('Cabo multiplex');
  });

  it('exporta pela gaveta, partindo do recorte em tela, com conteúdo, nível e arquivo', () => {
    const { fixture, el, exportarPainelSolicitacoes, obterPainelSolicitacoes } = montar();
    const criar = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:x');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    const clique = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
    const tela = fixture.componentInstance as unknown as {
      abrirExportacao(): void;
      executarExportacao(): void;
      expNivel: { set(v: string): void };
      expSituacao: { set(v: string): void };
      exportacaoAberta(): boolean;
    };

    tela.abrirExportacao();
    fixture.detectChanges();
    expect(texto(document.body)).toContain('Exportar solicitações');

    tela.expNivel.set('DETALHADO');
    tela.expSituacao.set('ATRASADA');
    tela.executarExportacao();

    const [filtros, opcoes] = exportarPainelSolicitacoes.mock.calls[0] as unknown as [object, object];
    expect(filtros).toEqual(obterPainelSolicitacoes.mock.calls[0][0]);
    expect(opcoes).toEqual({ conteudo: 'SOLICITACOES', nivel: 'DETALHADO', situacao: 'ATRASADA', incluirBacklog: true, formato: 'xlsx' });
    expect(criar).toHaveBeenCalled();
    expect(clique).toHaveBeenCalled();
    expect(tela.exportacaoAberta()).toBe(false);
    expect(el).toBeTruthy();
  });
});
