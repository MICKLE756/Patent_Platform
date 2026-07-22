const { apiBase: API_BASE } = require('../../config/api.js')

Page({
  data: {
    patents: [],
    loading: false,
    selectedIds: [],
    selectedCount: 0
  },

  onLoad() {
    this.loadUnboundPatents()
  },

  loadUnboundPatents() {
    const token = wx.getStorageSync('token') || ''
    this.setData({ loading: true })

    const url = `${API_BASE}/patents/unbound`

    wx.request({
      url: url,
      method: 'GET',
      header: {
        'content-type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      success: (res) => {
        const result = res.data || {}
        if (result.code >= 2000 && result.code < 3000) {
          const items = Array.isArray(result.data) ? result.data : []
          const processedItems = items.map(item => {
            if (item.techField) {
              const fields = item.techField.split(/[,，、]/).filter(f => f.trim())
              if (fields.length > 1) {
                return { ...item, displayTechField: fields[0] + '等', selected: false }
              } else {
                return { ...item, displayTechField: item.techField, selected: false }
              }
            }
            return { ...item, displayTechField: '', selected: false }
          })
          this.setData({ 
            patents: processedItems,
            selectedIds: [],
            selectedCount: 0
          })
        } else {
          this.setData({ patents: [], selectedIds: {}, selectedCount: 0 })
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

  toggleSelect(e) {
    const pid = String(e.currentTarget.dataset.id)
    const patents = [...this.data.patents]
    let count = this.data.selectedCount
    
    for (let i = 0; i < patents.length; i++) {
      if (String(patents[i].patentId) === pid) {
        patents[i].selected = !patents[i].selected
        count = patents[i].selected ? count + 1 : count - 1
        break
      }
    }
    
    this.setData({ patents, selectedCount: count })
  },

  onBindSelected() {
    const count = this.data.selectedCount
    if (count === 0) {
      wx.showToast({ title: '请先选择专利', icon: 'none' })
      return
    }

    const ids = this.data.patents
      .filter(item => item.selected)
      .map(item => String(item.patentId))

    const token = wx.getStorageSync('token') || ''
    this.setData({ loading: true })

    wx.request({
      url: `${API_BASE}/patents/bind`,
      method: 'POST',
      header: {
        'content-type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      data: {
        userType: 'research_team',
        patentIds: ids
      },
      success: (res) => {
        const result = res.data || {}
        if (result.code >= 2000 && result.code < 3000) {
          wx.showToast({ title: '绑定成功', icon: 'success' })
          setTimeout(() => {
            wx.navigateBack()
          }, 1500)
        } else {
          wx.showToast({ title: result.message || '绑定失败', icon: 'none' })
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
