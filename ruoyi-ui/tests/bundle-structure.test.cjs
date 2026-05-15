const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

function read(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, '..', relativePath), 'utf8');
}

test('App no longer mounts a hidden global ThemePicker', () => {
  const source = read('src/App.vue');
  assert.equal(source.includes('<theme-picker />'), false);
  assert.equal(source.includes('import ThemePicker'), false);
});

test('chart components use the shared lightweight echarts entry instead of the full bundle import', () => {
  const files = [
    'src/views/dashboard/BarChart.vue',
    'src/views/dashboard/LineChart.vue',
    'src/views/dashboard/PieChart.vue',
    'src/views/dashboard/RaddarChart.vue',
    'src/views/quant/review/components/ReviewEvidenceChart.vue',
    'src/views/quant/shadow/components/ShadowEvidenceChart.vue'
  ];

  files.forEach(file => {
    const source = read(file);
    assert.equal(source.includes("import * as echarts from 'echarts'"), false, `${file} still imports the full echarts bundle`);
    assert.equal(source.includes("from '@/utils/echarts'"), false, `${file} still statically imports the echarts adapter`);
    assert.equal(
      source.includes('loadEcharts') || source.includes("from '@/mixins/lazy-echarts'"),
      true,
      `${file} does not opt into the lazy echarts loader`
    );
  });
});

test('review and shadow pages lazy-register evidence chart components instead of statically importing them', () => {
  const files = [
    {
      path: 'src/views/quant/review/index.vue',
      componentName: 'ReviewEvidenceChart'
    },
    {
      path: 'src/views/quant/shadow/index.vue',
      componentName: 'ShadowEvidenceChart'
    }
  ];

  files.forEach(({ path: file, componentName }) => {
    const source = read(file);
    assert.equal(
      source.includes(`import ${componentName} from './components/${componentName}'`),
      false,
      `${file} still statically imports ${componentName}`
    );
    assert.equal(
      source.includes(`${componentName}: () => import(`),
      true,
      `${file} does not lazy-register ${componentName}`
    );
  });
});

test('main entry no longer installs the full Element UI plugin', () => {
  const source = read('src/main.js');
  assert.equal(source.includes("import Element from 'element-ui'"), false);
  assert.equal(source.includes('Vue.use(Element'), false);
  assert.equal(source.includes("import element from './plugins/element'"), true);
});

test('main entry lazy-registers heavyweight global form widgets', () => {
  const source = read('src/main.js');
  [
    'Pagination',
    'RightToolbar',
    'Editor',
    'FileUpload',
    'ImageUpload',
    'ImagePreview'
  ].forEach(componentName => {
    assert.equal(
      source.includes(`import ${componentName} from "@/components/${componentName}"`) ||
      source.includes(`import ${componentName} from '@/components/${componentName}'`),
      false,
      `${componentName} is still synchronously imported in main.js`
    );
    assert.equal(
      source.includes(`Vue.component('${componentName}', () => import(`),
      true,
      `${componentName} is not lazy-registered in main.js`
    );
  });
});

test('element theme overrides no longer pull the entire theme-chalk index bundle', () => {
  const source = read('src/assets/styles/element-variables.scss');
  assert.equal(
    source.includes('@import "~element-ui/packages/theme-chalk/src/index";'),
    false
  );
});

