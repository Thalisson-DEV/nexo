import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { ContextoExecucaoResponse, DetalhamentoTurnoResponse } from '../../../core/models/dashboard.models';
import { ExecucaoComercialResponse } from '../../../core/models/execucao.models';
import { DashboardService } from '../../../core/services/dashboard.service';
import { ExecucaoService } from '../../../core/services/execucao.service';
import { EventoHistoricoNota } from '../../../core/models/execucao.models';
import { RhService } from '../../../core/services/rh.service';
import { ExecucaoRelatorioComponent } from '../execucao-relatorio/execucao-relatorio.component';
import { TurnoDetalhamentoComponent } from '../turno-detalhamento/turno-detalhamento.component';

function texto(el: HTMLElement): string {
  return (el.textContent ?? '').replace(/\s+/g, ' ').trim();
}


const nota = (id: string, hora: string, valor: number): ExecucaoComercialResponse => ({
  id,
  referenciaNota: `N-${id}`,
  idTurno: 't1',
  tipoServico: id === 'e1' ? { id: 'ts-cs', codigo: 'CS', descricao: 'CORTE', ativo: true } : null,
  equipe: { id: 'eq1', codigo: 'EQ-01', membros: [] } as unknown as ExecucaoComercialResponse['equipe'],
  municipio: { id: 'm1', nome: 'Salvador' } as ExecucaoComercialResponse['municipio'],
  latitude: -12.97,
  longitude: -38.5,
  dataLancamento: `2026-10-09T${hora}:00-03:00`,
  statusExecucao: 'CONCLUIDO',
  valorTotalFaturado: valor,
  acoes: [{ id: 'x' + id, acao: { id: 'a1', codigo: 'A1', nome: 'Religação' } as never }],
  materiais: [],
  tipoNota: 'COMUM',
  referenciaProvisoria: null,
  dataNumeroOficial: null,
  aguardandoNumeroOficial: false,
  totalAlteracoes: 0,
  alterada: false
});

const rotaCom = (id: string) => ({
  paramMap: of(convertToParamMap({ id })),
  queryParamMap: of(convertToParamMap({})),
  snapshot: { paramMap: convertToParamMap({ id }) }
});

describe('Relatório de turno', () => {
  const relatorio: DetalhamentoTurnoResponse = {
    turno: {
      id: 't1',
      idEquipe: 'eq1',
      idUsuarioAbertura: 'u1',
      dataInicio: '2026-10-09T07:00:00-03:00',
      dataFim: '2026-10-09T15:00:00-03:00',
      status: 'FECHADO'
    },
    composicao: { abertoPor: 'Maria Souza', placa: 'ABC1D23', membros: ['João Lima', 'Maria Souza'] },
    execucoes: [nota('e1', '08:00', 300), nota('e2', '12:00', 200)],
    indicadores: {
      calculadoEm: '2026-10-09T18:00:00-03:00',
      emAndamento: false,
      abertoDesdeOutroDia: false,
      duracaoMinutos: 480,
      notas: 2,
      producao: 500,
      valorMedioNota: 250,
      producaoPorHora: 62.5,
      metaDiaria: 1000,
      percentualMetaDiaria: 50,
      minutosAtePrimeiroLancamento: 60,
      minutosAposUltimoLancamento: 180,
      maiorIntervaloSemLancamentoMinutos: 240,
      lancamentosSemLocalizacao: 0
    },
    historicoEquipe: {
      dias: 30,
      turnos: 8,
      mediaNotasPorTurno: 4,
      mediaProducaoPorTurno: 1000,
      mediaDuracaoMinutos: 470,
      valorMedioNota: 250
    }
  };

  function montar(detalharTurno: DashboardService['detalharTurno']) {
    TestBed.configureTestingModule({
      imports: [TurnoDetalhamentoComponent],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: rotaCom('t1') },
        { provide: DashboardService, useValue: { detalharTurno } },
        { provide: RhService, useValue: { buscarEquipePorId: () => of({ id: 'eq1', codigo: 'EQ-01', membros: [] }) } }
      ]
    });
    const fixture = TestBed.createComponent(TurnoDetalhamentoComponent);
    fixture.detectChanges();
    return fixture;
  }

  afterEach(() => TestBed.resetTestingModule());

  it('mostra indicadores, destaques para o gestor, notas e composição', async () => {
    const fixture = montar(() => of(relatorio));
    await fixture.whenStable();
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const conteudo = texto(el);

    expect(conteudo).toContain('EQ-01');
    expect(conteudo).toContain('−50% vs. média da equipe');
    expect(conteudo).toContain('Meta do dia não atingida');
    expect(conteudo).toContain('Produção 50% abaixo da média da equipe');
    expect(conteudo).toContain('4h sem lançar');
    expect(conteudo).toContain('Fim de turno sem produção');
    expect(el.querySelectorAll('table tbody tr').length).toBeGreaterThanOrEqual(2);
    expect(el.querySelectorAll('.trilho-ponto').length).toBe(2);
    expect(conteudo).toContain('Maria Souza');
    expect(conteudo).toContain('ABC1D23');
    expect(conteudo).toContain('Tipos de Serviço');
    expect(conteudo).toContain('CS CORTE');
    expect(conteudo).toContain('Não informado');
  });

  it('erro na carga oferece tentar novamente', async () => {
    const fixture = montar(() => throwError(() => ({ error: { detail: 'Turno não encontrado' } })));
    await fixture.whenStable();
    fixture.detectChanges();
    expect(texto(fixture.nativeElement)).toContain('Turno indisponível');
  });
});

