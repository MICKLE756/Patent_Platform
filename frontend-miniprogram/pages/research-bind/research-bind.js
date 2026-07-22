const app = getApp()
const { apiBase: API_BASE } = require('../../config/api.js')

Page({
  data: {
    mode: 'add',
    form: {
      id: '',
      teamName: '',
      contactName: '',
      institution: '',
      researchDomain: ''
    },
    loading: false
  },

  onLoad(options) {
    const mode = options.mode || 'add'
    
    if (mode === 'view') {
      wx.setNavigationBarTitle({ title: '科研团队信息详情' })
    } else if (mode === 'edit') {
      wx.setNavigationBarTitle({ title: '更新科研团队信息' })
    } else {
      wx.setNavigationBarTitle({ title: '绑定科研团队' })
    }
    
    const account = wx.getStorageSync('userInfo')?.account || {}
    this.setData({
      mode: mode,
      form: {
        id: account.id || '',
        teamName: account.teamName || '',
        contactName: account.contactName || '',
        institution: account.institution || '',
        researchDomain: account.researchDomain || ''
      }
    })
  },

  onInput(e) {
    const field = e.currentTarget.dataset.field
    this.setData({ [`form.${field}`]: e.detail.value })
  },

  onSubmit() {
    const f = this.data.form
    if (!f.teamName || !f.contactName || !f.institution) {
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
    const token = wx.getStorageSync('token') || ''
    this.setData({ loading: true })

    wx.request({
      url: `${API_BASE}/researchTeamUsers`,
      method: 'POST',
      header: {
        'content-type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      data: {
        teamName: this.data.form.teamName,
        contactName: this.data.form.contactName,
        institution: this.data.form.institution,
        researchDomain: this.data.form.researchDomain
      },
      success: (res) => {
        const result = res.data || {}
        if (result.code >= 2000 && result.code < 3000) {
          wx.showToast({ title: '绑定成功', icon: 'success' })
          this.refreshAndBack(2, result.data)
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
    const token = wx.getStorageSync('token') || ''
    const userInfo = wx.getStorageSync('userInfo') || {}
    const userId = userInfo.userId
    this.setData({ loading: true })

    wx.request({
      url: `${API_BASE}/researchTeamUsers/${userId}`,
      method: 'PUT',
      header: {
        'content-type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      data: {
        teamName: this.data.form.teamName,
        contactName: this.data.form.contactName,
        institution: this.data.form.institution,
        researchDomain: this.data.form.researchDomain
      },
      success: (res) => {
        const result = res.data || {}
        if (result.code >= 2000 && result.code < 3000) {
          wx.showToast({ title: '更新成功', icon: 'success' })
          this.refreshAndBack(2, result.data)
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
    if (targetUserType === 2) {
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
