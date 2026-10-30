# AGENTS.md

## Leitura obrigatória

Antes de analisar, planejar ou alterar este repositório:

1. leia [`CLAUDE.md`](./CLAUDE.md), referência autoritativa de arquitetura e domínio;
2. leia [`docs/design-system/README.md`](./docs/design-system/README.md), referência autoritativa da interface Nexo;
3. aplique as instruções do arquivo mais específico quando existir outro `AGENTS.md` no caminho do arquivo alterado.

## Regras do Design System Nexo

- O Design System Nexo é **source-owned**, Angular 21 e PrimeNG 21. Nas features, consuma somente os entrypoints públicos `@ces/ui`, `@ces/ui/*` e os tokens/contratos documentados.
- Antes de qualquer import direto de `primeng/*`, consulte o [registro de cobertura](./docs/design-system/prime-ng-coverage.md). Import direto só é permitido quando a disposição do item for `direto permitido`, dentro das condições registradas. Caso contrário, use ou evolua `@ces/ui`.
- Não adicione novo `::ng-deep`, nem hosts locais de Toast, ConfirmDialog ou ConfirmPopup. Toasts e confirmações pertencem aos hosts globais do shell.
- Não use cores semânticas hardcoded (`red-*`, `green-*`, hex/RGB equivalentes etc.) para comunicar estado. Use variantes e tokens semânticos do Nexo; cor nunca deve ser o único indicador.
- Toda interface deve cobrir, quando aplicável: repouso, hover, focus-visible, active/pressed, selected, disabled, read-only, loading, skeleton, vazio, erro, sucesso, aviso, informação, offline, enfileirado, sincronizando, sincronizado e conflito.
- Operações offline devem usar o vocabulário normativo: **`committed` — salvo no dispositivo**, **`queued` — enfileirado para sincronização**, **`syncing` — sincronizando**, **`synced` — sincronizado** e **`conflict` — conflito de sincronização**. Nunca apresentar uma operação apenas enfileirada como concluída no servidor.
- Ao adicionar ou mudar um componente, padrão ou integração PrimeNG, atualize no mesmo trabalho: implementação, catálogo, registro de cobertura, documentação pertinente e testes automatizados.
- Execute `npm run verify:design-system` antes de concluir alterações de UI/design system, além dos testes específicos e do build aplicáveis. Se o script ainda não estiver disponível na branch, registre o bloqueio; não substitua silenciosamente a verificação por outro comando.

## Checklist mínimo de contribuição

- API pública acessada por `@ces/ui` e sem dependência de internals do PrimeNG.
- Variantes semânticas e estados documentados e testados.
- Tema claro/escuro, responsividade mobile-first, teclado, leitor de tela, contraste e redução de movimento verificados.
- Catálogo e `docs/design-system/prime-ng-coverage.md` atualizados.
- Sem nova exceção arquitetural não registrada.
- Migração e depreciação documentadas quando houver mudança incompatível.
