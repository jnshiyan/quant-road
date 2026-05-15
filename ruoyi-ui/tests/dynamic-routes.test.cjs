const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')

function loadFilterDynamicRoutes(authOverrides = {}) {
  const source = fs.readFileSync(path.join(__dirname, '../src/store/modules/permission.js'), 'utf8')
  const match = source.match(/export function filterDynamicRoutes\(routes\) \{[\s\S]*?\n\}/)
  assert.ok(match, 'filterDynamicRoutes should exist in permission module')

  const sandbox = {
    auth: {
      hasPermiOr: () => false,
      hasRoleOr: () => false,
      ...authOverrides
    },
    module: { exports: {} }
  }

  const script = `
${match[0].replace('export function', 'function')}
module.exports = { filterDynamicRoutes }
`

  vm.runInNewContext(script, sandbox)
  return sandbox.module.exports.filterDynamicRoutes
}

test('filterDynamicRoutes keeps permissionless hidden routes for explicit in-app pages', () => {
  const filterDynamicRoutes = loadFilterDynamicRoutes()

  const result = filterDynamicRoutes([
    {
      path: '/quant',
      hidden: true,
      children: [
        { path: 'dispatch-manual' }
      ]
    }
  ])

  assert.equal(result.length, 1)
  assert.equal(result[0].path, '/quant')
})

test('filterDynamicRoutes still honors permission-gated routes', () => {
  const filterDynamicRoutes = loadFilterDynamicRoutes({
    hasPermiOr: (permissions) => Array.isArray(permissions) && permissions.includes('system:user:edit')
  })

  const result = filterDynamicRoutes([
    {
      path: '/system/user-auth',
      permissions: ['system:user:edit']
    }
  ])

  assert.equal(result.length, 1)
  assert.equal(result[0].path, '/system/user-auth')
})
