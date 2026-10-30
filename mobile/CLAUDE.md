# GEMINI.md — Contexto de Agente: Nexo Field App

> **Instruções para o Agente (Gemini CLI):** Este arquivo é seu contexto primário de desenvolvimento. Leia-o integralmente antes de iniciar qualquer tarefa. Toda decisão técnica e de design deve ser validada contra as regras aqui definidas. A documentação completa da API REST do backend está em **`/docs/`** — consulte-a sempre que precisar de contratos de endpoint, DTOs ou regras de negócio.

---

## 🎯 1. Identidade do Projeto

**Nome:** Nexo Field App (`ces-field-app`)
**Propósito:** Aplicação PWA Angular consumida exclusivamente por **encarregados de equipes de campo** (supervisores operacionais) da empresa. Eles atuam em campo, frequentemente sem conexão de rede estável, e precisam registrar execuções, lançar solicitações de materiais e consultar dados operacionais.

**Relacionamento com o backend:** Este app é um **cliente** do sistema backend Nexo (Gestão Operacional). O backend é um Monolith Modular (Spring Boot 4, Java 21, PostgreSQL 17.5) com API RESTful documentada via Swagger. A URL base da API deve ser configurável por ambiente (`environment.ts`).

**Audiência primária:** Encarregados de equipes de campo — usuários com baixa tolerância a complexidade de interface, operando em tablets Android em ambiente externo (sol, luvas, pressa).

---

## 📱 2. Filosofia de UI/UX — App Nativo, Não Website

Esta é a regra mais importante de design: **a aplicação deve parecer e se comportar como um app Android nativo**, nunca como um website responsivo.

### Mandamentos de UX
- **Bottom Navigation Bar** sempre visível (exceto tela de login). Nunca use menus laterais (drawer) como navegação principal.
- **Touch targets mínimos de 56dp** (equivalente a ~56px em dispositivos tablet). Botões e itens de lista devem ser fáceis de tocar com luvas.
- **Sem hover states** como indicador primário de ação — use `active` e `focus-visible`.
- **Feedback tátil imediato:** todo toque deve ter feedback visual instantâneo (ripple effect, escala, cor).
- **Gestos nativos:** suporte a swipe para voltar em flows lineares onde aplicável.
- **Loading states** são obrigatórios em toda operação assíncrona — use skeletons, nunca spinners centrais que bloqueiam toda a tela.
- **Formulários curtos por tela** — divida formulários longos em steps/wizard, não em scrolls imensos.
- **Tipografia grande:** mínimo `16px` para corpo de texto, `14px` para labels secundários.

### Paleta Visual e Identidade
- **Tema:** Industrial/Utilitário com toques de energia (referência: apps de logística como Lalamove, apps de inspeção industrial).
- **Primária:** Azul corporativo — `#0052A5` (azul corporativo).
- **Acento:** Âmbar operacional — `#F59E0B` (sinalização de atenção, alertas, badges offline).
- **Superfície:** Cinza escuro profundo `#111827` para dark mode (padrão), `#F9FAFB` para light.
- **Sucesso:** `#10B981`, **Erro:** `#EF4444`, **Aviso:** `#F59E0B`.
- **Modo padrão:** Light mode (condições de campo, brilho externo).
- **CSS Custom Properties:** Toda cor deve usar variáveis CSS definidas em `styles/tokens.scss`.

### Layout Padrão de Tela
```
┌─────────────────────────────────┐
│  [Status Bar Area]              │ ← 24px safe area top
├─────────────────────────────────┤
│  [App Header]                   │ ← 56px: título da tela + ações contextuais
├─────────────────────────────────┤
│                                 │
│  [Content Area]                 │ ← flex-grow, overflow-y: auto
│  (scroll interno)               │
│                                 │
├─────────────────────────────────┤
│  [Bottom Navigation]            │ ← 64px + safe-area-inset-bottom
└─────────────────────────────────┘
```

---

