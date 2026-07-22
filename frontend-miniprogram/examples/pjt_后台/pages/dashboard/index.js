Page({
  data: {
    todayActiveUsers: 128,
    todayNewPatents: 35,
    todayMatchConversations: 256,
    patentMatchRate: 85.2,
    intentionConversionRate: 32.7,
    hotTechFields: [
      { rank: 1, field: '人工智能', count: 1250 },
      { rank: 2, field: '区块链', count: 980 },
      { rank: 3, field: '物联网', count: 850 },
      { rank: 4, field: '大数据', count: 720 },
      { rank: 5, field: '云计算', count: 680 },
      { rank: 6, field: '生物医药', count: 550 },
      { rank: 7, field: '新能源', count: 480 },
      { rank: 8, field: '智能制造', count: 420 },
      { rank: 9, field: '5G技术', count: 380 },
      { rank: 10, field: '量子计算', count: 320 }
    ],
    hotPatents: [
      { rank: 1, title: '一种基于AI的专利检索方法', views: 1560 },
      { rank: 2, title: '区块链智能合约安全验证系统', views: 1280 },
      { rank: 3, title: '物联网设备数据加密传输方法', views: 1120 },
      { rank: 4, title: '大数据分析预测模型', views: 980 },
      { rank: 5, title: '云计算资源调度优化算法', views: 850 },
      { rank: 6, title: '生物医药分子结构预测方法', views: 720 },
      { rank: 7, title: '新能源汽车电池管理系统', views: 680 },
      { rank: 8, title: '智能制造生产线控制系统', views: 550 },
      { rank: 9, title: '5G网络信号增强方法', views: 480 },
      { rank: 10, title: '量子计算错误校正技术', views: 420 }
    ]
  },
  onReady() {
    // 使用onReady确保DOM已渲染
    this.initUserGrowthChart()
  },
  initUserGrowthChart() {
    const query = wx.createSelectorQuery()
    query.select('#userGrowthChart').boundingClientRect((rect) => {
      if (!rect) return
      
      const width = rect.width
      const height = rect.height
      const ctx = wx.createCanvasContext('userGrowthChart')
      
      // 模拟绘制折线图
      const labels = ['1月', '2月', '3月', '4月', '5月', '6月']
      const values = [1200, 1900, 3000, 4500, 6000, 8000]
      
      // 边距
      const padding = 40
      const chartWidth = width - padding * 2
      const chartHeight = height - padding * 2
      
      // 绘制网格
      ctx.setStrokeStyle('#e4e7ed')
      ctx.setLineWidth(1)
      for (let i = 0; i <= 5; i++) {
        const y = padding + (chartHeight / 5) * i
        ctx.beginPath()
        ctx.moveTo(padding, y)
        ctx.lineTo(width - padding, y)
        ctx.stroke()
      }
      
      // 绘制折线
      ctx.setStrokeStyle('#409eff')
      ctx.setLineWidth(2)
      ctx.beginPath()
      for (let i = 0; i < values.length; i++) {
        const x = padding + (chartWidth / (values.length - 1)) * i
        const y = padding + chartHeight - (values[i] / 8000) * chartHeight
        if (i === 0) {
          ctx.moveTo(x, y)
        } else {
          ctx.lineTo(x, y)
        }
      }
      ctx.stroke()
      
      // 绘制数据点
      ctx.setFillStyle('#409eff')
      for (let i = 0; i < values.length; i++) {
        const x = padding + (chartWidth / (values.length - 1)) * i
        const y = padding + chartHeight - (values[i] / 8000) * chartHeight
        ctx.beginPath()
        ctx.arc(x, y, 4, 0, 2 * Math.PI)
        ctx.fill()
      }
      
      // 绘制标签
      ctx.setFillStyle('#666')
      ctx.setFontSize(10)
      ctx.setTextAlign('center')
      for (let i = 0; i < labels.length; i++) {
        const x = padding + (chartWidth / (values.length - 1)) * i
        ctx.fillText(labels[i], x, height - 10)
      }
      
      ctx.draw()
    }).exec()
  }
})