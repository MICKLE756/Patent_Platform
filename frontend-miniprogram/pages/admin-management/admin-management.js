const { apiBase: API_BASE } = require('../../config/api.js')
const permissionManager = require('../../utils/permission.js')

Page({
  data: {
    activeTab: 'user',
    tabs: [
      { key: 'user', label: '用户管理' },
      { key: 'patent', label: '专利管理' }
    ],
    
    userTypeOptions: [
      { value: 'all', label: '所有用户' },
      { value: 'normal', label: '普通用户' },
      { value: 'enterprise', label: '企业用户' },
      { value: 'research', label: '科研团队用户' },
      { value: 'admin', label: '管理员用户' }
    ],
    selectedUserType: 'all',
    userTypeIndex: 0,
    
    loading: false,
    userList: [],
    patentList: [],
    pageNum: 1,
    pageSize: 10,
    total: 0,
    pages: 0,
    
    showModal: false,
    modalType: '',
    currentItem: null,
    formData: {},
    
    permissions: {},
    canAddUser: false,
    canAddPatent: false,
    showPatentTab: false,
    modalTitleText: '',
    modalButtonText: ''
  },

  onLoad() {
    this.loadPermissions()
    this.loadUserList()
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
          
          const canAddUser = permissions.permissionEnterpriseEdit === 1 || permissions.permissionResearchEdit === 1
          const canAddPatent = permissions.permissionPatentAudit === 1
          const showPatentTab = permissions.permissionPatentAudit === 1 || permissions.permissionPatentReview === 1
          
          this.setData({ 
            permissions,
            canAddUser,
            canAddPatent,
            showPatentTab
          })
        }
      }
    })
  },

  hasPermission(permKey) {
    return permissionManager.hasPermission(permKey)
  },

  processUserList(list, selectedUserType) {
    const typeMap = {
      'all': '',
      'normal': '普通用户',
      'enterprise': '企业用户',
      'research': '科研团队用户',
      'admin': '管理员'
    }
    return list.map(item => {
      const userTypeDisplay = item.userType !== undefined 
        ? this.getUserTypeLabel(item.userType) 
        : typeMap[selectedUserType] || '普通用户'
      
      const displayName = item.teamName || item.companyName || item.wechatNickname || item.realName || item.username || ''
      const avatarSource = item.wechatNickname || item.realName || item.username || '?'
      const avatarChar = avatarSource.charAt(0)
      const statusLabel = this.getUserStatusLabel(item.status)
      
      return {
        ...item,
        userTypeDisplay,
        displayName,
        avatarChar,
        statusLabel
      }
    })
  },

  onTabChange(e) {
    const key = e.currentTarget.dataset.key
    this.setData({ 
      activeTab: key,
      pageNum: 1 
    })
    if (key === 'user') {
      this.loadUserList()
    } else {
      this.loadPatentList()
    }
  },

  onUserTypeChange(e) {
    const index = e.detail.value
    const selectedUserType = this.data.userTypeOptions[index].value
    this.setData({ 
      selectedUserType: selectedUserType,
      userTypeIndex: index,
      pageNum: 1 
    })
    this.loadUserList()
  },

  loadUserList() {
    const token = wx.getStorageSync('token') || ''
    const { selectedUserType, pageNum, pageSize } = this.data
    this.setData({ loading: true })

    let url = ''
    switch(selectedUserType) {
      case 'enterprise':
        url = `${API_BASE}/enterpriseUsers?pageNum=${pageNum}&pageSize=${pageSize}`
        break
      case 'research':
        url = `${API_BASE}/researchTeamUsers?pageNum=${pageNum}&pageSize=${pageSize}`
        break
      case 'admin':
        url = `${API_BASE}/adminUsers`
        break
      case 'normal':
        url = `${API_BASE}/users?userType=0&pageNum=${pageNum}&pageSize=${pageSize}`
        break
      default:
        url = `${API_BASE}/users?pageNum=${pageNum}&pageSize=${pageSize}`
    }

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
          const data = result.data || {}
          let list = []
          if (selectedUserType === 'admin') {
            list = Array.isArray(data) ? data : []
            this.setData({ 
              userList: this.processUserList(list, selectedUserType),
              total: data.length || 0,
              pages: 1
            })
          } else {
            list = data.list || []
            this.setData({ 
              userList: this.processUserList(list, selectedUserType),
              total: data.total || 0,
              pages: data.pages || 1
            })
          }
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

  loadPatentList() {
    const token = wx.getStorageSync('token') || ''
    const { pageNum, pageSize } = this.data
    this.setData({ loading: true })

    wx.request({
      url: `${API_BASE}/patents?pageNum=${pageNum}&pageSize=${pageSize}`,
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
            patentList: data.data || [],
            total: data.total || 0,
            pages: data.pages || 1
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

  openDetailModal(e) {
    const item = e.currentTarget.dataset.item
    const activeTab = this.data.activeTab
    const modalTitleText = activeTab === 'user' ? '用户详情' : '专利详情'
    
    let processedItem = item
    if (activeTab === 'user') {
      processedItem = {
        ...item,
        nickName: item.wechatNickname || item.realName || '-',
        userTypeLabel: this.getUserTypeLabel(item.userType),
        statusLabel: this.getUserStatusLabel(item.status),
        username: item.username || '-',
        contactPhone: item.contactPhone || '-',
        contactEmail: item.contactEmail || '-',
        createTime: item.createTime || '-'
      }
    } else {
      processedItem = {
        ...item,
        patentId: item.patentId || '-',
        title: item.title || '-',
        applicant: item.applicant || '-',
        inventors: item.inventors || '-',
        patentType: item.patentType || '-',
        legalStatus: item.legalStatus || '-',
        validity: item.validity || '-',
        applicationDate: item.applicationDate || '-',
        publicationDate: item.publicationDate || '-',
        grantDate: item.grantDate || '-',
        abstractText: item.abstractText || '-'
      }
    }
    
    this.setData({
      showModal: true,
      modalType: 'detail',
      currentItem: processedItem,
      modalTitleText: modalTitleText
    })
  },

  openCreateModal() {
    const activeTab = this.data.activeTab
    const modalTitleText = activeTab === 'user' ? '新增用户' : '新增专利'
    const defaultForm = activeTab === 'user' ? {
      username: '',
      realName: '',
      contactPhone: '',
      contactEmail: '',
      status: 1
    } : {
      title: '',
      applicant: '',
      inventors: '',
      patentType: '',
      legalStatus: '',
      validity: '',
      abstractText: ''
    }
    this.setData({
      showModal: true,
      modalType: 'create',
      currentItem: null,
      formData: defaultForm,
      modalTitleText: modalTitleText,
      modalButtonText: '创建'
    })
  },

  openEditModal(e) {
    const item = e.currentTarget.dataset.item
    const activeTab = this.data.activeTab
    const modalTitleText = activeTab === 'user' ? '编辑用户' : '编辑专利'
    const formData = activeTab === 'user' ? {
      username: item.username || '',
      realName: item.realName || '',
      contactPhone: item.contactPhone || '',
      contactEmail: item.contactEmail || '',
      status: item.status || 1
    } : {
      title: item.title || '',
      applicant: item.applicant || '',
      inventors: item.inventors || '',
      patentType: item.patentType || '',
      legalStatus: item.legalStatus || '',
      validity: item.validity || '',
      abstractText: item.abstractText || ''
    }
    this.setData({
      showModal: true,
      modalType: 'edit',
      currentItem: item,
      formData: formData,
      modalTitleText: modalTitleText,
      modalButtonText: '保存'
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

  onStatusChange(e) {
    this.setData({
      'formData.status': e.detail.value ? 1 : 0
    })
  },

  submitForm() {
    const { modalType, formData, currentItem, activeTab } = this.data
    const token = wx.getStorageSync('token') || ''

    if (modalType === 'create') {
      let url = activeTab === 'user' ? `${API_BASE}/users` : `${API_BASE}/patents`
      wx.request({
        url: url,
        method: 'POST',
        header: {
          'content-type': 'application/json',
          'Authorization': `Bearer ${token}`
        },
        data: formData,
        success: (res) => {
          const result = res.data || {}
          if (result.code >= 2000 && result.code < 3000) {
            wx.showToast({ title: '创建成功', icon: 'success' })
            this.closeModal()
            if (activeTab === 'user') {
              this.loadUserList()
            } else {
              this.loadPatentList()
            }
          } else {
            wx.showToast({ title: result.message || '创建失败', icon: 'none' })
          }
        },
        fail: () => {
          wx.showToast({ title: '请求失败', icon: 'none' })
        }
      })
    } else {
      let url = activeTab === 'user' ? `${API_BASE}/users/${currentItem.id}` : `${API_BASE}/patents/${currentItem.patentId}`
      wx.request({
        url: url,
        method: 'PUT',
        header: {
          'content-type': 'application/json',
          'Authorization': `Bearer ${token}`
        },
        data: formData,
        success: (res) => {
          const result = res.data || {}
          if (result.code >= 2000 && result.code < 3000) {
            wx.showToast({ title: '更新成功', icon: 'success' })
            this.closeModal()
            if (activeTab === 'user') {
              this.loadUserList()
            } else {
              this.loadPatentList()
            }
          } else {
            wx.showToast({ title: result.message || '更新失败', icon: 'none' })
          }
        },
        fail: () => {
          wx.showToast({ title: '请求失败', icon: 'none' })
        }
      })
    }
  },

  deleteItem(e) {
    const item = e.currentTarget.dataset.item
    const activeTab = this.data.activeTab
    wx.showModal({
      title: '确认删除',
      content: `确定要删除${activeTab === 'user' ? '用户' : '专利'}「${item.title || item.username || item.wechatNickname || item.realName}」吗？`,
      success: (res) => {
        if (res.confirm) {
          this.doDeleteItem(item)
        }
      }
    })
  },

  upgradeToAdmin(e) {
    const item = e.currentTarget.dataset.item
    wx.showModal({
      title: '确认升级',
      content: `确定要将用户「${item.username || item.wechatNickname || item.realName}」升级为管理员吗？`,
      success: (res) => {
        if (res.confirm) {
          this.doUpgradeToAdmin(item)
        }
      }
    })
  },

  doUpgradeToAdmin(item) {
    const token = wx.getStorageSync('token') || ''
    wx.request({
      url: `${API_BASE}/adminUsers`,
      method: 'POST',
      header: {
        'content-type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      data: {
        username: item.username,
        realName: item.realName || item.wechatNickname
      },
      success: (res) => {
        const result = res.data || {}
        if (result.code >= 2000 && result.code < 3000) {
          wx.showToast({ title: '升级成功', icon: 'success' })
          this.loadUserList()
        } else {
          wx.showToast({ title: result.message || '升级失败', icon: 'none' })
        }
      },
      fail: () => {
        wx.showToast({ title: '请求失败', icon: 'none' })
      }
    })
  },

  doDeleteItem(item) {
    const token = wx.getStorageSync('token') || ''
    const activeTab = this.data.activeTab
    let url = activeTab === 'user' ? `${API_BASE}/users/${item.id}` : `${API_BASE}/patents/${item.patentId}`
    
    wx.request({
      url: url,
      method: 'DELETE',
      header: {
        'content-type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      success: (res) => {
        const result = res.data || {}
        if (result.code >= 2000 && result.code < 3000) {
          wx.showToast({ title: '删除成功', icon: 'success' })
          if (activeTab === 'user') {
            this.loadUserList()
          } else {
            this.loadPatentList()
          }
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
      if (this.data.activeTab === 'user') {
        this.loadUserList()
      } else {
        this.loadPatentList()
      }
    }
  },

  onNextPage() {
    if (this.data.pageNum < this.data.pages) {
      this.setData({ pageNum: this.data.pageNum + 1 })
      if (this.data.activeTab === 'user') {
        this.loadUserList()
      } else {
        this.loadPatentList()
      }
    }
  },

  getUserTypeLabel(userType) {
    const types = {
      0: '普通用户',
      1: '企业用户',
      2: '科研团队用户',
      3: '管理员'
    }
    return types[userType] || '未知'
  },

  getUserStatusLabel(status) {
    return status === 1 ? '正常' : '禁用'
  }
})