Page({
  data: {
    users: [
      { id: 1, username: '张三', email: 'zhangsan@example.com', status: '启用', registerTime: '2026-04-01 10:00:00' },
      { id: 2, username: '李四', email: 'lisi@example.com', status: '启用', registerTime: '2026-04-02 11:30:00' },
      { id: 3, username: '王五', email: 'wangwu@example.com', status: '禁用', registerTime: '2026-04-03 14:20:00' },
      { id: 4, username: '赵六', email: 'zhaoliu@example.com', status: '启用', registerTime: '2026-04-04 09:15:00' },
      { id: 5, username: '孙七', email: 'sunqi@example.com', status: '启用', registerTime: '2026-04-05 16:45:00' }
    ],
    patents: [
      { id: 1, title: '一种基于AI的专利检索方法', field: '人工智能', status: '上架', submitTime: '2026-04-01 10:00:00' },
      { id: 2, title: '区块链智能合约安全验证系统', field: '区块链', status: '上架', submitTime: '2026-04-02 11:30:00' },
      { id: 3, title: '物联网设备数据加密传输方法', field: '物联网', status: '下架', submitTime: '2026-04-03 14:20:00' },
      { id: 4, title: '大数据分析预测模型', field: '大数据', status: '上架', submitTime: '2026-04-04 09:15:00' },
      { id: 5, title: '云计算资源调度优化算法', field: '云计算', status: '上架', submitTime: '2026-04-05 16:45:00' }
    ],
    userSearchQuery: '',
    patentSearchQuery: '',
    markDialogVisible: false,
    patentTitleDialogVisible: false,
    currentPatent: null,
    patentTitleDialogContent: '',
    markTypes: [
      { value: '信息有误', label: '信息有误' },
      { value: '违规内容', label: '违规内容' },
      { value: '重复提交', label: '重复提交' },
      { value: '其他', label: '其他' }
    ],
    markForm: {
      type: '信息有误',
      reason: ''
    }
  },
  bindUserSearch(e) {
    this.setData({ userSearchQuery: e.detail.value })
  },
  searchUsers() {
    // 模拟搜索功能
    wx.showToast({ title: '搜索功能暂未实现', icon: 'none' })
  },
  bindPatentSearch(e) {
    this.setData({ patentSearchQuery: e.detail.value })
  },
  searchPatents() {
    // 模拟搜索功能
    wx.showToast({ title: '搜索功能暂未实现', icon: 'none' })
  },
  toggleUserStatus(e) {
    const id = e.currentTarget.dataset.id
    const users = this.data.users.map(user => {
      if (user.id === id) {
        return { ...user, status: user.status === '启用' ? '禁用' : '启用' }
      }
      return user
    })
    this.setData({ users })
    wx.showToast({ title: '状态已更新', icon: 'success' })
  },
  resetPassword(e) {
    const id = e.currentTarget.dataset.id
    wx.showModal({
      title: '重置密码',
      content: '确定要重置该用户的密码吗？',
      success: (res) => {
        if (res.confirm) {
          wx.showToast({ title: '密码已重置为123456', icon: 'success' })
        }
      }
    })
  },
  togglePatentStatus(e) {
    const id = e.currentTarget.dataset.id
    const patents = this.data.patents.map(patent => {
      if (patent.id === id) {
        return { ...patent, status: patent.status === '上架' ? '下架' : '上架' }
      }
      return patent
    })
    this.setData({ patents })
    wx.showToast({ title: '状态已更新', icon: 'success' })
  },
  markPatent(e) {
    const id = e.currentTarget.dataset.id
    const currentPatent = this.data.patents.find(patent => patent.id === id)
    this.setData({
      currentPatent,
      markForm: { type: '信息有误', reason: '' },
      markDialogVisible: true
    })
  },
  bindMarkType(e) {
    this.setData({ 'markForm.type': e.detail.value })
  },
  bindMarkReason(e) {
    this.setData({ 'markForm.reason': e.detail.value })
  },
  cancelMark() {
    this.setData({ markDialogVisible: false })
  },
  confirmMark() {
    if (!this.data.markForm.reason) {
      wx.showToast({ title: '请输入标记理由', icon: 'none' })
      return
    }
    wx.showToast({ title: '标记成功', icon: 'success' })
    this.setData({ markDialogVisible: false })
  },
  showFullPatentTitle(e) {
    const id = e.currentTarget.dataset.id
    const patent = this.data.patents.find(patent => patent.id === id)
    if (patent) {
      this.setData({
        patentTitleDialogContent: patent.title,
        patentTitleDialogVisible: true
      })
    }
  },
  closePatentTitleDialog() {
    this.setData({ patentTitleDialogVisible: false })
  }
})