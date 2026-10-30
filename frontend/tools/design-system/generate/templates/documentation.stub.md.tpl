# {{CLASS_NAME}}

> Integration stub. Promote this content to `docs/design-system/components/{{NAME}}.md` and
> remove this co-located file after the authoritative documentation is integrated.

## Objective

TODO: Describe the user problem and why this belongs in the design system rather than a feature.

## Status and import

- Status: experimental
- Selector: `ces-{{NAME}}`
- Public entrypoint after barrel integration: `{{PUBLIC_ENTRYPOINT}}`
- Introduced in: TODO

## Minimal example

```html
<ces-{{NAME}} ariaLabel="Descrição acessível">Conteúdo</ces-{{NAME}}>
```

## Public API

TODO: Document inputs, outputs, slots, defaults, and semantic decisions. Do not expose arbitrary
CSS or PrimeNG internals.

## Required states

Document relevant interaction, data, permission, and offline states. When synchronization applies,
use exactly: `committed`, `queued`, `syncing`, `synced`, and `conflict`.

## Responsive behavior and accessibility

TODO: Cover mobile-first layout, keyboard, focus, screen reader, 200% zoom/reflow, contrast, dark
mode, and `prefers-reduced-motion`.

## PrimeNG relationship

TODO: State whether this component is dependency-free or which registered PrimeNG entrypoint it
wraps. Update `docs/design-system/prime-ng-coverage.md` if the disposition or integration changes.

## Anti-patterns and migration

TODO: List unsafe local alternatives, migration steps, and deprecation notes when replacing an API.

## Tests

{{COVERAGE_GUIDANCE}}

Also include catalog integration, deterministic fixtures, and all relevant release checks from
`docs/design-system/testing.md`.