## 🏗️ 3. Stack Tecnológica

| Tecnologia | Versão | Papel |
|---|---|---|
| Angular | 20.x | Framework principal (Standalone Components) |
| TypeScript | 5.5+ | Linguagem base |
| Angular PWA (`@angular/pwa`) | latest | Service Worker, manifest, offline shell |
| Dexie.js | 4.x | Wrapper IndexedDB tipado para dados offline |
| RxJS | 7.x | Programação reativa, streams de dados |
| Angular Signals | nativo | Estado reativo local e global (sem NgRx) |
| Tailwind CSS | 3.x | Utilitários de estilo base |
| SCSS | — | Customizações, tokens de design, animações |
| Angular Animations | nativo | Transições de tela, micro-interações |

### Decisões Arquiteturais Firmes
- **Sem NgRx ou BehaviorSubject complexos:** Use **Angular Signals** (`signal`, `computed`, `effect`) como mecanismo de estado. Para estado global, use `Injectable({ providedIn: 'root' })` com signals.
- **Standalone Components:** Sem `NgModule`. Todo componente, diretiva e pipe deve ser `standalone: true`.
- **Lazy Loading por Rota:** Cada módulo de feature deve ser carregado via `loadComponent()` ou `loadChildren()`.
- **Functional Guards:** Use `CanActivateFn` e `CanMatchFn`.
- **HTTP Client:** Use o `HttpClient` do Angular com `provideHttpClient(withInterceptors([...]))`.
- **Sem jQuery, sem Bootstrap.**

---

## 📁 4. Estrutura de Diretórios

```
ces-field-app/
├── docs/                          ← 📌 DOCUMENTAÇÃO DA API (não modificar)
│   ├── iam.md
│   ├── rh.md
│   ├── comercial.md
│   ├── logistica.md
│   └── ...
├── src/
│   ├── app/
│   │   ├── core/                  ← Singleton services, guards, interceptors
│   │   │   ├── auth/
│   │   │   │   ├── auth.service.ts
│   │   │   │   ├── auth.guard.ts
│   │   │   │   └── token.service.ts
│   │   │   ├── http/
│   │   │   │   ├── auth.interceptor.ts
│   │   │   │   ├── offline.interceptor.ts
│   │   │   │   └── error.interceptor.ts
│   │   │   ├── sync/
│   │   │   │   ├── sync.service.ts         ← Orquestra sync online/offline
│   │   │   │   ├── outbox.service.ts       ← Fila de operações pendentes
│   │   │   │   └── connectivity.service.ts ← Navigator.onLine + eventos
│   │   │   └── db/
│   │   │       ├── app-db.ts               ← Definição Dexie (schema)
│   │   │       └── db.service.ts           ← Facade para operações DB
│   │   ├── shared/                ← Componentes reutilizáveis
│   │   │   ├── components/
│   │   │   │   ├── app-header/
│   │   │   │   ├── bottom-nav/
│   │   │   │   ├── status-badge/
│   │   │   │   ├── offline-banner/
│   │   │   │   ├── skeleton-loader/
│   │   │   │   ├── empty-state/
│   │   │   │   ├── confirmation-dialog/
│   │   │   │   └── sync-indicator/
│   │   │   ├── directives/
│   │   │   │   ├── ripple.directive.ts
│   │   │   │   └── long-press.directive.ts
│   │   │   └── pipes/
│   │   │       ├── relative-time.pipe.ts
│   │   │       └── status-label.pipe.ts
│   │   ├── features/
│   │   │   ├── auth/              ← Login screen
│   │   │   ├── dashboard/         ← Tela inicial com atalhos
│   │   │   ├── solicitacoes/      ← Solicitações de material
│   │   │   ├── campo/             ← Execuções e eventos de campo
│   │   │   └── perfil/            ← Perfil do encarregado
│   │   ├── app.routes.ts
│   │   ├── app.config.ts
│   │   └── app.component.ts
│   ├── styles/
│   │   ├── tokens.scss            ← CSS Custom Properties (cores, espaçamento)
│   │   ├── typography.scss
│   │   ├── animations.scss
│   │   └── global.scss
│   ├── environments/
│   │   ├── environment.ts
│   │   └── environment.prod.ts
│   ├── manifest.webmanifest
│   └── ngsw-config.json           ← Service Worker config
├── GEMINI.md                      ← Este arquivo
├── PLANNING.md
└── angular.json
```

