const { apiBase: API_BASE } = require('../../config/api.js')

Page({
  data: {
    todayActiveUsers: 0,
    todayNewPatents: 0,
    todayMatchConversations: 0,
    patentMatchRate: 0,
    intentionConversionRate: 0,
    hotTechFields: [],
    hotPatents: [],
    userGrowthData: []
  },

  onLoad() {
    this.loadDashboardData()
  },

  onReady() {
    this.initUserGrowthChart()
  },

  loadDashboardData() {
    const token = wx.getStorageSync('token') || ''

    Promise.all([
      this.loadTodayActiveUsers(),
      this.loadTodayNewPatents(),
      this.loadHotTechFields(),
      this.loadHotPatents(),
      this.loadUserGrowth()
    ])
  },

  loadTodayActiveUsers() {
    const token = wx.getStorageSync('token') || ''
    wx.request({
      url: `${API_BASE}/statistics/todayActiveUsers`,
      method: 'GET',
      header: {
        'content-type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      success: (res) => {
        if (res.data.code === 2000) {
          this.setData({ todayActiveUsers: res.data.data?.count || 0 })
        }
      }
    })
  },

  loadTodayNewPatents() {
    const token = wx.getStorageSync('token') || ''
    wx.request({
      url: `${API_BASE}/statistics/totalPatents`,
      method: 'GET',
      header: {
        'content-type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      success: (res) => {
        if (res.data.code === 2000) {
          this.setData({ todayNewPatents: res.data.data?.count || 0 })
        }
      }
    })
  },

  loadHotTechFields() {
    const token = wx.getStorageSync('token') || ''
    wx.request({
      url: `${API_BASE}/statistics/hotSearchKeywords`,
      method: 'GET',
      header: {
        'content-type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      success: (res) => {
        if (res.data.code === 2000) {
          const data = res.data.data || []
          const hotTechFields = data.map((item, index) => ({
            rank: index + 1,
            field: item.keyword,
            count: item.searchCount
          }))
          this.setData({ hotTechFields })
        }
      }
    })
  },

  loadHotPatents() {
    const token = wx.getStorageSync('token') || ''
    wx.request({
      url: `${API_BASE}/statistics/hotPatents`,
      method: 'GET',
      header: {
        'content-type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      success: (res) => {
        if (res.data.code === 2000) {
          const data = res.data.data || []
          const hotPatents = data.map((item, index) => ({
            rank: index + 1,
            title: item.title,
            views: item.viewCount
          }))
          this.setData({ hotPatents })
        }
      }
    })
  },

  loadUserGrowth() {
    const token = wx.getStorageSync('token') || ''
    wx.request({
      url: `${API_BASE}/statistics/userHistory`,
      method: 'GET',
      header: {
        'content-type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      success: (res) => {
        if (res.data.code === 2000) {
          this.setData({ userGrowthData: res.data.data || [] })
        }
      }
    })
  },

  initUserGrowthChart() {
    const query = wx.createSelectorQuery()
    query.select('#userGrowthChart').boundingClientRect((rect) => {
      if (!rect) return

      const width = rect.width
      const height = rect.height
      const ctx = wx.createCanvasContext('userGrowthChart')

      const data = this.data.userGrowthData || []
      let labels = ['1月', '2月', '3月', '4月', '5月', '6月']
      let values = [1200, 1900, 3000, 4500, 6000, 8000]

      if (data.length > 0) {
        labels = data.map(d => d.date.substring(5))
        values = data.map(d => d.count)
      }

      const padding = 40
      const chartWidth = width - padding * 2
      const chartHeight = height - padding * 2
      const maxValue = Math.max(...values) * 1.2

      ctx.setStrokeStyle('#e4e7ed')
      ctx.setLineWidth(1)
      for (let i = 0; i <= 5; i++) {
        const y = padding + (chartHeight / 5) * i
        ctx.beginPath()
        ctx.moveTo(padding, y)
        ctx.lineTo(width - padding, y)
        ctx.stroke()
      }

      ctx.setStrokeStyle('#409eff')
      ctx.setLineWidth(2)
      ctx.beginPath()
      for (let i = 0; i < values.length; i++) {
        const x = padding + (chartWidth / (values.length - 1)) * i
        const y = padding + chartHeight - (values[i] / maxValue) * chartHeight
        if (i === 0) {
          ctx.moveTo(x, y)
        } else {
          ctx.lineTo(x, y)
        }
      }
      ctx.stroke()

      ctx.setFillStyle('#409eff')
      for (let i = 0; i < values.length; i++) {
        const x = padding + (chartWidth / (values.length - 1)) * i
        const y = padding + chartHeight - (values[i] / maxValue) * chartHeight
        ctx.beginPath()
        ctx.arc(x, y, 4, 0, 2 * Math.PI)
        ctx.fill()
      }

      ctx.setFillStyle('#666')
      ctx.setFontSize(10)
      ctx.setTextAlign('center')
      for (let i = 0; i < labels.length; i++) {
        const x = padding + (chartWidth / (values.length - 1)) * i
        ctx.fillText(labels[i], x, height - 10)
      }

      ctx.draw()
    }).exec()
  }
})
