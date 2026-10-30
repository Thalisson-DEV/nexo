/**
 * Contratos do módulo de frota (backend `/v1/frota`). Decisões de domínio em
 * `docs/adr/0001-modulo-gestao-frota.md` na raiz do monorepo.
 */
import type { CesTone } from '@ces/ui';

export type SituacaoOperacional = 'ATIVO' | 'EM_MANUTENCAO' | 'PARADO' | 'RESERVA' | 'BAIXADO';
export type RegimeVeiculo = 'PROPRIO' | 'LOCADO';
export type Combustivel = 'DIESEL_S10' | 'DIESEL_S500' | 'GASOLINA' | 'ETANOL' | 'FLEX' | 'GNV' | 'ELETRICO';
export type ProvedorTelemetria = 'UBICALY' | 'ZUQ';
export type CategoriaCnh = 'A' | 'B' | 'C' | 'D' | 'E' | 'AB' | 'AC' | 'AD' | 'AE';
export type SituacaoVencimento = 'SEM_VENCIMENTO' | 'VIGENTE' | 'A_VENCER' | 'VENCIDO';
export type TipoDocumentoFrota =
  | 'CRLV'
  | 'LICENCIAMENTO'
  | 'IPVA'
  | 'SEGURO'
  | 'TACOGRAFO'
  | 'ANTT'
  | 'INSPECAO_VEICULAR'
  | 'LAUDO_IMPLEMENTO'
  | 'CONTRATO_LOCACAO'
  | 'OUTRO';
export type OrigemLeituraOdometro = 'MANUAL' | 'CHECKLIST' | 'ABASTECIMENTO' | 'MANUTENCAO' | 'TELEMETRIA' | 'IMPORTACAO';
export type SituacaoLeituraOdometro = 'VALIDA' | 'INCONSISTENTE' | 'ANULADA';
export type OrigemVinculoCondutor = 'MANUAL' | 'CHECKLIST' | 'TURNO' | 'IMPORTACAO';

export interface OpcaoFrota<T extends string = string> {
  valor: T;
  rotulo: string;
}

export interface OpcoesFrota {
  situacoes: OpcaoFrota<SituacaoOperacional>[];
  regimes: OpcaoFrota<RegimeVeiculo>[];
  combustiveis: OpcaoFrota<Combustivel>[];
  provedoresTelemetria: OpcaoFrota<ProvedorTelemetria>[];
  categoriasCnh: OpcaoFrota<CategoriaCnh>[];
  tiposDocumento: OpcaoFrota<TipoDocumentoFrota>[];
  origensOdometro: OpcaoFrota<OrigemLeituraOdometro>[];
}

export interface ParametrosFrota {
  faixasAlertaDias: number[];
  limiteKmDia: number;
}

export interface TipoVeiculo {
  id: string;
  nome: string;
  descricao: string | null;
  categoriaCnhMinima: CategoriaCnh | null;
  ativo: boolean;
}

export interface TipoVeiculoRequest {
  nome: string;
  descricao: string | null;
  categoriaCnhMinima: CategoriaCnh | null;
}

export interface TipoImplemento {
  id: string;
  nome: string;
  descricao: string | null;
  ativo: boolean;
}

export interface TipoImplementoRequest {
  nome: string;
  descricao: string | null;
}

export interface FiltroVeiculosFrota {
  busca?: string | null;
  idsBase?: string[];
  idsTipoVeiculo?: string[];
  situacoes?: SituacaoOperacional[];
  regime?: RegimeVeiculo | null;
  ativo?: boolean | null;
}

export interface VeiculoFrotaResumo {
  idVeiculo: string;
  placa: string;
  modelo: string | null;
  fabricante: string | null;
  idBaseOperacional: string | null;
  baseOperacional: string | null;
  idTipoVeiculo: string | null;
  tipoVeiculo: string | null;
  situacao: SituacaoOperacional;
  regime: RegimeVeiculo;
  combustivel: Combustivel | null;
  anoModelo: number | null;
  kmAtual: number | null;
  kmAtualEm: string | null;
  idCondutorAtual: string | null;
  condutorAtual: string | null;
  implementosAcoplados: string[];
  documentosVencidos: number;
  documentosAVencer: number;
  leiturasInconsistentes: number;
  ativo: boolean;
}