---

## 🔐 5. Segurança

### 5.1 Autenticação e JWT
- O backend emite um **JWT assinado com chave assimétrica** (veja `/docs/iam.md` para o contrato do endpoint `POST /v1/auth/login`).
- **Nunca armazene o token JWT em `localStorage` ou `sessionStorage`** — esses storages são vulneráveis a XSS.
- **Estratégia de armazenamento seguro:**
  - O token de acesso é mantido **em memória** (signal no `TokenService`).
  - Para persistência entre reloads (necessário para PWA offline), use `sessionStorage` **apenas** se o token tiver vida curta, ou implemente um refresh flow.
  - O `TokenService` expõe um `signal<string | null>` para o token atual.
- **Interceptor de autenticação (`auth.interceptor.ts`):** Adiciona o header `Authorization: Bearer <token>` em todas as requisições ao domínio da API.
- **Guard de autenticação (`auth.guard.ts`):** Protege todas as rotas exceto `/login`. Redireciona para `/login` se não autenticado.

### 5.1.1 Sessão em campo (regra que não se quebra)
- **Quem entrou só volta ao login tocando em "Sair".** Token vencido, falta de internet (inclusive 4G bloqueado ou Wi-Fi sem saída, quando `navigator.onLine` mente) e erro do servidor nunca mandam para `/login`: o `authGuard` e a retomada a frio aceitam qualquer sessão local.
- A tela `/login` tem o `loginGuard`: com sessão no aparelho segue para `volta` (ou `/hoje`) e, sem token, tenta o cookie antes. O Android reabre o app instalado na última página carregada, não pelo ícone; sem o guarda, quem tinha entrado pelo login via o formulário em toda reabertura. A abertura pelo ícone (`retomadaGuard`) também tenta o cookie antes de mandar ao login.
- O token de acesso (~1h) é renovado sozinho por `POST /v1/auth/refresh` com o cookie HttpOnly `ces_sessao` (`AuthService.renovarToken`, uma renovação por vez entre abas). O `authInterceptor` renova e repete a requisição; ele fica **depois** do `errorInterceptor` na cadeia.
- `SESSAO_INVALIDA` só marca `reautenticacaoNecessaria`: o app continua, a fila espera e o shell mostra o aviso "Entre novamente para enviar" (`ces-aviso-sessao`). O login só abre se a pessoa tocar em "Entrar".
- A renovação respeita o `ConnectivityService` (não sai pelo 4G bloqueado) e é tentada ao reabrir o app e quando a rede volta.
- Garantias cobertas por `e2e/piloto/sessao-campo.spec.ts` e `e2e/sw/sessao-offline.spec.ts` (modo avião + token vencido com o service worker real).

### 5.2 Dados Sensíveis no IndexedDB
- Dados armazenados offline no IndexedDB **não devem incluir** campos criptografados brutos do backend (ex: CPF em texto claro).
- Armazene apenas os dados necessários para operação offline (IDs, nomes de exibição, estoques, municípios).
- Se um dado sensível for necessário offline, consulte a equipe antes de implementar.

### 5.3 Content Security Policy
- Configure `meta[http-equiv="Content-Security-Policy"]` no `index.html` para restringir fontes de script.
- Nenhum `eval()`, nenhum `innerHTML` com dados externos.

