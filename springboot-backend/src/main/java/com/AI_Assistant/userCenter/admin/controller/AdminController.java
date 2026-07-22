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
import com.AI_Assistant.common.StateCode;
import com.AI_Assistant.common.exception.BadRequestException;
import com.AI_Assistant.common.exception.NotFoundException;
import com.AI_Assistant.common.exception.UnauthorizedException;
import com.AI_Assistant.common.util.UserContext;
import com.AI_Assistant.userCenter.admin.dto.AdminAccount;
import com.AI_Assistant.userCenter.admin.service.AdminService;
import com.AI_Assistant.userCenter.user.entity.User;

/**
 * ============================================
 * 管理员账号管理控制器
 * ============================================
 *
 * 【后端逻辑】
 * 本控制器负责管理员账号的增删改查操作，所有接口需要管理员权限（permissionSystemConfig = 1）
 * 权限验证由 PermissionInterceptor 统一处理
 *
 * 【前端调用逻辑】
 * 前端需要：
 * 1. 在请求头中携带有效的JWT Token（格式：Authorization: Bearer <token>）
 * 2. 当前登录用户必须具有管理员身份
 * 3. 所有接口返回统一的 Result 对象
 *
 * 【接口列表】
 * - GET    /api/v1/adminUsers         获取所有管理员列表
 * - GET    /api/v1/adminUsers/{id}    获取单个管理员详情
 * - POST   /api/v1/adminUsers         创建管理员（升级用户）
 * - DELETE /api/v1/adminUsers/{id}    注销管理员身份
 * - PUT    /api/v1/adminUsers/{id}    更新管理员信息
 */
@RestController
@RequestMapping("/api/v1")
public class AdminController {

    @Autowired
    private AdminService adminService;

    /**
     * ============================================
     * 获取所有管理员用户列表
     * ============================================
     *
     * 【后端逻辑】
     * 1. 调用 adminService.getAllAdminUsers() 获取所有管理员账号
     * 2. 返回包含角色信息的 AdminAccount 列表
     *
     * 【前端调用逻辑】
     * - 请求方式：GET
     * - 请求路径：/api/v1/adminUsers
     * - 请求参数：无
     * - 请求示例：
     *   fetch('/api/v1/adminUsers', {
     *     headers: { 'Authorization': 'Bearer ' + token }
     *   })
     *
     * 【返回数据】
     * {
     *   "code": 200,
     *   "message": "success",
     *   "data": [
     *     {
     *       "id": "用户ID",
     *       "username": "用户名",
     *       "realName": "真实姓名",
     *       "roleId": "角色ID",
     *       "roleName": "角色名称",
     *       "status": 1,
     *       "createTime": "创建时间"
     *     }
     *   ]
     * }
     */
    @GetMapping("/adminUsers")
    public Result<List<AdminAccount>> getAdminUsers() {
        List<AdminAccount> list = adminService.getAllAdminUsers();
        return Result.ok(list);
    }

    /**
     * ============================================
     * 获取单个管理员用户详情
     * ============================================
     *
     * 【后端逻辑】
     * 1. 根据 adminUserId 查询管理员账号详情
     * 2. 如果不存在，返回错误信息
     * 3. 返回包含角色信息的 AdminAccount 对象
     *
     * 【前端调用逻辑】
     * - 请求方式：GET
     * - 请求路径：/api/v1/adminUsers/{adminUserId}
     * - 请求参数：路径参数 adminUserId（管理员用户ID）
     * - 请求示例：
     *   fetch('/api/v1/adminUsers/123456', {
     *     headers: { 'Authorization': 'Bearer ' + token }
     *   })
     *
     * 【返回数据】
     * 成功：
     * {
     *   "code": 200,
     *   "message": "success",
     *   "data": {
     *     "id": "用户ID",
     *     "username": "用户名",
     *     "realName": "真实姓名",
     *     "roleId": "角色ID",
     *     "roleName": "角色名称",
     *     "status": 1
     *   }
     * }
     * 失败：
     * {
     *   "code": 400,
     *   "message": "管理员用户不存在",
     *   "data": null
     * }
     */
    @GetMapping("/adminUsers/{adminUserId}")
    public Result<AdminAccount> getAdminUser(@PathVariable String adminUserId) {
        // 如果传入"me"，获取当前登录用户ID
        String userId = adminUserId;
        if ("me".equalsIgnoreCase(adminUserId)) {
            User currentUser = UserContext.getUser();
            if (currentUser == null) {
                throw new UnauthorizedException("未登录");
            }
            userId = currentUser.getId();
        }
        
        AdminAccount admin = adminService.getAdminUserById(userId);
        if (admin == null) {
            throw new NotFoundException("管理员用户不存在");
        }
        return Result.ok(2000, "success", admin);
    }

