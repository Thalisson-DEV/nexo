import { PerfilAcessoResponse as PerfilAcessoRhResponse, ImportacaoStatus } from './rh.models';

export interface PreferenciasRequest {
  tema: 'light' | 'dark';
  corPrimaria: string;
  animacoesAtivadas: boolean;
}

/**
 * Bloco somente leitura devolvido junto do usuário: identifica a pessoa dona do acesso.
 * Vem `null` quando o acesso não pertence a ninguém do cadastro de RH (contas de
 * serviço, integrações) ou quando o vínculo aponta para um colaborador removido.
 */
export interface ColaboradorVinculado {
  id: string;
  nomeCompleto: string;
  funcao: string;
  baseOperacional: string;
  /** Situação do colaborador no RH — não confundir com `UsuarioResponse.ativo` (o acesso). */
  ativo: boolean;
}

export interface UsuarioResponse {
  id: string;
  idColaborador?: string;
  colaborador?: ColaboradorVinculado | null;
  login: string;
  email?: string;
  telefone?: string;
  tipo: 'ADMINISTRATIVO' | 'CAMPO';
  perfil: PerfilAcessoRhResponse;
  tema: 'light' | 'dark';
  corPrimaria: string;
  animacoesAtivadas: boolean;
  ativo: boolean;
}

export interface UsuarioRequest {
  login: string;
  email?: string;
  telefone?: string;
  senha?: string;
  idPerfil: string;
  idColaborador?: string;
  tipo: 'ADMINISTRATIVO' | 'CAMPO';
  tema?: 'light' | 'dark';
  corPrimaria?: string;
  animacoesAtivadas?: boolean;
}

export interface UsuarioUpdateRequest {
  login?: string;
  senha?: string;
  email?: string;
  telefone?: string;
  tipo?: 'ADMINISTRATIVO' | 'CAMPO';
  idPerfil?: string;
  tema?: 'light' | 'dark';
  corPrimaria?: string;
  animacoesAtivadas?: boolean;
}

// Perfis de Acesso
export interface PermissaoResponse {
  id: string;
  nome: string;
  ativo: boolean;
}

export interface PermissaoRequest {
  nome: string;
}

export interface PerfilAcessoResponse {
  id: string;
  nome: string;
  ativo: boolean;
}

export interface PerfilAcessoRequest {
  nome: string;
}

export interface PerfilAcessoImportacaoDTO {
  linha?: number;
  nome: string;
  status?: ImportacaoStatus;
  mensagemErro?: string;
  editing?: boolean;
}

export interface ImportacaoPerfilAcessoResponse {
  registros: PerfilAcessoImportacaoDTO[];
  totalProcessados: number;
  totalSucesso: number;
  totalFalha: number;
}

export interface ConfirmarImportacaoPerfilRequest {
  perfis: PerfilAcessoImportacaoDTO[];
}

export type TipoUsuario = 'ADMINISTRATIVO' | 'CAMPO';

export interface UsuarioImportacaoDTO {
  linha: number;
  login: string;
  email: string;
  telefone: string;
  senha: string;
  nomePerfil: string;
  tipo: string;
  nomeColaborador: string;
  status: ImportacaoStatus;
  mensagemErro?: string | null;
}

export interface ImportacaoUsuarioResponse {
  registros: UsuarioImportacaoDTO[];
  totalProcessados: number;
  totalSucesso: number;
  totalFalha: number;
}

export interface ConfirmarImportacaoUsuarioRequest {
  usuarios: UsuarioImportacaoDTO[];
}

export interface FavoritoRequest {
  titulo: string;
  rota: string;
  icone?: string;
}

export interface FavoritoResponse {
  id: string;
  titulo: string;
  rota: string;
  icone?: string;
}
