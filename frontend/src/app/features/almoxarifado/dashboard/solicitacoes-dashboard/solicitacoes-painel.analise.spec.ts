import { firstValueFrom } from 'rxjs';
import { describe, expect, it } from 'vitest';
import {
  MaterialDoPainel,
  PainelSolicitacoesResponse,
  SolicitacaoDoPainel
} from '../../../../core/models/painel-solicitacoes.models';
import {
  atrasada,
  cicloHoras,
  diasDeAtraso,
  filaDeAtencao,
  fluxoDasAbertas,
  idadeDasAbertas,
  indicadores,
  materiais,
  mediana,
  motivosDePendencia,
  ranking,
  recortar,
  serieDiaria,
  situacao,
  tempo
} from './solicitacoes-painel.analise';
import { visaoGerencial, vistaPainel } from './solicitacoes-painel.visao';

const GERADO_EM = '2026-10-13T12:00:00-03:00';

let seq = 0;
function sol(p: Partial<SolicitacaoDoPainel> = {}): SolicitacaoDoPainel {
  seq++;
  return {
    id: `s${seq}`,
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
    dataSolicitacao: '2026-10-05T09:00:00-03:00',
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
    itens: 2,
    quantidadeSolicitada: 10,
    quantidadeAtendida: 0,
    anteriorAoPeriodo: false,
    ...p
  };
}

function painel(solicitacoes: SolicitacaoDoPainel[], materiaisDoPainel: MaterialDoPainel[] = []): PainelSolicitacoesResponse {
  return {
    geradoEm: GERADO_EM,
    inicio: '2026-10-01T00:00:00-03:00',
    fim: '2026-10-13T23:59:59-03:00',
    limite: 5000,
    truncado: false,
    solicitacoes,
    materiais: materiaisDoPainel,
    periodoAnterior: [
      { contexto: 'COMERCIAL', total: 4, concluidas: 4, rejeitadas: 0, cicloMedioHoras: 24, aprovacaoMediaHoras: 2 },
      { contexto: 'TECNICO', total: 2, concluidas: 2, rejeitadas: 0, cicloMedioHoras: 72, aprovacaoMediaHoras: 4 }
    ]
  };
}

const concluida = (horas: number, p: Partial<SolicitacaoDoPainel> = {}) =>
  sol({
    status: 'ENTREGUE',
    etapa: 'CONCLUIDO',
    dataSolicitacao: '2026-10-02T08:00:00-03:00',
    dataEntrega: new Date(Date.parse('2026-10-02T08:00:00-03:00') + horas * 3_600_000).toISOString(),
    quantidadeAtendida: 10,
    ...p
  });

