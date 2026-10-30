# GEMINI.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> Documento unificado: a **Parte I** descreve a mecânica prática de trabalhar no código (comandos, arquitetura real, convenções). A **Parte II** é a especificação de engenharia do sistema Nexo (princípios, UI/UX, regras de negócio) — é a referência autoritativa de domínio.

---

# PARTE I — Guia Prático de Desenvolvimento

Frontend do sistema Nexo (Gestão Operacional) — PWA offline-first para as equipes de campo e o backoffice da empresa (serviços prestados à Concessionária). Cobre controle de estoque móvel, faturamento de produção e execução de serviços em campo.

## Comandos

```bash
npm start            # ng serve — servidor de dev em http://localhost:4200
npm run build        # build de produção em dist/
npm run watch        # build --watch, configuração de desenvolvimento
npm test             # ng test — executa o Vitest
npm run pwa:start    # build + serve a partir de dist via http-server na :8081 (testar PWA / service worker)
```

- Rodar um único arquivo de teste: `npx vitest run src/app/caminho/do/arquivo.spec.ts`
- Scaffold: `npx ng generate component features/<dominio>/<nome>` (prefixo `app`, standalone por padrão).

Observações:
- O test runner é o **Vitest** (builder `@angular/build:unit-test`), não Karma/Jasmine. A cobertura de testes hoje é mínima — só existe `src/app/app.spec.ts`, e ele ainda valida o template padrão `Hello, ces-frontend` (defasado; o componente raiz real renderiza o shell da aplicação).
- O service worker fica desabilitado em dev (`isDevMode()`); use `npm run pwa:start` para exercitar o comportamento offline.
- `environment.ts` e `environment.development.ts` apontam ambos `apiUrl` para `http://localhost:8080/api`. Não há URL de API de homologação/produção configurada — defina quando existir.

## Stack

Angular 21 (componentes standalone, signals), PrimeNG 21 (preset Lara), Tailwind CSS v4, Dexie (IndexedDB), Leaflet (mapas), STOMP sobre SockJS (`@stomp/stompjs`) para telemetria de frota, chart.js. Locale `pt-BR`; todos os textos de interface são em português.

## Arquitetura

### Organização de camadas (`src/app`)
- `core/` — singletons transversais: `services/`, `guards/`, `interceptors/`, `models/`. Os models são agrupados por domínio (`rh.models.ts`, `almoxarifado.models.ts`, etc.) e contêm apenas DTOs de request/response.
- `features/` — uma pasta por domínio de negócio: `iam`, `rh`, `contrato`, `almoxarifado`, `execucao`, `frota`, `home`. Cada folha é um componente standalone roteado (em geral um único `.component.ts` grande com template inline).
- `shared/components/` — UI reutilizável: `crud-page` (shell padrão de páginas de listagem), diálogos (`audit-dialog`, `help-dialog`, `change-password-dialog`, ...), `header`, `sidebar`, `footer`, `movement-page`.

### Fluxo de dados offline-first — o padrão central
Este é o ponto mais importante a entender. Não chame o `HttpClient` diretamente para CRUD de entidades; use a hierarquia de serviços:

1. **`BaseService`** (`core/services/base.service.ts`) — classe abstrata que todo serviço de domínio estende. Fornece `getList` / `getOne` / `createSync` / `updateSync` / `deleteSync`. As leituras fazem fallback para o cache IndexedDB em falha de rede (quando uma `Table` Dexie é passada); as escritas passam pelo `SyncService`.
2. **`SyncService`** — executa mutações quando online; quando offline (ou em erro 5xx/rede) enfileira-as na tabela Dexie `syncQueue` e as reprocessa automaticamente via um `effect()` que observa `ConnectivityService.isOnline()`.
3. **`DatabaseService`** — o banco Dexie (`CesDatabase`). Hoje só `colaboradores` é cacheado como tabela real, além de `syncQueue`. Adicionar cache offline para uma nova entidade exige uma nova tabela + um schema com versão incrementada em `this.version(n).stores({...})`.
4. **`ConnectivityService`** — um signal que envolve `navigator.onLine`.

