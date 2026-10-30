import { DestroyRef, inject } from '@angular/core';
import { CesTone } from '@ces/ui';

import { LookupStore } from '../../../core/lookup/store/lookup.store';
import { RemoteLookup } from '../../../core/lookup/store/remote-lookup';
import { ColaboradorResponse } from '../../../core/models/rh.models';
import { dataLocal } from '../../../shared/listagem/listagem';
import { formatarCpf } from '../colaboradores/colaboradores.component';

/** Situação de uma designação com vigência (coordenador, supervisor). */
export function situacaoVigencia(v: { ativo: boolean; dataFim: string | null }): { label: string; tone: CesTone } {
  if (!v.ativo) return { label: 'Inativo', tone: 'neutral' };
  const fim = dataLocal(v.dataFim);
  if (!fim) return { label: 'Vigente', tone: 'success' };
  const hoje = new Date();
  hoje.setHours(0, 0, 0, 0);
  return fim < hoje ? { label: `Encerrado em ${fim.toLocaleDateString('pt-BR')}`, tone: 'neutral' } : { label: `Vigente até ${fim.toLocaleDateString('pt-BR')}`, tone: 'warning' };
}

/** Busca paginada de colaboradores para o autocomplete, mantendo o selecionado visível. */
export function buscaColaboradores(selecionado: () => string | null): RemoteLookup<string> {
  const lookups = inject(LookupStore);
  return new RemoteLookup<string>({
    loader: ({ query, cursor }) => lookups.searchCollaborators({ query, cursor, limit: 50 }),
    selectedValues: () => {
      const id = selecionado();
      return id ? [id] : [];
    },
    destroyRef: inject(DestroyRef)
  });
}

export function rotuloColaborador(c: Pick<ColaboradorResponse, 'id' | 'nomeCompleto'>): { value: string; label: string } {
  return { value: c.id, label: c.nomeCompleto };
}

/** "CPF 000.000.000-00 · base · função · processo" para confirmar o colaborador escolhido. */
export function resumoColaborador(c: Partial<Pick<ColaboradorResponse, 'cpf' | 'baseOperacional' | 'funcao' | 'processo'>>): string {
  return [`CPF ${formatarCpf(c.cpf)}`, c.baseOperacional?.nome || 'sem base', c.funcao?.nome || 'sem função', c.processo?.nome || 'sem processo'].join(' · ');
}
