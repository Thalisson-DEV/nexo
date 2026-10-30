# PLANNING.md — Plano de Desenvolvimento: Nexo Field App

> **Objetivo:** Desenvolver o `ces-field-app`, uma PWA Angular 20 offline-first para tablets, consumida por encarregados de equipes de campo da empresa. Este documento guia o desenvolvimento em sprints sequenciais e incrementais.
> **Contexto de Agente:** `GEMINI.md` (leia antes de qualquer tarefa)
> **Documentação da API:** `/docs/` (consulte sempre antes de integrar endpoints)

---

## 📊 Visão Geral das Fases

```
FASE 0 — Fundação (Setup)
FASE 1 — Sprint 1: Shell, Auth e Offline Core
FASE 2 — Sprint 2: Sync Inicial e Dashboard
FASE 3 — Sprint 3: Módulo de Solicitações
FASE 4 — Sprint 4: Módulo de Campo
FASE 5 — Sprint 5: Perfil, Configurações e Polimento
FASE 6 — Sprint 6: Hardening, Testes e PWA Production
```

**Critério de "Pronto" global:** Uma sprint só é concluída quando o código compila sem erros (`ng build`), passa no linter (`ng lint`) e as funcionalidades descritas funcionam em modo offline no browser (Chrome DevTools → Network → Offline).

---

## 🏁 FASE 0 — Fundação e Setup do Projeto

**Objetivo:** Criar o projeto Angular com todas as dependências, estrutura de pastas e configurações base. Nenhuma feature de negócio aqui — apenas a base técnica perfeita.

### Tarefas

#### F0.1 — Criação do Projeto
- [ ] `ng new ces-field-app --routing --style=scss --strict --standalone`
- [ ] Configurar `tsconfig.json` para `strict: true`, `target: ES2022`.
- [ ] Adicionar `@angular/pwa`: `ng add @angular/pwa`
- [ ] Instalar dependências:
  ```bash
  npm install dexie jwt-decode
  npm install -D @types/dexie
  ```
- [ ] Instalar Tailwind CSS e configurar `tailwind.config.js`.
- [ ] Criar a pasta `/docs/` na raiz (placeholder para documentação da API).

#### F0.2 — Estrutura de Diretórios
- [ ] Criar a estrutura completa de pastas conforme definida no `GEMINI.md` Seção 4.
- [ ] Criar arquivos `index.ts` (barrel exports) em cada pasta `shared/components`, `core/auth`, `core/sync`, `core/db`.

#### F0.3 — Design Tokens e Estilos Globais
- [ ] Criar `src/styles/tokens.scss` com CSS Custom Properties:
  - Cores (primária, acento, superfície, sucesso, erro, aviso).
  - Tipografia (escalas de tamanho, pesos, famílias).
  - Espaçamento (grid 8px).
  - Border radius, shadows, z-index.
- [ ] Criar `src/styles/typography.scss` com classes de tipografia reutilizáveis.
- [ ] Criar `src/styles/animations.scss` com transições de tela, fade-in, slide-up.
- [ ] Configurar `angular.json` para incluir esses arquivos globais.
- [ ] Aplicar light mode como padrão via `prefers-color-scheme` e classe CSS.

#### F0.4 — Configuração de Ambientes
- [ ] `environment.ts`:
  ```typescript
  export const environment = {
    production: false,
    apiBaseUrl: 'http://localhost:8080/api',
    dbVersion: 1,
    dbName: 'ces-field-db',
  };
  ```
- [ ] `environment.prod.ts` com URL de produção.

#### F0.5 — PWA Manifest e Service Worker
- [ ] Configurar `manifest.webmanifest`:
  - `name: "Nexo Field"`, `short_name: "Nexo"`.
  - `display: "standalone"`, `orientation: "portrait"`.
  - Ícones nos tamanhos 192x192 e 512x512 (gerar placeholders).
  - `theme_color` e `background_color` com as cores do design system.
