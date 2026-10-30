import { CesTone } from '@ces/ui';
import { TipoVinculoSubprojeto } from '../../core/models/obras.models';

/**
 * Normaliza o número da obra de acordo com as invariantes de negócio:
 * - Remove espaços em branco nas extremidades.
 * - Remove prefixos 'B-' ou 'B' quando seguidos apenas por números (ex.: 'B-1228694' vira '1228694').
 * - Preserva zeros à esquerda (ex.: '0994094').
 * - Converte caracteres alfanuméricos para caixa alta.
 * - Limita ao tamanho máximo de 7 caracteres.
 */
export function sanitizarNumeroObra(raw?: string | null): string {
  if (!raw) return '';
  let limpo = raw.trim();

  // Remove B- ou B inicial se seguido somente de dígitos
  if (/^b-?\d+$/i.test(limpo)) {
    limpo = limpo.replace(/^b-?/i, '');
  }

  return limpo.toUpperCase().slice(0, 7);
}

/**
 * Sanitiza o título da obra:
 * - Converte para CAIXA ALTA.
 * - Reduz múltiplos espaços em branco consecutivos para um único espaço.
 * - Remove espaços das extremidades.
 * - Limita ao máximo de 300 caracteres.
 */
export function sanitizarTituloObra(raw?: string | null): string {
  if (!raw) return '';
  return raw
    .trim()
    .toUpperCase()
    .replace(/\s+/g, ' ')
    .slice(0, 300);
}

/**
 * Retorna o tom semântico do Design System (CesTone) para o status da obra.
 */
export function statusTone(statusNome?: string | null, concluida?: boolean): CesTone {
  const norm = (statusNome ?? '').toUpperCase().trim();

  if (norm.includes('CONCLU')) return 'success';
  if (norm.includes('CANCEL')) return 'danger';
  if (norm.includes('PARALIS') || norm.includes('AGUARD')) return 'warning';
  if (norm.includes('EXECU')) return 'info';
  if (norm.includes('PLANEJ')) return 'neutral';

  return concluida ? 'success' : 'neutral';
}

/**
 * Retorna classes CSS de estilização de badge para o status da obra.
 */
export function statusClass(statusNome?: string | null, concluida?: boolean): string {
  const norm = (statusNome ?? '').toUpperCase().trim();

  if (norm.includes('CONCLU')) {
    return 'bg-green-50 text-green-600 dark:bg-green-950/40 dark:text-green-300 border border-green-200/60 dark:border-green-800/60';
  }
  if (norm.includes('CANCEL')) {
    return 'bg-red-50 text-red-600 dark:bg-red-950/40 dark:text-red-300 border border-red-200/60 dark:border-red-800/60';
  }
  if (norm.includes('PARALIS') || norm.includes('AGUARD')) {
    return 'bg-amber-50 text-amber-600 dark:bg-amber-950/40 dark:text-amber-300 border border-amber-200/60 dark:border-amber-800/60';
  }
  if (norm.includes('EXECU')) {
    return 'bg-blue-50 text-blue-600 dark:bg-blue-950/40 dark:text-blue-300 border border-blue-200/60 dark:border-blue-800/60';
  }

  return 'bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300 border border-slate-200/60 dark:border-slate-700/60';
}

/**
 * Ícone correspondente ao status da obra.
 */
export function statusIcon(statusNome?: string | null): string {
  const norm = (statusNome ?? '').toUpperCase().trim();

  if (norm.includes('CONCLU')) return 'pi pi-check-circle';
  if (norm.includes('CANCEL')) return 'pi pi-times-circle';
  if (norm.includes('PARALIS')) return 'pi pi-pause-circle';
  if (norm.includes('EXECU')) return 'pi pi-spin pi-spinner';
  if (norm.includes('PLANEJ')) return 'pi pi-calendar';

  return 'pi pi-circle';
}

/**
 * Formata um valor numérico para Moeda Brasileira (R$).
 */
export function formatarMoeda(valor?: number | null): string {
  if (valor === undefined || valor === null || isNaN(valor)) {
    return 'R$ 0,00';
  }
  return valor.toLocaleString('pt-BR', {
    style: 'currency',
    currency: 'BRL',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  });
}

