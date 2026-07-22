package com.AI_Assistant.userCenter.admin.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.AI_Assistant.common.Result;
import com.AI_Assistant.common.exception.BadRequestException;
import com.AI_Assistant.common.exception.UnauthorizedException;
import com.AI_Assistant.common.util.UserContext;
import com.AI_Assistant.userCenter.admin.entity.Role;
import com.AI_Assistant.userCenter.admin.entity.RolePermission;
import com.AI_Assistant.userCenter.admin.service.RolePermissionService;
import com.AI_Assistant.userCenter.user.entity.User;

/**
 * ============================================
 * 角色管理控制器
 * ============================================
 *
 * 【后端逻辑】
 * 本控制器负责角色的CRUD操作和权限配置
 * 所有管理员都能创建、修改、删除角色，但只能操作权限范围 <= 自己权限的角色
 * 超级管理员可以操作任意权限的角色
 *
 * 【前端调用逻辑】
 * 前端需要：
 * 1. 在请求头中携带有效的JWT Token（格式：Authorization: Bearer <token>）
 * 2. 当前登录用户必须具有管理员身份
 * 3. 创建/修改/删除角色时，只能操作当前用户权限范围内的角色
 *
 * 【权限说明】
 * - 查看角色（GET）：需要管理员权限
 * - 创建角色（POST）：需要管理员权限，且只能创建权限范围 <= 自己权限的角色
 * - 修改角色信息（PUT）：需要管理员权限
 * - 修改角色权限（PUT /permissions）：需要管理员权限，且只能修改权限范围 <= 自己权限的角色
 * - 删除角色（DELETE）：需要管理员权限，且只能删除权限范围 <= 自己权限的角色
 *
 * 【接口列表】
 * - GET    /api/v1/roles              获取所有角色列表
 * - GET    /api/v1/roles/{roleId}     获取角色详情（包含权限配置）
 * - POST   /api/v1/roles              创建角色
 * - PUT    /api/v1/roles/{roleId}     更新角色信息
 * - PUT    /api/v1/roles/{roleId}/permissions  更新角色权限
 * - DELETE /api/v1/roles/{roleId}    删除角色
 */
