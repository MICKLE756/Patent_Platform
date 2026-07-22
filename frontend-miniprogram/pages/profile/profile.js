const app = getApp()
const { apiBase: API_BASE } = require('../../config/api.js')

const roleMap = {
  0: { name: '普通用户', class: 'role-normal' },
  1: { name: '企业用户', class: 'role-enterprise' },
  2: { name: '科研团队', class: 'role-team' },
  3: { name: '管理员', class: 'role-admin' }
}

Page({
  data: {
    userInfo: {},
    userId: '',
    roleName: '普通用户',
    roleClass: 'role-normal',
    avatarFirstChar: '微'
  },

  onLoad() {
    this.checkLogin()
    this.loadUserInfo()
  },

  onShow() {
    this.checkLogin()
    this.loadUserInfo()
  },

  checkLogin() {
    const token = wx.getStorageSync('token')
    if (!token) {
      wx.redirectTo({
        url: '/pages/login/login'
      })
    }
  },

  loadUserInfo() {
    const userInfo = wx.getStorageSync('userInfo')
    const token = wx.getStorageSync('token')
    if (userInfo) {
      const userType = userInfo.userType !== undefined ? parseInt(userInfo.userType) : 0
      const role = roleMap[userType] || roleMap[0]
      const nickName = userInfo.nickName || '微信用户'
      const firstChar = nickName.charAt(0) || '微'
      
      this.setData({
        userInfo: userInfo,
        userId: userInfo.userId || (token ? token.slice(-8) : '12345678'),
        roleName: role.name,
        roleClass: role.class,
        avatarFirstChar: firstChar
      })
    }
  },

  onMenuItemClick(e) {
    const menu = e.currentTarget.dataset.menu
    if (menu === 'settings') {
      wx.navigateTo({
        url: '/pages/settings/settings'
      })
      return
    }
    
    const menuNames = {
      'search-history': '搜索历史',
      'collection': '我的收藏',
      'about': '关于我们'
    }
    
    wx.showToast({
      title: menuNames[menu] + '功能开发中',
      icon: 'none'
    })
  },

  onLogout() {
    wx.showModal({
      title: '提示',
      content: '确定要退出登录吗？',
      success: (res) => {
        if (res.confirm) {
          this.doLogout()
        }
      }
    })
  },

  doLogout() {
    const token = wx.getStorageSync('token') || ''
    
    wx.request({
      url: `${API_BASE}/auth/logout`,
      method: 'POST',
      header: {
        'content-type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      success: (res) => {
        console.log('📥 退出登录响应:', res.data)
      },
      fail: (err) => {
        console.log('❌ 退出登录接口失败:', err)
      },
      complete: () => {
        this.clearLocalLoginData()
      }
    })
  },

  clearLocalLoginData() {
    app.globalData.userInfo = null
    app.globalData.token = null
    wx.removeStorageSync('userInfo')
    wx.removeStorageSync('token')
    
    wx.showToast({
      title: '已退出登录',
      icon: 'success',
      duration: 1500
    })
    
    setTimeout(() => {
      wx.redirectTo({
        url: '/pages/login/login'
      })
    }, 1500)
  }
})