describe('painel de solicitações — análise', () => {
  it('classifica a situação pela etapa operacional', () => {
    expect(situacao(sol({ etapa: 'CONCLUIDO' }))).toBe('concluida');
    expect(situacao(sol({ etapa: 'REJEITADO' }))).toBe('recusada');
    expect(situacao(sol({ etapa: 'CANCELADO' }))).toBe('recusada');
    expect(situacao(sol({ etapa: 'EM_SEPARACAO' }))).toBe('aberta');
  });

  it('considera atrasada só a aberta com data prevista vencida', () => {
    const hoje = '2026-10-13';
    expect(atrasada(sol({ dataPrevista: '2026-10-10' }), hoje)).toBe(true);
    expect(diasDeAtraso(sol({ dataPrevista: '2026-10-10' }), hoje)).toBe(3);
    expect(atrasada(sol({ dataPrevista: '2026-10-13' }), hoje)).toBe(false);
    expect(atrasada(sol({ dataPrevista: '2026-10-10', etapa: 'CONCLUIDO' }), hoje)).toBe(false);
    expect(atrasada(sol({ dataPrevista: null }), hoje)).toBe(false);
  });

  it('separa período, backlog e fila aberta, filtrando pelo modo', () => {
    const d = painel([
      sol(),
      concluida(10),
      sol({ anteriorAoPeriodo: true }),
      sol({ contexto: 'TECNICO', etapa: 'EM_SEPARACAO', tipoTecnico: '221' })
    ]);
    const geral = recortar(d, 'GERAL');
    expect(geral.periodo).toHaveLength(3);
    expect(geral.backlog).toHaveLength(1);
    expect(geral.abertas).toHaveLength(3);
    expect(geral.anterior?.total).toBe(6);
    expect(geral.hoje).toBe('2026-10-13');

    const comercial = recortar(d, 'COMERCIAL');
    expect(comercial.periodo).toHaveLength(2);
    expect(comercial.anterior?.total).toBe(4);
    expect(recortar(d, 'TECNICO').abertas).toHaveLength(1);
  });

  it('calcula conclusão sem recusadas, ciclo, percentil e atendimento', () => {
    const linhas = [
      concluida(4),
      concluida(24, { quantidadeAtendida: 5 }),
      concluida(48),
      sol({ etapa: 'REJEITADO', status: 'REJEITADO' }),
      sol()
    ];
    const ind = indicadores(linhas, 5);
    expect(ind.total).toBe(5);
    expect(ind.recusadas).toBe(1);
    expect(ind.taxaConclusao).toBe(75);
    expect(ind.cicloMediana).toBe(24);
    expect(ind.cicloP90).toBe(48);
    expect(ind.atendimento).toBeCloseTo(83.3, 1);
    expect(ind.porDia).toBe(1);
    expect(cicloHoras(sol())).toBeNull();
  });

  it('mediana e formatação de tempo', () => {
    expect(mediana([])).toBeNull();
    expect(mediana([3, 1, 2])).toBe(2);
    expect(mediana([1, 2, 3, 4])).toBe(2.5);
    expect(tempo(0.5)).toBe('30 min');
    expect(tempo(6)).toBe('6h');
    expect(tempo(72)).toBe('3 dias');
    expect(tempo(null)).toBe('—');
  });

  it('monta a série diária com dias vazios e situação atual', () => {
    const s = serieDiaria([sol({ dataSolicitacao: '2026-10-02T10:00:00-03:00' }), concluida(5)], '2026-10-01T00:00:00-03:00', '2026-10-03T23:00:00-03:00');
    expect(s.dias).toEqual(['2026-10-01', '2026-10-02', '2026-10-03']);
    expect(s.abertas).toEqual([0, 1, 0]);
    expect(s.concluidas).toEqual([0, 1, 0]);
    expect(s.comercial).toEqual([0, 2, 0]);
  });

  it('distribui as abertas pelo fluxo e pela idade', () => {
    const agora = Date.parse(GERADO_EM);
    const abertas = [
      sol({ etapa: 'PENDENTE_APROVACAO', dataSolicitacao: '2026-10-13T08:00:00-03:00' }),
      sol({ etapa: 'PENDENTE_RETIRADA', dataPrevista: '2026-10-01', dataSolicitacao: '2026-09-01T08:00:00-03:00' })
    ];
    const fluxo = fluxoDasAbertas(abertas, 'COMERCIAL', '2026-10-13', agora);
    expect(fluxo.map((e) => [e.etapa, e.total, e.atrasadas])).toEqual([
      ['PENDENTE_APROVACAO', 1, 0],
      ['PENDENTE_RETIRADA', 1, 1]
    ]);
    const idade = idadeDasAbertas(abertas, '2026-10-13', agora);
    expect(idade.find((f) => f.id === 'ate-1')?.total).toBe(1);
    expect(idade.find((f) => f.id === '30+')?.atrasadas).toBe(1);
    expect(filaDeAtencao(abertas, '2026-10-13', agora)[0]).toBe(abertas[1]);
  });

  it('ranqueia por dimensão contando a fila inteira (com backlog)', () => {
    const linhas = ranking(
      [sol(), concluida(5), sol({ idEquipe: 'eq-2', equipe: 'EQ-02' })],
      [sol({ anteriorAoPeriodo: true, dataPrevista: '2026-09-01' })],
      '2026-10-13',
      (l) => l.idEquipe,
      (l) => l.equipe
    );
    expect(linhas[0]).toMatchObject({ chave: 'eq-1', total: 2, abertas: 2, atrasadas: 1, concluidas: 1 });
    expect(linhas[1]).toMatchObject({ chave: 'eq-2', total: 1 });
  });

  it('soma o mesmo material dos dois processos e conta pendências por motivo', () => {
    const lista = materiais([
      { contexto: 'COMERCIAL', sentido: 'SAIDA', codigo: 'M1', descricao: 'Cabo', unidade: 'M', solicitacoes: 3, quantidadeSolicitada: 100, quantidadeAtendida: 50 },
      { contexto: 'TECNICO', sentido: 'SAIDA', codigo: 'M1', descricao: 'Cabo', unidade: 'M', solicitacoes: 1, quantidadeSolicitada: 100, quantidadeAtendida: 100 },
      { contexto: 'TECNICO', sentido: 'DEVOLUCAO', codigo: 'M2', descricao: 'Poste', unidade: 'UN', solicitacoes: 1, quantidadeSolicitada: 1, quantidadeAtendida: 0 }
    ]);
    expect(lista).toHaveLength(1);
    expect(lista[0]).toMatchObject({ codigo: 'M1', solicitacoes: 4, quantidadeSolicitada: 200, atendimento: 75, saldo: 50 });

    const motivos = motivosDePendencia([
      sol({ possuiPendencia: true, motivosPendencia: 'Falta de saldo, Aguardando compra' }),
      sol({ possuiPendencia: true, motivosPendencia: 'Falta de saldo' })
    ]);
    expect(motivos[0]).toMatchObject({ rotulo: 'Falta de saldo', total: 2 });
  });
});