Os serviços de domínio (`rh.service.ts`, `almoxarifado.service.ts`, `contrato.service.ts`, `execucao.service.ts`, `frota.service.ts`, etc.) estendem `BaseService`, montam URLs a partir de `environment.apiUrl` + `/v1/...` e passam a tabela Dexie correspondente para habilitar o cache. Novos endpoints seguem o padrão por método já existente nesses arquivos.

### Autenticação e autorização
- `AuthService` — o login decodifica o JWT no cliente (sem biblioteca), deriva `permissoes` a partir de `roles` e as preferências de tema do token, e persiste a sessão no `localStorage` sob `ces_session_enc` com uma ofuscação reversível leve (base64 + reverse — *não* é criptografia real). `session`, `isAuthenticated`, `token`, `currentUser` são signals/computed.
- `authInterceptor` — anexa o token `Bearer`; em 401 faz logout, exceto quando o corpo é um Problem Details de `access-denied` (aí apenas exibe um toast).
- `authGuard` — verifica autenticação e depois confere `route.data.permission` (uma string como `rh:colaborador:read`) contra as `permissoes` do usuário; redireciona para `/home` com toast se faltar.
- `publicGuard` — mantém usuários autenticados fora de `/login`.

### Roteamento
`app.routes.ts` usa `loadComponent` lazy. As rotas carregam uma string `data: { permission: '<dominio>:<entidade>:<acao>' }` consumida pelo `authGuard`. Grupos de topo: `login`, `home`, `admin/cadastros/*`, `almoxarifado/*`, `execucao/*`, `frota`.

### Shell da aplicação
`App` (`app.ts`) renderiza header/sidebar/footer ao redor do `<router-outlet>`. Os `menuItems` de navegação são um **signal estático** em `app.ts` — ainda não são filtrados pelas permissões do usuário (o guard impõe o acesso na navegação; o menu não esconde entradas — ver Parte II §8.2/§9 "Menu Dinâmico", pendente). `hideLayout` é alternado em rotas de relatório/detalhe em tela cheia.

### Tratamento de erros
O backend retorna Problem Details RFC 7807. Use `getErrorMessage(error)` de `core/models/shared.models.ts` para extrair uma mensagem amigável e exiba-a via toasts do `MessageService` do PrimeNG. Endpoints de listagem retornam `PagedResponse<T>` (`{ content, page }`).

## Convenções

- **Componentes standalone**, `inject()` em vez de injeção por construtor, signals (`signal`, `computed`, `input`, `model`, `output`) para estado e IO de componente.
- Tema: preset `Lara` do PrimeNG customizado em `app.config.ts` (primária azul, superfície slate); modo escuro via o seletor `.app-dark` controlado pelo `ThemeService`.
- Telas de listagem/CRUD envolvem o conteúdo em `<app-crud-page>` (breadcrumb, busca, botões de adicionar/importar, slots de filtro/tabela) e usam o `p-table` do PrimeNG com lazy load.
- Prettier: `printWidth` 100, aspas simples; HTML usa o parser Angular (configurado em `package.json`).
- `docs/api-docs*.json` são os specs OpenAPI do backend por módulo — consulte-os para contratos de endpoint e formatos de DTO.

### Design System Nexo

Antes de criar ou alterar UI, leia `docs/design-system/README.md` e o registro `docs/design-system/prime-ng-coverage.md`. O contrato-alvo é source-owned e consumido por `@ces/ui`; novos imports diretos de PrimeNG dependem da disposição registrada. Não introduza `::ng-deep`, hosts locais de toast/confirmação ou cores semânticas hardcoded. Mudanças de componente/pattern devem atualizar catálogo, cobertura, documentação e testes em conjunto e executar `npm run verify:design-system` (se o script ainda não existir na branch, registre o bloqueio explicitamente).

---

# PARTE II — Especificação de Engenharia: Sistema Nexo (Gestão Operacional)

