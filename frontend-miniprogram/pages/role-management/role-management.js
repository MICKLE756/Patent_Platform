const { apiBase: API_BASE } = require('../../config/api.js')

Page({
  data: {
    loading: false,
    roleList: [],
    currentUserRole: null,
    showModal: false,
    modalType: 'detail',
    currentRole: null,
    formData: {
      roleName: '',
      roleCode: '',
      description: ''
    },
    permissionList: [
      { key: 'permissionEnterpriseView', label: '企业视图', level: 1 },
      { key: 'permissionEnterpriseEdit', label: '企业编辑', level: 2 },
      { key: 'permissionEnterpriseDelete', label: '企业删除', level: 3 },
      { key: 'permissionResearchView', label: '科研视图', level: 1 },
      { key: 'permissionResearchEdit', label: '科研编辑', level: 2 },
      { key: 'permissionResearchDelete', label: '科研删除', level: 3 },
      { key: 'permissionPatentAudit', label: '专利审核', level: 2 },
      { key: 'permissionPatentReview', label: '专利审查', level: 3 },
      { key: 'permissionNoticePublish', label: '公告发布', level: 2 },
      { key: 'permissionStatisticsView', label: '统计查看', level: 2 },
      { key: 'permissionSystemConfig', label: '系统配置', level: 3 },
      { key: 'permissionIntentionAudit', label: '意向审核', level: 2 },
      { key: 'permissionLogView', label: '日志查看', level: 2 }
    ],
    permissions: {},
    disabledPermissions: []
  },

  onLoad() {
    this.loadCurrentUserRole()
    this.loadRoleList()
  },

  loadCurrentUserRole() {
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
          this.setData({ currentUserRole: result.data })
        }
      }
    })
  },

  loadRoleList() {
    const token = wx.getStorageSync('token') || ''
    this.setData({ loading: true })

    wx.request({
      url: `${API_BASE}/roles`,
      method: 'GET',
      header: {
        'content-type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      success: (res) => {
        const result = res.data || {}
        if (result.code >= 2000 && result.code < 3000) {
          this.setData({ roleList: Array.isArray(result.data) ? result.data : [] })
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
    this.loadRoleList()
  },

  openDetailModal(e) {
    const role = e.currentTarget.dataset.role
    this.setData({
      showModal: true,
      modalType: 'detail',
      currentRole: role
    })
    this.loadRoleDetail(role.id)
  },

  loadRoleDetail(roleId) {
    const token = wx.getStorageSync('token') || ''
    wx.request({
      url: `${API_BASE}/roles/${roleId}`,
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
            permissions: data.permission || {}
          })
        }
      }
    })
  },

  openCreateModal() {
    const disabledPermissions = this.getDisabledPermissions()
    this.setData({
      showModal: true,
      modalType: 'create',
      currentRole: null,
      formData: {
        roleName: '',
        roleCode: '',
        description: ''
      },
      permissions: {},
      disabledPermissions: disabledPermissions
    })
  },

  openEditModal(e) {
    const role = e.currentTarget.dataset.role
    const disabledPermissions = this.getDisabledPermissions()
    
    const permissions = {}
    if (role.permissions) {
      Object.keys(role.permissions).forEach(key => {
        permissions[key] = role.permissions[key]
      })
    }

    this.setData({
      showModal: true,
      modalType: 'edit',
      currentRole: role,
      formData: {
        roleName: role.roleName || '',
        roleCode: role.roleCode || '',
        description: role.description || ''
      },
      permissions: permissions,
      disabledPermissions: disabledPermissions
    })
  },

  getDisabledPermissions() {
    const { currentUserRole, permissionList } = this.data
    const disabled = []

    if (!currentUserRole || !currentUserRole.permissions) {
      return disabled
    }

    const userPermissions = currentUserRole.permissions

    permissionList.forEach(perm => {
      if (userPermissions[perm.key] !== 1) {
        disabled.push(perm.key)
      }
    })

    return disabled
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

  onPermissionChange(e) {
    const key = e.currentTarget.dataset.key
    if (this.data.disabledPermissions.includes(key)) {
      wx.showToast({ title: '您没有此权限', icon: 'none' })
      return
    }
    const checked = e.detail.value.length > 0
    this.setData({
      [`permissions.${key}`]: checked ? 1 : 0
    })
  },

  submitForm() {
    const { formData, permissions, modalType, currentRole } = this.data
    const token = wx.getStorageSync('token') || ''

    if (!formData.roleName.trim()) {
      wx.showToast({ title: '请输入角色名称', icon: 'none' })
      return
    }
    if (!formData.roleCode.trim()) {
      wx.showToast({ title: '请输入角色代码', icon: 'none' })
      return
    }

    const url = modalType === 'create' ? `${API_BASE}/roles` : `${API_BASE}/roles/${currentRole.id}`
    const method = modalType === 'create' ? 'POST' : 'PUT'

    wx.request({
      url: url,
      method: method,
      header: {
        'content-type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      data: {
        roleName: formData.roleName.trim(),
        roleCode: formData.roleCode.trim(),
        description: formData.description.trim(),
        permissions: permissions
      },
      success: (res) => {
        const result = res.data || {}
        if (result.code >= 2000 && result.code < 3000) {
          wx.showToast({ title: modalType === 'create' ? '创建成功' : '更新成功', icon: 'success' })
          this.closeModal()
          this.loadRoleList()
        } else {
          wx.showToast({ title: result.message || (modalType === 'create' ? '创建失败' : '更新失败'), icon: 'none' })
        }
      },
      fail: () => {
        wx.showToast({ title: '请求失败', icon: 'none' })
      }
    })
  },

  deleteRole(e) {
    const role = e.currentTarget.dataset.role
    wx.showModal({
      title: '确认删除',
      content: `确定要删除角色「${role.roleName}」吗？`,
      success: (res) => {
        if (res.confirm) {
          this.doDeleteRole(role.id)
        }
      }
    })
  },

  doDeleteRole(roleId) {
    const token = wx.getStorageSync('token') || ''
    wx.request({
      url: `${API_BASE}/roles/${roleId}`,
      method: 'DELETE',
      header: {
        'content-type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      success: (res) => {
        const result = res.data || {}
        if (result.code >= 2000 && result.code < 3000) {
          wx.showToast({ title: '删除成功', icon: 'success' })
          this.loadRoleList()
        } else {
          wx.showToast({ title: result.message || '删除失败', icon: 'none' })
        }
      },
      fail: () => {
        wx.showToast({ title: '请求失败', icon: 'none' })
      }
    })
  },

  isPermissionDisabled(key) {
    return this.data.disabledPermissions.includes(key)
  },

  getPermissionLabel(permission) {
    const perm = this.data.permissionList.find(p => p.key === permission)
    return perm ? perm.label : permission
  },

  hasPermission(key) {
    return this.data.permissions[key] === 1
  }
})