/**
 * Formata bytes para exibição legível (ex.: 4.5 MB, 320 KB).
 */
export function formatarBytes(bytes?: number | null): string {
  if (!bytes || bytes <= 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  const valor = parseFloat((bytes / Math.pow(k, i)).toFixed(2));
  return `${valor} ${sizes[i]}`;
}

/**
 * Converte data para o formato de API 'dd/MM/yyyy'.
 */
export function formatarDataParaApi(data?: Date | null): string | undefined {
  if (!data || isNaN(data.getTime())) return undefined;
  const dia = String(data.getDate()).padStart(2, '0');
  const mes = String(data.getMonth() + 1).padStart(2, '0');
  const ano = data.getFullYear();
  return `${dia}/${mes}/${ano}`;
}

/**
 * Converte string 'dd/MM/yyyy' em objeto Date do JavaScript.
 */
export function parseDataBrParaDate(str?: string | null): Date | null {
  if (!str) return null;
  const partes = str.trim().split('/');
  if (partes.length !== 3) return null;
  const dia = parseInt(partes[0], 10);
  const mes = parseInt(partes[1], 10) - 1;
  const ano = parseInt(partes[2], 10);
  const d = new Date(ano, mes, dia);
  return isNaN(d.getTime()) ? null : d;
}

/**
 * Formata data/hora para exibição legível 'dd/MM/yyyy HH:mm'.
 * Trata strings ISO (ex.: '2026-08-21T14:21:17.198446Z'), instâncias Date ou strings pt-BR.
 */
export function formatarDataHora(data?: string | Date | null): string {
  if (!data) return '—';

  let d: Date;
  if (data instanceof Date) {
    d = data;
  } else if (typeof data === 'string') {
    // Se for string no padrão ISO ou com fuso
    if (data.includes('T') || data.includes('-')) {
      d = new Date(data);
    } else if (/^\d{2}\/\d{2}\/\d{4}/.test(data)) {
      // Já é uma data brasileira
      return data;
    } else {
      d = new Date(data);
    }
  } else {
    return '—';
  }

  if (isNaN(d.getTime())) return typeof data === 'string' ? data : '—';

  const dia = String(d.getDate()).padStart(2, '0');
  const mes = String(d.getMonth() + 1).padStart(2, '0');
  const ano = d.getFullYear();
  const hora = String(d.getHours()).padStart(2, '0');
  const min = String(d.getMinutes()).padStart(2, '0');
  return `${dia}/${mes}/${ano} ${hora}:${min}`;
}

/**
 * Opções de vínculo de subprojeto.
 */
export const TIPO_VINCULO_OPTIONS: { label: string; value: TipoVinculoSubprojeto; descricao: string }[] = [
  {
    label: 'KIT',
    value: TipoVinculoSubprojeto.KIT,
    descricao: 'Subprojeto derivado de instalação de kit padrão da concessionária'
  },
  {
    label: 'MEDIDOR',
    value: TipoVinculoSubprojeto.MEDIDOR,
    descricao: 'Subprojeto específico de medidor'
  }
];

/**
 * Template CSV de exemplo para download de importação em lote (Layout Oficial de 18 Colunas).
 */
export const CSV_TEMPLATE_OBRAS =
  'Numero Obra;Titulo;Nota Obra;Descricao;Data Inicio;Data Previsao Fim;Valor Projeto;Processo;Centro Custo;Base Operacional;Municipio;Supervisor;Coordenador;Latitude;Longitude;Status Obra;Agente Responsavel;Codigos Equipes\n' +
  '1228694;REDE PEDAGOGICA REMANSO (CONCESSIONARIA);12345;Obra de ampliacao de rede;03/11/2025;30/11/2025;150000.00;Obras;CC-01;Remanso;Remanso;JOAO SILVA PEREIRA;CARLOS EDUARDO OLIVEIRA;-9.439007;-41.968228;PLANEJADA;MARLOS PEREIRA;EQ-01,EQ-02\n';

