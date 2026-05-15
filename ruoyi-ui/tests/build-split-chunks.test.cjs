const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

function read(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, '..', relativePath), 'utf8');
}

test('production webpack config avoids custom named split chunks that pull Vue styles into manual cache groups', () => {
  const source = read('vue.config.js');

  assert.equal(
    source.includes("name: 'chunk-elementUI'"),
    false,
    'vue.config.js should not define a manual element-ui cache group'
  );
  assert.equal(
    source.includes('elementUIAsync'),
    false,
    'vue.config.js should not define a manual async element-ui cache group'
  );
  assert.equal(
    source.includes("name: 'chunk-commons'"),
    false,
    'vue.config.js should not define a manual commons chunk for src/components'
  );
  assert.equal(
    source.includes("config.optimization.runtimeChunk('single')"),
    true,
    'vue.config.js should still keep a dedicated runtime chunk'
  );
});
