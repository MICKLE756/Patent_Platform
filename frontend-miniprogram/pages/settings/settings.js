const app = getApp()
const { apiBase: API_BASE } = require('../../config/api.js')

Page({
  data: {
    userType: 0,
    account: {},
    deleteLoading: false
  },

  onLoad() {
    this.loadUserData()
  },

  onShow() {
    this.loadUserData()
  },

  loadUserData() {
    const userInfo = wx.getStorageSync('userInfo') || {}
    const userType = userInfo.userType !== undefined ? parseInt(userInfo.userType) : 0
    const account = userInfo.account || {}
    
    this.setData({
      userType: userType,
      account: account
    })
  },

  goToIdentitySelect() {
    wx.navigateTo({
      url: '/pages/identity-select/identity-select'
    })
  },

  goToViewEnterprise() {
    const account = this.data.account
    const params = Object.keys(account).map(k => `${k}=${encodeURIComponent(account[k] || '')}`).join('&')
    wx.navigateTo({
      url: `/pages/enterprise-bind/enterprise-bind?mode=view&${params}`
    })
  },

  goToEditEnterprise() {
    const account = this.data.account
    const params = Object.keys(account).map(k => `${k}=${encodeURIComponent(account[k] || '')}`).join('&')
    wx.navigateTo({
      url: `/pages/enterprise-bind/enterprise-bind?mode=edit&${params}`
    })
  },

  doUnbindEnterprise() {
    const userInfo = wx.getStorageSync('userInfo') || {}
    const userId = userInfo.userId
    
    if (!userId) {
      wx.showToast({ title: '缺少用户ID', icon: 'none' })
      return
    }

    wx.showModal({
      title: '确认',
      content: '确定要注销企业身份吗？注销后将恢复为普通用户',
      success: (modalRes) => {
        if (modalRes.confirm) {
          this.doDeleteEnterprise(userId)
        }
      }
    })
  },

  doDeleteEnterprise(userId) {
    this.setData({ deleteLoading: true })
    
    const token = wx.getStorageSync('token') || ''
    
    wx.request({
      url: `${API_BASE}/enterpriseUsers/${userId}`,
      method: 'DELETE',
      header: {
        'content-type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      success: (res) => {
        const result = res.data || {}
        
        if (result.code >= 2000 && result.code < 3000) {
          wx.showToast({ title: '已注销企业身份', icon: 'success' })
          this.updateUserTypeToNormal()
        } else {
          wx.showToast({ title: result.message || '注销失败', icon: 'none' })
        }
      },
      fail: (err) => {
        wx.showToast({ title: '网络请求失败', icon: 'none' })
      },
      complete: () => {
        this.setData({ deleteLoading: false })
      }
    })
  },

  goToViewResearch() {
    wx.navigateTo({
      url: '/pages/research-bind/research-bind?mode=view'
    })
  },

  goToEditResearch() {
    wx.navigateTo({
      url: '/pages/research-bind/research-bind?mode=edit'
    })
  },

  doUnbindResearch() {
    const userInfo = wx.getStorageSync('userInfo') || {}
    const account = userInfo.account || {}
    const researchId = account.id
    
    if (!researchId) {
      wx.showToast({ title: '缺少科研团队ID', icon: 'none' })
      return
    }

    wx.showModal({
      title: '确认',
      content: '确定要注销科研团队身份吗？注销后将恢复为普通用户',
      success: (modalRes) => {
        if (modalRes.confirm) {
          this.doDeleteResearch(researchId)
        }
      }
    })
  },

  doDeleteResearch(researchId) {
    this.setData({ deleteLoading: true })
    
    const token = wx.getStorageSync('token') || ''
    
    wx.request({
      url: `${API_BASE}/researchTeamUsers/${researchId}`,
      method: 'DELETE',
      header: {
        'content-type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      success: (res) => {
        const result = res.data || {}
        
        if (result.code >= 2000 && result.code < 3000) {
          wx.showToast({ title: '已注销科研团队身份', icon: 'success' })
          this.updateUserTypeToNormal()
        } else {
          wx.showToast({ title: result.message || '注销失败', icon: 'none' })
        }
      },
      fail: (err) => {
        wx.showToast({ title: '网络请求失败', icon: 'none' })
      },
      complete: () => {
        this.setData({ deleteLoading: false })
      }
    })
  },

  goToPatentManage() {
    wx.navigateTo({
      url: '/pages/patent-manage/patent-manage'
    })
  },

  updateUserTypeToNormal() {
    let userInfo = wx.getStorageSync('userInfo') || {}
    userInfo.userType = 0
    userInfo.account = {}
    
    wx.setStorageSync('userInfo', userInfo)
    app.globalData.userInfo = userInfo
    
    setTimeout(() => {
      this.loadUserData()
    }, 1500)
  }
})
