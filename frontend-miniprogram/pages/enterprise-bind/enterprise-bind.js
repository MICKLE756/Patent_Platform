const app = getApp()
const { apiBase: API_BASE } = require('../../config/api.js')

Page({
  data: {
    mode: 'add',
    form: {
      companyName: '',
      contactName: '',
      businessLicense: '',
      companyAddress: '',
      companyIntro: ''
    },
    loading: false
  },

  onLoad(options) {
    const mode = options.mode || 'add'
    
    if (mode === 'view') {
      wx.setNavigationBarTitle({
        title: '企业信息详情'
      })
    } else if (mode === 'edit') {
      wx.setNavigationBarTitle({
        title: '更新企业信息'
      })
    } else {
      wx.setNavigationBarTitle({
        title: '绑定企业身份'
      })
    }
    
    const account = wx.getStorageSync('userInfo')?.account || {}
    this.setData({
      mode: mode,
      form: {
        id: account.id || '',
        companyName: account.companyName || '',
        contactName: account.contactName || '',
        businessLicense: account.businessLicense || '',
        companyAddress: account.companyAddress || '',
        companyIntro: account.companyIntro || ''
      }
    })
  },

  get canSubmit() {
    const f = this.data.form
    return f.companyName && f.contactName && f.businessLicense
  },

  onInput(e) {
    const field = e.currentTarget.dataset.field
    this.setData({
      [`form.${field}`]: e.detail.value
    })
  },

  onSubmit() {
    const f = this.data.form
    if (!f.companyName || !f.contactName || !f.businessLicense) {
      wx.showToast({ title: '请填写必填项', icon: 'none' })
      return
    }

    if (this.data.mode === 'edit') {
      this.doUpdate()
    } else {
      this.doBind()
    }
  },

  doBind() {
    this.setData({ loading: true })
    const token = wx.getStorageSync('token') || ''

    wx.request({
      url: `${API_BASE}/enterpriseUsers`,
      method: 'POST',
      header: {
        'content-type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      data: this.data.form,
      success: (res) => {
        const result = res.data || {}
        if (result.code >= 2000 && result.code < 3000) {
          wx.showToast({ title: '绑定成功', icon: 'success' })
          this.refreshAndBack(1, result.data)
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
  },

  doUpdate() {
    this.setData({ loading: true })
    const token = wx.getStorageSync('token') || ''
    const userInfo = wx.getStorageSync('userInfo') || {}
    const userId = userInfo.userId

    if (!userId) {
      wx.showToast({ title: '缺少用户ID', icon: 'none' })
      this.setData({ loading: false })
      return
    }

    wx.request({
      url: `${API_BASE}/enterpriseUsers/${userId}`,
      method: 'PUT',
      header: {
        'content-type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      data: this.data.form,
      success: (res) => {
        const result = res.data || {}
        if (result.code >= 2000 && result.code < 3000) {
          wx.showToast({ title: '更新成功', icon: 'success' })
          this.refreshAndBack(1, result.data)
        } else {
          wx.showToast({ title: result.message || '更新失败', icon: 'none' })
        }
      },
      fail: () => {
        wx.showToast({ title: '网络请求失败', icon: 'none' })
      },
      complete: () => {
        this.setData({ loading: false })
      }
    })
  },

  refreshAndBack(targetUserType, newAccountData) {
    let userInfo = wx.getStorageSync('userInfo') || {}
    userInfo.userType = targetUserType
    if (targetUserType === 1) {
      if (newAccountData) {
        userInfo.account = newAccountData
      } else {
        userInfo.account = Object.assign({}, userInfo.account, this.data.form)
      }
    } else {
      userInfo.account = {}
    }
    
    wx.setStorageSync('userInfo', userInfo)
    app.globalData.userInfo = userInfo

    setTimeout(() => {
      wx.reLaunch({
        url: '/pages/index/index'
      })
    }, 1500)
  }
})