- [ ] Configurar `ngsw-config.json` com estratégias de cache para assets e grupos de dados estáticos.

**Entregável F0:** Projeto Angular rodando (`ng serve`), estrutura de pastas correta, tokens de design aplicados, manifest PWA configurado, build sem erros.

---

## 🔐 SPRINT 1 — Shell do App, Autenticação e Core Offline

**Duração estimada:** 5–7 dias
**Objetivo:** Usuário consegue fazer login, ter o token gerenciado com segurança e o app funcionar como shell navegável.

### S1.1 — Schema do IndexedDB (Dexie)
- [ ] Implementar `src/app/core/db/app-db.ts`:
  - Definir todas as tabelas conforme `GEMINI.md` Seção 6.2.
  - Versionar o schema (v1 inicial).
  - Criar interfaces TypeScript para cada tabela (`EquipeLocal`, `ColaboradorLocal`, etc.).
- [ ] Implementar `DbService` como facade tipado com métodos CRUD para cada tabela.

**Atenção:** Verifique os campos necessários consultando os response DTOs em `/docs/rh.md` e `/docs/organizacional.md` para saber o que persistir localmente.

### S1.2 — ConnectivityService
- [ ] Implementar `ConnectivityService`:
  - `isOnline = signal(navigator.onLine)`.
  - Registrar listeners para eventos `window.addEventListener('online' | 'offline')`.
  - Atualizar o signal nos eventos.

### S1.3 — TokenService e AuthService
- [ ] Implementar `TokenService`:
  - Token em memória via `signal<string | null>(null)`.
  - `setToken()`, `clearToken()`, `getDecodedPayload()` (usando `jwt-decode`).
  - Extrair do payload: `sub` (ID do usuário), `nome`, `equipeId`, roles.
- [ ] Implementar `AuthService`:
  - `login(credentials: LoginRequestDto): Observable<void>` — chama `POST /v1/auth/login` (ver `/docs/iam.md`).
  - `logout(): void` — limpa token, limpa IndexedDB opcional, navega para `/login`.
  - `currentUser: Signal<UsuarioLogado | null>` — computado do token decodificado.
  - `isAuthenticated: Signal<boolean>`.

### S1.4 — Interceptors HTTP
- [ ] `auth.interceptor.ts`: Adiciona `Authorization: Bearer <token>` se `TokenService.token()` não for null.
- [ ] `offline.interceptor.ts`: Captura erros de rede (status 0, `TimeoutError`) e emite evento para tratamento pelo caller.
- [ ] `error.interceptor.ts`: Captura respostas 4xx/5xx, transforma em objetos `ApiError` tipados. Para erros Problem Details (RFC 7807), extrai `title`, `detail`, `status`.
- [ ] Registrar interceptors em `app.config.ts` via `withInterceptors([...])`.

### S1.5 — Auth Guard e Rota de Login
- [ ] Implementar `auth.guard.ts` (functional `CanActivateFn`): redireciona para `/login` se não autenticado.
- [ ] Implementar `LoginComponent` (`/features/auth/login.component.ts`):
  - Tela sem bottom nav.
  - Logo centralizado.
  - Reactive Form com campos Login e Senha.
  - Botão "Entrar" com loading state.
  - Exibição de erro de credenciais (HTTP 401).
  - Após login bem-sucedido: navega para `/dashboard` (sync será na próxima sprint).

### S1.6 — App Shell com Bottom Navigation
- [ ] Implementar `AppShellComponent`:
  - Layout de tela completo (ver diagrama no `GEMINI.md` Seção 2).
  - Contém: `AppHeaderComponent`, `router-outlet` para content area, `BottomNavComponent`.
- [ ] Implementar `BottomNavComponent`:
  - 4 abas: **Início** (house icon), **Solicitações** (clipboard icon), **Campo** (hard-hat icon), **Perfil** (person icon).
  - Indicador de aba ativa.
  - Badge numérico na aba "Solicitações" (count de pendentes do outbox — placeholder `0` por ora).
  - Altura 64px + `safe-area-inset-bottom`.