test('low-frequency Element UI widgets are locally imported by their consuming views', () => {
  const expectations = [
    {
      path: 'src/components/Editor/index.vue',
      imports: ["import ElUpload from 'element-ui/lib/upload'", "import 'element-ui/lib/theme-chalk/upload.css'"],
      registrations: ['ElUpload']
    },
    {
      path: 'src/components/FileUpload/index.vue',
      imports: [
        "import ElLink from 'element-ui/lib/link'",
        "import ElUpload from 'element-ui/lib/upload'",
        "import 'element-ui/lib/theme-chalk/link.css'",
        "import 'element-ui/lib/theme-chalk/upload.css'"
      ],
      registrations: ['ElLink', 'ElUpload']
    },
    {
      path: 'src/components/ImageUpload/index.vue',
      imports: ["import ElUpload from 'element-ui/lib/upload'", "import 'element-ui/lib/theme-chalk/upload.css'"],
      registrations: ['ElUpload']
    },
    {
      path: 'src/components/ImagePreview/index.vue',
      imports: ["import ElImage from 'element-ui/lib/image'", "import 'element-ui/lib/theme-chalk/image.css'"],
      registrations: ['ElImage']
    },
    {
      path: 'src/components/ExcelImportDialog/index.vue',
      imports: [
        "import ElLink from 'element-ui/lib/link'",
        "import ElUpload from 'element-ui/lib/upload'",
        "import 'element-ui/lib/theme-chalk/link.css'",
        "import 'element-ui/lib/theme-chalk/upload.css'"
      ],
      registrations: ['ElLink', 'ElUpload']
    },
    {
      path: 'src/views/system/user/profile/userAvatar.vue',
      imports: ["import ElUpload from 'element-ui/lib/upload'", "import 'element-ui/lib/theme-chalk/upload.css'"],
      registrations: ['ElUpload']
    },
    {
      path: 'src/views/quant/dashboard/index.vue',
      imports: ["import ElAlert from 'element-ui/lib/alert'", "import 'element-ui/lib/theme-chalk/alert.css'"],
      registrations: ['ElAlert']
    },
    {
      path: 'src/views/quant/jobs/index.vue',
      imports: [
        "import ElCollapse from 'element-ui/lib/collapse'",
        "import ElCollapseItem from 'element-ui/lib/collapse-item'",
        "import 'element-ui/lib/theme-chalk/collapse.css'"
      ],
      registrations: ['ElCollapse', 'ElCollapseItem']
    },
    {
      path: 'src/views/quant/jobs/components/CurrentDispatchMonitor.vue',
      imports: ["import ElAlert from 'element-ui/lib/alert'", "import 'element-ui/lib/theme-chalk/alert.css'"],
      registrations: ['ElAlert']
    },
    {
      path: 'src/views/quant/jobs/components/DispatchHistoryDrawer.vue',
      imports: [
        "import ElAlert from 'element-ui/lib/alert'",
        "import ElDrawer from 'element-ui/lib/drawer'",
        "import 'element-ui/lib/theme-chalk/alert.css'",
        "import 'element-ui/lib/theme-chalk/drawer.css'"
      ],
      registrations: ['ElAlert', 'ElDrawer']
    },
    {
      path: 'src/views/quant/review/index.vue',
      imports: [
        "import ElAlert from 'element-ui/lib/alert'",
        "import ElTimeline from 'element-ui/lib/timeline'",
        "import ElTimelineItem from 'element-ui/lib/timeline-item'",
        "import 'element-ui/lib/theme-chalk/alert.css'",
        "import 'element-ui/lib/theme-chalk/timeline.css'",
        "import 'element-ui/lib/theme-chalk/timeline-item.css'"
      ],
      registrations: ['ElAlert', 'ElTimeline', 'ElTimelineItem']
    },
    {
      path: 'src/views/quant/execution/index.vue',
      imports: [
        "import ElLink from 'element-ui/lib/link'",
        "import ElUpload from 'element-ui/lib/upload'",
        "import 'element-ui/lib/theme-chalk/link.css'",
        "import 'element-ui/lib/theme-chalk/upload.css'"
      ],
      registrations: ['ElLink', 'ElUpload']
    },
    {
      path: 'src/layout/components/Settings/index.vue',
      imports: [
        "import ElDivider from 'element-ui/lib/divider'",
        "import ElDrawer from 'element-ui/lib/drawer'",
        "import 'element-ui/lib/theme-chalk/divider.css'",
        "import 'element-ui/lib/theme-chalk/drawer.css'"
      ],
      registrations: ['ElDivider', 'ElDrawer']
    },
    {
      path: 'src/layout/components/HeaderNotice/DetailView.vue',
      imports: ["import ElDrawer from 'element-ui/lib/drawer'", "import 'element-ui/lib/theme-chalk/drawer.css'"],
      registrations: ['ElDrawer']
    },
    {
      path: 'src/views/system/user/view.vue',
      imports: ["import ElDrawer from 'element-ui/lib/drawer'", "import 'element-ui/lib/theme-chalk/drawer.css'"],
      registrations: ['ElDrawer']
    },
    {
      path: 'src/views/system/dict/detail.vue',
      imports: ["import ElDrawer from 'element-ui/lib/drawer'", "import 'element-ui/lib/theme-chalk/drawer.css'"],
      registrations: ['ElDrawer']
    },
    {
      path: 'src/views/index.vue',
      imports: [
        "import ElCollapse from 'element-ui/lib/collapse'",
        "import ElCollapseItem from 'element-ui/lib/collapse-item'",
        "import ElDivider from 'element-ui/lib/divider'",
        "import ElLink from 'element-ui/lib/link'",
        "import 'element-ui/lib/theme-chalk/collapse.css'",
        "import 'element-ui/lib/theme-chalk/collapse-item.css'",
        "import 'element-ui/lib/theme-chalk/divider.css'",
        "import 'element-ui/lib/theme-chalk/link.css'"
      ],
      registrations: ['ElCollapse', 'ElCollapseItem', 'ElDivider', 'ElLink']
    }
  ];

  expectations.forEach(({ path: file, imports, registrations }) => {
    const source = read(file);
    imports.forEach(statement => {
      assert.equal(source.includes(statement), true, `${file} is missing "${statement}"`);
    });
    registrations.forEach(componentName => {
      assert.equal(source.includes(componentName), true, `${file} does not register ${componentName}`);
    });
  });
});

test('production webpack config avoids brittle custom cache groups and keeps a dedicated runtime chunk', () => {
  const source = read('vue.config.js');
  assert.equal(source.includes("name: 'chunk-elementUI'"), false);
  assert.equal(source.includes('elementUIAsync'), false);
  assert.equal(source.includes("name: 'chunk-commons'"), false);
  assert.equal(source.includes("config.optimization.runtimeChunk('single')"), true);
});

test('router lazy-loads the authenticated layout shell', () => {
  const source = read('src/router/index.js');
  assert.equal(source.includes("import Layout from '@/layout'"), false, 'router should not synchronously import the layout shell');
  assert.equal(source.includes("const Layout = () => import('@/layout')"), true);
});

test('default home entry is hidden from sidebar and redirects to the quant dashboard', () => {
  const source = read('src/router/index.js');
  assert.equal(source.includes("redirect: '/quant/dashboard'"), true);
  assert.equal(source.includes("meta: { title: '首页', icon: 'dashboard', affix: true }"), true);
  assert.equal(source.includes("path: ''"), true);
  assert.equal(source.includes('hidden: true'), true);
});

test('quant task-center split ships dedicated task and operations files', () => {
  [
    'src/views/quant/jobs/components/TodayStatusCard.vue',
    'src/views/quant/jobs/components/PrimaryTaskCard.vue',
    'src/views/quant/jobs/components/TaskActionPanel.vue',
    'src/views/quant/jobs/components/TaskProgressTimeline.vue',
    'src/views/quant/jobs/jobs-task-center-state.js',
    'src/views/quant/operations/index.vue',
    'src/views/quant/operations/operations-center-state.js'
  ].forEach(file => {
    assert.equal(fs.existsSync(path.resolve(__dirname, '..', file)), true, `${file} is missing`);
  });
});
