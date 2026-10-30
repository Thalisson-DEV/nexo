import { definePreset } from '@primeuix/themes';
import Lara from '@primeuix/themes/lara';

/**
 * Tema oficial do Nexo.
 *
 * A paleta primária pode ser trocada em tempo de execução pelo ThemeService. Estados
 * semânticos (sucesso, alerta e erro) permanecem independentes dessa escolha.
 */
export const cesPreset = definePreset(Lara, {
  semantic: {
    primary: {
      50: '{blue.50}',
      100: '{blue.100}',
      200: '{blue.200}',
      300: '{blue.300}',
      400: '{blue.400}',
      500: '{blue.500}',
      600: '{blue.600}',
      700: '{blue.700}',
      800: '{blue.800}',
      900: '{blue.900}',
      950: '{blue.950}'
    },
    colorScheme: {
      light: {
        surface: {
          0: '#ffffff',
          50: '{slate.50}',
          100: '{slate.100}',
          200: '{slate.200}',
          300: '{slate.300}',
          400: '{slate.400}',
          500: '{slate.500}',
          600: '{slate.600}',
          700: '{slate.700}',
          800: '{slate.800}',
          900: '{slate.900}',
          950: '{slate.950}'
        }
      },
      dark: {
        surface: {
          0: '{slate.950}',
          50: '{slate.900}',
          100: '{slate.800}',
          200: '{slate.700}',
          300: '{slate.600}',
          400: '{slate.500}',
          500: '{slate.400}',
          600: '{slate.300}',
          700: '{slate.200}',
          800: '{slate.100}',
          900: '{slate.50}',
          950: '#ffffff'
        }
      }
    }
  },
  components: {
    dialog: {
      header: {
        padding: '1.25rem 1.5rem',
        gap: '0.5rem'
      },
      content: {
        padding: '1.25rem 1.5rem'
      },
      footer: {
        padding: '1.25rem 1.5rem',
        gap: '0.5rem'
      }
    },
    // O Lara escreve o detalhe do toast escuro com {surface.0}, que ele supõe branco. Como a
    // escala escura do Nexo é invertida (surface.0 = slate.950), o texto sumia no fundo.
    toast: {
      colorScheme: {
        dark: {
          info: { detailColor: '{slate.100}' },
          success: { detailColor: '{slate.100}' },
          warn: { detailColor: '{slate.100}' },
          error: { detailColor: '{slate.100}' },
          secondary: { detailColor: '{slate.100}' }
        }
      }
    }
  }
});
