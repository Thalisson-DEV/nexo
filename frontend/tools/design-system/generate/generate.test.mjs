import assert from 'node:assert/strict';
import { mkdtemp, mkdir, readFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';

import {
  GeneratorError,
  buildPlan,
  generate,
  parseCliArguments,
  toPascalCase,
  validateRequest,
} from './generate.mjs';

const makeRepository = async () => {
  const repositoryRoot = await mkdtemp(join(tmpdir(), 'ces-ds-generator-'));
  await mkdir(join(repositoryRoot, 'src', 'app', 'design-system', 'primitives'), {
    recursive: true,
  });
  await mkdir(join(repositoryRoot, 'src', 'app', 'design-system', 'forms'), { recursive: true });
  await mkdir(join(repositoryRoot, 'src', 'app', 'design-system', 'patterns'), { recursive: true });
  return repositoryRoot;
};

test('validates supported kinds and kebab-case names', () => {
  assert.doesNotThrow(() => validateRequest('primitive', 'sync-indicator'));
  assert.throws(() => validateRequest('overlay', 'sync-indicator'), GeneratorError);

  for (const invalidName of [
    'SyncIndicator',
    'sync_indicator',
    'sync indicator',
    '-sync',
    'sync-',
  ]) {
    assert.throws(() => validateRequest('form', invalidName), /Use kebab-case/);
  }
});

test('derives selector, class name, paths, and non-destructive patch guidance', () => {
  const repositoryRoot = join(tmpdir(), 'ces-generator-plan');
  const plan = buildPlan({ kind: 'pattern', name: 'sync-indicator', repositoryRoot });

  assert.equal(toPascalCase('sync-indicator'), 'SyncIndicator');
  assert.equal(plan.className, 'CesSyncIndicator');
  assert.equal(plan.selector, 'ces-sync-indicator');
  assert.equal(plan.files.length, 7);
  assert.match(plan.componentDir, /design-system[\\/]patterns[\\/]sync-indicator$/);
  assert.ok(plan.guidance.some((item) => item.path === 'src/app/design-system/patterns/index.ts'));
  assert.ok(plan.guidance.some((item) => item.path === 'docs/design-system/prime-ng-coverage.md'));
  assert.ok(plan.guidance.some((item) => item.path === 'docs/design-system/testing.md'));
});

test('dry-run renders the complete plan without creating the target directory', async () => {
  const repositoryRoot = await makeRepository();
  try {
    const plan = await generate({
      kind: 'form',
      name: 'work-order-field',
      repositoryRoot,
      dryRun: true,
    });

    assert.equal(plan.dryRun, true);
    assert.equal(plan.files.length, 7);
    assert.match(plan.files[0].content, /selector: 'ces-work-order-field'/);
    await assert.rejects(readFile(join(plan.componentDir, 'index.ts'), 'utf8'), { code: 'ENOENT' });
  } finally {
    await rm(repositoryRoot, { recursive: true, force: true });
  }
});

test('generation creates standalone OnPush component assets, spec, fixture, docs stub, and barrel', async () => {
  const repositoryRoot = await makeRepository();
  try {
    const plan = await generate({
      kind: 'primitive',
      name: 'sync-indicator',
      repositoryRoot,
    });

    const component = await readFile(
      join(plan.componentDir, 'sync-indicator.component.ts'),
      'utf8',
    );
    const fixture = await readFile(
      join(plan.componentDir, 'sync-indicator.catalog.fixture.ts'),
      'utf8',
    );
    const documentation = await readFile(
      join(plan.componentDir, 'sync-indicator.documentation.stub.md'),
      'utf8',
    );
    const spec = await readFile(
      join(plan.componentDir, 'sync-indicator.component.spec.ts'),
      'utf8',
    );
    const barrel = await readFile(join(plan.componentDir, 'index.ts'), 'utf8');

    assert.match(component, /standalone: true/);
    assert.match(component, /ChangeDetectionStrategy\.OnPush/);
    assert.match(component, /templateUrl: '\.\/sync-indicator\.component\.html'/);
    assert.match(component, /styleUrl: '\.\/sync-indicator\.component\.css'/);
    assert.match(fixture, /status: 'experimental'/);
    assert.match(fixture, /committed, queued, syncing, synced, and conflict/);
    assert.match(documentation, /docs\/design-system\/prime-ng-coverage\.md/);
    assert.match(spec, /TestBed/);
    assert.equal(barrel, "export * from './sync-indicator.component';\n");
  } finally {
    await rm(repositoryRoot, { recursive: true, force: true });
  }
});

test('rejects an existing component path without overwriting it', async () => {
  const repositoryRoot = await makeRepository();
  try {
    const target = join(
      repositoryRoot,
      'src',
      'app',
      'design-system',
      'patterns',
      'existing-pattern',
    );
    await mkdir(target, { recursive: true });

    await assert.rejects(
      generate({ kind: 'pattern', name: 'existing-pattern', repositoryRoot }),
      /Target already exists/,
    );
  } finally {
    await rm(repositoryRoot, { recursive: true, force: true });
  }
});

test('parses help, dry-run, and rejects unknown options', () => {
  assert.deepEqual(parseCliArguments(['--help']), { help: true });
  assert.deepEqual(parseCliArguments(['form', 'search-field', '--dry-run']), {
    help: false,
    dryRun: true,
    kind: 'form',
    name: 'search-field',
  });
  assert.throws(() => parseCliArguments(['primitive', 'badge', '--force']), /Unknown option/);
});
