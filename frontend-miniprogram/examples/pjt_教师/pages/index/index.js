Page({
  data: {
    activeTab: 'patents',
    selectedMessage: null,
    selectedPatent: null,
    confirmedMessages: [],
    rejectedMessages: [],
    // 消息数据
    messagesData: [
      {
        id: 1,
        sender: '李明',
        title: '请求指导毕业论文',
        content: '张教授您好，我是计算机专业大四学生李明，想请您指导我的毕业论文，研究方向是深度学习在图像识别中的应用。希望能得到您的指点。',
        time: '2024-01-15 10:30'
      },
      {
        id: 2,
        sender: '华为技术有限公司',
        title: '邀请合作研发项目',
        content: '尊敬的张教授，我们公司在人工智能领域有深入的研究，希望能与您合作开展关于计算机视觉的研发项目，期待您的回复。',
        time: '2024-01-14 16:45'
      },
      {
        id: 3,
        sender: '王教授',
        title: '学术交流邀请',
        content: '张教授，我们学院下周将举办一场关于人工智能的学术研讨会，诚挚邀请您参加并作主题报告，期待您的到来。',
        time: '2024-01-13 09:20'
      },
      {
        id: 4,
        sender: '教务处',
        title: '参加教学研讨会',
        content: '张教授，学校将于本月25日召开教学研讨会，讨论新学期的课程安排和教学改革，请您准时参加。',
        time: '2024-01-12 14:00'
      }
    ],
    // 专利数据
    patentsData: [
      {
        id: 1,
        title: '基于深度学习的图像识别方法',
        views: 1258,
        comments: [
          { id: 1, user: '张三', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=zhangsan', content: '这个专利很有创新性，希望能进一步交流', time: '2024-01-10 14:30' },
          { id: 2, user: '李四', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=lisi', content: '技术方案很完善，已收藏', time: '2024-01-08 09:15' },
          { id: 3, user: '王五', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=wangwu', content: '请问可以授权使用吗？', time: '2024-01-05 16:45' }
        ]
      },
      {
        id: 2,
        title: '一种新型的计算机网络安全防护系统',
        views: 892,
        comments: [
          { id: 1, user: '赵六', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=zhaoliu', content: '安全性设计很到位', time: '2024-01-12 11:20' },
          { id: 2, user: '钱七', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=qianqi', content: '希望能应用到实际项目中', time: '2024-01-09 15:30' }
        ]
      },
      {
        id: 3,
        title: '智能教学辅助系统',
        views: 2156,
        comments: [
          { id: 1, user: '孙八', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=sunba', content: '教育领域的创新应用', time: '2024-01-14 10:00' },
          { id: 2, user: '周九', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=zhoujiu', content: '已在教学中试用，效果很好', time: '2024-01-11 14:20' },
          { id: 3, user: '吴十', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=wushi', content: '期待更多功能更新', time: '2024-01-08 09:45' },
          { id: 4, user: '郑十一', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=zhengshiyi', content: '对提升教学效率很有帮助', time: '2024-01-06 16:30' }
        ]
      },
      {
        id: 4,
        title: '大数据分析平台',
        views: 1673,
        comments: [
          { id: 1, user: '陈十二', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=chenshier', content: '数据处理效率很高', time: '2024-01-13 13:15' },
          { id: 2, user: '刘十三', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=liushisan', content: '可视化界面设计得很直观', time: '2024-01-10 10:30' }
        ]
      }
    ],
    // 雷达图数据
    radarData: {
      labels: ['编程能力', '科研能力', '教学能力', '项目管理', '创新能力', '团队协作'],
      datasets: [
        {
          label: '能力水平',
          data: [90, 85, 95, 80, 88, 82],
          backgroundColor: 'rgba(75, 192, 192, 0.2)',
          borderColor: 'rgba(75, 192, 192, 1)',
          pointBackgroundColor: 'rgba(75, 192, 192, 1)',
          pointBorderColor: '#fff',
          pointHoverBackgroundColor: '#fff',
          pointHoverBorderColor: 'rgba(75, 192, 192, 1)'
        }
      ]
    }
  },

  onLoad() {
    // 页面加载时执行
    this.setData({
      confirmedMessages: [],
      rejectedMessages: []
    });
    this.drawRadarChart();
  },

  onReady() {
    // 页面初次渲染完成时执行
  },

  // 绘制雷达图
  drawRadarChart() {
    const ctx = wx.createCanvasContext('radarChart');
    const data = this.data.radarData;
    const labels = data.labels;
    const dataset = data.datasets[0];
    const dataPoints = dataset.data;
    const centerX = 170;
    const centerY = 100;
    const radius = 75;
    const angleStep = (2 * Math.PI) / labels.length;

    // 清除画布
    ctx.clearRect(0, 0, 300, 300);

    // 绘制网格
    ctx.setStrokeStyle('#e0e0e0');
    ctx.setLineWidth(1);
    for (let i = 1; i <= 5; i++) {
      const r = (radius / 5) * i;
      ctx.beginPath();
      for (let j = 0; j < labels.length; j++) {
        const angle = j * angleStep - Math.PI / 2;
        const x = centerX + Math.cos(angle) * r;
        const y = centerY + Math.sin(angle) * r;
        if (j === 0) {
          ctx.moveTo(x, y);
        } else {
          ctx.lineTo(x, y);
        }
      }
      ctx.closePath();
      ctx.stroke();
    }

    // 绘制轴线
    for (let i = 0; i < labels.length; i++) {
      const angle = i * angleStep - Math.PI / 2;
      const x = centerX + Math.cos(angle) * radius;
      const y = centerY + Math.sin(angle) * radius;
      ctx.beginPath();
      ctx.moveTo(centerX, centerY);
      ctx.lineTo(x, y);
      ctx.stroke();

      // 绘制标签
      const labelX = centerX + Math.cos(angle) * (radius + 15);
      const labelY = centerY + Math.sin(angle) * (radius + 15);
      ctx.setFontSize(12);
      ctx.setFillStyle('#333');
      ctx.setTextAlign('center');
      ctx.setTextBaseline('middle');
      ctx.fillText(labels[i], labelX, labelY);
    }

    // 绘制数据区域
    ctx.setFillStyle(dataset.backgroundColor);
    ctx.setStrokeStyle(dataset.borderColor);
    ctx.setLineWidth(2);
    ctx.beginPath();
    for (let i = 0; i < labels.length; i++) {
      const angle = i * angleStep - Math.PI / 2;
      const value = dataPoints[i] / 100;
      const x = centerX + Math.cos(angle) * (radius * value);
      const y = centerY + Math.sin(angle) * (radius * value);
      if (i === 0) {
        ctx.moveTo(x, y);
      } else {
        ctx.lineTo(x, y);
      }
    }
    ctx.closePath();
    ctx.fill();
    ctx.stroke();

    // 绘制数据点
    ctx.setFillStyle(dataset.pointBackgroundColor);
    ctx.setStrokeStyle(dataset.pointBorderColor);
    ctx.setLineWidth(2);
    for (let i = 0; i < labels.length; i++) {
      const angle = i * angleStep - Math.PI / 2;
      const value = dataPoints[i] / 100;
      const x = centerX + Math.cos(angle) * (radius * value);
      const y = centerY + Math.sin(angle) * (radius * value);
      ctx.beginPath();
      ctx.arc(x, y, 4, 0, 2 * Math.PI);
      ctx.fill();
      ctx.stroke();
    }

    ctx.draw();
  },

  // 切换选项卡
  switchTab(e) {
    const tab = e.currentTarget.dataset.tab;
    this.setData({
      activeTab: tab
    });
  },

  // 处理消息点击
  handleMessageClick(e) {
    const message = e.currentTarget.dataset.message;
    console.log('Clicked message:', message);
    console.log('Current confirmed:', this.data.confirmedMessages);
    console.log('Current rejected:', this.data.rejectedMessages);
    this.setData({
      selectedMessage: message,
      selectedPatent: null
    });
  },

  // 处理专利点击
  handlePatentClick(e) {
    const patent = e.currentTarget.dataset.patent;
    this.setData({
      selectedPatent: patent,
      selectedMessage: null
    });
  },

  // 关闭模态框
  closeModal() {
    this.setData({
      selectedMessage: null,
      selectedPatent: null
    });
  },

  // 阻止冒泡
  stopPropagation() {
    // 阻止事件冒泡
  },

  // 确认消息
  confirmMessage() {
    if (this.data.selectedMessage) {
      const messageId = this.data.selectedMessage.id;
      const confirmedMessages = this.data.confirmedMessages.slice();
      const rejectedMessages = this.data.rejectedMessages.filter(id => id !== messageId);
      if (confirmedMessages.indexOf(messageId) === -1) {
        confirmedMessages.push(messageId);
        this.setData({
          confirmedMessages: confirmedMessages,
          rejectedMessages: rejectedMessages
        });
      }
      this.closeModal();
    }
  },

  // 拒绝消息
  rejectMessage() {
    if (this.data.selectedMessage) {
      const messageId = this.data.selectedMessage.id;
      const rejectedMessages = this.data.rejectedMessages.slice();
      const confirmedMessages = this.data.confirmedMessages.filter(id => id !== messageId);
      if (rejectedMessages.indexOf(messageId) === -1) {
        rejectedMessages.push(messageId);
        this.setData({
          rejectedMessages: rejectedMessages,
          confirmedMessages: confirmedMessages
        });
      }
      this.closeModal();
    }
  },

  // 判断消息是否已确认
  isConfirmed(messageId) {
    return this.data.confirmedMessages.indexOf(messageId) !== -1;
  },

  // 判断消息是否已拒绝
  isRejected(messageId) {
    return this.data.rejectedMessages.indexOf(messageId) !== -1;
  }
})