### 5.4 Validação de Entrada
- **Todo formulário** deve ter validação tanto no frontend (Angular Reactive Forms com Validators) quanto a resposta de erro do backend deve ser tratada e exibida (RFC 7807 Problem Details).
- Sanitize inputs antes de exibir em templates (Angular faz isso por padrão, mas tenha atenção com `[innerHTML]`).

---

## 📡 6. Estratégia Offline-First (CRÍTICO)

Esta é a característica mais importante do app. Implemente com rigor.

### 6.1 Fluxo de Inicialização (Login → Cache)
```
1. Usuário faz login (POST /v1/auth/login)
   → Recebe JWT
   → TokenService armazena em memória

2. SyncService.initialSync() é chamado:
   → GET /v1/rh/colaboradores/{meuId}/equipe  (minha equipe)
   → GET /v1/municipios                        (municípios cobertos pela equipe)
   → GET /v1/veiculos (filtrado pela equipe)   (veículos da equipe)
   → GET /v1/processos-negocio                 (catálogo de processos)
   → GET /v1/comercial/acoes                   (catálogo de ações/serviços)
   → GET estoque/materiais disponíveis para a equipe (ver /docs/logistica.md)

3. Todos os dados são persistidos no IndexedDB via Dexie

4. Usuário é redirecionado ao Dashboard
   → App funciona offline a partir daqui
```

### 6.2 Schema IndexedDB (Dexie)
```typescript
// src/app/core/db/app-db.ts
class AppDatabase extends Dexie {
  equipe!: Table<EquipeLocal>;
  colaboradores!: Table<ColaboradorLocal>;
  municipios!: Table<MunicipioLocal>;
  veiculos!: Table<VeiculoLocal>;
  materiais!: Table<MaterialLocal>;
  estoque!: Table<EstoqueLocal>;
  processos!: Table<ProcessoLocal>;
  acoes!: Table<AcaoComercialLocal>;
  
  // Tabelas de operação offline
  solicitacoes!: Table<SolicitacaoLocal>;     ← Solicitações de material
  execucoesCampo!: Table<ExecucaoCampoLocal>; ← Execuções/eventos de campo
  outbox!: Table<OutboxItem>;                 ← Fila de sync pendente
}
```

### 6.3 Outbox Pattern (Operações Pendentes)
Toda operação de escrita (POST/PUT/PATCH/DELETE) que o usuário executar **offline** deve seguir este fluxo:

```
1. Usuário executa ação (ex: criar solicitação de material)
2. Dados são validados localmente (regras básicas no frontend)
3. Operação é gravada localmente no IndexedDB (status: 'PENDENTE')
4. Item é adicionado à tabela `outbox` com:
   - id: UUID gerado localmente (crypto.randomUUID())
   - endpoint: '/v1/solicitacoes'
   - method: 'POST'
   - payload: objeto serializado
   - status: 'PENDENTE' | 'ERRO' | 'SINCRONIZADO'
   - erroDetalhes: string | null  ← erro retornado pela API
   - tentativas: number
   - criadoEm: Date
5. UI mostra o item com badge "⏳ Pendente"
6. Quando conectividade retorna → SyncService processa outbox
7. Se API retorna sucesso → status = 'SINCRONIZADO', remove do outbox
8. Se API retorna erro de negócio (4xx) → status = 'ERRO', erroDetalhes preenchido
   ← ITEM FICA RETIDO, usuário deve corrigir e reenviar
9. Se API retorna erro de servidor (5xx) → retenta automaticamente (máx 3x)
```

### 6.4 ConnectivityService
```typescript
// Detecta mudanças de conectividade
@Injectable({ providedIn: 'root' })
export class ConnectivityService {
  readonly isOnline = signal(navigator.onLine);
  // Escuta window eventos 'online' e 'offline'
  // Ao ficar online → notifica SyncService para processar outbox
}
```

### 6.5 OfflineBanner Component
- Exibir banner persistente no topo do content area quando `isOnline() === false`.
- Cor âmbar (`#F59E0B`), ícone de nuvem cortada, texto "Modo Offline — alterações serão sincronizadas quando a conexão retornar".