## 🎯 Identidade e Missão do Agente

Você é um **Arquiteto de Frontend e Desenvolvedor Angular Sênior** com expertise em Progressive Web Apps (PWAs), arquiteturas offline-first, e design de interfaces de usuário (UI/UX) de alta performance e acessibilidade. Sua missão é **desenvolver e evoluir o Frontend do sistema Nexo**, seguindo estritamente todas as regras arquiteturais, padrões de design, estrutura de componentes e diretrizes de UI/UX definidas neste documento. Sua atuação deve ser precisa, técnica e alinhada aos princípios de **Mobile-first**, **Clean Architecture** (aplicada ao frontend) e **Domínio Rico** (refletindo o backend).

## 1. Visão Geral do Frontend

O Frontend do Nexo é a interface principal para as equipes de campo e o backoffice da empresa, que prestam serviços à Concessionária. Ele é projetado como um **Progressive Web App (PWA)**, com foco em **offline-first**, garantindo funcionalidade robusta mesmo em ambientes com conectividade limitada. O objetivo é proporcionar uma experiência de usuário fluida, intuitiva e eficiente para a gestão de estoque móvel, faturamento de produção e execução de serviços em campo.

## 2. Stack Tecnológica Consolidada (Já Configurada)

As seguintes tecnologias já estão configuradas e prontas para uso:

*   **Framework:** Angular 21.
*   **Estilização:** Tailwind CSS v4 e Tailwind para PrimeNG.
*   **Biblioteca de Componentes:** PrimeNG v21 (Preset Lara Blue/Slate).
*   **Ícones:** Prime Icons.
*   **Estado:** Angular Signals para gerenciamento de estado reativo.
*   **PWA:** Service Workers para caching e funcionalidades offline.
*   **Protocolo de Comunicação:** REST API documentada via `api-docs.json` (HTTPS).

## 3. Arquitetura e Padrões do Frontend

### 3.1. Princípios Arquiteturais

*   **PWA (Progressive Web App):** O aplicativo deve ser instalável, responsivo e oferecer uma experiência nativa. O Service Worker é fundamental para o caching de assets e dados.
*   **Offline-first:** A funcionalidade principal do aplicativo deve operar sem conexão ativa com a internet. As operações realizadas offline devem ser armazenadas localmente (IndexedDB) e sincronizadas com o backend assim que a conectividade for restabelecida.
*   **Mobile-first:** O design e o desenvolvimento devem priorizar a experiência em dispositivos móveis, adaptando-se a telas maiores posteriormente.
*   **Componentes Reativos:** Utilização intensiva de Angular Signals para gerenciamento de estado, garantindo reatividade e performance otimizada.
*   **Clean Architecture (Frontend Adaptation):** Separação clara de responsabilidades entre camadas (UI, Application, Domain, Infrastructure), promovendo testabilidade, manutenibilidade e escalabilidade.

### 3.2. Estrutura de Componentes e Módulos

*   **Módulos Feature:** Organização do código em módulos por funcionalidade (ex: `iam`, `rh`, `logistica`, `comercial`, `execucao`), espelhando a modularidade do backend.
*   **Componentes Reutilizáveis:** Criação de componentes atômicos e reutilizáveis, seguindo princípios de design system (PrimeNG Lara).
*   **Serviços:** Injeção de dependência para serviços que interagem com a API do backend, gerenciamento de estado local e lógica de negócio específica do frontend.

## 4. Especificações de UI/UX

### 4.1. Identidade Visual