- [ ] Implementar `AppHeaderComponent`:
  - Título da tela atual (recebido como input ou via rota).
  - Área de ações contextuais (projetado via `ng-content` ou input).
  - `SyncIndicatorComponent` no canto direito.
- [ ] Implementar `OfflineBannerComponent`:
  - Exibido entre o header e o content quando `!isOnline()`.
  - Cor âmbar, ícone de nuvem, texto informativo.
- [ ] Configurar `app.routes.ts` completo com guards e lazy loading.

**Entregável S1:** Login funcional, JWT em memória, shell navegável entre 4 abas (telas placeholder), offline banner funcionando ao desconectar a rede.

---

## 🔄 SPRINT 2 — Sincronização Inicial e Dashboard

**Duração estimada:** 5–7 dias
**Objetivo:** Após o login, carregar todos os dados base do encarregado e exibir o dashboard funcional.

### S2.1 — SyncService (Initial Sync)
- [ ] Implementar `SyncService`:
  - `initialSync(): Promise<void>`:
    1. Buscar dados da equipe do encarregado logado
    2. Buscar colaboradores da equipe.
    3. Buscar veículos da equipe.
    4. Buscar municípios — consulte `/docs/organizacional.md`.
    5. Buscar processos de negócio — consulte `/docs/` correspondente.
    6. Buscar ações comerciais (catálogo de serviços) — consulte `/docs/comercial.md`.
    7. Buscar materiais e estoque disponíveis — consulte `/docs/logistica.md`.
    8. Persistir tudo no IndexedDB via `DbService`.
    9. Atualizar `lastSyncAt = signal(new Date())`.
  - `readonly isSyncing: Signal<boolean>`.
  - `readonly lastSyncAt: Signal<Date | null>`.
  - Chamar `syncOutbox()` ao fim do `initialSync()` se houver pendências.

**Atenção:** Mapeie cuidadosamente os campos da API para as interfaces locais. Consulte cada arquivo em `/docs/` para os campos retornados. Use apenas os campos necessários para operação offline.

### S2.2 — Tela de Loading do Sync
- [ ] Implementar `SyncLoadingComponent` (tela intermediária):
  - Exibida após login, antes do dashboard.
  - Progresso por etapa ("⚙ Carregando equipe...", "📍 Carregando municípios...", "📦 Carregando materiais...").
  - Progress bar animado.
  - Em caso de falha de rede: opção "Continuar Offline" (se houver dados em cache do login anterior) ou "Tentar Novamente".

### S2.3 — OutboxService
- [ ] Implementar `OutboxService` completo:
  - `enqueue(item: Omit<OutboxItem, 'id' | 'criadoEm' | 'status' | 'tentativas'>): Promise<string>` — retorna UUID local gerado.
  - `getPendingItems(): Promise<OutboxItem[]>`.
  - `getErrorItems(): Promise<OutboxItem[]>`.
  - `updateStatus(id, status, erroDetalhes?): Promise<void>`.
  - `discard(id): Promise<void>`.
  - `getTotalPendingCount(): Promise<number>` — usado pelo badge da bottom nav.

### S2.4 — SyncService (Outbox Sync)
- [ ] Adicionar `syncOutbox(): Promise<SyncResult>` ao `SyncService`:
  - Processar itens `PENDENTE` do outbox em sequência (não paralelamente para evitar conflitos).
  - Para cada item:
    - Fazer a chamada HTTP correspondente.
    - Sucesso: marcar `SINCRONIZADO`, remover da tabela `outbox`, atualizar dado local se necessário.
    - Erro 4xx: marcar `ERRO`, salvar `erroDetalhes`. **Não retentar.**
    - Erro 5xx / rede: incrementar `tentativas`. Se `tentativas >= 3`: marcar `ERRO`.
  - Retornar `{ sincronizados: number, erros: number }`.
