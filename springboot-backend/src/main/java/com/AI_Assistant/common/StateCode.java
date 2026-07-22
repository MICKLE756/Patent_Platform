package com.AI_Assistant.common;

/**
 * 状态码常量类
 * 用于统一管理API返回的状态码，使开发者在使用时能够清晰知道每个状态码的含义
 */
public class StateCode {

    // ==================== 成功状态码 2xxx ====================

    /**
     * 请求成功 (OK)
     * 适用于：GET、PUT、PATCH、DELETE等请求成功的情况
     * 返回示例：{ "code": 2000, "message": "success" }
     */
    public static final int OK = 2000;

    /**
     * 资源创建成功 (CREATED)
     * 适用于：POST请求成功创建了新资源
     * 返回示例：{ "code": 2001, "message": "资源创建成功" }
     */
    public static final int CREATED = 2001;

    /**
     * 资源更新成功 (UPDATED)
     * 适用于：PUT或PATCH请求成功更新了资源
     * 返回示例：{ "code": 2002, "message": "资源更新成功" }
     */
    public static final int UPDATED = 2002;

    /**
     * 资源删除成功 (DELETED)
     * 适用于：DELETE请求成功删除了资源
     * 返回示例：{ "code": 2003, "message": "资源删除成功" }
     */
    public static final int DELETED = 2003;

    // ==================== 客户端错误状态码 4xxx ====================

    /**
     * 请求参数错误 (BAD_REQUEST)
     * 适用于：请求参数格式错误、缺少必需参数、参数校验失败
     * 示例场景：手机号格式不对、邮箱为空、年龄超出范围
     */
    public static final int BAD_REQUEST = 4000;

    /**
     * 未授权访问 (UNAUTHORIZED)
     * 适用于：用户未登录、token无效、token过期
     * 示例场景：访问需要登录的接口、token解析失败、token已过期
     */
    public static final int UNAUTHORIZED = 4001;

    /**
     * 禁止访问 (FORBIDDEN)
     * 适用于：用户已登录但没有权限访问该资源
     * 示例场景：普通用户访问管理员接口、跨用户访问他人数据
     */
    public static final int FORBIDDEN = 4002;

    /**
     * 资源不存在 (NOT_FOUND)
     * 适用于：请求的资源ID不存在、被删除或从未创建
     * 示例场景：查询一个不存在的用户ID、专利ID、消息ID
     */
    public static final int NOT_FOUND = 4003;

    /**
     * 请求方法不允许 (METHOD_NOT_ALLOWED)
     * 适用于：使用了该资源不支持的HTTP方法
     * 示例场景：某个接口只支持GET但收到了POST请求
     */
    public static final int METHOD_NOT_ALLOWED = 4004;

    /**
     * 资源冲突 (CONFLICT)
     * 适用于：请求的资源状态冲突、重复创建
     * 示例场景：用户名已存在、同一专利重复提交、状态机流程错误
     */
    public static final int CONFLICT = 4005;

    /**
     * 请求过于频繁 (TOO_MANY_REQUESTS)
     * 适用于：接口调用频率超出限制
     * 示例场景：接口限流、短信验证码发送频率限制、搜索频率限制
     */
    public static final int TOO_MANY_REQUESTS = 4006;

    // ==================== 服务端错误状态码 5xxx ====================

    /**
     * 服务器内部错误 (INTERNAL_SERVER_ERROR)
     * 适用于：代码逻辑错误、空指针异常、未捕获的异常
     * 示例场景：空指针异常、数组越界、业务逻辑错误
     */
    public static final int INTERNAL_SERVER_ERROR = 5000;

    /**
     * 服务不可用 (SERVICE_UNAVAILABLE)
     * 适用于：服务器维护、数据库连接失败、第三方服务不可用
     * 示例场景：Redis连接失败、MySQL不可用、短信服务不可用
     */
    public static final int SERVICE_UNAVAILABLE = 5001;

    /**
     * 网关超时 (GATEWAY_TIMEOUT)
     * 适用于：网关层向上游服务请求超时
     * 示例场景：调用外部API超时、数据库查询超时
     */
    public static final int GATEWAY_TIMEOUT = 5002;

    /**
     * 数据库操作失败 (DATABASE_ERROR)
     * 适用于：SQL执行错误、事务失败、连接池耗尽
     * 示例场景：SQL语法错误、主键冲突、事务回滚
     */
    public static final int DATABASE_ERROR = 5003;