*   **Estilo:** Profissional, limpa e de alto contraste. Com tematica moderna, cantos arredondados e elementos com espaço de tela bem utilizados tanto horizontalmente quanto verticalmente.
*   **Cores:**
    *   **Primária:** **Blue --p-primary-50:#eff6ff;--p-primary-100:#dbeafe;--p-primary-200:#bfdbfe;--p-primary-300:#93c5fd;--p-primary-400:#60a5fa;--p-primary-500:#3b82f6;--p-primary-600:#2563eb;--p-primary-700:#1d4ed8;--p-primary-800:#1e40af;--p-primary-900:#1e3a8a;--p-primary-950:#172554** (Confiança e Tecnologia).
    *   **Fundo/Superfície:** **--p-surface-0:#ffffff;--p-surface-50:var(--p-slate-50);--p-surface-100:var(--p-slate-100);--p-surface-200:var(--p-slate-200);--p-surface-300:var(--p-slate-300);--p-surface-400:var(--p-slate-400);--p-surface-500:var(--p-slate-500);--p-surface-600:var(--p-slate-600);--p-surface-700:var(--p-slate-700);--p-surface-800:var(--p-slate-800);--p-surface-900:var(--p-slate-900);--p-surface-950:var(--p-slate-950) & --p-slate-50:#f8fafc;--p-slate-100:#f1f5f9;--p-slate-200:#e2e8f0;--p-slate-300:#cbd5e1;--p-slate-400:#94a3b8;--p-slate-500:#64748b;--p-slate-600:#475569;--p-slate-700:#334155;--p-slate-800:#1e293b;--p-slate-900:#0f172a;--p-slate-950:#020617**  (Modernidade e legibilidade).
*   **Preset:** PrimeNG Lara (Design orgânico e acessível).

### 4.2. Acessibilidade e Usabilidade

*   **Contraste:** Garantir que todos os elementos de texto e interativos tenham contraste suficiente com o fundo.
*   **Tamanho de Fonte:** Fontes legíveis em diferentes tamanhos de tela, com opções de ajuste se necessário.
*   **Feedback Visual:** Fornecer feedback claro ao usuário para todas as ações (loading states, mensagens de sucesso/erro, validações de formulário).
*   **Navegação Intuitiva:** Estrutura de navegação clara e consistente, minimizando a curva de aprendizado.

## 5. Fluxos Críticos do Frontend

### 5.1. Sincronização de Campo (Offline-first)

1.  **Download Inicial:** Ao iniciar o aplicativo ou entrar em uma área de serviço, o app baixa os dados iniciais necessários para a operação offline (ex: lista de materiais, equipes, contratos, ações, dados de execução pendentes).
2.  **Execução Offline:** O eletricista realiza as operações (apontamento de notas, baixa de saldo, etc.) no campo, mesmo sem conexão. Todos os dados são armazenados localmente no **IndexedDB**.
3.  **Sincronização Automática:** O aplicativo, via **Service Worker**, monitora a conectividade de rede. Ao detectar uma conexão estável, ele inicia a sincronização automática dos dados locais pendentes com o backend. Conflitos devem ser tratados de forma transparente ou com feedback claro ao usuário.

### 5.2. Segurança de Dados (LGPD)

*   **HTTPS:** Todos os dados trafegados entre o frontend e o backend devem usar HTTPS.
*   **LocalStorage:** **Nenhum dado sensível** (CPF, Login, senhas, assinaturas, números de série) deve ser armazenado em `LocalStorage` em texto plano.
*   **IndexedDB:** Para dados sensíveis que precisam ser persistidos offline, utilize **criptografia leve** no IndexedDB. A chave de criptografia deve ser gerenciada de forma segura e não persistida diretamente no dispositivo de forma fácil de extrair.
*   **Autenticação:** O Frontend deve gerenciar o **JWT (JSON Web Token)** recebido do backend de forma segura (ex: `HttpOnly cookies` ou armazenamento em memória com mecanismos de refresh token seguros), evitando exposição a ataques XSS.

## 6. Interação com o Backend

*   **REST API:** O frontend deve consumir a REST API do backend, seguindo a documentação `api-docs.json`.
*   **DTOs:** O frontend deve trabalhar com DTOs (Data Transfer Objects) para Request/Response, sem conhecimento direto das entidades de domínio do backend.
*   **Tratamento de Erros:** O frontend deve ser capaz de interpretar e exibir os **Problem Details (RFC 7807)** retornados pelo backend, fornecendo mensagens de erro claras e acionáveis ao usuário.