export interface VeiculoFrotaRequest {
  placa: string;
  modelo: string;
  fabricante: string;
  idBaseOperacional: string;
  idTipoVeiculo: string | null;
  anoFabricacao: number | null;
  anoModelo: number | null;
  chassi: string | null;
  renavam: string | null;
  cor: string | null;
  combustivel: Combustivel | null;
  capacidadeTanqueLitros: number | null;
  consumoReferenciaKmL: number | null;
  situacao: SituacaoOperacional | null;
  regime: RegimeVeiculo | null;
  locadora: string | null;
  custoMensalLocacao: number | null;
  inicioLocacao: string | null;
  fimLocacao: string | null;
  provedorTelemetria: ProvedorTelemetria | null;
  idRastreador: string | null;
  observacao: string | null;
}

export interface VinculoCondutor {
  id: string;
  idVeiculo: string;
  placa: string | null;
  idColaborador: string;
  condutor: string | null;
  inicio: string;
  fim: string | null;
  origem: OrigemVinculoCondutor;
  observacao: string | null;
}

export interface AcoplamentoImplemento {
  id: string;
  idImplemento: string;
  identificacao: string;
  tipoImplemento: string;
  idVeiculo: string;
  placa: string | null;
  inicio: string;
  fim: string | null;
  observacao: string | null;
}

export interface VeiculoFrotaDetalhe {
  idVeiculo: string;
  placa: string;
  modelo: string | null;
  fabricante: string | null;
  idBaseOperacional: string | null;
  baseOperacional: string | null;
  tipoVeiculo: TipoVeiculo | null;
  anoFabricacao: number | null;
  anoModelo: number | null;
  chassi: string | null;
  renavam: string | null;
  cor: string | null;
  combustivel: Combustivel | null;
  capacidadeTanqueLitros: number | null;
  consumoReferenciaKmL: number | null;
  situacao: SituacaoOperacional;
  regime: RegimeVeiculo;
  locadora: string | null;
  custoMensalLocacao: number | null;
  inicioLocacao: string | null;
  fimLocacao: string | null;
  provedorTelemetria: ProvedorTelemetria | null;
  idRastreador: string | null;
  observacao: string | null;
  kmAtual: number | null;
  kmAtualEm: string | null;
  condutorAtual: VinculoCondutor | null;
  implementosAcoplados: AcoplamentoImplemento[];
  documentosVencidos: number;
  documentosAVencer: number;
  leiturasInconsistentes: number;
  ativo: boolean;
}

export interface VincularCondutorRequest {
  idColaborador: string;
  inicio: string;
  observacao: string | null;
}

export interface FiltroImplementos {
  busca?: string | null;
  idsBase?: string[];
  idsTipoImplemento?: string[];
  situacoes?: SituacaoOperacional[];
  idVeiculo?: string | null;
  acoplado?: boolean | null;
  ativo?: boolean | null;
}

export interface Implemento {
  id: string;
  identificacao: string;
  idTipoImplemento: string;
  tipoImplemento: string;
  fabricante: string | null;
  modelo: string | null;
  numeroSerie: string | null;
  anoFabricacao: number | null;
  idBaseOperacional: string;
  baseOperacional: string | null;
  situacao: SituacaoOperacional;
  observacao: string | null;
  acoplamentoAtual: AcoplamentoImplemento | null;
  ativo: boolean;
}

export interface ImplementoRequest {
  identificacao: string;
  idTipoImplemento: string;
  fabricante: string | null;
  modelo: string | null;
  numeroSerie: string | null;
  anoFabricacao: number | null;
  idBaseOperacional: string;
  situacao: SituacaoOperacional | null;
  observacao: string | null;
}

export interface AcoplarImplementoRequest {
  idVeiculo: string;
  inicio: string;
  observacao: string | null;
}

export interface FiltroCondutores {
  busca?: string | null;
  idsBase?: string[];
  categorias?: CategoriaCnh[];
  situacaoCnh?: SituacaoVencimento | null;
  ativo?: boolean | null;
}

