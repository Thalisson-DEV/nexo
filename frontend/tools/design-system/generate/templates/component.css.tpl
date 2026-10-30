:host {
  display: block;
  min-width: 0;
}

:host(:focus-visible) {
  outline: var(--ces-focus-ring-width, 0.125rem) solid var(--ces-focus-ring-color, currentColor);
  outline-offset: var(--ces-focus-ring-offset, 0.125rem);
}

@media (prefers-reduced-motion: reduce) {
  :host {
    scroll-behavior: auto;
  }
}