- [ ] No `ConnectivityService`: ao detectar `online`, chamar `SyncService.syncOutbox()`.

### S2.5 — DashboardComponent
- [ ] Implementar `DashboardComponent`:
  - **Header personalizado:** "Bom dia/tarde/noite, [nome] 👷 — [nome da equipe]".
  - **Card de alerta de erros:** visível se `outbox.getErrorItems().length > 0`. Cor vermelha, texto "X itens com erro de sincronização. Toque para ver →". Navega para lista de solicitações/execuções filtradas por ERRO.
  - **Cards de resumo** (4 cards em grid 2x2):
    - Pendentes de sync (count outbox PENDENTE).
    - Execuções hoje (count local de execuções com `data = hoje`).
    - Membros ativos (count colaboradores ativos da equipe).
    - Último sync (data/hora relativa — "há 5 min").
  - **Atalhos rápidos** (4 botões grandes, ícone + texto):
    - "+ Nova Solicitação" → `/solicitacoes/nova`.
    - "⚡ Registrar Execução" → `/campo/nova`.
    - "👥 Minha Equipe" → `/perfil/equipe`.
    - "📦 Ver Estoque" → `/solicitacoes/estoque`.
  - **Feed de atividade recente** (últimos 5 itens combinados de solicitações + execuções).
- [ ] Implementar `SyncIndicatorComponent` (no AppHeader):
  - Ícone de nuvem com estado: sincronizando (animação), online-sincronizado (check verde), offline (âmbar), erros (vermelho).
  - Toque: exibe toast com status detalhado.

**Entregável S2:** Login → Loading sync → Dashboard funcional com dados reais da API. App funciona offline após primeiro login.

---

## 📋 SPRINT 3 — Módulo de Solicitações de Material

**Duração estimada:** 6–8 dias
**Objetivo:** Encarregado consegue criar, visualizar e acompanhar solicitações de material, com suporte completo a offline e tratamento de erros de sync.

**Pré-requisito:** Consultar `/docs/logistica.md` para todos os contratos de endpoint desta feature.

### S3.1 — Lista de Solicitações
- [ ] `SolicitacoesListComponent` (`/solicitacoes`):
  - Busca solicitações no IndexedDB (tabela `solicitacoes`).
  - **Filtros por chips horizontais:** Todas | Pendente | Sincronizado | Erro.
  - Cada item da lista:
    - Título do processo/ação.
    - Data de criação (relativa).
    - **Status badge:** ⏳ Pendente / ✅ Sincronizado / ❌ Erro.
    - Para status ERRO: ícone de alerta vermelho e texto do erro em subtítulo.
  - **FAB (+)** no canto inferior direito para nova solicitação.
  - Empty state: ilustração + "Nenhuma solicitação ainda. Toque em + para criar."
  - Skeleton loaders enquanto carrega do IndexedDB.

### S3.2 — Wizard de Nova Solicitação
- [ ] `NovaSolicitacaoComponent` (`/solicitacoes/nova`):
  - **Wizard com progress bar de steps** (4 etapas).
  - Botões "Anterior" e "Próximo" na parte inferior. "Confirmar" no último step.
  - **Step 1 — Processo/Serviço:**
    - Seletor de processo de negócio (dados do IndexedDB `processos`).
    - Seletor de ação comercial (filtrado por processo — dados do IndexedDB `acoes`).
    - Busca/filtro textual nos itens.
  - **Step 2 — Materiais:**
    - Lista de materiais disponíveis (IndexedDB `materiais` + `estoque`).
    - Para cada material: nome, unidade, saldo disponível, campo de quantidade.
    - Validação: quantidade > 0 e <= saldo disponível.
    - Adicionar/remover materiais da seleção.
  - **Step 3 — Informações Complementares:**
    - Seletor de município (IndexedDB `municipios`).
    - Campo de observações (textarea, máx 500 chars).
    - Seletor de membros da equipe envolvidos (multi-select, IndexedDB `colaboradores`).
  - **Step 4 — Revisão:**
    - Resumo completo da solicitação.
    - Botão "Confirmar e Salvar".
  - **Ao confirmar:**
    - Gerar UUID local.
    - Salvar no IndexedDB `solicitacoes` com `status: 'PENDENTE'`.
    - Enfileirar no `outbox` com endpoint e payload corretos (consultar `/docs/logistica.md`).
    - Navegar para a listagem com toast "Solicitação criada! Será enviada quando houver conexão."

