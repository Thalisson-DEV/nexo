import {
  CorpoGovernancaResumo,
  DadosMembroCorpoGovernanca,
  PapelMembroGovernanca,
  RepresentacaoCipa,
  TipoCorpoGovernanca,
  TitularidadeCipa
} from '../../../core/models/governanca-sst.models';

export interface FormularioMembroGovernanca {
  idColaborador: string | null;
  papel: PapelMembroGovernanca | null;
  representacao: RepresentacaoCipa | null;
  titularidade: TitularidadeCipa | null;
  cicloEleitoral: string;
  classificacaoEleitoral: number | null;
  atoDesignacao: string;
  reeleito: boolean;
  inicioParticipacao: Date | null;
  turnoDisponivel: string;
}

export const PAPEIS_CIPA: Array<{ label: string; value: PapelMembroGovernanca }> = [
  { label: 'Presidente', value: 'PRESIDENTE' },
  { label: 'Vice-presidente', value: 'VICE_PRESIDENTE' },
  { label: 'Membro', value: 'MEMBRO' }
];

export const PAPEIS_BRIGADA: Array<{ label: string; value: PapelMembroGovernanca }> = [
  { label: 'Coordenador geral', value: 'COORDENADOR_GERAL' },
  { label: 'Chefe', value: 'CHEFE' },
  { label: 'Líder', value: 'LIDER' },
  { label: 'Brigadista', value: 'BRIGADISTA' }
];

export function dataLocal(data: Date): string {
  return `${data.getFullYear()}-${String(data.getMonth() + 1).padStart(2, '0')}-${String(data.getDate()).padStart(2, '0')}`;
}

export function dataInicialSugerida(corpo: CorpoGovernancaResumo, hoje = new Date()): Date {
  const inicio = new Date(`${corpo.inicioPeriodo}T12:00:00`);
  const fim = new Date(`${corpo.fimPeriodo}T12:00:00`);
  const referencia = new Date(hoje.getFullYear(), hoje.getMonth(), hoje.getDate(), 12);
  if (referencia < inicio) return inicio;
  if (referencia > fim) return fim;
  return referencia;
}

export function montarDadosMembro(
  tipo: TipoCorpoGovernanca,
  corpo: CorpoGovernancaResumo,
  formulario: FormularioMembroGovernanca
): DadosMembroCorpoGovernanca {
  const representanteNomeado = corpo.regimeComposicao === 'REPRESENTANTE_NOMEADO';
  const representacao = representanteNomeado ? 'EMPREGADOS' : formulario.representacao;
  const origem = representanteNomeado
    ? 'DESIGNADO'
    : representacao === 'EMPREGADOR'
      ? 'DESIGNADO'
      : representacao === 'EMPREGADOS'
        ? 'ELEITO'
        : null;
  const origemEfetiva = tipo === 'CIPA' ? origem : null;

  return {
    idColaborador: formulario.idColaborador!,
    papel: representanteNomeado ? 'REPRESENTANTE_NOMEADO' : formulario.papel!,
    representacao: tipo === 'CIPA' ? representacao : null,
    titularidade: tipo === 'CIPA' && !representanteNomeado ? formulario.titularidade : null,
    origem: origemEfetiva,
    cicloEleitoral: origemEfetiva === 'ELEITO' ? formulario.cicloEleitoral.trim() || null : null,
    classificacaoEleitoral: origemEfetiva === 'ELEITO' ? formulario.classificacaoEleitoral : null,
    atoDesignacao: origemEfetiva === 'DESIGNADO' ? formulario.atoDesignacao.trim() || null : null,
    reeleito: origemEfetiva === 'ELEITO' && formulario.reeleito,
    inicioParticipacao: dataLocal(formulario.inicioParticipacao!),
    turnoDisponivel: formulario.turnoDisponivel.trim() || null
  };
}