export interface Condutor {
  id: string;
  idColaborador: string;
  nome: string | null;
  funcao: string | null;
  baseOperacional: string | null;
  cnhNumeroMascarado: string | null;
  cnhCategoria: CategoriaCnh | null;
  cnhValidade: string | null;
  cnhPrimeiraHabilitacao: string | null;
  situacaoCnh: SituacaoVencimento;
  diasParaVencerCnh: number | null;
  idVeiculoAtual: string | null;
  placaAtual: string | null;
  conduzDesde: string | null;
  observacao: string | null;
  ativo: boolean;
}

export interface CondutorRequest {
  cnhNumero: string;
  cnhCategoria: CategoriaCnh;
  cnhValidade: string;
  cnhPrimeiraHabilitacao: string | null;
  observacao: string | null;
}

export interface FiltroDocumentosFrota {
  busca?: string | null;
  idVeiculo?: string | null;
  idImplemento?: string | null;
  idsBase?: string[];
  tipos?: TipoDocumentoFrota[];
  situacao?: SituacaoVencimento | null;
  vencimentoAte?: string | null;
}

export interface DocumentoFrota {
  id: string;
  idVeiculo: string | null;
  placa: string | null;
  idImplemento: string | null;
  implemento: string | null;
  tipo: TipoDocumentoFrota;
  tipoRotulo: string;
  descricao: string | null;
  numero: string | null;
  dataEmissao: string | null;
  dataVencimento: string | null;
  situacaoVencimento: SituacaoVencimento;
  diasParaVencer: number | null;
  observacao: string | null;
  possuiArquivo: boolean;
  arquivoNome: string | null;
  arquivoContentType: string | null;
  arquivoTamanhoBytes: number | null;
}

export interface DocumentoFrotaRequest {
  idVeiculo: string | null;
  idImplemento: string | null;
  tipo: TipoDocumentoFrota;
  descricao: string | null;
  numero: string | null;
  dataEmissao: string | null;
  dataVencimento: string | null;
  observacao: string | null;
}

export interface FiltroLeiturasOdometro {
  idVeiculo?: string | null;
  idsBase?: string[];
  situacoes?: SituacaoLeituraOdometro[];
  origens?: OrigemLeituraOdometro[];
}

export interface LeituraOdometro {
  id: string;
  idVeiculo: string;
  placa: string | null;
  km: number;
  lidoEm: string;
  origem: OrigemLeituraOdometro;
  idOrigem: string | null;
  situacao: SituacaoLeituraOdometro;
  motivoSituacao: string | null;
  kmAnterior: number | null;
  registradoPor: string | null;
  dataAnulacao: string | null;
  justificativaAnulacao: string | null;
}

export interface RegistrarLeituraRequest {
  km: number;
  lidoEm: string;
}

// ── Apresentação ───────────────────────────────────────────────────────────────

export const TOM_SITUACAO: Record<SituacaoOperacional, CesTone> = {
  ATIVO: 'success',
  EM_MANUTENCAO: 'warning',
  PARADO: 'danger',
  RESERVA: 'info',
  BAIXADO: 'neutral'
};

export const ROTULO_SITUACAO: Record<SituacaoOperacional, string> = {
  ATIVO: 'Ativo',
  EM_MANUTENCAO: 'Em manutenção',
  PARADO: 'Parado',
  RESERVA: 'Reserva',
  BAIXADO: 'Baixado'
};

export const ROTULO_REGIME: Record<RegimeVeiculo, string> = {
  PROPRIO: 'Próprio',
  LOCADO: 'Locado'
};

export const TOM_VENCIMENTO: Record<SituacaoVencimento, CesTone> = {
  SEM_VENCIMENTO: 'neutral',
  VIGENTE: 'success',
  A_VENCER: 'warning',
  VENCIDO: 'danger'
};

export const ROTULO_VENCIMENTO: Record<SituacaoVencimento, string> = {
  SEM_VENCIMENTO: 'Sem vencimento',
  VIGENTE: 'Vigente',
  A_VENCER: 'A vencer',
  VENCIDO: 'Vencido'
};

