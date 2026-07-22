const mockPatents = [
  {
    id: 1,
    name: '基于深度学习的智能图像识别方法及系统',
    fitRate: 92,
    tags: ['人工智能', '图像识别', '深度学习'],
    reason: '该专利技术与您描述的图像识别需求高度匹配，采用最新的CNN网络结构，在工业检测场景准确率达98.5%'
  },
  {
    id: 2,
    name: '一种自然语言处理的文本语义分析方法',
    fitRate: 87,
    tags: ['NLP', '语义分析', '神经网络'],
    reason: '针对文本理解和问答系统领域有良好的技术积累，可快速适配智能客服等应用场景'
  },
  {
    id: 3,
    name: '基于联邦学习的数据隐私保护机器学习框架',
    fitRate: 78,
    tags: ['隐私计算', '联邦学习', '数据安全'],
    reason: '在数据不出本地的前提下实现多源数据联合建模，符合您提到的数据安全和隐私合规要求'
  }
]

Page({
  data: {
    msgList: [
      {
        role: 'assistant',
        content: '您好！我是专利智能助手，请问您有什么技术需求或问题？我会为您智能推荐相关专利。'
      }
    ],
    inputText: '',
    loading: false
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
        content: `根据您描述的「${text.length > 12 ? text.slice(0,12)+'...' : text}」需求，为您智能推荐以下专利：`,
        patents: mockPatents
      }
      this.setData({
        msgList: [...msgList, aiResp],
        loading: false
      })
    }, 1500)
  },

  goToDetail(e) {
    const id = e.currentTarget.dataset.id
    wx.navigateTo({
      url: `/pages/patent-detail/patent-detail?id=${id}`
    })
  }
})
