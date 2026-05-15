const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const postcss = require('postcss')

function read(relativePath) {
  return fs.readFileSync(path.join(__dirname, '..', relativePath), 'utf8')
}

function extractScopedStyle(vueSource) {
  const match = vueSource.match(/<style scoped>([\s\S]*?)<\/style>/)
  assert.ok(match, 'expected scoped style block in operations page')
  return match[1]
}

test('operations page scoped CSS stays syntactically valid', () => {
  const source = read('src/views/quant/operations/index.vue')
  const css = extractScopedStyle(source)

  assert.doesNotThrow(() => {
    postcss.parse(css, { from: 'src/views/quant/operations/index.vue' })
  })
})