### S3.3 — Detalhe e Correção de Solicitação com Erro
- [ ] `SolicitacaoDetalheComponent` (`/solicitacoes/:id`):
  - Exibir todos os dados da solicitação.
  - Se `status === 'ERRO'`:
    - Card de alerta vermelho com o erro retornado pela API (`erroDetalhes`).
    - Botão "Editar e Reenviar" → abre wizard pré-preenchido com os dados atuais.
    - Botão "Descartar Solicitação" → confirmação → `OutboxService.discard()` → remove do IndexedDB.
  - Se `status === 'SINCRONIZADO'`: exibir dados no modo somente leitura.

### S3.4 — Tela de Estoque
- [ ] `EstoqueListComponent` (`/solicitacoes/estoque`):
  - Lista de materiais disponíveis para a equipe.
  - Busca textual por nome.
  - Exibe: nome do material, unidade, saldo atual.
  - Dados do IndexedDB — badge "Atualizado [hora]" no topo.

**Entregável S3:** Fluxo completo de solicitações funcionando offline. Itens com erro visíveis e corrigíveis.

---

## ⚡ SPRINT 4 — Módulo de Campo

**Duração estimada:** 6–8 dias
**Objetivo:** Encarregado consegue registrar execuções de campo e eventos operacionais, com suporte offline completo.

**Pré-requisito:** Consultar `/docs/campo.md` (ou equivalente) para contratos de endpoint.

### S4.1 — Lista de Execuções de Campo
- [ ] `CampoListComponent` (`/campo`):
  - **Seção "Hoje":** execuções com `data = today`, em destaque.
  - **Seção "Histórico":** execuções dos últimos 7 dias, agrupadas por data.
  - Status badge em cada item (⏳/✅/❌).
  - FAB (+) para nova execução.
  - Empty state por seção.

### S4.2 — Wizard de Nova Execução de Campo
- [ ] `NovaExecucaoComponent` (`/campo/nova`):
  - Wizard de 5 etapas com progress bar.
  - **Step 1 — Tipo de Execução:**
    - Seletor de processo de negócio (IndexedDB `processos`).
    - Data e hora da execução (datetime-local, default: agora).
  - **Step 2 — Localização:**
    - Seletor de município (IndexedDB `municipios`).
    - Seletor de base operacional (filtrada pelo município).
  - **Step 3 — Equipe Envolvida:**
    - Multi-select de colaboradores (IndexedDB `colaboradores`).
    - Seletor de veículo (opcional, IndexedDB `veiculos`).
  - **Step 4 — Materiais Consumidos (Opcional):**
    - Toggle "Houve consumo de materiais?".
    - Se sim: interface similar ao Step 2 de Solicitações.
    - Validação de quantidade vs estoque disponível.
  - **Step 5 — Observações e Revisão:**
    - Textarea de observações.
    - Resumo completo.
    - Botão "Confirmar Execução".
  - **Ao confirmar:**
    - UUID local.
    - Salvar em IndexedDB `execucoesCampo`.
    - Enfileirar no `outbox` com endpoint correto (consultar `/docs/campo.md`).
    - Se houver consumo de materiais: atualizar saldo no IndexedDB `estoque` localmente.
    - Toast de confirmação.

