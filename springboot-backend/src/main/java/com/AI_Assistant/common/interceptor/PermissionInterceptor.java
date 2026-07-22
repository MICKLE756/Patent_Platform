package com.AI_Assistant.common.interceptor;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.AI_Assistant.common.exception.ForbiddenException;
import com.AI_Assistant.common.exception.PermissionDeniedException;
import com.AI_Assistant.common.exception.UnauthorizedException;
import com.AI_Assistant.common.util.UserContext;
import com.AI_Assistant.userCenter.admin.entity.Admin;
import com.AI_Assistant.userCenter.admin.entity.RolePermission;
import com.AI_Assistant.userCenter.admin.mapper.AdminMapper;
import com.AI_Assistant.userCenter.admin.service.RolePermissionService;
import com.AI_Assistant.userCenter.user.entity.User;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 权限验证拦截器
 * 
 * 功能说明：
 * 1. 拦截所有 /api/v1/admin/**、/api/v1/adminUsers/**、/api/v1/roles/** 路径的请求
 * 2. 验证当前用户是否为管理员（user_type = 3）
 * 3. 根据请求路径验证是否具有相应权限
 * 4. 无权限时返回 403 Forbidden
 * 
 * 拦截器执行顺序（在 WebMvcConfig 中配置）：
 * 1. TokenInterceptor - 解析Token
 * 2. LoginRequiredInterceptor - 验证登录
 * 3. PermissionInterceptor - 验证权限
 * 
 * 权限配置说明（对应 RolePermission 实体字段）：
 * - permissionEnterpriseView: 企业查看权限
 * - permissionEnterpriseEdit: 企业编辑权限
 * - permissionEnterpriseDelete: 企业删除权限
 * - permissionResearchView: 科研团队查看权限
 * - permissionResearchEdit: 科研团队编辑权限
 * - permissionResearchDelete: 科研团队删除权限
 * - permissionPatentAudit: 专利审核权限
 * - permissionPatentReview: 专利查看权限
 * - permissionNoticePublish: 通知发布权限
 * - permissionStatisticsView: 统计查看权限
 * - permissionSystemConfig: 系统配置权限
 * - permissionIntentionAudit: 意向留言审核权限
 * - permissionLogView: 日志查看权限
 */
