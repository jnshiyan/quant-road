const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

test('quant page regulations are persisted in repository rule files', () => {
  const repoRoot = path.resolve(__dirname, '..', '..')
  assert.equal(fs.existsSync(path.join(repoRoot, 'CLAUDE.md')), true)
  assert.equal(fs.existsSync(path.join(repoRoot, '.claude/rules/quant-module-page-rules.md')), true)
})

test('quant page regulations are promoted into stable docs', () => {
  const repoRoot = path.resolve(__dirname, '..', '..')
  assert.equal(fs.existsSync(path.join(repoRoot, 'docs/quant/page-design-regulations.md')), true)
  assert.equal(fs.existsSync(path.join(repoRoot, 'docs/quant/page-delivery-regulations.md')), true)
  assert.equal(fs.existsSync(path.join(repoRoot, 'docs/quant/page-review-checklist.md')), true)
})

function read(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, '..', relativePath), 'utf8')
}

test('quant pages do not use first-screen hero shells', () => {
  const files = [
    'src/views/quant/jobs/index.vue',
    'src/views/quant/operations/index.vue',
    'src/views/quant/dispatch-detail/index.vue'
  ]
  files.forEach(file => {
    const source = read(file)
    assert.equal(source.includes('hero-shell'), false, `${file} still includes hero-shell`)
    assert.equal(source.includes('ops-hero'), false, `${file} still includes ops-hero`)
  })
})

test('quant pages do not use illustrative el-empty first-screen placeholders', () => {
  const files = [
    'src/views/quant/jobs/components/TaskProgressTimeline.vue',
    'src/views/quant/dispatch-detail/index.vue',
    'src/views/quant/jobs/components/JobResultTable.vue',
    'src/views/quant/jobs/components/JobShardTable.vue'
  ]
  files.forEach(file => {
    const source = read(file)
    assert.equal(source.includes('<el-empty'), false, `${file} still uses el-empty`)
  })
})

test('quant pages avoid single-card min-height on first-screen components', () => {
  const files = [
    'src/views/quant/jobs/components/TodayStatusCard.vue',
    'src/views/quant/jobs/components/PrimaryTaskCard.vue',
    'src/views/quant/jobs/index.vue',
    'src/views/quant/operations/index.vue'
  ]
  files.forEach(file => {
    const source = read(file)
    assert.equal(/min-height\s*:\s*\d+px/.test(source), false, `${file} still sets min-height`)
  })
})
