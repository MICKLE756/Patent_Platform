const app = getApp()
const { apiBase: API_BASE } = require('../../config/api.js')

Page({
  data: {
    loading: false,
    loginMode: 'wechat',
    username: 'admin',
    password: '123456',
    canLogin: false
  },

  onLoad() {
    wx.removeStorageSync('token')
    wx.removeStorageSync('userInfo')
  },

  switchLoginMode() {
    this.setData({
      loginMode: this.data.loginMode === 'wechat' ? 'password' : 'wechat',
      username: '',
      password: '',
      canLogin: false
    })
  },

  onUsernameInput(e) {
    const value = e.detail.value || ''
    const { password } = this.data
    const canLogin = value.trim().length > 0 && password.trim().length > 0
    this.setData({ username: value, canLogin })
  },

  onPasswordInput(e) {
    const value = e.detail.value || ''
    const { username } = this.data
    const canLogin = username.trim().length > 0 && value.trim().length > 0
    this.setData({ password: value, canLogin })
  },

  doRealLogin() {
    this.setData({ loading: true })
    wx.login({
      success: (res) => {
        if (res.code) {
          console.log('✅ wx.login code:', res.code)
          this.requestRealLogin(res.code)
        } else {
          this.setData({ loading: false })
          wx.showToast({ title: '获取code失败', icon: 'none' })
        }
      },
      fail: () => {
        this.setData({ loading: false })
        wx.showToast({ title: '登录失败，请重试', icon: 'none' })
      }
    })
  },

  requestRealLogin(code) {
    const requestUrl = `${API_BASE}/auth/wechat-login`
    console.log('📤 请求:', requestUrl, 'code:', code)
    
    wx.request({
      url: requestUrl,
      method: 'POST',
      data: { code: code },
      header: { 'content-type': 'application/json' },
      success: (res) => {
        console.log('📥 登录响应:', res.data)
        const result = res.data || {}
        
        if (result.code >= 2000 && result.code < 3000 && result.data) {
          const loginData = result.data
          const token = loginData.token
          const account = loginData.account || {}
          const userInfo = {
            nickName: account.wechatNickname || '微信用户',
            avatarUrl: account.wechatAvatar || '',
            userId: loginData.userId,
            userType: loginData.userType,
            account: account
          }
          this.handleLoginSuccess(token, userInfo)
        } else {
          const errMsg = result.message || '登录失败'
          console.log('❌ 登录失败:', errMsg)
          this.handleLoginFail(errMsg)
        }
      },
      fail: (err) => {
        console.error('❌ 后端连接失败:', err)
        this.handleLoginFail('无法连接后端，请检查服务状态')
      }
    })
  },

  doPasswordLogin() {
    const { username, password } = this.data
    if (!username.trim() || !password.trim()) {
      wx.showToast({ title: '请输入用户名和密码', icon: 'none' })
      return
    }

    this.setData({ loading: true })
    
    const requestUrl = `${API_BASE}/auth/login`
    console.log('📤 账密登录请求:', requestUrl)
    
    wx.request({
      url: requestUrl,
      method: 'POST',
      data: { 
        username: username.trim(),
        password: password.trim()
      },
      header: { 'content-type': 'application/json' },
      success: (res) => {
        console.log('📥 账密登录响应:', res.data)
        const result = res.data || {}
        
        if (result.code >= 2000 && result.code < 3000 && result.data) {
          const loginData = result.data
          const token = loginData.token
          const account = loginData.account || {}
          const userInfo = {
            nickName: account.realName || account.wechatNickname || account.username || '用户',
            avatarUrl: account.wechatAvatar || '',
            userId: loginData.userId,
            userType: loginData.userType,
            account: account
          }
          this.handleLoginSuccess(token, userInfo)
        } else {
          const errMsg = result.message || '登录失败'
          console.log('❌ 账密登录失败:', errMsg)
          this.handleLoginFail(errMsg)
        }
      },
      fail: (err) => {
        console.error('❌ 后端连接失败:', err)
        this.handleLoginFail('无法连接后端，请检查服务状态')
      }
    })
  },

  handleLoginSuccess(token, userInfo) {
    app.globalData.userInfo = userInfo
    app.globalData.token = token
    
    wx.setStorageSync('userInfo', userInfo)
    wx.setStorageSync('token', token)
    
    this.setData({ loading: false })
    
    wx.showToast({
      title: '登录成功',
      icon: 'success',
      duration: 1500
    })
    
    setTimeout(() => {
      wx.switchTab({
        url: '/pages/index/index'
      })
    }, 1000)
  },

  handleLoginFail(message) {
    this.setData({ loading: false })
    wx.showToast({
      title: message || '登录失败',
      icon: 'none',
      duration: 2500
    })
  }
})