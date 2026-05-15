let echartsPromise = null

function resolveModule(moduleValue) {
  return moduleValue && moduleValue.default ? moduleValue.default : moduleValue
}

function createDefaultImporter() {
  return import(
    /* webpackChunkName: "echarts-runtime" */
    '@/utils/echarts'
  )
}

function loadEcharts(importer = createDefaultImporter) {
  if (!echartsPromise) {
    echartsPromise = Promise.resolve()
      .then(() => importer())
      .then(resolveModule)
  }
  return echartsPromise
}

module.exports = {
  loadEcharts
}
