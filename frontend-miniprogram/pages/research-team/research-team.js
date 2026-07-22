const app = getApp()
const { apiBase: API_BASE } = require('../../config/api.js')

Page({
  data: {
    activeTab: 'patents',
    account: {},
    patents: [],
    messages: []
  },

  onLoad() {
    this.loadData()
  },

  onShow() {
    this.loadPatents()
  },

  loadData() {
    const userInfo = wx.getStorageSync('userInfo') || {}
    const account = userInfo.account || {}
    this.setData({ account: account })
    this.loadPatents()
  },

  loadPatents() {
    const token = wx.getStorageSync('token') || ''

    wx.request({
      url: `${API_BASE}/patents/bound`,
      method: 'GET',
      header: {
        'content-type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      success: (res) => {
        const result = res.data || {}
        if (result.code >= 2000 && result.code < 3000) {
          this.setData({ patents: Array.isArray(result.data) ? result.data : [] })
        } else {
          this.setData({ patents: [] })
        }
      },
      fail: () => {
        this.setData({ patents: [] })
      }
    })
  },

  switchTab(e) {
    const tab = e.currentTarget.dataset.tab
    this.setData({ activeTab: tab })
  }
})
