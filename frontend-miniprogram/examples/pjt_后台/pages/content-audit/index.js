Page({
  data: {
    pendingItems: [],
    auditHistory: [],
    rejectDialogVisible: false,
    editDialogVisible: false,
    contentDialogVisible: false,
    reasonDialogVisible: false,
    currentItem: null,
    contentDialogContent: '',
    reasonDialogContent: '',
    rejectForm: {
      reason: ''
    },
    editForm: {
      content: ''
    }
  },
  onLoad() {
    this.initData()
  },
  initData() {
    // 初始化待审核列表数据，添加预览字段
    const pendingItems = [
      { id: 1, type: '需求描述', content: '需要一种基于AI的专利检索方法，能够快速准确地匹配相关专利', user: '张三', submitTime: '2026-04-09 10:00:00' },
      { id: 2, type: '留言', content: '专利助手的匹配功能很实用，希望能增加更多技术领域的覆盖', user: '李四', submitTime: '2026-04-09 11:30:00' },
      { id: 3, type: '需求描述', content: '寻找区块链相关的专利，特别是智能合约安全方面的', user: '王五', submitTime: '2026-04-09 14:20:00' }
    ].map(item => {
      // 取前3个字加"..."
      const contentPreview = item.content.substring(0, 3) + '...'
      return { ...item, contentPreview }
    })
    
    // 初始化审核日记数据，添加预览字段
    const auditHistory = [
      { id: 1, itemId: 4, type: '需求描述', action: '通过', operator: '管理员', operationTime: '2026-04-08 16:00:00', reason: '' },
      { id: 2, itemId: 5, type: '留言', action: '驳回', operator: '管理员', operationTime: '2026-04-08 15:30:00', reason: '内容包含敏感信息，不符合平台规范' },
      { id: 3, itemId: 6, type: '需求描述', action: '修改', operator: '管理员', operationTime: '2026-04-08 14:15:00', reason: '优化描述语言，使其更加清晰准确' }
    ].map(item => {
      // 理由为空时显示"-"
      const reasonPreview = item.reason ? item.reason.substring(0, 3) + '...' : '-'
      return { ...item, reasonPreview }
    })
    
    this.setData({
      pendingItems,
      auditHistory
    })
  },
  approveItem(e) {
    const id = e.currentTarget.dataset.id
    // 模拟审核通过操作
    wx.showToast({ title: '审核通过', icon: 'success' })
    // 从待审核列表中移除
    const pendingItems = this.data.pendingItems.filter(item => item.id !== id)
    this.setData({ pendingItems })
    // 添加到审核日记
    const currentItem = this.data.pendingItems.find(item => item.id === id)
    if (currentItem) {
      const newHistoryItem = {
        id: this.data.auditHistory.length + 1,
        itemId: currentItem.id,
        type: currentItem.type,
        action: '通过',
        operator: '管理员',
        operationTime: new Date().toLocaleString(),
        reason: '',
        reasonPreview: '-'
      }
      const auditHistory = [newHistoryItem, ...this.data.auditHistory]
      this.setData({ auditHistory })
    }
  },
  rejectItem(e) {
    const id = e.currentTarget.dataset.id
    const currentItem = this.data.pendingItems.find(item => item.id === id)
    this.setData({
      currentItem,
      rejectForm: { reason: '' },
      rejectDialogVisible: true
    })
  },
  bindRejectReason(e) {
    this.setData({
      'rejectForm.reason': e.detail.value
    })
  },
  cancelReject() {
    this.setData({ rejectDialogVisible: false })
  },
  confirmReject() {
    if (!this.data.rejectForm.reason) {
      wx.showToast({ title: '请输入驳回理由', icon: 'none' })
      return
    }
    // 模拟驳回操作
    wx.showToast({ title: '驳回成功', icon: 'success' })
    // 从待审核列表中移除
    const pendingItems = this.data.pendingItems.filter(item => item.id !== this.data.currentItem.id)
    this.setData({ pendingItems })
    // 添加到审核日记
    const reason = this.data.rejectForm.reason
    const newHistoryItem = {
      id: this.data.auditHistory.length + 1,
      itemId: this.data.currentItem.id,
      type: this.data.currentItem.type,
      action: '驳回',
      operator: '管理员',
      operationTime: new Date().toLocaleString(),
      reason: reason,
      reasonPreview: reason.substring(0, 3) + '...'
    }
    const auditHistory = [newHistoryItem, ...this.data.auditHistory]
    this.setData({ 
      auditHistory,
      rejectDialogVisible: false 
    })
  },
  editItem(e) {
    const id = e.currentTarget.dataset.id
    const currentItem = this.data.pendingItems.find(item => item.id === id)
    this.setData({
      currentItem,
      editForm: { content: currentItem.content },
      editDialogVisible: true
    })
  },
  bindEditContent(e) {
    this.setData({
      'editForm.content': e.detail.value
    })
  },
  cancelEdit() {
    this.setData({ editDialogVisible: false })
  },
  confirmEdit() {
    if (!this.data.editForm.content) {
      wx.showToast({ title: '请输入内容', icon: 'none' })
      return
    }
    // 模拟修改操作
    wx.showToast({ title: '修改成功', icon: 'success' })
    // 更新待审核列表中的内容
    const newContent = this.data.editForm.content
    const pendingItems = this.data.pendingItems.map(item => {
      if (item.id === this.data.currentItem.id) {
        return { 
          ...item, 
          content: newContent,
          contentPreview: newContent.substring(0, 3) + '...'
        }
      }
      return item
    })
    this.setData({ pendingItems })
    // 添加到审核日记
    const newHistoryItem = {
      id: this.data.auditHistory.length + 1,
      itemId: this.data.currentItem.id,
      type: this.data.currentItem.type,
      action: '修改',
      operator: '管理员',
      operationTime: new Date().toLocaleString(),
      reason: '内容修改',
      reasonPreview: '内容...'
    }
    const auditHistory = [newHistoryItem, ...this.data.auditHistory]
    this.setData({ 
      auditHistory,
      editDialogVisible: false 
    })
  },
  showFullContent(e) {
    const id = e.currentTarget.dataset.id
    const item = this.data.pendingItems.find(item => item.id === id)
    if (item) {
      this.setData({
        contentDialogContent: item.content,
        contentDialogVisible: true
      })
    }
  },
  closeContentDialog() {
    this.setData({ contentDialogVisible: false })
  },
  showFullReason(e) {
    const id = e.currentTarget.dataset.id
    const item = this.data.auditHistory.find(item => item.id === id)
    if (item) {
      this.setData({
        reasonDialogContent: item.reason,
        reasonDialogVisible: true
      })
    }
  },
  closeReasonDialog() {
    this.setData({ reasonDialogVisible: false })
  }
})