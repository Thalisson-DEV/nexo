import { Injectable, inject, signal } from '@angular/core';
import { catchError, forkJoin, of } from 'rxjs';
import { UsuariosService } from './usuarios.service';
import { RhService } from './rh.service';
import { DatabaseService } from './database.service';

export interface UserLookupEntry {
  nome?: string;
  spl?: string;
}

@Injectable({
  providedIn: 'root'
})
export class UsuarioLookupService {
  private readonly usuariosService = inject(UsuariosService);
  private readonly rhService = inject(RhService);
  private readonly db = inject(DatabaseService);

  private readonly userMap = signal<Map<string, UserLookupEntry>>(new Map());
  private loaded = false;

  constructor() {
    this.carregar();
  }

  /**
   * Carrega os usuários do IAM e os colaboradores do RH para o cache de lookup em memória.
   */
  carregar(force = false): void {
    if (this.loaded && !force) return;

    // Tentar carregar usuários do IAM e colaboradores do RH
    forkJoin({
      usuarios: this.usuariosService.listarUsuarios({ size: 1000 }).pipe(catchError(() => of(null))),
      colaboradores: this.rhService.listarColaboradores({ size: 1000 }).pipe(catchError(() => of(null)))
    }).subscribe({
      next: ({ usuarios, colaboradores }) => {
        const nextMap = new Map(this.userMap());

        // 1. Processar Colaboradores do RH
        if (colaboradores?.content) {
          for (const colab of colaboradores.content) {
            const nome = colab.nomeCompleto || `${colab.primeiroNome || ''} ${colab.ultimoNome || ''}`.trim();
            if (nome) {
              if (colab.id) nextMap.set(colab.id, { nome });
              if (colab.idUsuario) nextMap.set(colab.idUsuario, { nome });
            }
          }
        }

        // 2. Processar Usuários do IAM
        if (usuarios?.content) {
          for (const user of usuarios.content) {
            const colabNome = user.colaborador?.nomeCompleto;
            const spl = user.login;
            const entry: UserLookupEntry = {
              nome: colabNome || nextMap.get(user.id)?.nome,
              spl
            };
            if (user.id) nextMap.set(user.id, entry);
            if (user.idColaborador) {
              const existing = nextMap.get(user.idColaborador);
              nextMap.set(user.idColaborador, { ...existing, ...entry });
            }
          }
        }

        this.userMap.set(nextMap);
        this.loaded = true;
      },
      error: () => {
        // Fallback: carregar pelo menos colaboradores do Dexie se offline
        this.carregarDoDexie();
      }
    });
  }

  private async carregarDoDexie(): Promise<void> {
    try {
      const colabs = await this.db.colaboradores.toArray();
      if (colabs.length > 0) {
        const nextMap = new Map(this.userMap());
        for (const colab of colabs) {
          const nome = colab.nomeCompleto || `${colab.primeiroNome || ''} ${colab.ultimoNome || ''}`.trim();
          if (nome) {
            if (colab.id) nextMap.set(colab.id, { nome });
            if (colab.idUsuario) nextMap.set(colab.idUsuario, { nome });
          }
        }
        this.userMap.set(nextMap);
      }
    } catch {
      // IndexedDB indisponível
    }
  }

  /**
   * Resolve um identificador de usuário ou objeto autor para o Nome do Colaborador vinculado,
   * com fallback para o USR (login) ou identificador formatado.
   */
  resolverNome(idOuAutor?: string | { id?: string; nome?: string } | null, fallback = 'Sistema'): string {
    if (!idOuAutor) return fallback;

    // Caso seja objeto autor: { id, nome }
    if (typeof idOuAutor === 'object') {
      const { id, nome } = idOuAutor;

      // 1. Tentar pelo ID no mapa
      if (id) {
        const entry = this.userMap().get(id);
        if (entry?.nome) return entry.nome;
        if (entry?.spl) return entry.spl;
      }

      // 2. Verificar se o nome fornecido é um nome real ou um placeholder 'USUARIO <uuid>'
      if (nome) {
        const isPlaceholder = /^USUARIO\s+([0-9a-f-]{20,})/i.exec(nome);
        if (isPlaceholder) {
          const extractedId = isPlaceholder[1];
          const entry = this.userMap().get(extractedId);
          if (entry?.nome) return entry.nome;
          if (entry?.spl) return entry.spl;
          return `USR-${extractedId.slice(0, 8).toUpperCase()}`;
        }
        return nome;
      }

      // 3. Fallback para ID
      if (id) {
        return `USR-${id.slice(0, 8).toUpperCase()}`;
      }
      return fallback;
    }

    // Caso seja string (ID, login ou texto)
    const str = idOuAutor.trim();

    // Checar no mapa direto
    const entry = this.userMap().get(str);
    if (entry?.nome) return entry.nome;
    if (entry?.spl) return entry.spl;

    // Se é formato 'USUARIO <uuid>'
    const isPlaceholder = /^USUARIO\s+([0-9a-f-]{20,})/i.exec(str);
    if (isPlaceholder) {
      const extractedId = isPlaceholder[1];
      const found = this.userMap().get(extractedId);
      if (found?.nome) return found.nome;
      if (found?.spl) return found.spl;
      return `USR-${extractedId.slice(0, 8).toUpperCase()}`;
    }

    // Se é UUID puro
    if (/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(str)) {
      return `USR-${str.slice(0, 8).toUpperCase()}`;
    }

    return str;
  }
}
