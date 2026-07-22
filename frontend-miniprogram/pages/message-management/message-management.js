const { apiBase: API_BASE } = require('../../config/api.js')
const permissionManager = require('../../utils/permission.js')

Page({
  data: {
    activeTab: 'intention',
    tabs: [
      { key: 'intention', label: '意向审核' },
      { key: 'notice', label: '通知管理' },
      { key: 'log', label: '系统日志' }
    ],
    
    loading: false,
    intentionList: [],
    noticeList: [],
    logList: [],
    
    pageNum: 1,
    pageSize: 10,
    total: 0,
    pages: 0,
    
    showModal: false,
    modalType: '',
    currentItem: null,
    formData: {
      title: '',
      content: '',
      publishTime: ''
    },
    
    permissions: {}
  },

  onLoad() {
    this.loadPermissions()
    this.loadIntentionList()
  },

  loadPermissions() {
    const token = wx.getStorageSync('token') || ''
    wx.request({
      url: `${API_BASE}/adminUsers/me`,
      method: 'GET',
      header: {
        'content-type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      success: (res) => {
        const result = res.data || {}
        if (result.code >= 2000 && result.code < 3000) {
          const permissions = result.data?.permissions || {}
          permissionManager.setPermissions(permissions)
          this.setData({ permissions })
          
          const availableTabs = []
          if (permissions.permissionIntentionAudit === 1) availableTabs.push('intention')
          if (permissions.permissionNoticePublish === 1) availableTabs.push('notice')
          if (permissions.permissionLogView === 1) availableTabs.push('log')
          
          if (availableTabs.length > 0 && !availableTabs.includes(this.data.activeTab)) {
            this.setData({ activeTab: availableTabs[0] })
            this.loadCurrentTab()
          }
        }
      }
    })
  },

  onTabChange(e) {
    const key = e.currentTarget.dataset.key
    this.setData({ 
      activeTab: key,
      pageNum: 1 
    })
    if (key === 'intention') {
      this.loadIntentionList()
    } else if (key === 'notice') {
      this.loadNoticeList()
    } else {
      this.loadLogList()
    }
  },

  loadIntentionList() {
    const token = wx.getStorageSync('token') || ''
    this.setData({ loading: true })

    wx.request({
      url: `${API_BASE}/intentions?pageNum=${this.data.pageNum}&pageSize=${this.data.pageSize}&status=pending`,
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
            intentionList: data.data || [],
            total: data.total || 0,
            pages: data.pages || 1
          })
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

  loadNoticeList() {
    const token = wx.getStorageSync('token') || ''
    this.setData({ loading: true })

    wx.request({
      url: `${API_BASE}/notices?pageNum=${this.data.pageNum}&pageSize=${this.data.pageSize}`,
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
            noticeList: data.data || [],
            total: data.total || 0,
            pages: data.pages || 1
          })
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

  loadLogList() {
    const token = wx.getStorageSync('token') || ''
    this.setData({ loading: true })

    wx.request({
      url: `${API_BASE}/systemLogs?pageNum=${this.data.pageNum}&pageSize=${this.data.pageSize}`,
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
            logList: data.data || [],
            total: data.total || 0,
            pages: data.pages || 1
          })
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

  approveIntention(e) {
    const item = e.currentTarget.dataset.item
    wx.showModal({
      title: '确认审核',
      content: '确定要通过这条意向留言吗？',
      success: (res) => {
        if (res.confirm) {
          this.doApproveIntention(item.intentionId)
        }
      }
    })
  },

  rejectIntention(e) {
    const item = e.currentTarget.dataset.item
    wx.showModal({
      title: '确认拒绝',
      content: '确定要拒绝这条意向留言吗？',
      success: (res) => {
        if (res.confirm) {
          this.doRejectIntention(item.intentionId)
        }
      }
    })
  },

  doApproveIntention(intentionId) {
    const token = wx.getStorageSync('token') || ''
    wx.request({
      url: `${API_BASE}/intentions/${intentionId}/approve`,
      method: 'PUT',
      header: {
        'content-type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      success: (res) => {
        const result = res.data || {}
        if (result.code >= 2000 && result.code < 3000) {
          wx.showToast({ title: '审核通过', icon: 'success' })
          this.loadIntentionList()
        } else {
          wx.showToast({ title: result.message || '审核失败', icon: 'none' })
        }
      },
      fail: () => {
        wx.showToast({ title: '请求失败', icon: 'none' })
      }
    })
  },

  doRejectIntention(intentionId) {
    const token = wx.getStorageSync('token') || ''
    wx.request({
      url: `${API_BASE}/intentions/${intentionId}/reject`,
      method: 'PUT',
      header: {
        'content-type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      success: (res) => {
        const result = res.data || {}
        if (result.code >= 2000 && result.code < 3000) {
          wx.showToast({ title: '已拒绝', icon: 'success' })
          this.loadIntentionList()
        } else {
          wx.showToast({ title: result.message || '操作失败', icon: 'none' })
        }
      },
      fail: () => {
        wx.showToast({ title: '请求失败', icon: 'none' })
      }
    })
  },

  openCreateNoticeModal() {
    this.setData({
      showModal: true,
      modalType: 'create',
      currentItem: null,
      formData: {
        title: '',
        content: '',
        publishTime: ''
      }
    })
  },

  openEditNoticeModal(e) {
    const item = e.currentTarget.dataset.item
    this.setData({
      showModal: true,
      modalType: 'edit',
      currentItem: item,
      formData: {
        title: item.title || '',
        content: item.content || '',
        publishTime: item.publishTime || ''
      }
    })
  },

  closeModal() {
    this.setData({ showModal: false })
  },

  onInputChange(e) {
    const field = e.currentTarget.dataset.field
    this.setData({
      [`formData.${field}`]: e.detail.value
    })
  },

  submitNotice() {
    const { formData, modalType, currentItem } = this.data
    const token = wx.getStorageSync('token') || ''

    if (!formData.title.trim()) {
      wx.showToast({ title: '请输入标题', icon: 'none' })
      return
    }
    if (!formData.content.trim()) {
      wx.showToast({ title: '请输入内容', icon: 'none' })
      return
    }

    const url = modalType === 'create' ? `${API_BASE}/notices` : `${API_BASE}/notices/${currentItem.id}`
    const method = modalType === 'create' ? 'POST' : 'PUT'

    wx.request({
      url: url,
      method: method,
      header: {
        'content-type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      data: formData,
      success: (res) => {
        const result = res.data || {}
        if (result.code >= 2000 && result.code < 3000) {
          wx.showToast({ title: modalType === 'create' ? '发布成功' : '更新成功', icon: 'success' })
          this.closeModal()
          this.loadNoticeList()
        } else {
          wx.showToast({ title: result.message || '操作失败', icon: 'none' })
        }
      },
      fail: () => {
        wx.showToast({ title: '请求失败', icon: 'none' })
      }
    })
  },

  deleteNotice(e) {
    const item = e.currentTarget.dataset.item
    wx.showModal({
      title: '确认删除',
      content: `确定要删除通知「${item.title}」吗？`,
      success: (res) => {
        if (res.confirm) {
          this.doDeleteNotice(item.id)
        }
      }
    })
  },

  doDeleteNotice(id) {
    const token = wx.getStorageSync('token') || ''
    wx.request({
      url: `${API_BASE}/notices/${id}`,
      method: 'DELETE',
      header: {
        'content-type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      success: (res) => {
        const result = res.data || {}
        if (result.code >= 2000 && result.code < 3000) {
          wx.showToast({ title: '删除成功', icon: 'success' })
          this.loadNoticeList()
        } else {
          wx.showToast({ title: result.message || '删除失败', icon: 'none' })
        }
      },
      fail: () => {
        wx.showToast({ title: '请求失败', icon: 'none' })
      }
    })
  },

  onPrevPage() {
    if (this.data.pageNum > 1) {
      this.setData({ pageNum: this.data.pageNum - 1 })
      this.loadCurrentTab()
    }
  },

  onNextPage() {
    if (this.data.pageNum < this.data.pages) {
      this.setData({ pageNum: this.data.pageNum + 1 })
      this.loadCurrentTab()
    }
  },

  loadCurrentTab() {
    switch(this.data.activeTab) {
      case 'intention':
        this.loadIntentionList()
        break
      case 'notice':
        this.loadNoticeList()
        break
      case 'log':
        this.loadLogList()
        break
    }
  }
})