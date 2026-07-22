-- =============================================
-- AI_Patent_Assistant 数据库初始化脚本
-- 数据库类型: MySQL
-- =============================================

CREATE DATABASE IF NOT EXISTS AI_Patent_Assistant DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE AI_Patent_Assistant;

-- =============================================
-- 一、用户账户主表（统一用户管理）
-- =============================================

-- ----------------------------
-- 1.1 用户主表
-- 用户类型: 0-未认证, 1-企业用户, 2-科研团队用户, 3-管理员
-- ----------------------------
CREATE TABLE IF NOT EXISTS user (
    id              VARCHAR(64) PRIMARY KEY COMMENT '用户ID',
    username        VARCHAR(100) NOT NULL UNIQUE COMMENT '用户名',
    password        VARCHAR(255) NOT NULL COMMENT '密码（加密存储）',
    user_type       TINYINT DEFAULT 0 COMMENT '用户类型: 0-未认证, 1-企业用户, 2-科研团队用户, 3-管理员',
    wechat_openid   VARCHAR(64) UNIQUE COMMENT '微信OpenID',
    wechat_unionid  VARCHAR(64) COMMENT '微信UnionID',
    wechat_nickname VARCHAR(100) COMMENT '微信昵称',
    wechat_avatar   VARCHAR(255) COMMENT '微信头像URL',
    contact_phone   VARCHAR(20) COMMENT '联系电话',
    contact_email   VARCHAR(100) COMMENT '联系邮箱',
    notification_addr VARCHAR(500) COMMENT 'RabbitMQ通知地址',
    status          TINYINT DEFAULT 1 COMMENT '状态: 0-禁用, 1-正常, 2-待审核',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    last_login_time DATETIME COMMENT '最后登录时间',
    INDEX idx_username (username),
    INDEX idx_user_type (user_type),
    INDEX idx_wechat_openid (wechat_openid),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户主表';

-- =============================================
-- 二、账号扩展信息表（存储各类型特有属性）
-- =============================================

-- ----------------------------
-- 2.1 企业账号扩展表
-- ----------------------------
CREATE TABLE IF NOT EXISTS enterprise (
    id              VARCHAR(64) PRIMARY KEY COMMENT '企业扩展ID',
    user_id         VARCHAR(64) NOT NULL UNIQUE COMMENT '关联用户ID',
    company_name    VARCHAR(200) NOT NULL COMMENT '公司名称',
    contact_name    VARCHAR(50) COMMENT '联系人姓名',
    business_license VARCHAR(255) COMMENT '营业执照编号',
    company_intro   TEXT COMMENT '公司简介',
    company_address VARCHAR(500) COMMENT '公司地址',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user_id (user_id),
    CONSTRAINT fk_enterprise_user FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='企业账号扩展表';

-- ----------------------------
-- 2.2 科研团队账号扩展表
-- ----------------------------
CREATE TABLE IF NOT EXISTS research_team (
    id              VARCHAR(64) PRIMARY KEY COMMENT '科研团队扩展ID',
    user_id         VARCHAR(64) NOT NULL COMMENT '关联用户ID',
    team_name       VARCHAR(200) NOT NULL COMMENT '团队名称',
    contact_name    VARCHAR(50) COMMENT '联系人姓名',
    institution     VARCHAR(200) COMMENT '所属单位',
    team_code       INT NOT NULL DEFAULT 0 COMMENT '团队编号（同单位同名团队计数器，从0开始）',
    research_domain VARCHAR(200) COMMENT '研究方向/领域',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user_id (user_id),
    UNIQUE KEY uk_user_team (user_id, institution, team_name, team_code),
    CONSTRAINT fk_research_team_user FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='科研团队账号扩展表';

-- ----------------------------
-- 2.3 管理员账号扩展表
-- ----------------------------
CREATE TABLE IF NOT EXISTS admin (
    id              VARCHAR(64) PRIMARY KEY COMMENT '管理员扩展ID',
    user_id         VARCHAR(64) NOT NULL UNIQUE COMMENT '关联用户ID',
    real_name       VARCHAR(50) COMMENT '真实姓名',
    role_id         VARCHAR(64) COMMENT '角色ID',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user_id (user_id),
    INDEX idx_role_id (role_id),
    CONSTRAINT fk_admin_user FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员账号扩展表';

-- =============================================
-- 三、前端展示对象 - 列表可选项
-- =============================================

-- =============================================
-- 四、日志表
-- =============================================

-- ----------------------------
-- 4.1 管理员回绝日志表
-- ----------------------------
CREATE TABLE IF NOT EXISTS rejection_log (
    id                  VARCHAR(64) PRIMARY KEY COMMENT '回绝日志ID',
    intention_id        VARCHAR(64) NOT NULL COMMENT '关联意向留言ID',
    admin_id            VARCHAR(64) NOT NULL COMMENT '操作管理员ID',
    rejection_reason    TEXT COMMENT '回绝理由',
    rejection_time      DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '回绝时间',
    INDEX idx_intention_id (intention_id),
    INDEX idx_admin_id (admin_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员回绝日志表';

-- =============================================
-- 五、数据记录表
-- =============================================

-- ----------------------------
-- 5.1 专利浏览记录表（关联外部专利库）
-- ----------------------------
CREATE TABLE IF NOT EXISTS patent_view_record (
    id              VARCHAR(64) PRIMARY KEY COMMENT '记录ID',
    patent_id       VARCHAR(100) NOT NULL COMMENT '专利ID（外部专利库）',
    viewer_id       VARCHAR(64) COMMENT '浏览账号ID',
    viewer_type     VARCHAR(20) COMMENT '浏览账号类型',
    view_time       DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '浏览时间',
    stay_duration   INT DEFAULT 0 COMMENT '停留时长（秒）',
    INDEX idx_patent_id (patent_id),
    INDEX idx_viewer (viewer_id, viewer_type),
    INDEX idx_view_time (view_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专利浏览记录表';

-- ----------------------------
-- 5.2 专利统计聚合表
-- ----------------------------
CREATE TABLE IF NOT EXISTS patent_statistics (
    patent_id       VARCHAR(100) PRIMARY KEY COMMENT '专利ID',
    view_count      INT DEFAULT 0 COMMENT '浏览总数',
    search_count    INT DEFAULT 0 COMMENT '搜索展示次数',
    click_count     INT DEFAULT 0 COMMENT '点击次数',
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    -- [已修复 - 问题85] 添加view_count排序索引，优化热门专利查询
    INDEX idx_view_count (view_count DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专利统计聚合表';

-- ----------------------------
-- 5.3 账号统计表
-- ----------------------------
CREATE TABLE IF NOT EXISTS user_statistics (
    id              VARCHAR(64) PRIMARY KEY COMMENT '统计ID',
    stat_date       DATE NOT NULL COMMENT '统计日期',
    total_user_count INT DEFAULT 0 COMMENT '用户总数',
    enterprise_count    INT DEFAULT 0 COMMENT '企业总数',
    research_team_count INT DEFAULT 0 COMMENT '科研团队总数',
    admin_count     INT DEFAULT 0 COMMENT '管理员总数',
    new_user_count  INT DEFAULT 0 COMMENT '新增用户数',
    new_enterprise_count    INT DEFAULT 0 COMMENT '新增企业数',
    new_research_team_count  INT DEFAULT 0 COMMENT '新增科研团队数',
    active_user_count INT DEFAULT 0 COMMENT '活跃用户数',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_stat_date (stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户统计表';

-- =============================================
-- 六、消息通知表
-- =============================================

-- ----------------------------
-- 6.1 系统通知表
-- ----------------------------
CREATE TABLE IF NOT EXISTS system_notice (
    id              VARCHAR(64) PRIMARY KEY COMMENT '通知ID',
    publisher_id    VARCHAR(64) NOT NULL COMMENT '发布者ID',
    publisher_type  VARCHAR(20) NOT NULL COMMENT '发布者类型',
    notice_type     VARCHAR(50) NOT NULL COMMENT '通知类型: system/activity/announcement',
    title           VARCHAR(200) COMMENT '通知标题',
    content         TEXT NOT NULL COMMENT '通知内容',
    notice_time     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '通知时间',
    target_type     VARCHAR(20) DEFAULT 'all' COMMENT '目标类型: all-全体, enterprise-企业, research_team-科研团队, specific-指定',
    target_ids      TEXT COMMENT '指定目标ID列表(JSON)',
    status          TINYINT DEFAULT 1 COMMENT '状态: 0-草稿, 1-已发布, 2-已撤回',
    publish_time    DATETIME COMMENT '实际发布时间',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_publisher (publisher_id, publisher_type),
    INDEX idx_notice_time (notice_time),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统通知表';

-- ----------------------------
-- 6.2 意向留言表
-- ----------------------------
CREATE TABLE IF NOT EXISTS intention_message (
    id              VARCHAR(64) PRIMARY KEY COMMENT '意向留言ID',
    initiator_id    VARCHAR(64) NOT NULL COMMENT '发起者ID',
    initiator_type  VARCHAR(20) NOT NULL COMMENT '发起者类型: enterprise/research_team',
    receiver_id     VARCHAR(64) NOT NULL COMMENT '接收者ID',
    receiver_type   VARCHAR(20) NOT NULL COMMENT '接收者类型: enterprise/research_team',
    content         TEXT NOT NULL COMMENT '留言内容',
    company_address VARCHAR(500) COMMENT '公司地址',
    company_attr    VARCHAR(100) COMMENT '企业属性',
    admin_status    VARCHAR(20) DEFAULT 'pending' COMMENT '管理员审批状态: pending-待审核, approved-通过, rejected-拒绝',
    admin_id        VARCHAR(64) COMMENT '审批管理员ID',
    admin_audit_time DATETIME COMMENT '管理员审批时间',
    team_status     VARCHAR(20) DEFAULT 'pending' COMMENT '科研团队审批状态: pending-待审核, approved-通过, rejected-拒绝',
    team_audit_time DATETIME COMMENT '科研团队审批时间',
    send_time       DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    read_status     TINYINT DEFAULT 0 COMMENT '阅读状态: 0-未读, 1-已读',
    status          TINYINT DEFAULT 1 COMMENT '状态: 0-已撤回, 1-有效',
    INDEX idx_initiator (initiator_id, initiator_type),
    INDEX idx_receiver (receiver_id, receiver_type),
    INDEX idx_admin_status (admin_status),
    INDEX idx_team_status (team_status),
    INDEX idx_send_time (send_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='意向留言表';

-- =============================================
-- 七、权限管理表
-- =============================================

-- ----------------------------
-- 7.1 角色表
-- ----------------------------
CREATE TABLE IF NOT EXISTS role (
    id              VARCHAR(64) PRIMARY KEY COMMENT '角色ID',
    role_name       VARCHAR(50) NOT NULL COMMENT '角色名称',
    role_code       VARCHAR(50) NOT NULL COMMENT '角色代码',
    description     VARCHAR(200) COMMENT '角色描述',
    status          TINYINT DEFAULT 1 COMMENT '状态: 0-禁用, 1-启用',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- ----------------------------
-- 7.2 角色权限表
-- ----------------------------
CREATE TABLE IF NOT EXISTS role_permission (
    id              VARCHAR(64) PRIMARY KEY COMMENT '记录ID',
    role_id         VARCHAR(64) NOT NULL COMMENT '角色ID',
    permission_enterprise_view    TINYINT DEFAULT 0 COMMENT '企业查看权限',
    permission_enterprise_edit    TINYINT DEFAULT 0 COMMENT '企业编辑权限',
    permission_enterprise_delete  TINYINT DEFAULT 0 COMMENT '企业删除权限',
    permission_research_view     TINYINT DEFAULT 0 COMMENT '科研团队查看权限',
    permission_research_edit     TINYINT DEFAULT 0 COMMENT '科研团队编辑权限',
    permission_research_delete   TINYINT DEFAULT 0 COMMENT '科研团队删除权限',
    permission_patent_audit     TINYINT DEFAULT 0 COMMENT '专利审核权限',
    permission_patent_review    TINYINT DEFAULT 0 COMMENT '专利查看权限',
    permission_notice_publish   TINYINT DEFAULT 0 COMMENT '通知发布权限',
    permission_statistics_view   TINYINT DEFAULT 0 COMMENT '统计查看权限',
    permission_system_config    TINYINT DEFAULT 0 COMMENT '系统配置权限',
    permission_intention_audit  TINYINT DEFAULT 0 COMMENT '意向留言审核权限',
    permission_log_view         TINYINT DEFAULT 0 COMMENT '日志查看权限',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限表';

-- =============================================
-- 八、关联关系表
-- =============================================
/*
注：科研团队专利关联数据存储在此表中，通过 user_type='research_team' 区分
登录时查询科研团队用户绑定的专利：SELECT * FROM bound_patent WHERE user_id = ? AND user_type = 'research_team'
*/

-- ----------------------------
-- 8.1 已绑定专利扩展表（记录用户绑定的专利及统计数据）
-- ----------------------------
CREATE TABLE IF NOT EXISTS bound_patent (
    id              VARCHAR(64) PRIMARY KEY COMMENT '记录ID',
    patent_id       VARCHAR(100) NOT NULL COMMENT '专利ID（外部专利库/Milvus）',
    inventor        VARCHAR(100) NOT NULL COMMENT '发明人',
    patent_name     VARCHAR(500) NOT NULL COMMENT '专利名称',
    view_count      INT DEFAULT 0 COMMENT '浏览次数',
    search_count    INT DEFAULT 0 COMMENT '搜索次数',
    click_count     INT DEFAULT 0 COMMENT '点击次数',
    user_id         VARCHAR(64) COMMENT '绑定用户ID',
    user_type       VARCHAR(20) COMMENT '绑定用户类型: enterprise/research_team',
    bind_time       DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_patent_id (patent_id),
    INDEX idx_inventor (inventor),
    INDEX idx_user (user_id, user_type),
    UNIQUE KEY uk_patent_user (patent_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='已绑定专利扩展表';

-- ----------------------------
-- 8.2 专利统计数据表（统一存储统计数据，支持多人共享绑定）
-- ----------------------------
CREATE TABLE IF NOT EXISTS patent_statistics (
    patent_id       VARCHAR(100) PRIMARY KEY COMMENT '专利ID（外部专利库/Milvus）',
    view_count      INT DEFAULT 0 COMMENT '浏览次数',
    search_count    INT DEFAULT 0 COMMENT '搜索次数',
    click_count     INT DEFAULT 0 COMMENT '点击次数',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_patent_id (patent_id),
    -- [已修复 - 问题85] 添加view_count排序索引，优化热门专利查询
    INDEX idx_view_count (view_count DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专利统计数据表';

-- =============================================
-- 初始化数据
-- =============================================

-- 插入默认管理员角色
INSERT INTO role (id, role_name, role_code, description, status) VALUES
('role_super_admin', '超级管理员', 'SUPER_ADMIN', '拥有所有权限', 1),
('role_auditor', '审核员', 'AUDITOR', '负责专利和意向留言审核', 1),
('role_operator', '运营员', 'OPERATOR', '负责日常运营和通知发布', 1);

-- 插入超级管理员默认权限
INSERT INTO role_permission (id, role_id, permission_enterprise_view, permission_enterprise_edit, permission_enterprise_delete,
    permission_research_view, permission_research_edit, permission_research_delete,
    permission_patent_audit, permission_patent_review, permission_notice_publish,
    permission_statistics_view, permission_system_config, permission_intention_audit, permission_log_view) VALUES
('perm_super_admin', 'role_super_admin', 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1),
('perm_auditor', 'role_auditor', 1, 0, 0, 1, 0, 0, 1, 1, 0, 1, 0, 1, 0),
('perm_operator', 'role_operator', 1, 1, 0, 1, 1, 0, 0, 1, 1, 1, 0, 0, 1);

-- 插入默认管理员用户（密码为 admin123，已使用MD5加密）
INSERT INTO user (id, username, password, user_type, status) VALUES
('user_admin001', 'admin', 'e10adc3949ba59abbe56e057f20f883e', 3, 1);

-- 将默认管理员用户关联到超级管理员角色
INSERT INTO admin (id, user_id, real_name, role_id, create_time, update_time) VALUES
('admin001', 'user_admin001', '超级管理员', 'role_super_admin', NOW(), NOW());

-- =============================================
-- 初始化枚举数据（从专利mock数据中提取）
-- =============================================

-- =============================================
-- 八、搜索记录表
-- =============================================

-- ----------------------------
-- 8.1 搜索日志表
-- ----------------------------
CREATE TABLE IF NOT EXISTS search_log (
    id              VARCHAR(64) PRIMARY KEY COMMENT '记录ID',
    user_id         VARCHAR(64) COMMENT '搜索用户ID',
    maturity        VARCHAR(50) COMMENT '成熟度等级名称',
    keyword         VARCHAR(200) COMMENT '搜索关键词',
    search_time     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '搜索时间',
    INDEX idx_maturity_time (maturity, search_time),
    INDEX idx_user_id (user_id),
    INDEX idx_search_time (search_time),
    -- [已修复 - 问题86] 添加keyword和search_time复合索引，优化热门搜索词统计（GROUP BY）
    INDEX idx_keyword_time (keyword, search_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='搜索记录表';

-- =============================================
-- 九、专利主表
-- =============================================

-- ----------------------------
-- 9.1 专利信息表
-- ----------------------------
-- 字段定义完全遵循 专利详情字段.md
CREATE TABLE IF NOT EXISTS patent (
    -- 专利号/公开号
    patent_id              VARCHAR(50) PRIMARY KEY COMMENT '专利号/公开号',
    
    -- 专利标题
    title                  VARCHAR(500) NOT NULL COMMENT '专利标题',
    
    -- 申请人
    applicant              VARCHAR(500) COMMENT '申请人',
    
    -- 当前权利人
    current_owner          VARCHAR(500) COMMENT '当前权利人',
    
    -- 发明人
    inventors              TEXT COMMENT '发明人',
    
    -- 专利类型
    patent_type            VARCHAR(50) COMMENT '专利类型',
    
    -- 法律状态
    legal_status           VARCHAR(50) COMMENT '法律状态',
    
    -- 专利有效性
    validity               VARCHAR(20) COMMENT '专利有效性',
    
    -- 申请日
    application_date       VARCHAR(20) COMMENT '申请日',
    
    -- 公开日
    publication_date       VARCHAR(20) COMMENT '公开日',
    
    -- 授权公告日
    grant_date             VARCHAR(20) COMMENT '授权公告日',
    
    -- 预估到期日
    estimated_expiry_date  VARCHAR(20) COMMENT '预估到期日',
    
    -- 技术摘要
    abstract               TEXT COMMENT '技术摘要',
    
    -- 技术功效句
    technical_effect_sentences TEXT COMMENT '技术功效句',
    
    -- 技术领域（IPC中文分类+新兴产业分类+知识密集型分类+学科分类）
    tech_field             VARCHAR(1000) COMMENT '技术领域',
    
    -- 技术稳定性评分
    technical_stability    VARCHAR(10) COMMENT '技术稳定性',
    
    -- 技术先进性评分
    technical_advancement  VARCHAR(10) COMMENT '技术先进性',
    
    -- 外部链接（incoPat详情页）
    source_link            VARCHAR(500) COMMENT '外部链接'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专利信息表';

-- =============================================
-- 十、异步消息模块表
-- =============================================

-- ----------------------------
-- 10.1 用户消息表（存储用户收到的消息，支持离线消息）
-- ----------------------------
CREATE TABLE IF NOT EXISTS user_message (
    id              VARCHAR(64) PRIMARY KEY COMMENT '消息ID',
    user_id         VARCHAR(64) NOT NULL COMMENT '目标用户ID',
    message_type    VARCHAR(50) NOT NULL COMMENT '消息类型: intention/notice/audit/system',
    sub_type        VARCHAR(50) COMMENT '消息子类型: create/update/approve/reject/publish',
    title           VARCHAR(200) NOT NULL COMMENT '消息标题',
    content         TEXT COMMENT '消息内容(JSON格式)',
    sender_id       VARCHAR(64) COMMENT '发送者ID',
    sender_type     VARCHAR(20) COMMENT '发送者类型: enterprise/research_team/admin/system',
    related_id      VARCHAR(64) COMMENT '关联业务ID（意向留言ID/通知ID）',
    priority        TINYINT DEFAULT 2 COMMENT '优先级: 1-高, 2-中, 3-低',
    status          TINYINT DEFAULT 0 COMMENT '阅读状态: 0-未读, 1-已读',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_priority (priority),
    INDEX idx_create_time (create_time),
    INDEX idx_message_type (message_type),
    INDEX idx_user_status (user_id, status),
    INDEX idx_user_type (user_id, message_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户消息表';

-- ----------------------------
-- 10.2 消息投递状态表（跟踪RabbitMQ消息投递状态）
-- ----------------------------
CREATE TABLE IF NOT EXISTS message_delivery (
    id              VARCHAR(64) PRIMARY KEY COMMENT '记录ID',
    message_id      VARCHAR(64) NOT NULL COMMENT '消息ID',
    queue_name      VARCHAR(100) NOT NULL COMMENT '队列名称',
    exchange_name   VARCHAR(100) NOT NULL COMMENT '交换机名称',
    routing_key     VARCHAR(200) COMMENT '路由键',
    status          VARCHAR(20) DEFAULT 'pending' COMMENT '状态: pending-待投递, delivered-已投递, confirmed-已确认, failed-失败',
    retry_count     INT DEFAULT 0 COMMENT '重试次数',
    last_attempt_time DATETIME COMMENT '最后尝试时间',
    error_message   TEXT COMMENT '错误信息',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_message_id (message_id),
    INDEX idx_status (status),
    INDEX idx_queue_name (queue_name),
    -- [已修复 - 问题87] 添加复合索引，优化定时补偿任务查询待重试消息
    INDEX idx_status_time (status, create_time),
    INDEX idx_status_retry (status, retry_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息投递状态表';

-- ----------------------------
-- 10.3 用户WebSocket连接状态表（记录在线用户连接）
-- ----------------------------
CREATE TABLE IF NOT EXISTS websocket_connection (
    id              VARCHAR(64) PRIMARY KEY COMMENT '连接ID',
    user_id         VARCHAR(64) NOT NULL COMMENT '用户ID',
    session_id      VARCHAR(100) NOT NULL COMMENT 'WebSocket会话ID',
    connection_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '连接时间',
    last_heartbeat  DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '最后心跳时间',
    status          TINYINT DEFAULT 1 COMMENT '状态: 0-断开, 1-连接中',
    INDEX idx_user_id (user_id),
    INDEX idx_session_id (session_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='WebSocket连接状态表';

-- ----------------------------
-- 10.4 广播消息表（存储系统广播通知）
-- ----------------------------
CREATE TABLE IF NOT EXISTS broadcast_message (
    id              VARCHAR(64) PRIMARY KEY COMMENT '消息ID',
    title           VARCHAR(200) NOT NULL COMMENT '消息标题',
    content         TEXT COMMENT '消息内容(JSON格式)',
    sender_id       VARCHAR(64) COMMENT '发送者ID',
    sender_type     VARCHAR(20) COMMENT '发送者类型: admin/system',
    priority        TINYINT DEFAULT 2 COMMENT '优先级: 1-高, 2-中, 3-低',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_create_time (create_time),
    INDEX idx_priority (priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='广播消息表';