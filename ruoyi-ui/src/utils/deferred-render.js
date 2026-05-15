function getWindowRuntime() {
  if (typeof window === 'undefined') {
    return {}
  }
  return window
}

function pickNumber(value, fallback) {
  return Number.isFinite(value) ? value : fallback
}

function scheduleDeferredMount(callback, runtime = {}) {
  const source = runtime || {}
  const browserRuntime = getWindowRuntime()
  const requestIdleCallback = source.requestIdleCallback || browserRuntime.requestIdleCallback
  const cancelIdleCallback = source.cancelIdleCallback || browserRuntime.cancelIdleCallback
  const setTimeoutFn = source.setTimeout || setTimeout
  const clearTimeoutFn = source.clearTimeout || clearTimeout
  const timeout = pickNumber(source.timeout, 200)
  const delay = pickNumber(source.delay, 48)

  let cancelled = false
  let handle = null
  let strategy = 'timeout'

  const run = () => {
    if (cancelled) {
      return
    }
    cancelled = true
    callback()
  }

  if (typeof requestIdleCallback === 'function') {
    strategy = 'idle'
    handle = requestIdleCallback(run, { timeout })
  } else {
    handle = setTimeoutFn(run, delay)
  }

  return {
    strategy,
    cancel() {
      if (cancelled) {
        return
      }
      cancelled = true
      if (strategy === 'idle') {
        if (typeof cancelIdleCallback === 'function') {
          cancelIdleCallback(handle)
        }
        return
      }
      clearTimeoutFn(handle)
    }
  }
}

module.exports = {
  scheduleDeferredMount
}
