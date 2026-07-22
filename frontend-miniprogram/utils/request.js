const { apiBase: API_BASE } = require('../config/api.js')

function request(options) {
  const token = wx.getStorageSync('token') || ''

  const defaultOptions = {
    url: '',
    method: 'GET',
    data: {},
    header: {
      'content-type': 'application/json',
      'Authorization': `Bearer ${token}`
    },
    success: () => {},
    fail: () => {},
    complete: () => {}
  }

  const opts = Object.assign({}, defaultOptions, options)

  wx.request({
    ...opts,
    success: (res) => {
      const result = res.data || {}

      if (result.code === 4001 ||
          result.message && result.message.includes('access_token') ||
          result.message && result.message.includes('需要重新登录')) {
        console.log('=== Token失效，需要重新登录 ===')
        wx.removeStorageSync('token')
        wx.removeStorageSync('userInfo')
        wx.redirectTo({ url: '/pages/login/login' })
        return
      }

      opts.success && opts.success(res)
    },
    fail: (err) => {
      opts.fail && opts.fail(err)
    },
    complete: (res) => {
      opts.complete && opts.complete(res)
    }
  })
}

module.exports = request