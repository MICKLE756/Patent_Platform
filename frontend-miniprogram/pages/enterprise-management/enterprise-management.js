const { apiBase: API_BASE } = require('../../config/api.js')

Page({
  data: {
    loading: false,
    userList: [],
    pageNum: 1,
    pageSize: 10,
    total: 0,
    pages: 0,
    showModal: false,
    modalType: 'detail',
    currentUser: null,
    formData: {
      companyName: '',
      contactName: '',
      contactPhone: '',
      contactEmail: ''
    }
  },

  onLoad() {
    this.loadUserList()
  },

  loadUserList() {
    const token = wx.getStorageSync('token') || ''
    this.setData({ loading: true })

    wx.request({
      url: `${API_BASE}/enterpriseUsers?pageNum=${this.data.pageNum}&pageSize=${this.data.pageSize}`,
      method: 'GET',
      header: {
        'content-type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      success: (res) => {
        const result = res.data || {}
        if (result.code >= 2000 && result.code < 3000) {
          const data = result.data || {}
          this.setData({
            userList: data.list || [],
            total: data.total || 0,
            pageNum: data.pageNum || 1,
            pages: data.pages || 0
          })
        } else {
          wx.showToast({ title: result.message || '加载失败', icon: 'none' })
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

  onShow() {
    this.loadUserList()
  },

  openDetailModal(e) {
    const user = e.currentTarget.dataset.user
    this.setData({
      showModal: true,
      modalType: 'detail',
      currentUser: user
    })
  },

  closeModal() {
    this.setData({ showModal: false })
  },

  onPrevPage() {
    if (this.data.pageNum > 1) {
      this.setData({ pageNum: this.data.pageNum - 1 })
      this.loadUserList()
    }
  },

  onNextPage() {
    if (this.data.pageNum < this.data.pages) {
      this.setData({ pageNum: this.data.pageNum + 1 })
      this.loadUserList()
    }
  }
})