    /**
     * ============================================
     * 创建管理员用户（将已有用户升级为管理员）
     * ============================================
     *
     * 【后端逻辑】
     * 1. 从请求上下文获取当前登录的管理员（操作员）
     * 2. 验证操作员是否为管理员
     * 3. 检查被升级用户名的用户是否存在（必须是已注册的普通用户）
     * 4. 如果未指定角色ID，默认分配空白角色（BLANK）
     * 5. 如果指定了角色，检查操作员的权限继承（操作员只能分配自己拥有的权限）
     * 6. 将用户类型从普通用户(0)升级为管理员(3)
     * 7. 创建管理员扩展信息（admin表记录）
     *
     * 【前端调用逻辑】
     * - 请求方式：POST
     * - 请求路径：/api/v1/adminUsers
     * - 请求头：Content-Type: application/json
     * - 请求参数：
     *   {
     *     "username": "被升级用户的用户名（必填）",
     *     "realName": "真实姓名（可选）",
     *     "roleId": "角色ID（可选，不填则默认空白角色）"
     *   }
     * - 请求示例：
     *   fetch('/api/v1/adminUsers', {
     *     method: 'POST',
     *     headers: {
     *       'Authorization': 'Bearer ' + token,
     *       'Content-Type': 'application/json'
     *     },
     *     body: JSON.stringify({
     *       username: 'zhangsan',
     *       realName: '张三',
     *       roleId: '角色UUID'  // 可选
     *     })
     *   })
     *
     * 【返回数据】
     * 成功：
     * {
     *   "code": 2001,
     *   "message": "管理员账号创建成功",
     *   "data": { 管理员账号信息 }
     * }
     *
     * 【错误码】
     * - 4001 UNAUTHORIZED: 请先登录
     * - 4002 FORBIDDEN: 只有管理员才能创建管理员账号
     * - 4003 NOT_FOUND: 用户不存在，请先注册普通用户
     * - 4005 CONFLICT: 该用户已是管理员
     * - 6009 PERMISSION_DENIED: 权限不足，无法分配该角色
     */
    @PostMapping("/adminUsers")
    public Result<AdminAccount> createAdminUser(@RequestBody Map<String, Object> request) {
        // 获取当前登录的管理员用户（操作管理员）
        User currentUser = UserContext.getUser();
        if (currentUser == null) {
            throw new UnauthorizedException("请先登录");
        }

        String username = (String) request.get("username");
        String realName = (String) request.get("realName");
        String roleId = (String) request.get("roleId");

        if (username == null || username.isEmpty()) {
            throw new BadRequestException("用户名不能为空");
        }

        AdminAccount account = adminService.createAdminUser(currentUser.getId(), username, realName, roleId);
        // [已修复 - 问题82] 创建成功使用CREATED状态码(2001)而非OK(2000)
        return Result.ok(StateCode.CREATED, "管理员账号创建成功", account);
    }

    /**
     * ============================================
     * 删除管理员用户（注销管理员身份，保留用户账号）
     * ============================================
     *
     * 【后端逻辑】
     * 1. 根据 adminUserId 查询管理员用户
     * 2. 验证用户是否存在且为管理员
     * 3. 删除管理员扩展信息（admin表记录）
     * 4. 将用户类型从管理员(3)降级为普通用户(0)
     * 5. 保留用户的基础信息（username、密码等）
     *
     * 【前端调用逻辑】
     * - 请求方式：DELETE
     * - 请求路径：/api/v1/adminUsers/{adminUserId}
     * - 请求参数：路径参数 adminUserId（要注销的管理员用户ID）
     * - 请求示例：
     *   fetch('/api/v1/adminUsers/123456', {
     *     method: 'DELETE',
     *     headers: { 'Authorization': 'Bearer ' + token }
     *   })
     *
     * 【返回数据】
     * 成功：
     * {
     *   "code": 200,
     *   "message": "注销管理员身份成功",
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
     * - "用户不存在"：用户ID不存在
     * - "该用户不是管理员用户"：该用户不是管理员
     */
    @DeleteMapping("/adminUsers/{adminUserId}")
    public Result<Void> deleteAdminUser(@PathVariable String adminUserId) {
        adminService.deleteAdminUser(adminUserId);
        return Result.ok("注销管理员身份成功");
    }

    /**
     * ============================================
     * 更新管理员用户信息
     * ============================================
     *
     * 【后端逻辑】
     * 1. 从请求上下文获取当前登录的管理员（操作员）
     * 2. 验证操作员是否为管理员
     * 3. 验证被更新的管理员是否存在
     * 4. 如果要修改角色，检查操作员的权限继承
     * 5. 更新管理员信息（真实姓名、角色、状态等）
     *
     * 【前端调用逻辑】
     * - 请求方式：PUT
     * - 请求路径：/api/v1/adminUsers/{adminUserId}
     * - 请求头：Content-Type: application/json
     * - 请求参数：
     *   {
     *     "realName": "真实姓名（可选）",
     *     "roleId": "角色ID（可选）",
     *     "status": 1（可选，1=启用，0=禁用）
     *   }
     * - 请求示例：
     *   fetch('/api/v1/adminUsers/123456', {
     *     method: 'PUT',
     *     headers: {
     *       'Authorization': 'Bearer ' + token,
     *       'Content-Type': 'application/json'
     *     },
     *     body: JSON.stringify({
     *       realName: '新姓名',
     *       roleId: '新角色UUID',
     *       status: 1
     *     })
     *   })
     *
     * 【返回数据】
     * 成功：
     * {
     *   "code": 200,
     *   "message": "修改管理员账号信息成功",
     *   "data": { 更新后的管理员账号信息 }
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
     * - "只有管理员才能更新管理员账号"：当前用户不是管理员
     * - "您不能分配超出自己权限范围的角色"：操作员权限不足
     */
    @PutMapping("/adminUsers/{adminUserId}")
    public Result<AdminAccount> updateAdminUser(@PathVariable String adminUserId, @RequestBody Map<String, Object> request) {
        // 获取当前登录的管理员用户（操作管理员）
        User currentUser = UserContext.getUser();
        if (currentUser == null) {
            throw new UnauthorizedException("请先登录");
        }

        String realName = (String) request.get("realName");
        String roleId = (String) request.get("roleId");
        Integer status = (Integer) request.get("status");

        AdminAccount account = adminService.updateAdminUser(adminUserId, realName, roleId, status, currentUser.getId());
        return Result.ok("修改管理员账号信息成功", account);
    }
}