### 6.6 Service Worker (ngsw-config.json)
- Cache de assets estáticos: `prefetch` para shell do app.
- Cache de dados da API: estratégia `freshness` para dados que mudam, `performance` para dados estáticos (municípios, processos).
- Background sync via `SyncService` (não depender do SW Sync API por compatibilidade).

---

## 🧩 7. Módulos e Features

### 7.1 Feature: Auth (`/login`)
- Tela limpa, sem bottom nav.
- Logo no topo.
- Campos: **Login** e **Senha** (consultar `/docs/iam.md` para o contrato).
- Ao submeter: chama `AuthService.login()` → armazena token → `initialSync()` → navega para `/dashboard`.
- Exibir erro de credenciais inválidas (HTTP 401) de forma clara.
- Enquanto carrega o sync inicial, exibir tela de loading com progresso (ex: "Carregando equipe...", "Carregando municípios...").

### 7.2 Feature: Dashboard (`/dashboard`)
- Header: "Bom dia, [Nome do Encarregado] 👷" + nome da equipe.
- **Cards de resumo rápido:**
  - Solicitações pendentes (count de itens no outbox com status PENDENTE)
  - Execuções de hoje
  - Membros da equipe ativos
  - Status de sincronização (último sync bem-sucedido)
- **Atalhos rápidos** (grid 2x2 de botões grandes):
  - Nova Solicitação
  - Registrar Execução
  - Ver Equipe
  - Materiais Disponíveis
- **Feed de atividade recente** (últimas execuções/solicitações)
- **Indicador de itens com erro** de sync — card de alerta vermelho com link para a lista de pendências.

### 7.3 Feature: Solicitações (`/solicitacoes`)
Gerenciamento de solicitações de material para a equipe.

**Listagem:**
- Lista paginada de solicitações (local + sync status badge).
- Filtros: status (Pendente/Sincronizado/Erro/Todos), data.
- FAB (+) para nova solicitação.

**Criação (wizard multi-step):**
- Step 1: Selecionar processo/serviço (dados locais do IndexedDB).
- Step 2: Selecionar materiais e quantidades (estoque local).
- Step 3: Informações adicionais (observações, município de atendimento).
- Step 4: Revisão e confirmação.
- Ao confirmar: grava no IndexedDB + enfileira no outbox.

**Detalhe de solicitação com erro:**
- Exibir o erro retornado pela API (campo `erroDetalhes`).
- Permitir **edição e reenvio**.
- Botão "Descartar" para cancelar a solicitação com erro.

**Consultar `/docs/logistica.md`** para os contratos de endpoint de solicitações de material.

### 7.4 Feature: Campo (`/campo`)
Registro de execuções operacionais e eventos de campo.

**Listagem:**
- Execuções do dia atual em destaque.
- Histórico dos últimos 7 dias.
- Status badge de sync em cada item.

**Abertura do turno (`/turno/abrir`):**
- O líder (responsável da equipe) confere o veículo e as pessoas antes de começar e pode trocar a placa e incluir/tirar parceiros. As mudanças vão junto com `POST /api/v1/turnos/abrir` (`idVeiculo`, `idsColaboradores`).
- Composição, opções e meta do ciclo vêm de `GET /api/v1/turnos/minha-equipe/composicao` e ficam salvas no aparelho.

**Lançar execução (etapa única, sem "iniciar/finalizar"):**
- Passo 1: número da nota + localização coletada automaticamente pelo GPS (funciona offline). Não existe município.
- Passo 2: serviços (ações). Passo 3: materiais — "Meu saldo" ou "Outros materiais".
- Material fora do saldo pode ser aplicado: o saldo da equipe apenas fica negativo e o app avisa o encarregado para informar o gestor. Não existe pendência para tratar.
- Retirada: o material retirado em campo vai no veículo (entra como sucata no estoque da equipe) e é devolvido quando a equipe puder.
- No desktop, ao entregar material para a equipe, o almoxarifado vê um aviso nos materiais com saldo negativo nessa equipe (informativo, não trava a entrega).
- Passo 4: conferir e lançar. A execução vai para o outbox e é enviada quando houver conexão.

