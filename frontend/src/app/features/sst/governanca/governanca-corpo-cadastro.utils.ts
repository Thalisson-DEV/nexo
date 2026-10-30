import {
  CriarCorpoGovernancaRequest,
  PerfilNormativoGovernanca,
  RegimeComposicaoGovernanca,
  TipoCorpoGovernanca
} from '../../../core/models/governanca-sst.models';

export const UNIDADES_FEDERATIVAS = [
  'AC', 'AL', 'AP', 'AM', 'BA', 'CE', 'DF', 'ES', 'GO', 'MA', 'MT', 'MS', 'MG',
  'PA', 'PB', 'PR', 'PE', 'PI', 'RJ', 'RN', 'RS', 'RO', 'RR', 'SC', 'SP', 'SE', 'TO'
].map((value) => ({ label: value, value }));

export const GRAUS_RISCO = [1, 2, 3, 4].map((value) => ({
  label: `Grau de risco ${value}`,
  value
}));

export const OPCOES_CONFIRMACAO = [
  { label: 'Sim, a hipótese foi confirmada', value: true },
  { label: 'Não', value: false }
];

export interface DadosCadastroCorpo {
  nome: string;
  idBaseOperacional: string | null;
  regimeComposicao: RegimeComposicaoGovernanca;
  unidadeGovernanca: string;
  uf: string | null;
  inicioPeriodo: Date | null;
  fimPeriodo: Date | null;
  grauRisco: number | null;
  idPerfilNormativo: string | null;
  hipoteseRepresentanteNomeadoConfirmada: boolean;
  idsBasesAbrangidas: string[];
}

export function dataLocal(data: Date): string {
  return `${data.getFullYear()}-${String(data.getMonth() + 1).padStart(2, '0')}-${String(data.getDate()).padStart(2, '0')}`;
}

export function fimMandatoAnual(inicio: Date): Date {
  const fim = new Date(inicio.getFullYear(), inicio.getMonth(), inicio.getDate());
  fim.setFullYear(fim.getFullYear() + 1);
  fim.setDate(fim.getDate() - 1);
  return fim;
}

export function periodoCipaAnual(inicio: Date | null, fim: Date | null): boolean {
  return !!inicio && !!fim && dataLocal(fimMandatoAnual(inicio)) === dataLocal(fim);
}

export function perfilAplicaAoCadastro(
  perfil: PerfilNormativoGovernanca,
  tipo: TipoCorpoGovernanca,
  uf: string | null,
  inicio: Date | null
): boolean {
  if (!perfil.ativo || !perfil.confirmado || perfil.tipoCorpo !== tipo || !inicio) return false;
  const data = dataLocal(inicio);
  if (data < perfil.inicioVigencia || (perfil.fimVigencia && data > perfil.fimVigencia)) return false;
  return perfil.escopo !== 'ESTADUAL' || perfil.jurisdicao?.toUpperCase() === uf?.toUpperCase();
}

export function montarCriacaoCorpo(
  tipo: TipoCorpoGovernanca,
  dados: DadosCadastroCorpo
): CriarCorpoGovernancaRequest {
  if (!dados.idBaseOperacional || !dados.uf || !dados.inicioPeriodo || !dados.fimPeriodo || !dados.grauRisco) {
    throw new Error('O cadastro do corpo está incompleto.');
  }
  return {
    tipo,
    regimeComposicao: tipo === 'CIPA' ? dados.regimeComposicao : null,
    nome: dados.nome.trim(),
    idBaseOperacional: dados.idBaseOperacional,
    unidadeGovernanca: dados.unidadeGovernanca.trim(),
    uf: dados.uf,
    inicioPeriodo: dataLocal(dados.inicioPeriodo),
    fimPeriodo: dataLocal(dados.fimPeriodo),
    grauRisco: dados.grauRisco,
    idPerfilNormativo: dados.idPerfilNormativo,
    hipoteseRepresentanteNomeadoConfirmada:
      tipo === 'CIPA' && dados.regimeComposicao === 'REPRESENTANTE_NOMEADO'
        ? dados.hipoteseRepresentanteNomeadoConfirmada
        : false,
    idsBasesAbrangidas: dados.idsBasesAbrangidas.filter(
      (id) => id !== dados.idBaseOperacional
    )
  };
}
