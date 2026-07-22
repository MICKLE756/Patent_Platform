const { apiBase: API_BASE } = require('../../config/api.js')

Page({
  data: {
    keyword: '',
    patentType: '',
    legalStatus: '',
    validity: '',
    
    resultList: [],
    pageNum: 1,
    pageSize: 10,
    total: 0,
    pages: 0,
    loading: false,
    loadingMore: false
  },

  onLoad() {
    this.loadPatents(true)
  },

  onKeywordInput(e) {
    this.setData({ keyword: e.detail.value })
  },

  onKeywordConfirm() {
    this.setData({ pageNum: 1, resultList: [], loadingMore: false })
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
    setData.loadingMore = false
    this.setData(setData)
    this.loadPatents(true)
  },

  onSearch() {
    this.setData({ pageNum: 1, resultList: [], loadingMore: false })
    this.loadPatents(true)
  },

  loadPatents(reset = false) {
    const token = wx.getStorageSync('token') || ''
    const { keyword, patentType, legalStatus, validity, pageNum, pageSize, resultList } = this.data

    if (this.data.loadingMore && !reset) return

    this.setData({ loading: reset, loadingMore: !reset })

    let url = `${API_BASE}/patents?pageNum=${pageNum}&pageSize=${pageSize}`
    if (keyword) url += `&keyword=${encodeURIComponent(keyword)}`
    if (patentType) url += `&patentType=${encodeURIComponent(patentType)}`
    if (legalStatus) url += `&legalStatus=${encodeURIComponent(legalStatus)}`
    if (validity) url += `&validity=${encodeURIComponent(validity)}`

    console.log('=== 专利列表请求 ===')
    console.log('URL:', url)

    wx.request({
      url: url,
      method: 'GET',
      header: {
        'content-type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      success: (res) => {
        console.log('状态码:', res.statusCode)
        
        const result = res.data || {}
        
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
          
          const newList = reset ? items : [...resultList, ...items]
          
          this.setData({
            resultList: newList,
            total: pageData.total || 0,
            pages: pageData.pages || 1,
            loading: false,
            loadingMore: false
          })
        } else {
          wx.showToast({ title: result.message || '加载失败', icon: 'none' })
          this.setData({ loading: false, loadingMore: false })
        }
      },
      fail: (err) => {
        console.log('请求失败:', err)
        wx.showToast({ title: '网络请求失败', icon: 'none' })
        this.setData({ loading: false, loadingMore: false })
      }
    })
  },

  onReachBottom() {
    if (this.data.pageNum < this.data.pages && !this.data.loading && !this.data.loadingMore) {
      this.setData({ pageNum: this.data.pageNum + 1 })
      this.loadPatents(false)
    }
  },

  onPullDownRefresh() {
    this.setData({ pageNum: 1, resultList: [] })
    this.loadPatents(true)
    setTimeout(() => { wx.stopPullDownRefresh() }, 800)
  },

  goToDetail(e) {
    const id = e.currentTarget.dataset.id
    wx.navigateTo({
      url: `/pages/patent-detail/patent-detail?id=${id}`
    })
  }
})
