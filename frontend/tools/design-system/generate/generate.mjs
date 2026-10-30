#!/usr/bin/env node

import { randomUUID } from 'node:crypto';
import { constants } from 'node:fs';
import { access, mkdir, readFile, rename, rm, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { basename, dirname, join, relative, resolve, sep } from 'node:path';

const GENERATOR_ROOT = dirname(fileURLToPath(import.meta.url));
const DEFAULT_REPOSITORY_ROOT = resolve(GENERATOR_ROOT, '..', '..', '..');
const TEMPLATES_ROOT = join(GENERATOR_ROOT, 'templates');
const VALID_KINDS = Object.freeze(['primitive', 'form', 'pattern']);
const KEBAB_CASE = /^[a-z][a-z0-9]*(?:-[a-z0-9]+)*$/;
const HELP_FLAGS = new Set(['--help', '-h']);

const KIND_CONFIG = Object.freeze({
  primitive: {
    sourceArea: 'primitives',
    publicEntrypoint: '@ces/ui',
    catalogGroup: 'Primitives',
    coverage: 'Primitive simples: unitário + axe + visual + teclado.',
  },
  form: {
    sourceArea: 'forms',
    publicEntrypoint: '@ces/ui/forms',
    catalogGroup: 'Forms',
    coverage: 'Form: unitário + interação + axe + foco manual.',
  },
  pattern: {
    sourceArea: 'patterns',
    publicEntrypoint: '@ces/ui/patterns',
    catalogGroup: 'Patterns',
    coverage: 'Pattern offline, quando aplicável: unitário + integração de fila + E2E do fluxo.',
  },
});

const GENERATED_FILES = Object.freeze([
  { template: 'component.ts.tpl', suffix: '.component.ts' },
  { template: 'component.html.tpl', suffix: '.component.html' },
  { template: 'component.css.tpl', suffix: '.component.css' },
  { template: 'component.spec.ts.tpl', suffix: '.component.spec.ts' },
  { template: 'catalog.fixture.ts.tpl', suffix: '.catalog.fixture.ts' },
  { template: 'documentation.stub.md.tpl', suffix: '.documentation.stub.md' },
  { template: 'index.ts.tpl', fixedName: 'index.ts' },
]);

export class GeneratorError extends Error {
  constructor(message) {
    super(message);
    this.name = 'GeneratorError';
  }
}

export const helpText = `Nexo design-system generator

Usage:
  npm run ds:add -- <primitive|form|pattern> <name> [--dry-run]
  node tools/design-system/generate/generate.mjs <primitive|form|pattern> <name> [--dry-run]

Arguments:
  kind       Component layer: primitive, form, or pattern.
  name       Kebab-case component name, for example sync-indicator.

Options:
  --dry-run  Validate and print the file/patch plan without writing files.
  --help     Show this help.

Safety:
  The generator rejects an existing component directory. It never edits public barrels,
  the live catalog, documentation, or PrimeNG coverage automatically; it prints reviewable
  patch guidance instead.
`;

const pathExists = async (path) => {
  try {
    await access(path, constants.F_OK);
    return true;
  } catch (error) {
    if (error?.code === 'ENOENT') return false;
    throw error;
  }
};

const assertInsideRepository = (repositoryRoot, candidate) => {
  const repositoryRelative = relative(repositoryRoot, candidate);
  if (
    repositoryRelative === '' ||
    repositoryRelative === '..' ||
    repositoryRelative.startsWith(`..${sep}`) ||
    repositoryRelative.startsWith(sep)
  ) {
    throw new GeneratorError(`Refusing to generate outside the repository: ${candidate}`);
  }
};

export const validateRequest = (kind, name) => {
  if (!VALID_KINDS.includes(kind)) {
    throw new GeneratorError(
      `Invalid kind ${JSON.stringify(kind)}. Expected one of: ${VALID_KINDS.join(', ')}.`,
    );
  }

  if (!KEBAB_CASE.test(name ?? '')) {
    throw new GeneratorError(
      `Invalid name ${JSON.stringify(name)}. Use kebab-case, for example sync-indicator.`,
    );
  }
};

export const toPascalCase = (value) =>
  value
    .split('-')
    .map((part) => `${part[0].toUpperCase()}${part.slice(1)}`)
    .join('');

const renderTemplate = (template, replacements) =>
  template.replace(/\{\{([A-Z_]+)\}\}/g, (match, key) => {
    if (!(key in replacements)) {
      throw new GeneratorError(`Template placeholder ${match} has no replacement.`);
    }
    return replacements[key];
  });

const toRepositoryPath = (repositoryRoot, path) =>
  relative(repositoryRoot, path).split(sep).join('/');

const buildPatchGuidance = ({ kind, name, className, componentDir, repositoryRoot }) => {
  const config = KIND_CONFIG[kind];
  const entrypointIndex = resolve(
    repositoryRoot,
    'src',
    'app',
    'design-system',
    config.sourceArea,
    'index.ts',
  );
  const componentIndex = join(componentDir, 'index.ts');
  const catalogComponent = resolve(
    repositoryRoot,
    'src',
    'app',
    'design-system',
    'catalog',
    'design-system-catalog.component.ts',
  );
  const coverageDocument = resolve(repositoryRoot, 'docs', 'design-system', 'prime-ng-coverage.md');
  const documentationTarget = resolve(
    repositoryRoot,
    'docs',
    'design-system',
    'components',
    `${name}.md`,
  );
  const testingDocument = resolve(repositoryRoot, 'docs', 'design-system', 'testing.md');

  return [
    {
      title: `Export ${className}Component from the ${config.publicEntrypoint} entrypoint`,
      path: toRepositoryPath(repositoryRoot, entrypointIndex),
      patch: `export * from './${name}';`,
      note: `The generated local barrel is ${toRepositoryPath(repositoryRoot, componentIndex)}.`,
    },
    {
      title: `Register the ${config.catalogGroup} catalog fixture and rendered scenario`,
      path: toRepositoryPath(repositoryRoot, catalogComponent),
      patch: `Register the exported ${className}CatalogFixture from ${toRepositoryPath(repositoryRoot, join(componentDir, `${name}.catalog.fixture.ts`))}, or move its metadata into the catalog's chosen fixture registry.`,
      note: 'Add deterministic rest, disabled, loading/error, responsive, theme, keyboard, and offline states when relevant.',
    },
    {
      title: 'Promote the generated documentation stub into the authoritative docs tree',
      path: toRepositoryPath(repositoryRoot, documentationTarget),
      patch: `Move/adapt ${toRepositoryPath(repositoryRoot, join(componentDir, `${name}.documentation.stub.md`))} to this path.`,
      note: 'Keep the generated stub beside the component only until the documentation owner integrates it.',
    },
    {
      title:
        'Review PrimeNG coverage whenever implementation adds or changes a PrimeNG integration',
      path: toRepositoryPath(repositoryRoot, coverageDocument),
      patch:
        'Update the disposition, rationale, catalog relationship, and migration recipe if PrimeNG coverage changes.',
      note: 'No coverage edit is required for a dependency-free native Angular component with no new PrimeNG entrypoint.',
    },
    {
      title: 'Meet and update the documented test coverage where relevant',
      path: toRepositoryPath(repositoryRoot, testingDocument),
      patch: config.coverage,
      note: 'The generated Vitest spec is a smoke/accessibility baseline, not complete release coverage.',
    },
  ];
};

export const buildPlan = ({ kind, name, repositoryRoot = DEFAULT_REPOSITORY_ROOT }) => {
  validateRequest(kind, name);

  const config = KIND_CONFIG[kind];
  const className = `Ces${toPascalCase(name)}`;
  const componentDir = resolve(
    repositoryRoot,
    'src',
    'app',
    'design-system',
    config.sourceArea,
    name,
  );
  assertInsideRepository(repositoryRoot, componentDir);

  const replacements = Object.freeze({
    NAME: name,
    CLASS_NAME: className,
    KIND: kind,
    SOURCE_AREA: config.sourceArea,
    PUBLIC_ENTRYPOINT: config.publicEntrypoint,
    CATALOG_GROUP: config.catalogGroup,
    COVERAGE_GUIDANCE: config.coverage,
  });

  return {
    kind,
    name,
    className,
    selector: `ces-${name}`,
    repositoryRoot,
    componentDir,
    files: GENERATED_FILES.map(({ template, suffix, fixedName }) => ({
      templatePath: join(TEMPLATES_ROOT, template),
      targetPath: join(componentDir, fixedName ?? `${name}${suffix}`),
    })),
    guidance: buildPatchGuidance({ kind, name, className, componentDir, repositoryRoot }),
    replacements,
  };
};

export const generate = async ({
  kind,
  name,
  dryRun = false,
  repositoryRoot = DEFAULT_REPOSITORY_ROOT,
}) => {
  const plan = buildPlan({ kind, name, repositoryRoot });

  if (await pathExists(plan.componentDir)) {
    throw new GeneratorError(
      `Target already exists: ${toRepositoryPath(plan.repositoryRoot, plan.componentDir)}. No files were changed.`,
    );
  }

  const renderedFiles = [];
  for (const file of plan.files) {
    const template = await readFile(file.templatePath, 'utf8');
    renderedFiles.push({
      ...file,
      content: renderTemplate(template, plan.replacements),
    });
  }

  if (!dryRun) {
    const temporaryDir = join(
      dirname(plan.componentDir),
      `.${basename(plan.componentDir)}.ces-generate-${randomUUID()}`,
    );
    assertInsideRepository(plan.repositoryRoot, temporaryDir);

    try {
      await mkdir(temporaryDir, { recursive: false });
      for (const file of renderedFiles) {
        await writeFile(join(temporaryDir, basename(file.targetPath)), file.content, {
          encoding: 'utf8',
          flag: 'wx',
        });
      }
      await rename(temporaryDir, plan.componentDir);
    } catch (error) {
      await rm(temporaryDir, { recursive: true, force: true });
      throw new GeneratorError(
        `Generation failed while preparing ${basename(error?.path ?? plan.componentDir)}: ${error.message}. No target component files were kept.`,
      );
    }
  }

  return { ...plan, dryRun, files: renderedFiles };
};

const formatPlan = (plan) => {
  const lines = [
    `${plan.dryRun ? 'Dry run' : 'Generated'} ${plan.kind} ${plan.name}`,
    `Selector: ${plan.selector}`,
    `Target: ${toRepositoryPath(plan.repositoryRoot, plan.componentDir)}`,
    '',
    'Files:',
  ];

  for (const file of plan.files) {
    lines.push(
      `  ${plan.dryRun ? 'would create' : 'created'} ${toRepositoryPath(plan.repositoryRoot, file.targetPath)}`,
    );
  }

  lines.push('', 'Manual patch guidance (not applied):');
  for (const [index, item] of plan.guidance.entries()) {
    lines.push(`  ${index + 1}. ${item.title}`);
    lines.push(`     Path: ${item.path}`);
    lines.push(`     Patch: ${item.patch}`);
    lines.push(`     Note: ${item.note}`);
  }

  lines.push(
    '',
    'Verification after integration:',
    `  node --test tools/design-system/generate/generate.test.mjs`,
    `  npx vitest run ${toRepositoryPath(plan.repositoryRoot, join(plan.componentDir, `${plan.name}.component.spec.ts`))}`,
    '  npm run verify:design-system',
  );

  return `${lines.join('\n')}\n`;
};

export const parseCliArguments = (args) => {
  if (args.some((argument) => HELP_FLAGS.has(argument))) {
    return { help: true };
  }

  const dryRun = args.includes('--dry-run');
  const unknownOptions = args.filter(
    (argument) => argument.startsWith('-') && argument !== '--dry-run',
  );
  if (unknownOptions.length > 0) {
    throw new GeneratorError(`Unknown option(s): ${unknownOptions.join(', ')}.`);
  }

  const positional = args.filter((argument) => !argument.startsWith('-'));
  if (positional.length !== 2) {
    throw new GeneratorError(
      'Expected <primitive|form|pattern> and <name>. Use --help for examples.',
    );
  }

  return { help: false, dryRun, kind: positional[0], name: positional[1] };
};

export const runCli = async (args = process.argv.slice(2), io = console) => {
  try {
    const parsed = parseCliArguments(args);
    if (parsed.help) {
      io.log(helpText.trimEnd());
      return 0;
    }

    const plan = await generate(parsed);
    io.log(formatPlan(plan).trimEnd());
    return 0;
  } catch (error) {
    if (error instanceof GeneratorError) {
      io.error(`Error: ${error.message}`);
      io.error('Run with --help for usage.');
      return 1;
    }
    throw error;
  }
};

const isDirectExecution =
  process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isDirectExecution) {
  process.exitCode = await runCli();
}