**Meta do ciclo:** a meta é cadastrada no Nexo por janela — ciclo comercial (dia 26 a 25 do mês seguinte, nomeado pelo mês em que termina) para as equipes do processo comercial, ou mês civil para as demais. A composição (`metaDoCiclo`) traz a janela, o valor, a meta diária e o faturamento que o servidor já recebeu. A tela Hoje mostra, além da meta do dia, o cartão do ciclo: realizado (servidor + lançamentos ainda só no aparelho), percentual, quanto falta, quanto fazer por dia útil restante e se a equipe está no ritmo. Passado o fim do ciclo sem sincronizar, o cartão pede sincronização em vez de mostrar números velhos.

**Consultar `/docs/campo.md` ou o endpoint equivalente** na documentação da API.

### 7.5 Feature: Perfil (`/perfil`)
- Dados do encarregado logado (carregados do cache local).
- Nome da equipe e membros (listagem resumida).
- Veículos vinculados.
- **Configurações do app:**
  - Tema (Dark/Light)
  - Forçar sincronização manual (botão "Sincronizar agora")
  - Ver itens pendentes de sync
- **Logout** (limpa token em memória, opcionalmente limpa IndexedDB).

---

## 🔄 8. Serviços Core

### AuthService
```typescript
login(credentials): Observable<void>
logout(): void
readonly currentUser: Signal<UsuarioLogado | null>
readonly isAuthenticated: Signal<boolean>
```

### TokenService
```typescript
readonly token: Signal<string | null>
setToken(token: string): void
clearToken(): void
getDecodedPayload(): JwtPayload | null  ← use biblioteca jwt-decode
```

### SyncService
```typescript
initialSync(): Promise<void>          ← Chamado após login
syncOutbox(): Promise<SyncResult>     ← Processa fila de pendências
forceFullSync(): Promise<void>        ← Botão "Sincronizar agora"
readonly lastSyncAt: Signal<Date | null>
readonly isSyncing: Signal<boolean>
```

### OutboxService
```typescript
enqueue(item: OutboxItem): Promise<void>
getPendingItems(): Promise<OutboxItem[]>
getErrorItems(): Promise<OutboxItem[]>
updateStatus(id: string, status, erroDetalhes?): Promise<void>
retry(id: string): Promise<void>
discard(id: string): Promise<void>
```

### ConnectivityService
```typescript
readonly isOnline: Signal<boolean>
// Ao ficar online: emite evento para SyncService
```

### DbService
Facade sobre o Dexie AppDatabase. Expõe métodos tipados para cada tabela.

---

## ⚠️ 9. Tratamento de Erros

### Erros de Negócio da API (4xx — RFC 7807)
O backend retorna erros no formato Problem Details:
```json
{
  "type": "...",
  "title": "Recurso não encontrado",
  "status": 404,
  "detail": "Colaborador com ID X não existe.",
  "instance": "/v1/rh/colaboradores/X"
}
```
- O `error.interceptor.ts` deve capturar esses erros e transformá-los em objetos tipados `ApiError`.
- Para erros de validação (422), extraia a lista de campos com erro e exiba inline nos formulários.
- Para operações offline (outbox), persista o `detail` como `erroDetalhes` no registro.

### Erros de Conectividade
- Se `HttpClient` lançar `0` status ou `TimeoutError` → o `offline.interceptor.ts` deve interceptar e redirecionar a operação para o `OutboxService` em vez de propagar o erro para o usuário.

### UX de Erro
- Nunca exibir stack traces ou mensagens técnicas ao usuário.
- Mensagens de erro devem ser em português, claras e orientadas à ação ("Quantidade inválida. Informe um valor entre 1 e 999.").
- Erros globais não tratados → toast/snackbar no rodapé da tela.