describe('painel de solicitações — visão gerencial e análises', () => {
  const linhas = [
    sol({ dataPrevista: '2026-10-08', etapa: 'PENDENTE_RETIRADA' }),
    concluida(30),
    sol({ etapa: 'REJEITADO', status: 'REJEITADO', motivoRejeicao: 'Sem saldo' }),
    sol({ contexto: 'TECNICO', tipoTecnico: '222', etapa: 'PENDENTE_DEVOLUCAO', anteriorAoPeriodo: true, dataSolicitacao: '2026-09-10T08:00:00-03:00' }),
    sol({ contexto: 'TECNICO', tipoTecnico: '221', etapa: 'PENDENTE_PLANEJAMENTO', possuiPendencia: true, motivosPendencia: 'Falta de saldo' })
  ];
  const d = painel(linhas);

  it('sem movimento devolve um veredito neutro', () => {
    const vg = visaoGerencial(recortar(painel([]), 'GERAL'), 'GERAL');
    expect(vg.veredito.tom).toBe('neutral');
    expect(vg.leituras).toEqual([]);
  });

  it('aponta atrasos, gargalo, backlog e as leituras do técnico', () => {
    const vg = visaoGerencial(recortar(d, 'GERAL'), 'GERAL');
    expect(vg.veredito.tom).toBe('danger');
    expect(vg.veredito.titulo).toContain('1 solicitação atrasada');
    const ids = vg.leituras.map((l) => l.id);
    expect(ids).toEqual(expect.arrayContaining(['gargalo', 'atrasadas', 'backlog', 'recusas', 'pendencias', 'planejamento', 'devolucoes']));
    expect(vg.pergunta).toContain('gargalos');
  });

  it('o modo comercial não traz leituras do técnico', () => {
    const ids = visaoGerencial(recortar(d, 'COMERCIAL'), 'COMERCIAL').leituras.map((l) => l.id);
    expect(ids).not.toContain('planejamento');
    expect(ids).not.toContain('devolucoes');
  });

  it('abre análises com origem que fecha com o número do painel', async () => {
    const r = recortar(d, 'GERAL');
    const ctx = { r, modo: 'GERAL' as const, inicio: d.inicio, fim: d.fim, recorteTexto: 'teste' };

    const atrasadas = vistaPainel({ dimension: 'kpi', value: 'atrasadas' }, ctx)!;
    expect(atrasadas.titulo).toBe('Atrasadas na data prevista');
    expect((await firstValueFrom(atrasadas.origem!.carregar())).total).toBe(1);

    const etapa = vistaPainel({ dimension: 'etapa', value: 'PENDENTE_PLANEJAMENTO' }, ctx)!;
    expect(etapa.anterior?.alvo.value).toBe('PENDENTE_APROVACAO');
    expect(etapa.proximo?.alvo.value).toBe('PLANEJADO');

    const equipe = vistaPainel({ dimension: 'equipe', value: 'eq-1' }, ctx)!;
    expect(equipe.titulo).toBe('EQ-01');
    expect((await firstValueFrom(equipe.origem!.carregar())).total).toBe(5);

    const detalhe = equipe.origem!.detalhar!(linhas[3], linhas);
    expect(detalhe.links?.[0].rota).toEqual(['/almoxarifado/solicitacoes-tecnicas', linhas[3].id]);

    expect(vistaPainel({ dimension: 'kpi', value: 'inexistente' }, ctx)).toBeNull();
    expect(vistaPainel({ dimension: 'equipe', value: 'nao-existe' }, ctx)).toBeNull();
  });
});
