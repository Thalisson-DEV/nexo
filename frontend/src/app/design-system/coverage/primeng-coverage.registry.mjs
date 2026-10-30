/**
 * @param {'wrapper' | 'pattern' | 'styled-direct' | 'infrastructure' | 'excluded'} disposition
 * @param {string} rationale
 * @param {readonly string[]} entrypoints
 */
const defineEntries = (disposition, rationale, entrypoints) =>
  entrypoints.map((entrypoint) => ({ entrypoint, disposition, rationale }));

const wrapperEntrypoints = [
  'primeng/autocomplete',
  'primeng/button',
  'primeng/checkbox',
  'primeng/datepicker',
  'primeng/fileupload',
  'primeng/inputmask',
  'primeng/inputnumber',
  'primeng/inputotp',
  'primeng/inputtext',
  'primeng/listbox',
  'primeng/multiselect',
  'primeng/password',
  'primeng/radiobutton',
  'primeng/select',
  'primeng/selectbutton',
  'primeng/slider',
  'primeng/textarea',
  'primeng/togglebutton',
  'primeng/toggleswitch',
  'primeng/treeselect'
];

const patternEntrypoints = [
  'primeng/breadcrumb',
  'primeng/card',
  'primeng/carousel',
  'primeng/dataview',
  'primeng/fieldset',
  'primeng/floatlabel',
  'primeng/fluid',
  'primeng/iconfield',
  'primeng/iftalabel',
  'primeng/inplace',
  'primeng/inputgroup',
  'primeng/inputgroupaddon',
  'primeng/inputicon',
  'primeng/orderlist',
  'primeng/organizationchart',
  'primeng/overlaybadge',
  'primeng/paginator',
  'primeng/panel',
  'primeng/picklist',
  'primeng/splitter',
  'primeng/stepper',
  'primeng/table',
  'primeng/tabs',
  'primeng/timeline',
  'primeng/toolbar',
  'primeng/tree',
  'primeng/treetable'
];

const styledDirectEntrypoints = [
  'primeng/accordion',
  'primeng/animateonscroll',
  'primeng/autofocus',
  'primeng/avatar',
  'primeng/avatargroup',
  'primeng/badge',
  'primeng/blockui',
  'primeng/buttongroup',
  'primeng/cascadeselect',
  'primeng/chart',
  'primeng/chip',
  'primeng/colorpicker',
  'primeng/confirmdialog',
  'primeng/confirmpopup',
  'primeng/contextmenu',
  'primeng/dialog',
  'primeng/divider',
  'primeng/dock',
  'primeng/dragdrop',
  'primeng/drawer',
  'primeng/dynamicdialog',
  'primeng/editor',
  'primeng/galleria',
  'primeng/image',
  'primeng/imagecompare',
  'primeng/keyfilter',
  'primeng/knob',
  'primeng/megamenu',
  'primeng/menu',
  'primeng/menubar',
  'primeng/message',
  'primeng/metergroup',
  'primeng/overlay',
  'primeng/panelmenu',
  'primeng/popover',
  'primeng/progressbar',
  'primeng/progressspinner',
  'primeng/rating',
  'primeng/ripple',
  'primeng/scroller',
  'primeng/scrollpanel',
  'primeng/scrolltop',
  'primeng/skeleton',
  'primeng/speeddial',
  'primeng/splitbutton',
  'primeng/steps',
  'primeng/styleclass',
  'primeng/tag',
  'primeng/terminal',
  'primeng/tieredmenu',
  'primeng/toast',
  'primeng/tooltip'
];

const infrastructureEntrypoints = [
  'primeng/api',
  'primeng/bind',
  'primeng/classnames',
  'primeng/config',
  'primeng/dom',
  'primeng/focustrap',
  'primeng/icons',
  'primeng/motion',
  'primeng/passthrough',
  'primeng/utils'
];

const styledDirectIconEntrypoints = [
  'primeng/icons/angledoubledown',
  'primeng/icons/angledoubleleft',
  'primeng/icons/angledoubleright',
  'primeng/icons/angledoubleup',
  'primeng/icons/angledown',
  'primeng/icons/angleleft',
  'primeng/icons/angleright',
  'primeng/icons/angleup',
  'primeng/icons/arrowdown',
  'primeng/icons/arrowdownleft',
  'primeng/icons/arrowdownright',
  'primeng/icons/arrowleft',
  'primeng/icons/arrowright',
  'primeng/icons/arrowup',
  'primeng/icons/ban',
  'primeng/icons/bars',
  'primeng/icons/blank',
  'primeng/icons/calendar',
  'primeng/icons/caretleft',
  'primeng/icons/caretright',
  'primeng/icons/check',
  'primeng/icons/chevrondown',
  'primeng/icons/chevronleft',
  'primeng/icons/chevronright',
  'primeng/icons/chevronup',
  'primeng/icons/exclamationtriangle',
  'primeng/icons/eye',
  'primeng/icons/eyeslash',
  'primeng/icons/filter',
  'primeng/icons/filterslash',
  'primeng/icons/home',
  'primeng/icons/infocircle',
  'primeng/icons/minus',
  'primeng/icons/pencil',
  'primeng/icons/plus',
  'primeng/icons/refresh',
  'primeng/icons/search',
  'primeng/icons/searchminus',
  'primeng/icons/searchplus',
  'primeng/icons/sortalt',
  'primeng/icons/sortamountdown',
  'primeng/icons/sortamountupalt',
  'primeng/icons/spinner',
  'primeng/icons/star',
  'primeng/icons/starfill',
  'primeng/icons/thlarge',
  'primeng/icons/times',
  'primeng/icons/timescircle',
  'primeng/icons/trash',
  'primeng/icons/undo',
  'primeng/icons/upload',
  'primeng/icons/windowmaximize',
  'primeng/icons/windowminimize'
];