describe('Relatório de execução', () => {
  const contexto: ContextoExecucaoResponse = {
    idExecucao: 'e2',
    turno: { id: 't1', status: 'FECHADO', dataInicio: '2026-10-09T07:00:00-03:00', dataFim: '2026-10-09T15:00:00-03:00' },
    lancamentosDoTurno: [
      { idExecucao: 'e1', referenciaNota: 'N-e1', dataLancamento: '2026-10-09T08:00:00-03:00', status: 'CONCLUIDO', valor: 300, latitude: -12.97, longitude: -38.5 },
      { idExecucao: 'e2', referenciaNota: 'N-e2', dataLancamento: '2026-10-09T12:00:00-03:00', status: 'CONCLUIDO', valor: 200, latitude: -12.98, longitude: -38.5 }
    ],
    diaDaEquipe: { data: '2026-10-09', notas: 2, producao: 500, metaDiaria: 1000, percentualMetaDiaria: 50 },
    referenciaEquipe: { dias: 30, notas: 40, valorMedioNota: 80 },
    valoresPorAcao: [{ idAcao: 'a1', valor: 200 }]
  };

  const historico: EventoHistoricoNota[] = [
    {
      id: 'h1', tipo: 'LANCADA', origem: 'CAMPO', autor: 'João Lima', motivo: null, valorAnterior: null, valorNovo: 150,
      detalhes: { acoesAdicionadas: [{ idAcao: 'a1', codigo: 'A1', nome: 'Religação' }], acoesRemovidas: [], materiaisAdicionados: [], materiaisRemovidos: [], referenciaAnterior: null, referenciaNova: null },
      ocorridoEm: '2026-10-09T12:00:00-03:00', registradoEm: '2026-10-09T12:00:00-03:00', reconstruido: false
    },
    {
      id: 'h2', tipo: 'ALTERADA', origem: 'ESCRITORIO', autor: 'Ana Prado', motivo: 'Material lançado errado', valorAnterior: 150, valorNovo: 200,
      detalhes: {
        acoesAdicionadas: [], acoesRemovidas: [],
        materiaisAdicionados: [],
        materiaisRemovidos: [{ idMaterial: 'm1', codigo: 'C16', descricao: 'CABO 16MM', unidadeMedida: 'M', quantidade: 3, operacao: 'APLICADO', numSerie: null, origem: 'EQUIPE' }],
        referenciaAnterior: null, referenciaNova: null
      },
      ocorridoEm: '2026-10-10T09:00:00-03:00', registradoEm: '2026-10-10T09:00:00-03:00', reconstruido: false
    }
  ];

  function montar(contextualizar: DashboardService['contextualizarExecucao'], execucao = nota('e2', '12:00', 200)) {
    TestBed.configureTestingModule({
      imports: [ExecucaoRelatorioComponent],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: rotaCom('e2') },
        { provide: ExecucaoService, useValue: { buscarPorId: () => of(execucao), historico: () => of(historico) } },
        { provide: DashboardService, useValue: { contextualizarExecucao: contextualizar } }
      ]
    });
    const fixture = TestBed.createComponent(ExecucaoRelatorioComponent);
    fixture.detectChanges();
    return fixture;
  }

  afterEach(() => TestBed.resetTestingModule());

  it('situa a nota no turno e no dia da equipe, com valor de referência por ação', async () => {
    const fixture = montar(() => of(contexto));
    await fixture.whenStable();
    fixture.detectChanges();
    const conteudo = texto(fixture.nativeElement);

    expect(conteudo).toContain('NOTA N-e2');
    expect(conteudo).toContain('Tipo: Não informado');
    expect(conteudo).toContain('2ª de 2');
    expect(conteudo).toContain('a 1,1 km da nota anterior');
    expect(conteudo).toContain('esta nota é 40% do dia');
    expect(conteudo).toContain('Valor 150% acima da média da equipe');
    expect(conteudo).toContain('(esta nota)');
    expect(conteudo).toContain('R$ 200,00');
  });

  it('sem contexto (falha) a nota continua visível', async () => {
    const fixture = montar(() => throwError(() => new Error('falhou')));
    await fixture.whenStable();
    fixture.detectChanges();
    const conteudo = texto(fixture.nativeElement);
    expect(conteudo).toContain('NOTA N-e2');
    expect(conteudo).toContain('Sem informações do turno desta nota.');
  });

  it('nota alterada avisa que não é mais a lançada e mostra a linha do tempo', async () => {
    const alterada = { ...nota('e2', '12:00', 200), totalAlteracoes: 1, alterada: true, dataAlteracao: '2026-10-10T09:00:00-03:00' };
    const fixture = montar(() => of(contexto), alterada);
    await fixture.whenStable();
    fixture.detectChanges();
    expect(texto(fixture.nativeElement)).toContain('Esta nota foi alterada depois de sincronizada.');
    expect(texto(fixture.nativeElement)).toContain('Linha do tempo de alterações');
    expect(texto(fixture.nativeElement)).toContain('1 alteração');

    // A linha do tempo fica num acordeão, como no relatório da solicitação: só carrega aberta.
    (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>('p-accordion-header button, p-accordion-header')!.click();
    await fixture.whenStable();
    fixture.detectChanges();
    const conteudo = texto(fixture.nativeElement);
    expect(conteudo).toContain('Nota lançada');
    expect(conteudo).toContain('Nota alterada');
    expect(conteudo).toContain('Material lançado errado');
    expect(conteudo).toContain('C16 · CABO 16MM · 3 M aplicado');
    expect(conteudo).toContain('Escritório');
  });
});