@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {

    @Autowired
    private RolePermissionService rolePermissionService;

    /**
     * ============================================
     * 获取所有角色列表（不含权限详情）
     * ============================================
     *
     * 【后端逻辑】
     * 1. 调用 rolePermissionService.getAllRoles() 获取所有角色
     * 2. 返回角色基本信息列表（不包含权限配置详情）
     *
     * 【前端调用逻辑】
     * - 请求方式：GET
     * - 请求路径：/api/v1/roles
     * - 请求参数：无
     * - 请求示例：
     *   fetch('/api/v1/roles', {
     *     headers: { 'Authorization': 'Bearer ' + token }
     *   })
     *
     * 【返回数据】
     * {
     *   "code": 200,
     *   "message": "success",
     *   "data": [
     *     {
     *       "id": "角色ID",
     *       "roleName": "角色名称",
     *       "roleCode": "角色代码",
     *       "description": "角色描述",
     *       "status": 1,
     *       "createTime": "创建时间"
     *     }
     *   ]
     * }
     */
    @GetMapping
    public Result<List<Role>> getAllRoles() {
        List<Role> roles = rolePermissionService.getAllRoles();
        return Result.ok(roles);
    }

    /**
     * ============================================
     * 获取角色详情（包含权限配置）
     * ============================================
     *
     * 【后端逻辑】
     * 1. 根据 roleId 查询角色基本信息
     * 2. 查询该角色的权限配置
     * 3. 组装并返回角色详情（包含角色信息和权限配置）
     *
     * 【前端调用逻辑】
     * - 请求方式：GET
     * - 请求路径：/api/v1/roles/{roleId}
     * - 请求参数：路径参数 roleId（角色ID）
     * - 请求示例：
     *   fetch('/api/v1/roles/角色UUID', {
     *     headers: { 'Authorization': 'Bearer ' + token }
     *   })
     *
     * 【返回数据】
     * 成功：
     * {
     *   "code": 200,
     *   "message": "success",
     *   "data": {
     *     "id": "角色ID",
     *     "roleName": "角色名称",
     *     "roleCode": "角色代码",
     *     "description": "角色描述",
     *     "status": 1,
     *     "createTime": "创建时间",
     *     "permission": {
     *       "permissionEnterpriseView": 1,
     *       "permissionEnterpriseEdit": 0,
     *       "permissionEnterpriseDelete": 0,
     *       ... 其他权限字段
     *     }
     *   }
     * }
     * 失败：
     * {
     *   "code": 400,
     *   "message": "角色不存在",
     *   "data": null
     * }
     */
    @GetMapping("/{roleId}")
    public Result<Map<String, Object>> getRoleDetail(@PathVariable String roleId) {
        Map<String, Object> detail = rolePermissionService.getRoleDetail(roleId);
        return Result.ok(detail);
    }

    /**
     * ============================================
     * 创建角色（包含权限配置）
     * ============================================
     *
     * 【后端逻辑】
     * 1. 获取当前登录用户并验证管理员身份
     * 2. 检查角色代码是否已存在（唯一性校验）
     * 3. 检查权限继承（目标权限不能超过操作者的权限）
     * 4. 创建角色记录到role表
     * 5. 创建角色权限配置记录到role_permission表
     * 6. @Transactional注解保证角色和权限配置同时创建成功
     *
     * 【权限继承规则】
     * - 所有管理员都能创建角色，但只能创建权限范围 <= 自己权限的角色
     * - 目标角色的每个权限值都不能超过操作管理员的对应权限
     * - 超级管理员可以创建任意权限的角色
     * - 如果不指定权限配置（默认为0），直接通过
     *
     * 【前端调用逻辑】
     * - 请求方式：POST
     * - 请求路径：/api/v1/roles
     * - 请求头：Content-Type: application/json
     * - 请求参数：
     *   {
     *     "roleName": "角色名称（必填）",
     *     "roleCode": "角色代码（必填，唯一）",
     *     "description": "角色描述（可选）",
     *     "permissions": {  // 权限配置（可选，不填则所有权限为0）
     *       "permissionEnterpriseView": 1,
     *       "permissionEnterpriseEdit": 1,
     *       "permissionPatentAudit": 1
     *     }
     *   }
     * - 请求示例：
     *   fetch('/api/v1/roles', {
     *     method: 'POST',
     *     headers: {
     *       'Authorization': 'Bearer ' + token,
     *       'Content-Type': 'application/json'
     *     },
     *     body: JSON.stringify({
     *       roleName: '审核员',
     *       roleCode: 'AUDITOR',
     *       description: '负责审核工作',
     *       permissions: {
     *         permissionEnterpriseView: 1,
     *         permissionPatentAudit: 1
     *       }
     *     })
     *   })
     *
     * 【返回数据】
     * 成功：
     * {
     *   "code": 2001,
     *   "message": "角色创建成功",
     *   "data": { 角色信息 }
     * }
     *
     * 【错误码】
     * - 4001 UNAUTHORIZED: 请先登录
     * - 4002 FORBIDDEN: 只有管理员才能创建角色
     * - 4005 CONFLICT: 角色代码已存在
     * - 6009 PERMISSION_DENIED: 权限不足，无法创建超出自己权限范围的角色
     */
    @PostMapping
    public Result<Role> createRole(@RequestBody Map<String, Object> request) {
        // 获取当前登录的管理员用户
        User currentUser = UserContext.getUser();
        if (currentUser == null) {
            throw new UnauthorizedException("请先登录");
        }

        String roleName = (String) request.get("roleName");
        String roleCode = (String) request.get("roleCode");
        String description = (String) request.get("description");
        @SuppressWarnings("unchecked")
        Map<String, Integer> permissions = (Map<String, Integer>) request.get("permissions");

        if (roleName == null || roleName.isEmpty()) {
            throw new BadRequestException("角色名称不能为空");
        }
        if (roleCode == null || roleCode.isEmpty()) {
            throw new BadRequestException("角色代码不能为空");
        }

        Role role = rolePermissionService.createRole(roleName, roleCode, description, permissions, currentUser.getId());
        return Result.ok("角色创建成功", role);
    }

    /**
     * ============================================
     * 更新角色基本信息（不含权限）
     * ============================================
     *
     * 【后端逻辑】
     * 1. 验证管理员身份
     * 2. 检查角色是否存在
     * 3. 更新角色基本信息（角色名称、描述、状态）
     *
     * 【前端调用逻辑】
     * - 请求方式：PUT
     * - 请求路径：/api/v1/roles/{roleId}
     * - 请求头：Content-Type: application/json
     * - 请求参数：
     *   {
     *     "roleName": "新角色名称（可选）",
     *     "description": "新角色描述（可选）",
     *     "status": 1（可选，1=启用，0=禁用）
     *   }
     * - 请求示例：
     *   fetch('/api/v1/roles/角色UUID', {
     *     method: 'PUT',
     *     headers: {
     *       'Authorization': 'Bearer ' + token,
     *       'Content-Type': 'application/json'
     *     },
     *     body: JSON.stringify({
     *       roleName: '高级审核员',
     *       description: '高级审核权限',
     *       status: 1
     *     })
     *   })
     *
     * 【返回数据】
     * 成功：
     * {
     *   "code": 200,
     *   "message": "角色信息更新成功",
     *   "data": { 更新后的角色信息 }
     * }
     * 失败：
     * {
     *   "code": 400,
     *   "message": "错误原因",
     *   "data": null
     * }
     */
    @PutMapping("/{roleId}")
    public Result<Role> updateRole(@PathVariable String roleId, @RequestBody Map<String, Object> request) {
        // 获取当前登录的管理员用户
        User currentUser = UserContext.getUser();
        if (currentUser == null) {
            throw new UnauthorizedException("请先登录");
        }

        String roleName = (String) request.get("roleName");
        String description = (String) request.get("description");
        Integer status = (Integer) request.get("status");

        Role role = rolePermissionService.updateRole(roleId, roleName, description, status, currentUser.getId());
        return Result.ok("角色信息更新成功", role);
    }

    /**
     * ============================================
     * 更新角色权限配置
     * ============================================
     *
     * 【后端逻辑】
     * 1. 验证管理员身份
     * 2. 检查角色是否存在
     * 3. 检查权限继承（目标权限不能超过操作者的权限）
     * 4. 查询现有权限配置
     * 5. 如果权限配置不存在则创建，存在则更新
     *
     * 【权限继承规则】
     * - 管理员只能修改权限范围 <= 自己权限的角色
     * - 目标角色的每个权限值都不能超过操作管理员的对应权限
     * - 超级管理员可以修改任意角色的权限
     *
     * 【前端调用逻辑】
     * - 请求方式：PUT
     * - 请求路径：/api/v1/roles/{roleId}/permissions
     * - 请求头：Content-Type: application/json
     * - 请求参数：
     *   {
     *     "permissionEnterpriseView": 1,
     *     "permissionEnterpriseEdit": 1,
     *     "permissionEnterpriseDelete": 0,
     *     "permissionResearchView": 1,
     *     "permissionResearchEdit": 0,
     *     "permissionResearchDelete": 0,
     *     "permissionPatentAudit": 1,
     *     "permissionPatentReview": 1,
     *     "permissionNoticePublish": 0,
     *     "permissionStatisticsView": 1,
     *     "permissionSystemConfig": 0,
     *     "permissionIntentionAudit": 0,
     *     "permissionLogView": 0
     *   }
     * - 请求示例：
     *   fetch('/api/v1/roles/角色UUID/permissions', {
     *     method: 'PUT',
     *     headers: {
     *       'Authorization': 'Bearer ' + token,
     *       'Content-Type': 'application/json'
     *     },
     *     body: JSON.stringify({
     *       permissionEnterpriseView: 1,
     *       permissionPatentAudit: 1
     *     })
     *   })
     *
     * 【返回数据】
     * 成功：
     * {
     *   "code": 200,
     *   "message": "角色权限更新成功",
     *   "data": { 更新后的权限信息 }
     * }
     * 失败：
     * {
     *   "code": 400,
     *   "message": "错误原因",
     *   "data": null
     * }
     *
     * 【错误码】
     * - "请先登录"：用户未登录
     * - "只有管理员才能修改角色权限"：当前用户不是管理员
     * - "角色不存在"：角色ID不存在
     * - "权限不足，无法修改超出自己权限范围的角色"：操作员权限不够
     *
     * 【权限字段说明】
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
     * - permissionIntentionAudit: 意向审核权限
     * - permissionLogView: 日志查看权限
     */
    @PutMapping("/{roleId}/permissions")
    public Result<RolePermission> updateRolePermission(@PathVariable String roleId, @RequestBody Map<String, Integer> permissions) {
        // 获取当前登录的管理员用户
        User currentUser = UserContext.getUser();
        if (currentUser == null) {
            throw new UnauthorizedException("请先登录");
        }

        RolePermission permission = rolePermissionService.updateRolePermission(roleId, permissions, currentUser.getId());
        return Result.ok("角色权限更新成功", permission);
    }

    /**
     * ============================================
     * 删除角色（同时删除关联的权限配置）
     * ============================================
     *
     * 【后端逻辑】
     * 1. 验证管理员身份
     * 2. 检查角色是否存在
     * 3. 检查是否为系统保留角色（超级管理员角色、空白角色），系统保留角色不能删除
     * 4. 检查管理员是否删除自己的角色，不能删除自己的角色
     * 5. 检查权限继承（只能删除权限范围 <= 自己权限的角色）
     * 6. 将使用该角色的管理员换为空白角色
     * 7. 删除角色关联的权限配置
     * 8. 删除角色记录
     * 9. 事务保证所有操作原子性
     *
     * 【权限继承规则】
     * - 管理员只能删除权限范围 <= 自己权限的角色
     * - 超级管理员可以删除任意角色
     *
     * 【前端调用逻辑】
     * - 请求方式：DELETE
     * - 请求路径：/api/v1/roles/{roleId}
     * - 请求参数：路径参数 roleId（角色ID）
     * - 请求示例：
     *   fetch('/api/v1/roles/角色UUID', {
     *     method: 'DELETE',
     *     headers: { 'Authorization': 'Bearer ' + token }
     *   })
     *
     * 【返回数据】
     * 成功：
     * {
     *   "code": 200,
     *   "message": "角色删除成功",
     *   "data": null
     * }
     * 失败：
     * {
     *   "code": 400,
     *   "message": "错误原因",
     *   "data": null
     * }
     *
     * 【错误码】
     * - "请先登录"：用户未登录
     * - "只有管理员才能删除角色"：当前用户不是管理员
     * - "角色不存在"：角色ID不存在
     * - "该角色正在被管理员使用，无法删除"：有管理员绑定了该角色
     * - "权限不足，无法删除超出自己权限范围的角色"：操作员权限不够
     */
    @DeleteMapping("/{roleId}")
    public Result<Void> deleteRole(@PathVariable String roleId) {
        // 获取当前登录的管理员用户
        User currentUser = UserContext.getUser();
        if (currentUser == null) {
            throw new UnauthorizedException("请先登录");
        }

        rolePermissionService.deleteRole(roleId, currentUser.getId());
        return Result.ok("角色删除成功");
    }
}