    /**
     * 网络连接失败 (NETWORK_ERROR)
     * 适用于：网络中断、DNS解析失败、连接被拒绝
     * 示例场景：服务器无法访问、HTTP连接失败
     */
    public static final int NETWORK_ERROR = 5004;

    /**
     * 第三方服务调用失败 (THIRD_PARTY_SERVICE_ERROR)
     * 适用于：调用外部接口失败、第三方SDK错误
     * 示例场景：微信支付失败、阿里云OSS上传失败、地图API调用失败
     */
    public static final int THIRD_PARTY_SERVICE_ERROR = 5005;

    /**
     * 文件上传失败 (FILE_UPLOAD_ERROR)
     * 适用于：文件上传过程中出错
     * 示例场景：文件大小超限、文件类型不支持、上传到云存储失败
     */
    public static final int FILE_UPLOAD_ERROR = 5006;

    /**
     * 文件下载失败 (FILE_DOWNLOAD_ERROR)
     * 适用于：文件下载过程中出错
     * 示例场景：文件不存在、下载路径错误、存储服务异常
     */
    public static final int FILE_DOWNLOAD_ERROR = 5007;

    // ==================== 业务特定错误码 6xxx ====================

    /**
     * 令牌已过期 (TOKEN_EXPIRED)
     * 适用于：JWT token或session超过有效期
     * 示例场景：登录token过期、需要重新登录
     */
    public static final int TOKEN_EXPIRED = 6000;

    /**
     * 令牌无效 (TOKEN_INVALID)
     * 适用于：token格式错误、被篡改、已被吊销
     * 示例场景：token签名验证失败、token被拉黑
     */
    public static final int TOKEN_INVALID = 6001;

    /**
     * 未提供令牌 (TOKEN_NOT_PROVIDED)
     * 适用于：请求头中没有携带token
     * 示例场景：Authorization头缺失、token为空
     */
    public static final int TOKEN_NOT_PROVIDED = 6002;

    /**
     * 验证码错误 (VERIFICATION_CODE_ERROR)
     * 适用于：用户输入的验证码与发送的不匹配
     * 示例场景：短信验证码输入错误、邮箱验证码错误
     */
    public static final int VERIFICATION_CODE_ERROR = 6003;

    /**
     * 验证码已过期 (VERIFICATION_CODE_EXPIRED)
     * 适用于：验证码已超过有效期
     * 示例场景：短信验证码5分钟过期、邮箱验证码30分钟过期
     */
    public static final int VERIFICATION_CODE_EXPIRED = 6004;

    /**
     * 密码错误 (PASSWORD_ERROR)
     * 适用于：登录时密码输入错误
     * 示例场景：用户登录时密码输入错误
     */
    public static final int PASSWORD_ERROR = 6005;

    /**
     * 两次输入密码不一致 (PASSWORD_NOT_MATCH)
     * 适用于：注册或修改密码时两次密码输入不匹配
     * 示例场景：确认密码与新密码不一致
     */
    public static final int PASSWORD_NOT_MATCH = 6006;

    /**
     * 用户已被禁用 (USER_DISABLED)
     * 适用于：用户账号被禁用或封禁
     * 示例场景：违规用户被封禁、管理员禁用某账号
     */
    public static final int USER_DISABLED = 6007;

    /**
     * 用户未激活 (USER_NOT_ACTIVE)
     * 适用于：用户注册后未完成激活流程
     * 示例场景：邮箱未验证、短信未验证激活
     */
    public static final int USER_NOT_ACTIVE = 6008;

    /**
     * 权限不足 (PERMISSION_DENIED)
     * 适用于：用户没有权限执行某操作
     * 示例场景：非管理员操作管理员功能、查看他人私有数据
     */
    public static final int PERMISSION_DENIED = 6009;

    /**
     * 数据验证失败 (DATA_VALIDATION_ERROR)
     * 适用于：业务数据校验不通过
     * 示例场景：专利名称重复、企业信息不完整、格式不符合规范
     */
    public static final int DATA_VALIDATION_ERROR = 6010;

    /**
     * 资源被锁定 (RESOURCE_LOCKED)
     * 适用于：资源正在被其他操作占用
     * 示例场景：专利正在审核中、文件正在被编辑、订单正在处理
     */
    public static final int RESOURCE_LOCKED = 6011;

    /**
     * 操作过于频繁 (OPERATION_TOO_FREQUENT)
     * 适用于：用户操作频率超出业务限制
     * 示例场景：短时间内多次查询、频繁提交表单、快速切换页面
     */
    public static final int OPERATION_TOO_FREQUENT = 6012;
}