### S4.3 — Detalhe de Execução com Erro
- [ ] `ExecucaoDetalheComponent` (`/campo/:id`):
  - Padrão idêntico ao `SolicitacaoDetalheComponent`.
  - Exibição de erro da API, opção de editar e reenviar, opção de descartar.

### S4.4 — Tratamento de Conflito de Estoque
- Quando offline, o estoque é deduzido localmente ao registrar consumo.
- Se ao sincronizar a API retornar erro de saldo insuficiente (4xx):
  - Marcar execução como ERRO no outbox.
  - `erroDetalhes`: "Saldo insuficiente para [Material X]. Estoque atual: Y."
  - Reverter a dedução local de estoque (soma de volta).
  - Notificar o usuário via badge de erro.

**Entregável S4:** Registro de execuções de campo funcionando offline com tratamento robusto de erros.

---

## 👤 SPRINT 5 — Perfil, Equipe e Polimento Geral

**Duração estimada:** 4–5 dias
**Objetivo:** Módulo de perfil completo e refinamento de UX em todo o app.

### S5.1 — PerfilComponent
- [ ] `PerfilComponent` (`/perfil`):
  - **Card do encarregado:** avatar com iniciais, nome completo, função, equipe.
  - **Seção "Minha Equipe":** lista de colaboradores (nome, função, status ativo). Toque expande detalhes.
  - **Seção "Veículos":** lista de veículos da equipe (placa, modelo, status).
  - **Seção "Sincronização":**
    - Status: "Online ✅" / "Offline ⚠️".
    - Último sync: data/hora.
    - Itens pendentes: count.
    - Itens com erro: count (com link).
    - Botão "Sincronizar Agora" (desabilitado se offline).
  - **Seção "Configurações":**
    - Toggle Dark/Light mode.
    - Toggle "Notificações" (placeholder para fase futura).
  - **Botão "Sair"** com confirmação.

### S5.2 — Tela de Pendências/Erros Centralizada
- [ ] `PendenciasComponent` (`/perfil/pendencias`):
  - Lista unificada de todos os itens do outbox (PENDENTE + ERRO).
  - Agrupados por tipo (Solicitações / Execuções de Campo).
  - Para cada item: tipo, data, status, erro (se houver).
  - Ações: Reenviar (PENDENTE), Editar (ERRO), Descartar (ambos).
  - Botão "Sincronizar Tudo" no header.

### S5.3 — Polimento de UX
- [ ] Animações de transição entre telas (slide in/out conforme posição na bottom nav).
- [ ] Ripple effect em todos os elementos clicáveis.
- [ ] Confirmação por diálogo (bottom sheet) para ações destrutivas (descartar, sair).
- [ ] Toast/Snackbar global para feedbacks rápidos (sucesso, erro, info).
- [ ] Pull-to-refresh no dashboard (força `forceFullSync()`).
- [ ] Scroll to top ao trocar de aba na bottom nav.
- [ ] Estados vazios (empty states) ilustrados em todas as listas.
- [ ] Badge de contagem no ícone "Solicitações" da bottom nav refletindo total de pendentes + erros.

### S5.4 — Acessibilidade
- [ ] Garantir `aria-label` em todos os botões com apenas ícone.
- [ ] Contraste mínimo WCAG AA em todos os textos.
- [ ] Focus visible em todos os elementos interativos (navegação por teclado para testes).

**Entregável S5:** App completo e polido com todos os módulos integrados e UX refinada.

---

## 🔒 SPRINT 6 — Hardening, Testes e PWA em Produção

**Duração estimada:** 4–5 dias
**Objetivo:** App seguro, testado e pronto para deploy como PWA instalável.

### S6.1 — Segurança Final
- [ ] Auditoria de segurança:
  - Confirmar que nenhum dado sensível vai para `localStorage`.
  - Confirmar que o token não é logado no console em produção.
  - Confirmar que inputs são sanitizados.
  - Revisar CSP no `index.html`.
