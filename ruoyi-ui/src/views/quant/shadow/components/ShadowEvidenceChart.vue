<template>
  <div :style="{ height, width }" />
</template>

<script>
import lazyEcharts from '@/mixins/lazy-echarts'

export default {
  name: 'ShadowEvidenceChart',
  mixins: [lazyEcharts],
  props: {
    option: {
      type: Object,
      default: () => ({})
    },
    width: {
      type: String,
      default: '100%'
    },
    height: {
      type: String,
      default: '320px'
    }
  },
  data() {
    return {
      chart: null
    }
  },
  watch: {
    option: {
      handler() {
        this.renderChart()
      },
      deep: true
    }
  },
  beforeDestroy() {
    if (!this.chart) {
      return
    }
    this.chart.dispose()
    this.chart = null
  },
  methods: {
    initChart() {
      this.chart = this.echarts.init(this.$el, 'macarons')
      this.renderChart()
    },
    renderChart() {
      if (!this.chart) {
        return
      }
      this.chart.clear()
      this.chart.setOption(this.option || {}, true)
    }
  }
}
</script>
