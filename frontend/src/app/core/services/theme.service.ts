import { Injectable, inject, signal } from '@angular/core';
import { updatePreset } from '@primeuix/themes';
import { PrimeNG } from 'primeng/config';
import { CesDensity } from '../../design-system/core';

export const CES_PRIMARY_COLORS = ['blue', 'emerald', 'violet', 'amber', 'rose', 'slate'] as const;
export type CesPrimaryColor = (typeof CES_PRIMARY_COLORS)[number];

@Injectable({
  providedIn: 'root'
})
export class ThemeService {
  private primeng = inject(PrimeNG);
  
  private readonly STORAGE_KEY = 'ces_theme_mode';
  private readonly COLOR_KEY = 'ces_primary_color';
  private readonly RIPPLE_KEY = 'ces_ripple_enabled';
  private readonly DENSITY_KEY = 'ces_density';

  isDarkMode = signal<boolean>(false);
  primaryColor = signal<CesPrimaryColor>('blue');
  rippleEnabled = signal<boolean>(true);
  density = signal<CesDensity>('compact');

  constructor() {
    // Fallback inicial via LocalStorage
    const savedMode = localStorage.getItem(this.STORAGE_KEY);
    const savedColor = localStorage.getItem(this.COLOR_KEY);
    const savedRipple = localStorage.getItem(this.RIPPLE_KEY);
    const savedDensity = localStorage.getItem(this.DENSITY_KEY);

    if (savedMode === 'dark') this.setDarkMode(true);
    if (this.isPrimaryColor(savedColor)) this.updatePrimaryColor(savedColor);
    if (savedRipple === 'false') this.setRipple(false);
    this.setDensity(savedDensity === 'comfortable' ? 'comfortable' : 'compact');
  }

  setDarkMode(isDark: boolean) {
    this.isDarkMode.set(isDark);
    if (isDark) {
      document.documentElement.classList.add('app-dark', 'dark');
      localStorage.setItem(this.STORAGE_KEY, 'dark');
    } else {
      document.documentElement.classList.remove('app-dark', 'dark');
      localStorage.setItem(this.STORAGE_KEY, 'light');
    }
  }

  setRipple(enabled: boolean) {
    const isEnabled = enabled ?? true;
    this.rippleEnabled.set(isEnabled);
    localStorage.setItem(this.RIPPLE_KEY, isEnabled.toString());
    
    if (this.primeng?.ripple) {
      this.primeng.ripple.set(isEnabled);
    }
  }

  setDensity(density: CesDensity) {
    this.density.set(density);
    document.documentElement.dataset['cesDensity'] = density;
    localStorage.setItem(this.DENSITY_KEY, density);
  }

  updatePrimaryColor(colorName: string) {
    const supportedColor = this.isPrimaryColor(colorName) ? colorName : 'blue';
    this.primaryColor.set(supportedColor);
    localStorage.setItem(this.COLOR_KEY, supportedColor);

    updatePreset({
      semantic: {
        primary: {
          50: `{${supportedColor}.50}`,
          100: `{${supportedColor}.100}`,
          200: `{${supportedColor}.200}`,
          300: `{${supportedColor}.300}`,
          400: `{${supportedColor}.400}`,
          500: `{${supportedColor}.500}`,
          600: `{${supportedColor}.600}`,
          700: `{${supportedColor}.700}`,
          800: `{${supportedColor}.800}`,
          900: `{${supportedColor}.900}`,
          950: `{${supportedColor}.950}`
        }
      }
    });
  }

  private isPrimaryColor(value: string | null): value is CesPrimaryColor {
    return CES_PRIMARY_COLORS.some((color) => color === value);
  }
}
