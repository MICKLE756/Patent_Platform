App({
  onLaunch() {
    console.log('🚀 App启动')
    // 每次启动都清空存储，让用户重新登录
    wx.removeStorageSync('token')
    wx.removeStorageSync('userInfo')
    this.globalData.token = null
    this.globalData.userInfo = null
    console.log('🧹 已清空登录信息，请重新登录')
  },

  globalData: {
    userInfo: null,
    token: null,
    userType: 0,
    appId: 'wxf307a30a18e70ce7'
  }
})