const excludedEntries = [
  {
    entrypoint: 'primeng',
    disposition: 'excluded',
    rationale:
      'The package root resolves to an empty compatibility module in PrimeNG 21.1.3; use explicit public subpaths for traceable ownership and tree shaking.'
  },
  ...[
    'primeng/base',
    'primeng/basecomponent',
    'primeng/baseeditableholder',
    'primeng/baseinput',
    'primeng/basemodelholder'
  ].map((entrypoint) => ({
    entrypoint,
    disposition: 'excluded',
    rationale:
      'PrimeNG component-implementation base class; extending it would make Nexo contracts depend on framework internals rather than the supported component API.'
  })),
  {
    entrypoint: 'primeng/icons/baseicon',
    disposition: 'excluded',
    rationale:
      'Base class and style plumbing for PrimeNG SVG icon implementations, not an application-facing icon.'
  },
  {
    entrypoint: 'primeng/icons/filterfill',
    disposition: 'excluded',
    rationale:
      'Secondary table icon leaf omitted from the public primeng/icons barrel; avoid treating this implementation-facing export as a Nexo feature contract.'
  },
  {
    entrypoint: 'primeng/ts-helpers',
    disposition: 'excluded',
    rationale:
      'Declaration helper surface whose JavaScript module is empty; use import type from supported component or primeng/types subpaths when needed.'
  },
  {
    entrypoint: 'primeng/usestyle',
    disposition: 'excluded',
    rationale:
      'PrimeNG internal style-injection and theme-loader machinery; Nexo styling is governed by the central preset, tokens, and adapters.'
  }
];

/**
 * Executable registry for Node tooling. TypeScript consumers use the adjacent
 * `.types.ts` contract without importing this build-time dataset into the app.
 * @type {import('./primeng-coverage.types').PrimeNgCoverageRegistry}
 */
export const primengCoverageRegistry = Object.freeze({
  schemaVersion: 1,
  primeNgVersion: '21.1.3',
  practicalEntrypointPolicy: 'exact-public-consumer-export-v2',
  practicalEntrypointDefinition:
    'Start from every exact key declared by the installed primeng package exports. Filter package metadata and the generated ./types/* family, which remains public for import type. Require an explicit disposition for every other public JavaScript subpath, including nested icon leaves and implementation-oriented exports that are deliberately excluded.',
  entries: /** @type {readonly import('./primeng-coverage.types').PrimeNgCoverageEntry[]} */ (
    Object.freeze([
      ...defineEntries(
        'wrapper',
        'Interactive form control; expose a Nexo wrapper to standardize labels, validation, sizing, accessibility, and value semantics.',
        wrapperEntrypoints
      ),
      ...defineEntries(
        'pattern',
        'Composite layout or data-display primitive; standardize composition and responsive behavior as a reusable Nexo pattern.',
        patternEntrypoints
      ),
      ...defineEntries(
        'styled-direct',
        'Use the PrimeNG public entrypoint directly with the Nexo preset, tokens, and documented adapter classes; no wrapper contract is required by default.',
        styledDirectEntrypoints
      ),
      ...defineEntries(
        'styled-direct',
        'Public SVG icon leaf re-exported by the primeng/icons barrel; direct use is acceptable with an accessible name for semantic icons and aria-hidden for decorative icons.',
        styledDirectIconEntrypoints
      ),
      ...defineEntries(
        'infrastructure',
        'Framework service, directive foundation, utility, icon plumbing, or configuration surface; consume through design-system internals rather than a visual wrapper.',
        infrastructureEntrypoints
      ),
      ...excludedEntries
    ].sort((left, right) => left.entrypoint.localeCompare(right.entrypoint)))
  ),
  filteredPackageExports: Object.freeze([
    {
      exportKey: './package.json',
      rationale: 'Package metadata is public for tooling but is not an application UI or runtime entrypoint.'
    },
    {
      exportKey: './types/*',
      rationale:
        'Represents all exact nested ./types/<component> exports. They remain valid public import-type surfaces, but are generated alongside component contracts and are accounted for as a family rather than repeated visual dispositions.'
    }
  ])
});