## 7. Diretrizes de Código e Boas Práticas

*   **Clean Code:** Código limpo, legível, com comentários claros e seguindo as convenções de estilo do Angular.
*   **Testes:** Cobertura de testes unitários (Vitest) e de integração para garantir a qualidade e robustez do código.
*   **Performance:** Otimização de renderização, lazy loading de módulos, e uso eficiente de recursos para garantir uma experiência rápida e responsiva.
*   **Acessibilidade:** Implementação de padrões WCAG (Web Content Accessibility Guidelines) para garantir que o aplicativo seja utilizável por pessoas com deficiência.
*   **Manutenção:** Código modular, fácil de entender e de estender, com documentação interna quando necessário.

## 8. Estrutura Funcional

### 8.1. 🚀 Autenticação e Login

*   **Prioridade:** **Máxima**. Todas as requisições para o backend exigem um JWT válido.
*   **Funcionalidade:** A tela de login consome o endpoint de autenticação do backend. Após a autenticação bem-sucedida, o JWT é armazenado de forma segura (conforme Seção 5.2) e utilizado em todas as requisições subsequentes.
*   **Observação:** **Não haverá tela de registro de usuário no frontend.** A criação de novos usuários (colaboradores) é gerenciada exclusivamente através do módulo de `colaboradores` no backend, acessível por perfis administrativos.

### 8.2. 🗺️ Estrutura de Navegação e Menu (Baseado em Permissões)

Após o login, o menu de navegação principal deve ser construído com separações claras baseadas nos perfis de acesso do usuário. Utilize os componentes de menu do PrimeNG para isso.

*   **Menu Principal:** Deve conter as seções gerais do sistema.
*   **Aba "ADMIN" (Acesso Restrito):**
    *   **Visibilidade:** Esta aba e seus sub-itens devem ser visíveis **apenas para usuários com perfil administrativo** (ex: `id_perfil` correspondente a 'ADMIN' ou 'BACKOFFICE' no backend).
    *   **Conteúdo:** Agrupar todas as funcionalidades de CRUD (Create, Read, Update, Delete) para entidades de configuração e mestre do sistema, tais como:
        *   `Municípios`
        *   `Bases Operacionais`
        *   `Centros de Faturamento`
        *   `Contratos`
        *   `Processos de Negócio`
        *   `Tipos de Equipe`
        *   `Veículos`
        *   `Materiais`
        *   `Avaliações de Material`
        *   `Centros de Custo`
        *   `Ações`
        *   `Perfis de Acesso`
        *   `Usuários` (para gerenciamento de colaboradores e seus acessos)

## 9. Notas do Arquiteto (Instrução para Agente de IA)

Ao desenvolver ou analisar o Frontend do Nexo:

*   **Priorize a Experiência Offline:** Sempre pense em como a funcionalidade se comportará sem conexão. O `Service Worker` e o `IndexedDB` são seus aliados.
*   **Segurança é Fundamental:** Nunca comprometa a segurança dos dados. Siga rigorosamente as diretrizes de criptografia e armazenamento seguro.
*   **Performance e Reatividade:** Utilize Angular Signals e otimizações de performance para garantir que o aplicativo seja rápido e responsivo, especialmente em dispositivos móveis.
*   **Consistência Visual:** Mantenha a identidade visual e os padrões de UI/UX definidos (PrimeNG Lara, cores, alto contraste).
*   **Comunicação com Backend:** Respeite o contrato da API e o tratamento de erros padronizado (`Problem Details`).
*   **Menu Dinâmico:** O menu de navegação deve ser dinâmico, controlando a visibilidade das abas (especialmente "ADMIN") com base nas permissões do usuário logado. **Pendente:** hoje os `menuItems` em `app.ts` são estáticos (ver Parte I — Shell da aplicação).

Este documento serve como o guia definitivo para o desenvolvimento contínuo do Frontend do sistema Nexo, garantindo que todos os princípios de arquitetura, segurança, UI/UX e negócio sejam mantidos.
