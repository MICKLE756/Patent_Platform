const { apiBase: API_BASE } = require('../../config/api.js')
const permissionManager = require('../../utils/permission.js')

function checkTokenError(result) {
  if (result.code === 401 || 
      result.message && result.message.includes('access_token') || 
      result.message && result.message.includes('需要重新登录')) {
    console.log('=== Token失效，需要重新登录 ===')
    wx.removeStorageSync('token')
    wx.removeStorageSync('userInfo')
    wx.redirectTo({ url: '/pages/login/login' })
    return true
  }
  return false
}

const roleMenuConfig = {
  0: [
    { label: '专利筛选', icon: '🔍', page: 'patent-list' }
  ],
  1: [
    { label: 'AI智能助手', icon: '🤖', page: 'patent-assistant' },
    { label: '专利筛选', icon: '🔍', page: 'patent-list' }
  ],
  2: [
    { label: '我的专利', icon: '📋', page: 'team-patents' },
    { label: '意向消息', icon: '💬', page: 'team-messages' },
    { label: '意向审核', icon: '📝', page: 'intention-review' },
    { label: '专利筛选', icon: '🔍', page: 'patent-list' }
  ],
  3: [
    { label: '数据看板', icon: '📊', page: 'dashboard' },
    { label: '消息管理', icon: '💬', page: 'message-management' },
    { label: '管理后台', icon: '🔧', page: 'admin-management' },
    { label: '角色管理', icon: '👥', page: 'role-management' },
    { label: '系统管理', icon: '⚙️', page: 'system-management' }
  ]
}