@Component
public class PermissionInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(PermissionInterceptor.class);

    @Autowired
    private AdminMapper adminMapper;

    @Autowired
    private RolePermissionService rolePermissionService;

    /**
     * 路径-权限映射表
     * key: 请求路径模式
     * value: 所需权限标识（使用枚举常量，避免字符串硬编码）
     */
    private static final Map<String, PermissionType> PATH_PERMISSION_MAP = new HashMap<>();

    /**
     * 权限类型枚举（对应 RolePermission 的字段）
     * 使用枚举避免反射，提高性能和类型安全
     */
    private enum PermissionType {
        ENTERPRISE_VIEW("permissionEnterpriseView", RolePermission::getPermissionEnterpriseView),
        ENTERPRISE_EDIT("permissionEnterpriseEdit", RolePermission::getPermissionEnterpriseEdit),
        ENTERPRISE_DELETE("permissionEnterpriseDelete", RolePermission::getPermissionEnterpriseDelete),
        RESEARCH_VIEW("permissionResearchView", RolePermission::getPermissionResearchView),
        RESEARCH_EDIT("permissionResearchEdit", RolePermission::getPermissionResearchEdit),
        RESEARCH_DELETE("permissionResearchDelete", RolePermission::getPermissionResearchDelete),
        PATENT_AUDIT("permissionPatentAudit", RolePermission::getPermissionPatentAudit),
        PATENT_REVIEW("permissionPatentReview", RolePermission::getPermissionPatentReview),
        NOTICE_PUBLISH("permissionNoticePublish", RolePermission::getPermissionNoticePublish),
        STATISTICS_VIEW("permissionStatisticsView", RolePermission::getPermissionStatisticsView),
        SYSTEM_CONFIG("permissionSystemConfig", RolePermission::getPermissionSystemConfig),
        INTENTION_AUDIT("permissionIntentionAudit", RolePermission::getPermissionIntentionAudit),
        LOG_VIEW("permissionLogView", RolePermission::getPermissionLogView);

        private final String fieldName;
        private final Function<RolePermission, Integer> getter;

        PermissionType(String fieldName, Function<RolePermission, Integer> getter) {
            this.fieldName = fieldName;
            this.getter = getter;
        }

        public boolean hasPermission(RolePermission permission) {
            Integer value = getter.apply(permission);
            return value != null && value == 1;
        }

        public String getFieldName() {
            return fieldName;
        }
    }

    static {
        // ===== 管理员账号管理 =====
        PATH_PERMISSION_MAP.put("/api/v1/adminUsers", PermissionType.SYSTEM_CONFIG);
        PATH_PERMISSION_MAP.put("/api/v1/adminUsers/*", PermissionType.SYSTEM_CONFIG);
        
        // ===== 角色管理 =====
        PATH_PERMISSION_MAP.put("/api/v1/roles", PermissionType.SYSTEM_CONFIG);
        PATH_PERMISSION_MAP.put("/api/v1/roles/*", PermissionType.SYSTEM_CONFIG);
        PATH_PERMISSION_MAP.put("/api/v1/roles/*/permissions", PermissionType.SYSTEM_CONFIG);
        
        // ===== 审核操作 =====
        PATH_PERMISSION_MAP.put("/api/v1/admin/approvals/pending", PermissionType.INTENTION_AUDIT);
        PATH_PERMISSION_MAP.put("/api/v1/admin/approvals/*", PermissionType.INTENTION_AUDIT);
        PATH_PERMISSION_MAP.put("/api/v1/admin/approvals/*/approve", PermissionType.INTENTION_AUDIT);
        PATH_PERMISSION_MAP.put("/api/v1/admin/approvals/*/reject", PermissionType.INTENTION_AUDIT);
        PATH_PERMISSION_MAP.put("/api/v1/admin/approvals/history", PermissionType.INTENTION_AUDIT);
        
        // ===== 统计查看 =====
        PATH_PERMISSION_MAP.put("/api/v1/admin/statistics", PermissionType.STATISTICS_VIEW);
        PATH_PERMISSION_MAP.put("/api/v1/admin/statistics/**", PermissionType.STATISTICS_VIEW);
        PATH_PERMISSION_MAP.put("/api/v1/statistics/userTypes", PermissionType.STATISTICS_VIEW);
        PATH_PERMISSION_MAP.put("/api/v1/statistics/enterprises", PermissionType.STATISTICS_VIEW);
        PATH_PERMISSION_MAP.put("/api/v1/statistics/researchTeams", PermissionType.STATISTICS_VIEW);
        PATH_PERMISSION_MAP.put("/api/v1/statistics/pendingPatents", PermissionType.STATISTICS_VIEW);
        
        // ===== 企业用户管理 =====
        PATH_PERMISSION_MAP.put("/api/v1/enterpriseUsers", PermissionType.ENTERPRISE_VIEW);
        PATH_PERMISSION_MAP.put("/api/v1/enterpriseUsers/*", PermissionType.ENTERPRISE_VIEW);
        PATH_PERMISSION_MAP.put("/api/v1/enterpriseUsers/*/patents", PermissionType.ENTERPRISE_VIEW);
        PATH_PERMISSION_MAP.put("/api/v1/enterpriseUsers/*/updatePassword", PermissionType.ENTERPRISE_EDIT);
        PATH_PERMISSION_MAP.put("/api/v1/enterpriseUsers/*/updateProfile", PermissionType.ENTERPRISE_EDIT);
        PATH_PERMISSION_MAP.put("/api/v1/enterpriseUsers/*/delete", PermissionType.ENTERPRISE_DELETE);
        
        // ===== 科研团队用户管理 =====
        PATH_PERMISSION_MAP.put("/api/v1/researchTeamUsers", PermissionType.RESEARCH_VIEW);
        PATH_PERMISSION_MAP.put("/api/v1/researchTeamUsers/*", PermissionType.RESEARCH_VIEW);
        PATH_PERMISSION_MAP.put("/api/v1/researchTeamUsers/*/patents", PermissionType.RESEARCH_VIEW);
        PATH_PERMISSION_MAP.put("/api/v1/researchTeamUsers/*/updatePassword", PermissionType.RESEARCH_EDIT);
        PATH_PERMISSION_MAP.put("/api/v1/researchTeamUsers/*/updateProfile", PermissionType.RESEARCH_EDIT);
        PATH_PERMISSION_MAP.put("/api/v1/researchTeamUsers/*/delete", PermissionType.RESEARCH_DELETE);
        
        // ===== 专利管理 =====
        PATH_PERMISSION_MAP.put("/api/v1/patents/search", PermissionType.PATENT_REVIEW);
        
        // ===== 系统配置 =====
        PATH_PERMISSION_MAP.put("/api/v1/admin/system", PermissionType.SYSTEM_CONFIG);
        PATH_PERMISSION_MAP.put("/api/v1/admin/system/**", PermissionType.SYSTEM_CONFIG);
        
        // ===== 通知发布 =====
        PATH_PERMISSION_MAP.put("/api/v1/admin/notices", PermissionType.NOTICE_PUBLISH);
        PATH_PERMISSION_MAP.put("/api/v1/admin/notices/*", PermissionType.NOTICE_PUBLISH);
        PATH_PERMISSION_MAP.put("/api/v1/admin/notices/*/publish", PermissionType.NOTICE_PUBLISH);
        PATH_PERMISSION_MAP.put("/api/v1/admin/notices/*/withdraw", PermissionType.NOTICE_PUBLISH);
        
        // ===== 日志查看 =====
        PATH_PERMISSION_MAP.put("/api/v1/admin/logs", PermissionType.LOG_VIEW);
        PATH_PERMISSION_MAP.put("/api/v1/admin/logs/**", PermissionType.LOG_VIEW);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String requestUri = request.getRequestURI();
        logger.debug("权限拦截器处理请求: {}", requestUri);

        // 1. 获取当前用户（已由 TokenInterceptor 放入 ThreadLocal）
        User currentUser = UserContext.getUser();
        if (currentUser == null) {
            logger.warn("访问管理员接口但未登录，URI: {}", requestUri);
            throw new UnauthorizedException("请先登录");
        }

        // 2. 验证是否为管理员用户（user_type = 3）
        Integer userType = currentUser.getUserType();
        if (userType == null || userType != 3) {
            logger.warn("非管理员用户尝试访问管理员接口，用户ID: {}, 用户类型: {}, URI: {}", 
                    currentUser.getId(), userType, requestUri);
            throw new ForbiddenException("非管理员用户无法访问此接口");
        }

        // 3. 获取管理员角色权限配置
        RolePermission permission = getAdminPermission(currentUser.getId());
        if (permission == null) {
            logger.warn("管理员用户未配置权限，用户ID: {}, URI: {}", currentUser.getId(), requestUri);
            throw new PermissionDeniedException("您的账号未配置权限");
        }

        // 4. 根据请求路径验证权限
        PermissionType requiredPermission = matchPathPermission(requestUri);
        if (requiredPermission != null) {
            boolean hasPermission = requiredPermission.hasPermission(permission);
            if (!hasPermission) {
                logger.warn("管理员用户权限不足，用户ID: {}, 所需权限: {}, URI: {}", 
                        currentUser.getId(), requiredPermission.getFieldName(), requestUri);
                throw new PermissionDeniedException("权限不足");
            }
            logger.debug("权限验证通过，用户ID: {}, 权限: {}", currentUser.getId(), requiredPermission.getFieldName());
        }

        return true;
    }

    /**
     * 超级管理员角色ID常量
     */
    private static final String SUPER_ADMIN_ROLE_ID = "role_super_admin";

    /**
     * 获取管理员的角色权限配置
     * 
     * @param userId 用户ID
     * @return RolePermission 权限配置对象，如果获取失败返回 null
     */
    private RolePermission getAdminPermission(String userId) {
        try {
            // 查询管理员信息获取角色ID
            LambdaQueryWrapper<Admin> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Admin::getUserId, userId);
            Admin admin = adminMapper.selectOne(wrapper);

            if (admin == null) {
                logger.warn("管理员信息不存在，userId: {}", userId);
                return null;
            }

            String roleId = admin.getRoleId();
            if (roleId == null || roleId.isEmpty()) {
                logger.warn("管理员未分配角色，userId: {}", userId);
                return null;
            }

            // 如果是超级管理员，直接返回一个拥有所有权限的对象
            if (SUPER_ADMIN_ROLE_ID.equals(roleId)) {
                logger.debug("超级管理员用户，跳过权限验证，userId: {}", userId);
                RolePermission superAdminPermission = new RolePermission();
                superAdminPermission.setPermissionEnterpriseView(1);
                superAdminPermission.setPermissionEnterpriseEdit(1);
                superAdminPermission.setPermissionEnterpriseDelete(1);
                superAdminPermission.setPermissionResearchView(1);
                superAdminPermission.setPermissionResearchEdit(1);
                superAdminPermission.setPermissionResearchDelete(1);
                superAdminPermission.setPermissionPatentAudit(1);
                superAdminPermission.setPermissionPatentReview(1);
                superAdminPermission.setPermissionNoticePublish(1);
                superAdminPermission.setPermissionStatisticsView(1);
                superAdminPermission.setPermissionSystemConfig(1);
                superAdminPermission.setPermissionIntentionAudit(1);
                superAdminPermission.setPermissionLogView(1);
                return superAdminPermission;
            }

            // 根据角色ID获取权限配置
            return rolePermissionService.getPermissionByRoleId(roleId);
        } catch (Exception e) {
            logger.error("获取管理员权限失败，userId: {}", userId, e);
            return null;
        }
    }

    /**
     * 匹配请求路径对应的权限
     * 匹配优先级：精确匹配 > 前缀匹配（**）> 通配符匹配（*）
     * 
     * @param requestUri 请求路径
     * @return 所需的权限类型，如果不需要特殊权限返回 null
     */
    private PermissionType matchPathPermission(String requestUri) {
        // 1. 精确匹配
        if (PATH_PERMISSION_MAP.containsKey(requestUri)) {
            return PATH_PERMISSION_MAP.get(requestUri);
        }

        // 2. 通配符匹配（按优先级顺序）
        for (Map.Entry<String, PermissionType> entry : PATH_PERMISSION_MAP.entrySet()) {
            String pattern = entry.getKey();
            
            if (pattern.endsWith("/**")) {
                // 前缀匹配：/api/v1/admin/statistics/** 匹配 /api/v1/admin/statistics/users
                String prefix = pattern.substring(0, pattern.length() - 2);
                if (requestUri.startsWith(prefix)) {
                    return entry.getValue();
                }
            } else if (pattern.contains("/*/")) {
                // 路径段通配：/api/v1/adminUsers/* 匹配 /api/v1/adminUsers/123
                String regexPattern = pattern.replace("/*/", "/[^/]+/");
                if (requestUri.matches(regexPattern)) {
                    return entry.getValue();
                }
            } else if (pattern.endsWith("/*")) {
                // 末尾通配：/api/v1/adminUsers/* 匹配 /api/v1/adminUsers/123
                String prefix = pattern.substring(0, pattern.length() - 1);
                if (requestUri.startsWith(prefix) && requestUri.indexOf('/', prefix.length()) == -1) {
                    return entry.getValue();
                }
            }
        }

        return null;
    }
}