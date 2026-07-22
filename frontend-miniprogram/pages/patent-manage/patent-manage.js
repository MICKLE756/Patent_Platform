const { apiBase: API_BASE } = require('../../config/api.js')

Page({
  data: {
    patents: [],
    loading: false
  },

  goToAddPatent() {
    wx.navigateTo({
      url: '/pages/patent-select/patent-select'
    })
  },

  onLoad() {
    this.loadBoundPatents()
  },

  onShow() {
    this.loadBoundPatents()
  },

  loadBoundPatents() {
    const token = wx.getStorageSync('token') || ''
    this.setData({ loading: true })

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
        wx.showToast({ title: '加载失败', icon: 'none' })
      },
      complete: () => {
        this.setData({ loading: false })
      }
    })
  },

  onUnbindPatent(e) {
    const patent = e.currentTarget.dataset.patent
    const token = wx.getStorageSync('token') || ''
    const pid = patent.patentId || patent.id

    wx.showModal({
      title: '确认解绑',
      content: `确定要解除「${patent.title || patent.patentName || '该专利'}」的绑定吗？`,
      success: (modalRes) => {
        if (modalRes.confirm) {
          this.doUnbind(token, pid)
        }
      }
    })
  },

  doUnbind(token, patentId) {
    this.setData({ loading: true })

    wx.request({
      url: `${API_BASE}/patents/unbind`,
      method: 'POST',
      header: {
        'content-type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      data: { patentIds: [patentId] },
      success: (res) => {
        const result = res.data || {}
        if (result.code >= 2000 && result.code < 3000) {
          if (result.data && result.data.patents !== undefined) {
            this.setData({ patents: Array.isArray(result.data.patents) ? result.data.patents : [] })
          } else {
            this.loadBoundPatents()
          }
          wx.showToast({ title: '解绑成功', icon: 'success' })
        } else {
          wx.showToast({ title: result.message || '解绑失败', icon: 'none' })
        }
      },
      fail: () => {
        wx.showToast({ title: '网络请求失败', icon: 'none' })
      },
      complete: () => {
        this.setData({ loading: false })
      }
    })
  }
})