Page({
  data: {
    sidebarOpen: false,
    userType: 0,
    menuList: [],
    activeMenu: '',
    currentMenu: {},
    teamPatents: [],
    
    permissions: {},
    canAddUser: false,
    canAddPatent: false,
    showPatentTab: false,
    adminUserTypeOptions: [
      { value: 'all', label: '所有用户' },
      { value: 'normal', label: '普通用户' },
      { value: 'enterprise', label: '企业用户' },
      { value: 'research', label: '科研团队用户' },
      { value: 'admin', label: '管理员用户' }
    ],
    adminSelectedUserType: 'all',
    adminUserTypeIndex: 0,
    adminUserList: [],
    adminPatentList: [],
    patentSearchKeyword: '',
    adminPageNum: 1,
    adminPageSize: 10,
    adminTotal: 0,
    adminPages: 0,
    adminLoading: false,
    
    showModal: false,
    modalType: '',
    currentItem: null,
    formData: {},
    modalTitleText: '',
    modalButtonText: '',
    adminDataType: 'user',
    
    roleList: [],
    roleLoading: false,
    rolePermissionList: [],
    
    messageActiveTab: 'intention',
    intentionList: [],
    noticeList: [],
    logList: [],
    messageLoading: false,
    
    keyword: '',
    techField: '',
    patentType: '',
    legalStatus: '',
    validity: '',
    
    techFields: [
      { id: '', name: '不限' },
      { id: '人工智能', name: '人工智能' },
      { id: '互联网与云计算', name: '互联网与云计算' }
    ],
    patentTypes: [
      { id: '', name: '不限' },
      { id: '发明', name: '发明' },
      { id: '实用新型', name: '实用新型' },
      { id: '外观设计', name: '外观设计' }
    ],
    legalStatuses: [
      { id: '', name: '不限' },
      { id: '授权', name: '授权' },
      { id: '公开', name: '公开' },
      { id: '实质审查', name: '实质审查' }
    ],
    validities: [
      { id: '', name: '不限' },
      { id: '有效', name: '有效' },
      { id: '无效', name: '无效' },
      { id: '终止', name: '终止' }
    ],
    
    resultList: [],
    pageNum: 1,
    pageSize: 10,
    total: 0,
    pages: 0,
    loadingMore: false,
    
    dashboardData: {
      todayActiveUsers: 0,
      todayNewPatents: 0,
      todayMatchConversations: 0,
      patentMatchRate: 0,
      intentionConversionRate: 0,
      hotTechFields: [],
      hotPatents: [],
      userGrowthData: []
    },
    

    
    msgList: [
      {
        role: 'assistant',
        content: '您好！我是AI智能助手，有什么可以帮助您的吗？'
      }
    ],
    inputText: '',
    loading: false
  },

  onLoad() {
    this.initByUserType()
  },

  onShow() {
    const prevActiveMenu = wx.getStorageSync('prevActiveMenu')
    
    this.initByUserType()
    
    if (prevActiveMenu && prevActiveMenu !== this.data.activeMenu) {
      const menuItem = this.data.menuList.find(item => item.page === prevActiveMenu)
      if (menuItem) {
        this.setData({
          activeMenu: prevActiveMenu,
          currentMenu: menuItem
        })
        wx.setNavigationBarTitle({
          title: menuItem.label
        })
        if (prevActiveMenu === 'patent-list' && this.data.resultList.length === 0) {
          this.setData({ pageNum: 1 })
          this.loadPatents(true)
        }
      }
      wx.removeStorageSync('prevActiveMenu')
    }
    
    if (this.data.userType === 2) {
      this.loadTeamPatents()
    }
  },

  initByUserType() {
    const token = wx.getStorageSync('token')
    if (token) {
      const userInfo = wx.getStorageSync('userInfo') || {}
      const userType = userInfo.userType !== undefined ? parseInt(userInfo.userType) : 0
      let menuList = roleMenuConfig[userType] || roleMenuConfig[0]
      
      console.log('=== 初始化菜单调试 ===')
      console.log('userType:', userType)
      console.log('原始菜单列表:', menuList)

      if (userType === 3) {
        this.loadAdminPermissions(token, (permissions) => {
          permissionManager.setPermissions(permissions)
          let filteredMenuList = permissionManager.filterMenuByPermission(menuList)
          console.log('过滤后的菜单列表:', filteredMenuList)
          console.log('过滤后菜单数量:', filteredMenuList.length)
          if (filteredMenuList.length === 0) {
            console.log('过滤后菜单为空，使用原始菜单作为兜底')
            filteredMenuList = menuList
          }
          this.setupMenu(userType, filteredMenuList)
          
          if (this.data.activeMenu === 'dashboard') {
            this.loadDashboardData()
          } else if (this.data.activeMenu === 'admin-management') {
            this.loadAdminUsers()
          } else if (this.data.activeMenu === 'role-management') {
            this.loadRoles()
          } else if (this.data.activeMenu === 'message-management') {
            this.loadMessages()
          }
        })
      } else {
        this.setupMenu(userType, menuList)
      }
    } else {
      wx.redirectTo({
        url: '/pages/login/login'
      })
    }
  },

  loadAdminPermissions(token, callback) {
    wx.request({
      url: `${API_BASE}/adminUsers/me`,
      method: 'GET',
      header: {
        'content-type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      success: (res) => {
        const result = res.data || {}
        
        if (checkTokenError(result)) {
          return
        }
        
        const permissions = result.code >= 2000 && result.code < 3000 
          ? (result.data?.permissions || {}) 
          : {}
        
        console.log('========================================')
        console.log('======= 管理员权限信息调试 =======')
        console.log('接口返回code:', result.code)
        console.log('接口返回data:', result.data)
        console.log('----------------------------------------')
        console.log('解析后的权限列表:')
        console.log('----------------------------------------')
        
        const permissionLabels = {
          permissionEnterpriseView: '企业用户查看',
          permissionEnterpriseEdit: '企业用户编辑',
          permissionEnterpriseDelete: '企业用户删除',
          permissionResearchView: '科研团队查看',
          permissionResearchEdit: '科研团队编辑',
          permissionResearchDelete: '科研团队删除',
          permissionPatentAudit: '专利审核',
          permissionPatentReview: '专利查看',
          permissionNoticePublish: '通知发布',
          permissionStatisticsView: '统计查看',
          permissionSystemConfig: '系统配置',
          permissionIntentionAudit: '意向审核',
          permissionLogView: '日志查看'
        }
        
        let grantedCount = 0
        let totalCount = Object.keys(permissionLabels).length
        
        Object.keys(permissionLabels).forEach(key => {
          const hasPermission = permissions[key] === 1
          if (hasPermission) grantedCount++
          console.log(`${hasPermission ? '[√]' : '[×]'} ${permissionLabels[key]}`)
        })
        
        console.log('----------------------------------------')
        console.log(`权限统计: ${grantedCount}/${totalCount} 项已授权`)
        console.log('是否超级管理员:', grantedCount === totalCount)
        console.log('========================================')
        
        callback(permissions)
      },
      fail: (err) => {
        console.log('=== 权限接口请求失败 ===')
        console.log('错误信息:', err)
        callback({})
      }
    })
  },

  setupMenu(userType, menuList) {
    const defaultMenu = menuList[0] ? menuList[0].page : ''
    const defaultTitle = menuList[0] ? menuList[0].label : '功能'

    let currentActiveMenu = this.data.activeMenu
    if (!currentActiveMenu || !menuList.find(item => item.page === currentActiveMenu)) {
      currentActiveMenu = defaultMenu
    }
    const currentMenuItem = menuList.find(item => item.page === currentActiveMenu) || menuList[0] || {}

    this.setData({
      userType: userType,
      menuList: menuList,
      activeMenu: currentActiveMenu,
      currentMenu: currentMenuItem
    })

    wx.setNavigationBarTitle({
      title: currentMenuItem.label || defaultTitle
    })

    if (userType === 2 && this.data.teamPatents.length === 0) {
      this.loadTeamPatents()
    }
    
    if (currentActiveMenu === 'patent-list' && this.data.resultList.length === 0) {
      this.setData({ pageNum: 1 })
      this.loadPatents(true)
    }
  },







  loadTeamPatents() {
    const token = wx.getStorageSync('token') || ''
    wx.request({
      url: `${API_BASE}/patents/bound`,
      method: 'GET',
      header: {
        'content-type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      success: (res) => {
        const result = res.data || {}
        
        if (checkTokenError(result)) {
          return
        }
        
        if (result.code >= 2000 && result.code < 3000) {
          const items = Array.isArray(result.data) ? result.data : []
          const processedItems = items.map(item => {
            if (item.techField) {
              const fields = item.techField.split(/[,，、;；\s]+/).filter(f => f.trim())
              if (fields.length > 1) {
                return { ...item, displayTechField: fields[0] + ' 等' }
              } else {
                return { ...item, displayTechField: item.techField }
              }
            }
            return { ...item, displayTechField: '' }
          })
          this.setData({ teamPatents: processedItems })
        } else {
          this.setData({ teamPatents: [] })
        }
      },
      fail: () => {
        this.setData({ teamPatents: [] })
      }
    })
  },

  goToPatentDetail(e) {
    const id = e.currentTarget.dataset.id
    if (id) {
      wx.setStorageSync('prevActiveMenu', this.data.activeMenu)
      wx.navigateTo({
        url: `/pages/patent-detail/patent-detail?id=${id}`
      })
    }
  },

  onTabChange(e) {
    const page = e.currentTarget.dataset.page
    const label = e.currentTarget.dataset.label
    
    if (page === this.data.activeMenu) return

    this.setData({
      activeMenu: page,
      currentMenu: { page, label }
    })

    wx.setNavigationBarTitle({
      title: label
    })

    if (page === 'patent-list') {
      if (this.data.resultList.length === 0) {
        this.setData({ pageNum: 1 })
        this.loadPatents(true)
      }
    } else if (page === 'dashboard') {
      this.loadDashboardData()
    } else if (page === 'admin-management') {
      this.loadAdminUsers()
    } else if (page === 'role-management') {
      this.loadRoles()
    } else if (page === 'message-management') {
      this.loadMessages()
    }
  },

  onKeywordInput(e) {
    this.setData({ keyword: e.detail.value })
  },

  onKeywordConfirm() {
    this.setData({ pageNum: 1, resultList: [] })
    this.loadPatents(true)
  },

  onFilterChange(e) {
    const type = e.currentTarget.dataset.type
    const val = e.currentTarget.dataset.value
    
    const resetVal = val === this.data[type] ? '' : val
    const setData = {}
    setData[type] = resetVal
    setData.pageNum = 1
    setData.resultList = []
    this.setData(setData)
    this.loadPatents(true)
  },

  onSearch() {
    this.setData({ pageNum: 1, resultList: [] })
    this.loadPatents(true)
  },

  loadPatents(reset = false) {
    const token = wx.getStorageSync('token') || ''
    const { keyword, patentType, legalStatus, validity, pageNum, pageSize, resultList } = this.data

    if (this.data.loadingMore && !reset) {
      return
    }

    this.setData({ loadingMore: true })

    let url = `${API_BASE}/patents?pageNum=${pageNum}&pageSize=${pageSize}`
    if (keyword) url += `&keyword=${encodeURIComponent(keyword)}`
    if (patentType) url += `&patentType=${encodeURIComponent(patentType)}`
    if (legalStatus) url += `&legalStatus=${encodeURIComponent(legalStatus)}`
    if (validity) url += `&validity=${encodeURIComponent(validity)}`

    console.log('专利列表请求:', url)

    wx.request({
      url: url,
      method: 'GET',
      header: {
        'content-type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      success: (res) => {
        const result = res.data || {}
        console.log('专利列表响应:', result)
        
        if (checkTokenError(result)) {
          return
        }
        
        if (result.code >= 2000 && result.code < 3000) {
          const pageData = result.data || {}
          
          let items = []
          if (Array.isArray(pageData.data)) {
            items = pageData.data
          } else if (Array.isArray(pageData.records)) {
            items = pageData.records
          } else if (Array.isArray(result.data)) {
            items = result.data
          }
          
          const processedItems = items.map(item => {
            if (item.techField) {
              const fields = item.techField.split(/[,，、;；\s]+/).filter(f => f.trim())
              if (fields.length > 1) {
                return { ...item, displayTechField: fields[0] + ' 等' }
              } else {
                return { ...item, displayTechField: item.techField }
              }
            }
            return { ...item, displayTechField: '' }
          })
          
          const newList = reset ? processedItems : [...resultList, ...processedItems]
          
          this.setData({
            resultList: newList,
            total: pageData.total || 0,
            pages: pageData.pages || 1,
            loadingMore: false
          })
        } else {
          wx.showToast({ title: result.message || '加载失败', icon: 'none' })
          this.setData({ loadingMore: false })
        }
      },
      fail: () => {
        wx.showToast({ title: '网络请求失败', icon: 'none' })
        this.setData({ loadingMore: false })
      }
    })
  },

  onReachBottom() {
    if (this.data.activeMenu !== 'patent-list') return
    if (this.data.loadingMore) return
    
    const { pageNum, pages, total, pageSize, resultList } = this.data
    const hasMore = pages > 1 ? (pageNum < pages) : (resultList.length < total)
    
    if (hasMore) {
      this.setData({ pageNum: pageNum + 1 })
      this.loadPatents(false)
    }
  },

  onPullDownRefresh() {
    if (this.data.activeMenu === 'patent-list') {
      this.setData({ pageNum: 1, resultList: [] })
      this.loadPatents(true)
    }
    setTimeout(() => { wx.stopPullDownRefresh() }, 800)
  },

  goToDetail(e) {
    const id = e.currentTarget.dataset.id
    wx.navigateTo({
      url: `/pages/patent-detail/patent-detail?id=${id}`
    })
  },

  onInput(e) {
    this.setData({ inputText: e.detail.value })
  },

  sendMessage() {
    const text = this.data.inputText.trim()
    if (!text || this.data.loading) return

    const newMsg = { role: 'user', content: text }
    const msgList = [...this.data.msgList, newMsg]
    
    this.setData({ 
      msgList: msgList, 
      inputText: '', 
      loading: true 
    })

    setTimeout(() => {
      const aiResp = {
        role: 'assistant',
        content: '正在处理您的请求，请稍候...'
      }
      this.setData({ 
        msgList: [...this.data.msgList, aiResp],
        loading: false 
      })
    }, 1000)
  },

  loadDashboardData() {
    const token = wx.getStorageSync('token') || ''
    
    this.loadTodayActiveUsers()
    this.loadTodayNewPatents()
    this.loadHotTechFields()
    this.loadHotPatents()
    this.loadUserGrowth()
  },

  loadTodayActiveUsers() {
    const token = wx.getStorageSync('token') || ''
    wx.request({
      url: `${API_BASE}/statistics/todayActiveUsers`,
      method: 'GET',
      header: {
        'content-type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      success: (res) => {
        if (res.data.code === 2000) {
          this.setData({
            'dashboardData.todayActiveUsers': res.data.data?.count || 0
          })
        }
      }
    })
  },

  loadTodayNewPatents() {
    const token = wx.getStorageSync('token') || ''
    wx.request({
      url: `${API_BASE}/statistics/totalPatents`,
      method: 'GET',
      header: {
        'content-type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      success: (res) => {
        if (res.data.code === 2000) {
          this.setData({
            'dashboardData.todayNewPatents': res.data.data?.count || 0
          })
        }
      }
    })
  },

  loadHotTechFields() {
    const token = wx.getStorageSync('token') || ''
    wx.request({
      url: `${API_BASE}/statistics/hotSearchKeywords`,
      method: 'GET',
      header: {
        'content-type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      success: (res) => {
        if (res.data.code === 2000) {
          const data = res.data.data || []
          const hotTechFields = data.map((item, index) => ({
            rank: index + 1,
            field: item.keyword,
            count: item.searchCount
          }))
          this.setData({
            'dashboardData.hotTechFields': hotTechFields
          })
        }
      }
    })
  },

  loadHotPatents() {
    const token = wx.getStorageSync('token') || ''
    wx.request({
      url: `${API_BASE}/statistics/hotPatents`,
      method: 'GET',
      header: {
        'content-type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      success: (res) => {
        if (res.data.code === 2000) {
          const data = res.data.data || []
          const hotPatents = data.map((item, index) => ({
            rank: index + 1,
            title: item.title,
            views: item.viewCount
          }))
          this.setData({
            'dashboardData.hotPatents': hotPatents
          })
        }
      }
    })
  },

  loadUserGrowth() {
    const token = wx.getStorageSync('token') || ''
    wx.request({
      url: `${API_BASE}/statistics/userHistory`,
      method: 'GET',
      header: {
        'content-type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      success: (res) => {
        if (res.data.code === 2000) {
          this.setData({
            'dashboardData.userGrowthData': res.data.data || []
          })
          this.initUserGrowthChart()
        }
      }
    })
  },

  initUserGrowthChart() {
    const query = wx.createSelectorQuery()
    query.select('#userGrowthChart').boundingClientRect((rect) => {
      if (!rect) return

      const width = rect.width
      const height = rect.height
      const ctx = wx.createCanvasContext('userGrowthChart')

      const data = this.data.dashboardData.userGrowthData || []
      let labels = ['1月', '2月', '3月', '4月', '5月', '6月']
      let values = [1200, 1900, 3000, 4500, 6000, 8000]

      if (data.length > 0) {
        labels = data.map(d => d.date.substring(5))
        values = data.map(d => d.count)
      }

      const padding = 40
      const chartWidth = width - padding * 2
      const chartHeight = height - padding * 2
      const maxValue = Math.max(...values) * 1.2

      ctx.setStrokeStyle('#e4e7ed')
      ctx.setLineWidth(1)
      for (let i = 0; i <= 5; i++) {
        const y = padding + (chartHeight / 5) * i
        ctx.beginPath()
        ctx.moveTo(padding, y)
        ctx.lineTo(width - padding, y)
        ctx.stroke()
      }

      ctx.setStrokeStyle('#409eff')
      ctx.setLineWidth(2)
      ctx.beginPath()
      for (let i = 0; i < values.length; i++) {
        const x = padding + (chartWidth / (values.length - 1)) * i
        const y = padding + chartHeight - (values[i] / maxValue) * chartHeight
        if (i === 0) {
          ctx.moveTo(x, y)
        } else {
          ctx.lineTo(x, y)
        }
      }
      ctx.stroke()

      ctx.setFillStyle('#409eff')
      for (let i = 0; i < values.length; i++) {
        const x = padding + (chartWidth / (values.length - 1)) * i
        const y = padding + chartHeight - (values[i] / maxValue) * chartHeight
        ctx.beginPath()
        ctx.arc(x, y, 4, 0, 2 * Math.PI)
        ctx.fill()
      }

      ctx.setFillStyle('#666')
      ctx.setFontSize(10)
      ctx.setTextAlign('center')
      for (let i = 0; i < labels.length; i++) {
        const x = padding + (chartWidth / (values.length - 1)) * i
        ctx.fillText(labels[i], x, height - 10)
      }

      ctx.draw()
    }).exec()
  },

  loadAdminUsers() {
    const token = wx.getStorageSync('token') || ''
    const { adminPageNum, adminPageSize, adminSelectedUserType } = this.data
    
    this.setData({ adminLoading: true })
    
    let url = `${API_BASE}/users?pageNum=${adminPageNum}&pageSize=${adminPageSize}`
    if (adminSelectedUserType === 'normal') {
      url = `${API_BASE}/users?userType=0&pageNum=${adminPageNum}&pageSize=${adminPageSize}`
    } else if (adminSelectedUserType === 'enterprise') {
      url = `${API_BASE}/enterpriseUsers?pageNum=${adminPageNum}&pageSize=${adminPageSize}`
    } else if (adminSelectedUserType === 'research') {
      url = `${API_BASE}/researchTeamUsers?pageNum=${adminPageNum}&pageSize=${adminPageSize}`
    } else if (adminSelectedUserType === 'admin') {
      url = `${API_BASE}/adminUsers?pageNum=${adminPageNum}&pageSize=${adminPageSize}`
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
          // 正确处理空列表情况：只有当 list 存在且是数组时才使用
          let list = []
          if (Array.isArray(data.list)) {
            list = data.list
          } else if (Array.isArray(data)) {
            list = data
          }
          // 如果 list 不是数组或不存在，保持为空数组
          
          list = this.processAdminUserList(list, adminSelectedUserType)
          
          this.setData({
            adminUserList: list,
            adminTotal: data.total || 0,
            adminPages: data.pages || 1,
            adminLoading: false
          })
        } else {
          this.setData({ adminLoading: false })
        }
      },
      fail: () => {
        this.setData({ adminLoading: false })
      }
    })
  },

  loadAdminPatents() {
    const token = wx.getStorageSync('token') || ''
    const { adminPageNum, adminPageSize, patentSearchKeyword } = this.data
    
    let url = `${API_BASE}/patents?pageNum=${adminPageNum}&pageSize=${adminPageSize}`
    if (patentSearchKeyword) {
      url += `&keyword=${encodeURIComponent(patentSearchKeyword)}`
    }
    
    this.setData({ adminLoading: true })
    
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
          if (Array.isArray(data.list)) {
            list = data.list
          } else if (Array.isArray(data)) {
            list = data
          }
          
          this.setData({
            adminPatentList: list,
            adminTotal: data.total || 0,
            adminPages: data.pages || 1,
            adminLoading: false
          })
        } else {
          this.setData({ adminLoading: false })
        }
      },
      fail: () => {
        this.setData({ adminLoading: false })
      }
    })
  },

  onPatentSearchInput(e) {
    this.setData({
      patentSearchKeyword: e.detail.value || ''
    })
  },

  onPatentSearch() {
    this.setData({ adminPageNum: 1 })
    this.loadAdminPatents()
  },

  processAdminUserList(list, selectedUserType) {
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

  getUserTypeLabel(type) {
    const typeMap = {
      0: '普通用户',
      1: '企业用户',
      2: '科研团队用户',
      3: '管理员'
    }
    return typeMap[type] || '普通用户'
  },

  getUserStatusLabel(status) {
    return status === 1 ? '正常' : '禁用'
  },

  onAdminUserTypeChange(e) {
    const index = e.detail.value
    const option = this.data.adminUserTypeOptions[index]
    this.setData({ 
      adminUserTypeIndex: index,
      adminSelectedUserType: option.value,
      adminPageNum: 1 
    })
    this.loadAdminUsers()
  },

  openDetailModal(e) {
    const item = e.currentTarget.dataset.item
    const dataType = e.currentTarget.dataset.type || 'user'
    const modalTitleText = dataType === 'user' ? '用户详情' : '专利详情'
    
    let processedItem = item
    if (dataType === 'user') {
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
      modalTitleText: modalTitleText,
      adminDataType: dataType
    })
  },

  openCreateModal(e) {
    const dataType = e.currentTarget.dataset.type || 'user'
    const modalTitleText = dataType === 'user' ? '新增用户' : '新增专利'
    const defaultForm = dataType === 'user' ? {
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
      modalButtonText: '创建',
      adminDataType: dataType
    })
  },

  openEditModal(e) {
    const item = e.currentTarget.dataset.item
    const dataType = e.currentTarget.dataset.type || 'user'
    const modalTitleText = dataType === 'user' ? '编辑用户' : '编辑专利'
    const formData = dataType === 'user' ? {
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
      modalButtonText: '保存',
      adminDataType: dataType
    })
  },

  closeModal() {
    this.setData({ 
      showModal: false,
      modalType: '',
      currentItem: null,
      formData: {}
    })
  },

  preventBubble() {},

  onInputChange(e) {
    const field = e.currentTarget.dataset.field
    const value = e.detail.value
    this.setData({
      [`formData.${field}`]: value
    })
  },

  onStatusChange(e) {
    this.setData({
      'formData.status': e.detail.value ? 1 : 0
    })
  },

  submitForm() {
    const { modalType, currentItem, formData, adminDataType } = this.data
    const token = wx.getStorageSync('token') || ''
    
    let url = ''
    let method = ''
    let data = {}
    
    if (adminDataType === 'user') {
      if (modalType === 'create') {
        url = `${API_BASE}/users`
        method = 'POST'
        data = {
          username: formData.username,
          realName: formData.realName,
          contactPhone: formData.contactPhone,
          contactEmail: formData.contactEmail,
          status: formData.status
        }
      } else {
        url = `${API_BASE}/users/${currentItem.id}`
        method = 'PUT'
        data = {
          realName: formData.realName,
          contactPhone: formData.contactPhone,
          contactEmail: formData.contactEmail,
          status: formData.status
        }
      }
    } else {
      if (modalType === 'create') {
        url = `${API_BASE}/patents`
        method = 'POST'
        data = {
          title: formData.title,
          applicant: formData.applicant,
          inventors: formData.inventors,
          patentType: formData.patentType,
          legalStatus: formData.legalStatus,
          validity: formData.validity,
          abstractText: formData.abstractText
        }
      } else {
        url = `${API_BASE}/patents/${currentItem.patentId}`
        method = 'PUT'
        data = {
          title: formData.title,
          applicant: formData.applicant,
          inventors: formData.inventors,
          patentType: formData.patentType,
          legalStatus: formData.legalStatus,
          validity: formData.validity,
          abstractText: formData.abstractText
        }
      }
    }
    
    wx.request({
      url: url,
      method: method,
      header: {
        'content-type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      data: data,
      success: (res) => {
        if (res.data.code >= 2000 && res.data.code < 3000) {
          wx.showToast({
            title: modalType === 'create' ? '创建成功' : '保存成功',
            icon: 'success'
          })
          this.closeModal()
          if (adminDataType === 'user') {
            this.loadAdminUsers()
          } else {
            this.loadAdminPatents()
          }
        } else {
          wx.showToast({
            title: res.data.message || '操作失败',
            icon: 'none'
          })
        }
      },
      fail: () => {
        wx.showToast({
          title: '网络错误',
          icon: 'none'
        })
      }
    })
  },

  deleteItem(e) {
    const item = e.currentTarget.dataset.item
    const dataType = e.currentTarget.dataset.type || 'user'
    const token = wx.getStorageSync('token') || ''
    
    wx.showModal({
      title: '确认删除',
      content: `确定要删除吗？`,
      success: (res) => {
        if (res.confirm) {
          let url = dataType === 'user' 
            ? `${API_BASE}/users/${item.id}` 
            : `${API_BASE}/patents/${item.patentId}`
            
          wx.request({
            url: url,
            method: 'DELETE',
            header: {
              'content-type': 'application/json',
              'Authorization': `Bearer ${token}`
            },
            success: (res) => {
              if (res.data.code >= 2000 && res.data.code < 3000) {
                wx.showToast({
                  title: '删除成功',
                  icon: 'success'
                })
                if (dataType === 'user') {
                  this.loadAdminUsers()
                } else {
                  this.loadAdminPatents()
                }
              } else {
                wx.showToast({
                  title: res.data.message || '删除失败',
                  icon: 'none'
                })
              }
            },
            fail: () => {
              wx.showToast({
                title: '网络错误',
                icon: 'none'
              })
            }
          })
        }
      }
    })
  },

  upgradeToAdmin(e) {
    const item = e.currentTarget.dataset.item
    const token = wx.getStorageSync('token') || ''
    
    wx.showModal({
      title: '升级管理员',
      content: `确定要将"${item.displayName}"升级为管理员吗？`,
      success: (res) => {
        if (res.confirm) {
          wx.request({
            url: `${API_BASE}/adminUsers`,
            method: 'POST',
            header: {
              'content-type': 'application/json',
              'Authorization': `Bearer ${token}`
            },
            data: {
              username: item.username,
              realName: item.realName || item.wechatNickname || ''
            },
            success: (res) => {
              if (res.data.code >= 2000 && res.data.code < 3000) {
                wx.showToast({
                  title: '升级成功',
                  icon: 'success'
                })
                this.loadAdminUsers()
              } else {
                wx.showToast({
                  title: res.data.message || '升级失败',
                  icon: 'none'
                })
              }
            },
            fail: () => {
              wx.showToast({
                title: '网络错误',
                icon: 'none'
              })
            }
          })
        }
      }
    })
  },

  onAdminPrevPage() {
    if (this.data.adminPageNum > 1) {
      this.setData({ adminPageNum: this.data.adminPageNum - 1 })
      this.loadAdminUsers()
      if (this.data.showPatentTab) {
        this.loadAdminPatents()
      }
    }
  },

  onAdminNextPage() {
    if (this.data.adminPageNum < this.data.adminPages) {
      this.setData({ adminPageNum: this.data.adminPageNum + 1 })
      this.loadAdminUsers()
      if (this.data.showPatentTab) {
        this.loadAdminPatents()
      }
    }
  },

  loadRoles() {
    const token = wx.getStorageSync('token') || ''
    
    this.setData({ roleLoading: true })
    
    wx.request({
      url: `${API_BASE}/roles`,
      method: 'GET',
      header: {
        'content-type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      success: (res) => {
        const result = res.data || {}
        if (result.code === 200 || (result.code >= 2000 && result.code < 3000)) {
          // 处理后端返回的字段映射
          const data = result.data || []
          const roleList = data.map(item => ({
            id: item.id,
            name: item.roleName || item.name || '',
            code: item.roleCode || item.code || '',
            description: item.description || '',
            status: item.status
          }))
          this.setData({
            roleList: roleList,
            roleLoading: false
          })
        } else {
          this.setData({ roleLoading: false })
        }
      },
      fail: () => {
        this.setData({ roleLoading: false })
      }
    })
  },

  // 获取角色详情（包含权限）
  loadRoleDetail(roleId, callback) {
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
        if (result.code === 200 || (result.code >= 2000 && result.code < 3000)) {
          if (callback) callback(result.data)
        }
      }
    })
  },

  // 打开角色编辑弹窗
  openRoleEditModal(e) {
    const item = e.currentTarget.dataset.item
    this.loadRoleDetail(item.id, (roleDetail) => {
      this.setData({
        showModal: true,
        modalType: 'edit',
        currentItem: roleDetail,
        modalTitleText: '编辑角色',
        modalButtonText: '保存',
        adminDataType: 'role'
      })
    })
  },

  // 打开角色权限详情弹窗
  openRolePermissionModal(e) {
    const item = e.currentTarget.dataset.item
    this.loadRoleDetail(item.id, (roleDetail) => {
      const permissions = roleDetail.permission || {}
      const permissionLabels = {
        permissionEnterpriseView: '企业查看',
        permissionEnterpriseEdit: '企业编辑',
        permissionEnterpriseDelete: '企业删除',
        permissionResearchView: '科研查看',
        permissionResearchEdit: '科研编辑',
        permissionResearchDelete: '科研删除',
        permissionPatentAudit: '专利审核',
        permissionPatentReview: '专利查看',
        permissionNoticePublish: '通知发布',
        permissionStatisticsView: '统计查看',
        permissionSystemConfig: '系统配置',
        permissionIntentionAudit: '意向审核',
        permissionLogView: '日志查看'
      }
      
      const permissionList = Object.keys(permissionLabels).map(key => ({
        key: key,
        label: permissionLabels[key],
        value: permissions[key] === 1
      }))
      
      this.setData({
        showModal: true,
        modalType: 'permission',
        currentItem: roleDetail,
        rolePermissionList: permissionList,
        modalTitleText: `${roleDetail.roleName || roleDetail.name} - 权限详情`,
        modalButtonText: '',
        adminDataType: 'role'
      })
    })
  },

  // 删除角色
  deleteRole(e) {
    const item = e.currentTarget.dataset.item
    const token = wx.getStorageSync('token') || ''
    
    wx.showModal({
      title: '确认删除',
      content: `确定要删除角色"${item.name}"吗？`,
      success: (res) => {
        if (res.confirm) {
          wx.request({
            url: `${API_BASE}/roles/${item.id}`,
            method: 'DELETE',
            header: {
              'content-type': 'application/json',
              'Authorization': `Bearer ${token}`
            },
            success: (res) => {
              if (res.data.code === 200 || (res.data.code >= 2000 && res.data.code < 3000)) {
                wx.showToast({ title: '删除成功', icon: 'success' })
                this.loadRoles()
              } else {
                wx.showToast({ title: res.data.message || '删除失败', icon: 'none' })
              }
            },
            fail: () => {
              wx.showToast({ title: '网络错误', icon: 'none' })
            }
          })
        }
      }
    })
  },

  loadMessages() {
    const token = wx.getStorageSync('token') || ''
    
    this.setData({ messageLoading: true })
    
    wx.request({
      url: `${API_BASE}/intentions?status=pending`,
      method: 'GET',
      header: {
        'content-type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      success: (res) => {
        if (res.data.code >= 2000 && res.data.code < 3000) {
          this.setData({
            intentionList: res.data.data || [],
            messageLoading: false
          })
        } else {
          this.setData({ messageLoading: false })
        }
      },
      fail: () => {
        this.setData({ messageLoading: false })
      }
    })
    
    wx.request({
      url: `${API_BASE}/notices`,
      method: 'GET',
      header: {
        'content-type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      success: (res) => {
        if (res.data.code >= 2000 && res.data.code < 3000) {
          this.setData({
            noticeList: res.data.data || []
          })
        }
      }
    })
    
    wx.request({
      url: `${API_BASE}/logs`,
      method: 'GET',
      header: {
        'content-type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      success: (res) => {
        if (res.data.code >= 2000 && res.data.code < 3000) {
          this.setData({
            logList: res.data.data || []
          })
        }
      }
    })
  },

  onMessageTabChange(e) {
    const key = e.currentTarget.dataset.key
    this.setData({ messageActiveTab: key })
  }
})