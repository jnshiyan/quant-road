import resize from '@/views/dashboard/mixins/resize'

const { scheduleDeferredMount } = require('@/utils/deferred-render')
const { loadEcharts } = require('@/utils/load-echarts')

function isComponentDestroyed(vm) {
  return vm._isDestroyed || vm._isBeingDestroyed
}

export default {
  mixins: [resize],
  data() {
    return {
      echarts: null,
      chartInitHandle: null
    }
  },
  mounted() {
    this.$nextTick(() => {
      this.chartInitHandle = scheduleDeferredMount(async () => {
        const echarts = await loadEcharts()
        if (isComponentDestroyed(this) || typeof this.initChart !== 'function') {
          return
        }
        this.echarts = echarts
        this.initChart()
      })
    })
  },
  beforeDestroy() {
    if (!this.chartInitHandle) {
      return
    }
    this.chartInitHandle.cancel()
    this.chartInitHandle = null
  }
}