- [ ] Implementar timeout de sessão: se o token expirar enquanto o app está em uso, exibir diálogo "Sua sessão expirou. Faça login novamente." e redirecionar para `/login`.
- [ ] Limpar dados sensíveis do IndexedDB ao fazer logout (ou oferecer opção para manter cache).

### S6.2 — Testes Unitários
- [ ] `AuthService` — testar login, logout, estado de autenticação.
- [ ] `TokenService` — testar armazenamento e decodificação do JWT.
- [ ] `OutboxService` — testar enqueue, update status, discard.
- [ ] `SyncService` — testar `syncOutbox()` com mocks de HTTP (sucesso, 4xx, 5xx).
- [ ] `ConnectivityService` — testar detecção de online/offline.

### S6.3 — Build e Performance
- [ ] `ng build --configuration production`.
- [ ] Analisar bundle com `ng build --stats-json` + webpack-bundle-analyzer.
- [ ] Garantir que o bundle principal seja < 500KB gzipped.
- [ ] Lazy loading funcionando corretamente (verificar Network tab).
- [ ] Lighthouse score no modo PWA: mínimo 90 em Performance e 100 em PWA.

### S6.4 — Service Worker em Produção
- [ ] Testar service worker com `ng serve --configuration production` ou `http-server dist/`.
- [ ] Verificar que o app instala corretamente ("Adicionar à tela inicial") em tablet Android.
- [ ] Verificar que atualizações de SW são detectadas e o usuário é notificado.
- [ ] Implementar `SwUpdate` handler: notificar usuário sobre atualização disponível com botão "Atualizar Agora".

### S6.5 — Documentação Final
- [ ] Atualizar `GEMINI.md` com qualquer decisão técnica tomada durante o desenvolvimento que divergiu do plano.
- [ ] Criar `README.md` do projeto com instruções de setup, build e deploy.

**Entregável S6:** App em estado production-ready, com PWA instalável, testes passando e Lighthouse ≥ 90.

---

## 🗂️ Backlog — Fases Futuras (Não Implementar Agora)

Estas features estão no horizonte mas fora do escopo atual. Documentadas aqui para não serem "inventadas" durante o desenvolvimento.

| Feature | Complexidade | Dependência |
|---|---|---|
| Upload de fotos em execuções | Alta | Backend de storage (Cloudflare R2) |
| Assinatura digital em solicitações | Alta | Backend de assinatura |
| Notificações Push | Média | Backend de push notifications |
| Relatórios e exportação PDF | Média | Biblioteca PDF no frontend |
| Rastreamento de veículos em tempo real | Alta | Integração Vivo Frota Inteligente |
| Dashboard gerencial (não encarregado) | Alta | Novo perfil de usuário |
| Modo light por padrão / seleção de tema | Baixa | S5.3 (já tem toggle) |

---

## ⚡ Guia de Decisão Rápida para o Agente

> Use este quadro como referência rápida ao tomar decisões de implementação.

| Situação | Decisão |
|---|---|
| Preciso de estado local no componente | `signal()` |
| Preciso de estado compartilhado entre componentes | `Injectable` service com `signal()` |
| Preciso reagir a mudança de estado | `effect()` |
| Preciso de valor derivado de state | `computed()` |
| Preciso de stream de eventos HTTP | `Observable` + `async pipe` |
| Usuário está offline e tenta gravar | Outbox + IndexedDB, nunca bloquear |
| API retorna 4xx | Marcar ERRO no outbox, exibir para usuário corrigir |
| API retorna 5xx | Retentar até 3x, depois marcar ERRO |
| Dados de listagem (municípios, equipe, etc.) | Sempre do IndexedDB, nunca HTTP direto |
| Dúvida sobre contrato de endpoint | Consultar `/docs/` antes de qualquer coisa |
| Formulário inválido | Não enfileirar no outbox, bloquear na UI |
| Componente novo | Sempre `standalone: true`, sempre `OnPush` |