export const TOM_LEITURA: Record<SituacaoLeituraOdometro, CesTone> = {
  VALIDA: 'success',
  INCONSISTENTE: 'warning',
  ANULADA: 'neutral'
};

export const ROTULO_LEITURA: Record<SituacaoLeituraOdometro, string> = {
  VALIDA: 'Válida',
  INCONSISTENTE: 'Inconsistente',
  ANULADA: 'Anulada'
};

export const ROTULO_ORIGEM_LEITURA: Record<OrigemLeituraOdometro, string> = {
  MANUAL: 'Lançamento manual',
  CHECKLIST: 'Checklist',
  ABASTECIMENTO: 'Abastecimento',
  MANUTENCAO: 'Manutenção',
  TELEMETRIA: 'Telemetria',
  IMPORTACAO: 'Importação'
};

/** Frase curta do prazo de um vencimento: "vence hoje", "vence em 3 dias", "venceu há 2 dias". */
export function descreverPrazo(dias: number | null | undefined): string {
  if (dias === null || dias === undefined) return 'Sem vencimento';
  if (dias === 0) return 'Vence hoje';
  if (dias > 0) return `Vence em ${dias} ${dias === 1 ? 'dia' : 'dias'}`;
  const atraso = -dias;
  return `Venceu há ${atraso} ${atraso === 1 ? 'dia' : 'dias'}`;
}

/** Data local (`Date`) → `yyyy-MM-dd`, sem deslocar pelo fuso. */
export function paraDataIso(data: Date | null | undefined): string | null {
  if (!data) return null;
  const ano = data.getFullYear();
  const mes = String(data.getMonth() + 1).padStart(2, '0');
  const dia = String(data.getDate()).padStart(2, '0');
  return `${ano}-${mes}-${dia}`;
}

/** `yyyy-MM-dd` → `Date` local à meia-noite. */
export function deDataIso(valor: string | null | undefined): Date | null {
  if (!valor) return null;
  const [ano, mes, dia] = valor.substring(0, 10).split('-').map(Number);
  return new Date(ano, mes - 1, dia);
}

/** Instante local → ISO 8601 com o deslocamento do navegador (ex.: `2026-10-09T14:30:00-03:00`). */
export function paraInstanteIso(data: Date): string {
  const deslocamento = -data.getTimezoneOffset();
  const sinal = deslocamento >= 0 ? '+' : '-';
  const abs = Math.abs(deslocamento);
  const pad = (n: number) => String(n).padStart(2, '0');
  return (
    `${data.getFullYear()}-${pad(data.getMonth() + 1)}-${pad(data.getDate())}` +
    `T${pad(data.getHours())}:${pad(data.getMinutes())}:00` +
    `${sinal}${pad(Math.floor(abs / 60))}:${pad(abs % 60)}`
  );
}

/** Placa no formato de exibição: AAA-1234 ou AAA1A23 (Mercosul). */
export function formatarPlaca(placa: string | null | undefined): string {
  if (!placa) return '';
  const limpa = placa.toUpperCase().replace(/[^A-Z0-9]/g, '');
  return /^[A-Z]{3}\d{4}$/.test(limpa) ? `${limpa.slice(0, 3)}-${limpa.slice(3)}` : limpa;
}

/**
 * Número com unidade em pt-BR (`12.345 km`, `1,5 km/l`). Valor ausente vira o texto de
 * `vazio`, para a tela nunca mostrar "null km".
 */
export function formatarMedida(
  valor: number | null | undefined,
  unidade: string,
  casas = 0,
  vazio = '—'
): string {
  if (valor === null || valor === undefined || Number.isNaN(valor)) return vazio;
  const numero = valor.toLocaleString('pt-BR', { maximumFractionDigits: casas });
  return unidade ? `${numero} ${unidade}` : numero;
}

/** "1 veículo", "3 veículos", "Nenhum veículo" — contagem com o plural certo. */
export function contar(quantidade: number, singular: string, plural: string, nenhum?: string): string {
  if (quantidade === 0 && nenhum) return nenhum;
  return `${quantidade.toLocaleString('pt-BR')} ${quantidade === 1 ? singular : plural}`;
}
