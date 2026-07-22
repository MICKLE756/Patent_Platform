const { apiBase: API_BASE } = require('../../config/api.js')

Page({
  data: {
    patent: null,
    loading: true
  },

  onLoad(options) {
    const patentId = options.id
    console.log('专利ID:', patentId)
    if (patentId) {
      this.loadPatentDetail(patentId)
    }
  },

  loadPatentDetail(patentId) {
    const token = wx.getStorageSync('token') || ''
    this.setData({ loading: true })

    wx.request({
      url: `${API_BASE}/patents/${patentId}`,
      method: 'GET',
      header: {
        'content-type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      success: (res) => {
        const result = res.data || {}
        console.log('专利详情数据:', result.data)
        if (result.code >= 2000 && result.code < 3000) {
          let patent = result.data || {}
          
          console.log('abstractText:', patent.abstractText)
          console.log('所有字段:', Object.keys(patent))
          
          if (patent.techField) {
            patent.techFieldList = patent.techField.split(/[;；]/).filter(f => f.trim())
          }
          
          this.setData({ patent: patent, loading: false })
          if (patent.title) {
            wx.setNavigationBarTitle({
              title: patent.title.length > 10 ? patent.title.slice(0,10) + '...' : patent.title
            })
          }
        } else {
          wx.showToast({ title: result.message || '加载失败', icon: 'none' })
          this.setData({ patent: null, loading: false })
        }
      },
      fail: () => {
        wx.showToast({ title: '网络请求失败', icon: 'none' })
        this.setData({ patent: null, loading: false })
      }
    })
  },

  onCopyLink(e) {
    const link = e.currentTarget.dataset.link
    if (!link) return
    
    wx.setClipboardData({
      data: link,
      success: () => {
        wx.showToast({
          title: '链接已复制',
          icon: 'success'
        })
      },
      fail: () => {
        wx.showToast({
          title: '复制失败',
          icon: 'none'
        })
      }
    })
  },

  onContactTeacher() {
    wx.showModal({
      title: '确认发送',
      content: '确定发送对此专利的意向吗？',
      confirmText: '发送',
      confirmColor: '#07c160',
      success: (res) => {
        if (res.confirm) {
          wx.showToast({
            title: '发送成功',
            icon: 'success'
          })
        }
      }
    })
  }
})