---

## 📋 10. Convenções de Código

### Nomenclatura
- **Componentes:** `kebab-case` para seletores (`app-bottom-nav`), `PascalCase` para classes.
- **Signals:** sufixo sem `$` (use `isLoading`, não `isLoading$`). Use `$` apenas para Observables RxJS.
- **Interfaces de domínio local:** sufixo `Local` (ex: `ColaboradorLocal`, `MunicipioLocal`).
- **DTOs de API:** sufixo `Dto` (ex: `LoginRequestDto`, `ColaboradorResponseDto`).

### Padrões de Componente
```typescript
@Component({
  selector: 'app-nome',
  standalone: true,
  imports: [...],
  template: `...`,
  changeDetection: ChangeDetectionStrategy.OnPush, // SEMPRE
})
export class NomeComponent {
  // Signals primeiro
  // Injeções via inject()
  // Lifecycle hooks
  // Métodos públicos
  // Métodos privados
}
```

### Injeção de Dependência
- Use `inject()` em vez de injeção por construtor (padrão Angular moderno).

### Roteamento
```typescript
// app.routes.ts
export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./features/auth/login.component').then(m => m.LoginComponent) },
  {
    path: '',
    canActivate: [authGuard],
    component: AppShellComponent, // Contém bottom nav + router-outlet
    children: [
      { path: 'dashboard', loadComponent: ... },
      { path: 'solicitacoes', loadChildren: ... },
      { path: 'campo', loadChildren: ... },
      { path: 'perfil', loadComponent: ... },
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
    ]
  },
  { path: '**', redirectTo: 'dashboard' }
];
```

---

## 🧪 11. Qualidade e Testes

- **Unit tests:** Jasmine/Karma para services críticos (`AuthService`, `SyncService`, `OutboxService`).
- **Não escreva testes para componentes UI** na fase inicial — priorize serviços de negócio.
- **Linting:** ESLint com regras Angular. Todos os arquivos devem passar em `ng lint` antes de commit.
- **Build de produção:** `ng build --configuration production`. Verifique bundle size regularmente.

---

## 🚫 12. O Que NUNCA Fazer

- ❌ Usar `NgModule` — apenas Standalone Components.
- ❌ Armazenar JWT em `localStorage`.
- ❌ Colocar lógica de negócio em componentes — use services.
- ❌ Fazer chamadas HTTP diretamente em componentes — use services.
- ❌ Usar `any` em TypeScript — tipar tudo corretamente.
- ❌ Ignorar erros de sync — todo item com erro deve ser visível e corrigível pelo usuário.
- ❌ Usar `setInterval` para polling — use eventos de conectividade e triggers explícitos.
- ❌ Exibir dados sensíveis em logs do console em produção.
- ❌ Quebrar a estrutura de diretórios definida na Seção 4.
- ❌ Introduzir dependências não listadas na Seção 3 sem justificativa documentada.

---

## 📌 13. Referências Rápidas

| Necessidade | Onde Buscar |
|---|---|
| Contratos de endpoint IAM/Auth | `/docs/` |
| Contratos RH (Colaboradores, Equipes, Veículos) | `/docs/` |
| Contratos Comercial (Contratos, Ações) | `/docs/api-docs-acoes.json` |
| Contratos Logística (Materiais, Estoque, Solicitações) | `/docs/logistica.md` |
| Contratos Campo (Execuções, Eventos) | `/docs/api-docs-execucoes-comerciais.json e /docs/api-docs-turnos.json` |
| Municípios e Bases Operacionais | `/docs/api-docs-bases-operacionais.json` |
| Regras de paginação da API | `PageResponse<T>` — qualquer arquivo `/docs/` |

> **Lembre-se:** Antes de implementar qualquer integração com a API, consulte o arquivo `/docs/` correspondente. Nunca assuma o contrato de um endpoint sem verificar a documentação.