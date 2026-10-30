import { defineConfig } from 'vitest/config';
import fs from 'node:fs';
import path from 'node:path';

function angularInlinePlugin() {
  return {
    name: 'angular-inline-plugin',
    transform(code: string, id: string) {
      if (!id.endsWith('.ts') || id.includes('node_modules')) return null;
      let newCode = code;
      newCode = newCode.replace(/templateUrl:\s*['"](\.[^'"]+)['"]/g, (match, relPath) => {
        const fullPath = path.resolve(path.dirname(id), relPath);
        if (fs.existsSync(fullPath)) {
          const content = fs.readFileSync(fullPath, 'utf-8');
          return `template: ${JSON.stringify(content)}`;
        }
        return match;
      });
      newCode = newCode.replace(/styleUrl:\s*['"](\.[^'"]+)['"]/g, (match, relPath) => {
        const fullPath = path.resolve(path.dirname(id), relPath);
        if (fs.existsSync(fullPath)) {
          const content = fs.readFileSync(fullPath, 'utf-8');
          return `styles: [${JSON.stringify(content)}]`;
        }
        return match;
      });
      newCode = newCode.replace(/styleUrls:\s*\[([^\]]+)\]/g, () => `styles: []`);
      return { code: newCode, map: null };
    },
  };
}

export default defineConfig({
  plugins: [angularInlinePlugin()],
  test: {
    environment: 'jsdom',
    globals: true,
  },
  resolve: {
    alias: [
      { find: /^@ces\/ui\/forms$/, replacement: path.resolve(__dirname, './src/app/design-system/forms/index.ts') },
      { find: /^@ces\/ui\/autocomplete$/, replacement: path.resolve(__dirname, './src/app/design-system/autocomplete/index.ts') },
      { find: /^@ces\/ui\/data$/, replacement: path.resolve(__dirname, './src/app/design-system/data/index.ts') },
      { find: /^@ces\/ui\/overlays$/, replacement: path.resolve(__dirname, './src/app/design-system/overlays/index.ts') },
      { find: /^@ces\/ui\/feedback$/, replacement: path.resolve(__dirname, './src/app/design-system/feedback/index.ts') },
      { find: /^@ces\/ui\/patterns$/, replacement: path.resolve(__dirname, './src/app/design-system/patterns/index.ts') },
      { find: /^@ces\/ui\/testing$/, replacement: path.resolve(__dirname, './src/app/design-system/testing/index.ts') },
      { find: /^@ces\/ui$/, replacement: path.resolve(__dirname, './src/app/design-system/index.ts') },
    ],
  },
});
