import { Injectable, signal } from '@angular/core';
import { TipoCorpoGovernanca } from '../../../core/models/governanca-sst.models';

export type GovernancaSecao =
  | 'painel'
  | 'composicoes'
  | 'reunioes'
  | 'documentos'
  | 'treinamentos'
  | 'acoes'
  | 'configuracoes';

export const GOVERNANCA_ABAS: ReadonlyArray<{
  id: GovernancaSecao;
  label: string;
  descricao: string;
  icon: string;
}> = [
  { id: 'painel', label: 'Visão geral', descricao: 'Pendências e indicadores', icon: 'pi pi-home' },
  {
    id: 'composicoes',
    label: 'Composição e equipe',
    descricao: 'Mandatos e participantes',
    icon: 'pi pi-users'
  },
  {
    id: 'reunioes',
    label: 'Reuniões e atas',
    descricao: 'Agenda, presença e registros',
    icon: 'pi pi-calendar'
  },
  { id: 'documentos', label: 'Documentos', descricao: 'Arquivos e vencimentos', icon: 'pi pi-folder' },
  {
    id: 'treinamentos',
    label: 'Treinamentos',
    descricao: 'Exigências e certificados',
    icon: 'pi pi-graduation-cap'
  },
  {
    id: 'acoes',
    label: 'Inspeções e ações',
    descricao: 'Planos e acompanhamentos',
    icon: 'pi pi-check-square'
  },
  {
    id: 'configuracoes',
    label: 'Configurações',
    descricao: 'Modelos e regras de uso',
    icon: 'pi pi-cog'
  }
];

const CHAVE_CONTEXTO = 'sst-governanca-contexto';

export function normalizarTipoGovernanca(valor?: string | null): TipoCorpoGovernanca | null {
  return valor === 'CIPA' || valor === 'BRIGADA' ? valor : null;
}

@Injectable({ providedIn: 'root' })
export class GovernancaContextoState {
  readonly tipo = signal<TipoCorpoGovernanca>(this.lerTipoSalvo());

  selecionar(tipo: TipoCorpoGovernanca): void {
    this.tipo.set(tipo);
    try {
      sessionStorage.setItem(CHAVE_CONTEXTO, tipo);
    } catch {
      // O contexto continua válido durante a navegação mesmo sem armazenamento disponível.
    }
  }

  private lerTipoSalvo(): TipoCorpoGovernanca {
    try {
      return normalizarTipoGovernanca(sessionStorage.getItem(CHAVE_CONTEXTO)) ?? 'CIPA';
    } catch {
      return 'CIPA';
    }
  }
}
