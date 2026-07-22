package com.AI_Assistant.userCenter.admin.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.AI_Assistant.common.exception.ConflictException;
import com.AI_Assistant.common.exception.ForbiddenException;
import com.AI_Assistant.common.exception.NotFoundException;
import com.AI_Assistant.common.exception.PermissionDeniedException;
import com.AI_Assistant.userCenter.admin.entity.Admin;
import com.AI_Assistant.userCenter.admin.entity.Role;
import com.AI_Assistant.userCenter.admin.entity.RolePermission;
import com.AI_Assistant.userCenter.admin.mapper.AdminMapper;
import com.AI_Assistant.userCenter.admin.mapper.RoleMapper;
import com.AI_Assistant.userCenter.admin.mapper.RolePermissionMapper;
import com.AI_Assistant.userCenter.user.entity.User;
import com.AI_Assistant.userCenter.user.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import cn.hutool.core.util.IdUtil;

/**
 * ============================================
 * 角色权限服务
 * ============================================
 *
 * 【服务职责】
 * 本服务负责角色的CRUD操作和权限配置，包括：
 * 1. 角色的创建、修改、删除、查询
 * 2. 角色权限的配置和更新
 * 3. 管理员身份验证
 *
 * 【核心概念】
 * - 角色(Role)：一组权限的集合，如"审核员"、"普通管理员"等
 * - 权限配置(RolePermission)：角色的具体权限项，每项为0或1
 * - 超级管理员(SUPER_ADMIN)：拥有所有权限的角色，系统最高权限
 * - 空白角色(BLANK)：没有任何权限的角色
 *
 * 【权限规则】
 * - 所有管理员都能创建角色，但只能创建权限范围 <= 自己权限的角色
 * - 管理员只能修改/删除权限范围 <= 自己权限的角色
 * - 超级管理员可以创建/修改/删除任意权限的角色
 * - 普通管理员可以查看所有角色列表和详情
 * - 删除角色时，如果角色正在被使用（已有管理员绑定），则不能删除
 *
 * 【数据库表】
 * - role表：存储角色信息（角色名称、代码、描述、状态等）
 * - role_permission表：存储角色权限配置（13个权限项）
 */
@Service
public class RolePermissionService {

    private static final Logger logger = LoggerFactory.getLogger(RolePermissionService.class);

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private RolePermissionMapper rolePermissionMapper;

    @Autowired
    private AdminMapper adminMapper;

    @Autowired
    private UserMapper userMapper;

    /**
     * ============================================
     * 获取所有角色列表（不含权限详情）
     * ============================================
     *
     * 【后端逻辑】
     * 1. 查询role表获取所有角色列表
     * 2. 返回角色基本信息列表（不包含权限配置）
     *
     * 【业务场景】
     * - 角色下拉选择框
     * - 角色管理列表页面
     *
     * 【参数】
     * - 无
     *
     * 【返回】
     * - List<Role> 角色列表
     */
    public List<Role> getAllRoles() {
        logger.info("查询所有角色列表");
        return roleMapper.selectList(null);
    }

    /**
     * ============================================
     * 获取角色详情（包含权限配置）
     * ============================================
     *
     * 【后端逻辑】
     * 1. 根据roleId查询角色基本信息
     * 2. 查询该角色的权限配置
     * 3. 组装结果，包含角色信息和权限配置
     *
     * 【业务场景】
     * - 角色编辑页面展示角色详情
     * - 角色权限查看
     *
     * 【参数】
     * - roleId: 角色ID
     *
     * 【返回】
     * - 成功：包含角色信息和权限配置的Map
     * - 失败：Result.error("角色不存在", null)
     */
    public Map<String, Object> getRoleDetail(String roleId) {
        logger.info("查询角色详情，roleId: {}", roleId);

        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            logger.warn("角色不存在，roleId: {}", roleId);
            throw new NotFoundException("角色不存在");
        }

        LambdaQueryWrapper<RolePermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RolePermission::getRoleId, roleId);
        RolePermission permission = rolePermissionMapper.selectOne(wrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("role", role);
        result.put("permission", permission);

        return result;
    }

    /**
     * ============================================
     * 创建角色（包含权限配置）
     * ============================================
     *
     * 【后端逻辑】
     * 1. 验证操作者是管理员（任何管理员都能创建角色）
     * 2. 检查角色代码是否已存在（唯一性校验）
     * 3. 如果指定了权限配置，检查权限继承（目标权限不能超过操作者的权限）
     * 4. 创建角色记录到role表
     * 5. 创建角色权限配置记录到role_permission表
     * 6. @Transactional注解保证角色和权限配置同时创建成功
     *
     * 【权限继承规则】
     * - 所有管理员都能创建角色，但只能创建权限范围 <= 自己权限的角色
     * - 目标角色的每个权限值都不能超过操作管理员的对应权限
     * - 超级管理员可以创建任意权限的角色
     * - 如果不指定权限配置（默认为0），直接通过（0永远 <= 任何值）
     *
     * 【业务场景】
     * - 管理员创建新角色（在自己权限范围内）
     * - 系统初始化时由AdminInitConfig调用
     *
     * 【参数】
     * - roleName: 角色名称（必填）
     * - roleCode: 角色代码（必填，唯一标识，如"AUDITOR"）
     * - description: 角色描述（可选）
     * - permissionMap: 权限配置Map（可选，不传则所有权限为0）
     * - operatorUserId: 操作管理员ID（必填）
     *
     * 【返回】
     * - 成功：{ "code": 2001, "message": "角色创建成功", "data": Role对象 }
     * - 失败：见【错误码】
     *
     * 【错误码】
     * - 4002 FORBIDDEN: 只有管理员才能创建角色
     * - 4005 CONFLICT: 角色代码已存在
     * - 6009 PERMISSION_DENIED: 权限不足，无法创建超出自己权限范围的角色
     */
    @Transactional
    public Role createRole(String roleName, String roleCode, String description, Map<String, Integer> permissionMap, String operatorUserId) {
        logger.info("创建角色，roleName: {}, roleCode: {}, operatorUserId: {}", roleName, roleCode, operatorUserId);

        if (!checkIsAdmin(operatorUserId)) {
            logger.warn("非管理员尝试创建角色，operatorUserId: {}", operatorUserId);
            throw new ForbiddenException("只有管理员才能创建角色");
        }

        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Role::getRoleCode, roleCode);
        if (roleMapper.exists(wrapper)) {
            logger.warn("角色代码已存在: {}", roleCode);
            throw new ConflictException("角色代码已存在");
        }

        if (permissionMap != null && hasAnyPermission(permissionMap)) {
            if (!checkPermissionInheritanceForCreate(operatorUserId, permissionMap)) {
                logger.warn("管理员尝试创建超出自己权限范围的角色，operatorUserId: {}", operatorUserId);
                throw new PermissionDeniedException("权限不足，无法创建超出自己权限范围的角色");
            }
        }

        Role role = new Role();
        role.setId(IdUtil.simpleUUID());
        role.setRoleName(roleName);
        role.setRoleCode(roleCode);
        role.setDescription(description);
        role.setStatus(1);
        role.setCreateTime(LocalDateTime.now());
        role.setUpdateTime(LocalDateTime.now());
        roleMapper.insert(role);

        RolePermission permission = new RolePermission();
        permission.setId(IdUtil.simpleUUID());
        permission.setRoleId(role.getId());
        fillPermission(permission, permissionMap);
        permission.setCreateTime(LocalDateTime.now());
        permission.setUpdateTime(LocalDateTime.now());
        rolePermissionMapper.insert(permission);

        logger.info("角色创建成功，roleId: {}", role.getId());
        return role;
    }

    /**
     * ============================================
     * 检查用户是否为管理员
     * ============================================
     *
     * 【后端逻辑】
     * 1. 查询用户的管理员信息
     * 2. 检查用户类型是否为管理员(3)
     *
     * 【参数】
     * - userId: 用户ID
     *
     * 【返回】
     * - true: 是管理员
     * - false: 不是管理员
     */
    private boolean checkIsAdmin(String userId) {
        User user = userMapper.selectById(userId);
        if (user == null || user.getUserType() == null || user.getUserType() != 3) {
            return false;
        }
        return true;
    }

    /**
     * ============================================
     * 检查权限配置中是否有任何权限
     * ============================================
     *
     * 【参数】
     * - permissionMap: 权限配置Map
     *
     * 【返回】
     * - true: 至少有一项权限为1
     * - false: 所有权限都为0或null
     */
    private boolean hasAnyPermission(Map<String, Integer> permissionMap) {
        if (permissionMap == null) {
            return false;
        }
        for (Integer value : permissionMap.values()) {
            if (value != null && value == 1) {
                return true;
            }
        }
        return false;
    }

    /**
     * ============================================
     * 检查创建角色时的权限继承
     * ============================================
     *
     * 【后端逻辑】
     * 1. 如果是超级管理员，直接返回true
     * 2. 获取操作管理员的权限配置
     * 3. 逐项比较目标权限是否都在操作管理员权限范围内
     *
     * 【参数】
     * - operatorUserId: 操作管理员ID
     * - targetPermissionMap: 目标权限配置Map
     *
     * 【返回】
     * - true: 操作员有权创建该角色
     * - false: 操作员权限不足
     */
    private boolean checkPermissionInheritanceForCreate(String operatorUserId, Map<String, Integer> targetPermissionMap) {
        // 超级管理员可以创建任意权限的角色
        if (checkSuperAdmin(operatorUserId)) {
            return true;
        }

        // 获取操作管理员的权限配置
        RolePermission operatorPermission = getOperatorPermission(operatorUserId);
        if (operatorPermission == null) {
            logger.warn("操作管理员权限配置不存在，operatorUserId: {}", operatorUserId);
            return false;
        }

        // 逐项比较权限：目标权限不能超过操作者权限
        if (comparePermission(targetPermissionMap.get("permissionEnterpriseView"), operatorPermission.getPermissionEnterpriseView()) > 0) {
            return false;
        }
        if (comparePermission(targetPermissionMap.get("permissionEnterpriseEdit"), operatorPermission.getPermissionEnterpriseEdit()) > 0) {
            return false;
        }
        if (comparePermission(targetPermissionMap.get("permissionEnterpriseDelete"), operatorPermission.getPermissionEnterpriseDelete()) > 0) {
            return false;
        }
        if (comparePermission(targetPermissionMap.get("permissionResearchView"), operatorPermission.getPermissionResearchView()) > 0) {
            return false;
        }
        if (comparePermission(targetPermissionMap.get("permissionResearchEdit"), operatorPermission.getPermissionResearchEdit()) > 0) {
            return false;
        }
        if (comparePermission(targetPermissionMap.get("permissionResearchDelete"), operatorPermission.getPermissionResearchDelete()) > 0) {
            return false;
        }
        if (comparePermission(targetPermissionMap.get("permissionPatentAudit"), operatorPermission.getPermissionPatentAudit()) > 0) {
            return false;
        }
        if (comparePermission(targetPermissionMap.get("permissionPatentReview"), operatorPermission.getPermissionPatentReview()) > 0) {
            return false;
        }
        if (comparePermission(targetPermissionMap.get("permissionNoticePublish"), operatorPermission.getPermissionNoticePublish()) > 0) {
            return false;
        }
        if (comparePermission(targetPermissionMap.get("permissionStatisticsView"), operatorPermission.getPermissionStatisticsView()) > 0) {
            return false;
        }
        if (comparePermission(targetPermissionMap.get("permissionSystemConfig"), operatorPermission.getPermissionSystemConfig()) > 0) {
            return false;
        }
        if (comparePermission(targetPermissionMap.get("permissionIntentionAudit"), operatorPermission.getPermissionIntentionAudit()) > 0) {
            return false;
        }
        if (comparePermission(targetPermissionMap.get("permissionLogView"), operatorPermission.getPermissionLogView()) > 0) {
            return false;
        }

        return true;
    }

    /**
     * ============================================
     * 比较权限值
     * ============================================
     *
     * 【规则】
     * - null 视为 0
     * - 如果 target > operator，返回 > 0
     * - 如果 target <= operator，返回 <= 0
     *
     * 【参数】
     * - target: 目标权限值
     * - operator: 操作者权限值
     *
     * 【返回】
     * - 正数：目标权限超过操作者权限（不允许）
     * - 0或负数：目标权限在操作者权限范围内（允许）
     */
    private int comparePermission(Integer target, Integer operator) {
        int t = (target == null) ? 0 : target;
        int o = (operator == null) ? 0 : operator;
        return t - o;
    }

    /**
     * ============================================
     * 获取操作管理员的权限配置
     * ============================================
     */
    private RolePermission getOperatorPermission(String operatorUserId) {
        LambdaQueryWrapper<Admin> adminWrapper = new LambdaQueryWrapper<>();
        adminWrapper.eq(Admin::getUserId, operatorUserId);
        Admin operatorAdmin = adminMapper.selectOne(adminWrapper);

        if (operatorAdmin == null || operatorAdmin.getRoleId() == null) {
            return null;
        }

        LambdaQueryWrapper<RolePermission> permWrapper = new LambdaQueryWrapper<>();
        permWrapper.eq(RolePermission::getRoleId, operatorAdmin.getRoleId());
        return rolePermissionMapper.selectOne(permWrapper);
    }

    /**
     * ============================================
     * 检查用户是否为超级管理员
     * ============================================
     *
     * 【后端逻辑】
     * 1. 查询用户的管理员信息
     * 2. 检查用户的角色代码是否为"SUPER_ADMIN"
     *
     * 【参数】
     * - userId: 用户ID
     *
     * 【返回】
     * - true: 是超级管理员
     * - false: 不是超级管理员
     */
    private boolean checkSuperAdmin(String userId) {
        LambdaQueryWrapper<Admin> adminWrapper = new LambdaQueryWrapper<>();
        adminWrapper.eq(Admin::getUserId, userId);
        Admin admin = adminMapper.selectOne(adminWrapper);

        if (admin == null || admin.getRoleId() == null) {
            return false;
        }

        LambdaQueryWrapper<Role> roleWrapper = new LambdaQueryWrapper<>();
        roleWrapper.eq(Role::getId, admin.getRoleId()).eq(Role::getRoleCode, "SUPER_ADMIN");
        return roleMapper.exists(roleWrapper);
    }

    /**
     * ============================================
     * 更新角色基本信息（不含权限）
     * ============================================
     *
     * 【后端逻辑】
     * 1. 验证操作者是管理员
     * 2. 检查角色是否存在
     * 3. 更新角色基本信息（名称、描述、状态）
     *
     * 【业务场景】
     * - 修改角色名称或描述
     * - 启用/禁用角色
     *
     * 【参数】
     * - roleId: 角色ID（必填）
     * - roleName: 新角色名称（可选）
     * - description: 新角色描述（可选）
     * - status: 角色状态（可选，1=启用，0=禁用）
     * - operatorUserId: 操作管理员ID（必填）
     *
     * 【返回】
     * - 成功：{ "code": 2002, "message": "角色信息更新成功", "data": Role对象 }
     * - 失败：见【错误码】
     *
     * 【错误码】
     * - 4002 FORBIDDEN: 只有管理员才能更新角色
     * - 4003 NOT_FOUND: 角色不存在
     */
    public Role updateRole(String roleId, String roleName, String description, Integer status, String operatorUserId) {
        logger.info("更新角色信息，roleId: {}, operatorUserId: {}", roleId, operatorUserId);

        if (!checkIsAdmin(operatorUserId)) {
            logger.warn("非管理员尝试更新角色，operatorUserId: {}", operatorUserId);
            throw new ForbiddenException("只有管理员才能更新角色");
        }

        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            logger.warn("角色不存在，roleId: {}", roleId);
            throw new NotFoundException("角色不存在");
        }

        if (roleName != null) {
            role.setRoleName(roleName);
        }
        if (description != null) {
            role.setDescription(description);
        }
        if (status != null) {
            role.setStatus(status);
        }
        role.setUpdateTime(LocalDateTime.now());
        roleMapper.updateById(role);

        logger.info("角色信息更新成功，roleId: {}", roleId);
        return role;
    }

    /**
     * ============================================
     * 更新角色权限配置
     * ============================================
     *
     * 【后端逻辑】
     * 1. 验证操作者是管理员（任何管理员都能修改角色权限）
     * 2. 检查角色是否存在
     * 3. 检查权限继承（目标权限不能超过操作者的权限）
     * 4. 查询现有权限配置
     * 5. 如果权限配置不存在则创建，存在则更新
     * 6. @Transactional注解保证操作的原子性
     *
     * 【权限继承规则】
     * - 管理员只能修改权限范围 <= 自己权限的角色
     * - 目标角色的每个权限值都不能超过操作管理员的对应权限
     * - 超级管理员可以修改任意角色的权限
     *
     * 【业务场景】
     * - 修改角色的权限配置
     * - 角色权限调整
     *
     * 【参数】
     * - roleId: 角色ID（必填）
     * - permissionMap: 权限配置Map（必填）
     * - operatorUserId: 操作管理员ID（必填）
     *
     * 【返回】
     * - 成功：{ "code": 2002, "message": "角色权限更新成功", "data": RolePermission对象 }
     * - 失败：见【错误码】
     *
     * 【错误码】
     * - 4002 FORBIDDEN: 只有管理员才能修改角色权限
     * - 4003 NOT_FOUND: 角色不存在
     * - 6009 PERMISSION_DENIED: 权限不足，无法修改超出自己权限范围的角色
     */
    @Transactional
    public RolePermission updateRolePermission(String roleId, Map<String, Integer> permissionMap, String operatorUserId) {
        logger.info("更新角色权限，roleId: {}, operatorUserId: {}", roleId, operatorUserId);

        if (!checkIsAdmin(operatorUserId)) {
            logger.warn("非管理员尝试更新角色权限，operatorUserId: {}", operatorUserId);
            throw new ForbiddenException("只有管理员才能修改角色权限");
        }

        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            logger.warn("角色不存在，roleId: {}", roleId);
            throw new NotFoundException("角色不存在");
        }

        if (permissionMap != null && hasAnyPermission(permissionMap)) {
            if (!checkPermissionInheritanceForUpdate(operatorUserId, roleId, permissionMap)) {
                logger.warn("管理员尝试修改超出自己权限范围的角色权限，operatorUserId: {}", operatorUserId);
                throw new PermissionDeniedException("权限不足，无法修改超出自己权限范围的角色");
            }
        }

        LambdaQueryWrapper<RolePermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RolePermission::getRoleId, roleId);
        RolePermission permission = rolePermissionMapper.selectOne(wrapper);

        if (permission == null) {
            permission = new RolePermission();
            permission.setId(IdUtil.simpleUUID());
            permission.setRoleId(roleId);
            fillPermission(permission, permissionMap);
            permission.setCreateTime(LocalDateTime.now());
            permission.setUpdateTime(LocalDateTime.now());
            rolePermissionMapper.insert(permission);
        } else {
            fillPermission(permission, permissionMap);
            permission.setUpdateTime(LocalDateTime.now());
            rolePermissionMapper.updateById(permission);
        }

        logger.info("角色权限更新成功，roleId: {}", roleId);
        return permission;
    }

    /**
     * ============================================
     * 检查更新角色权限时的权限继承
     * ============================================
     *
     * 【后端逻辑】
     * 1. 如果是超级管理员，直接返回true
     * 2. 获取操作管理员的权限配置
     * 3. 逐项比较目标权限是否都在操作管理员权限范围内
     *
     * 【参数】
     * - operatorUserId: 操作管理员ID
     * - roleId: 目标角色ID
     * - targetPermissionMap: 目标权限配置Map
     *
     * 【返回】
     * - true: 操作员有权修改该角色的权限
     * - false: 操作员权限不足
     */
    private boolean checkPermissionInheritanceForUpdate(String operatorUserId, String roleId, Map<String, Integer> targetPermissionMap) {
        // 超级管理员可以修改任意角色的权限
        if (checkSuperAdmin(operatorUserId)) {
            return true;
        }

        // 获取操作管理员的权限配置
        RolePermission operatorPermission = getOperatorPermission(operatorUserId);
        if (operatorPermission == null) {
            logger.warn("操作管理员权限配置不存在，operatorUserId: {}", operatorUserId);
            return false;
        }

        // 逐项比较权限：目标权限不能超过操作者权限
        if (comparePermission(targetPermissionMap.get("permissionEnterpriseView"), operatorPermission.getPermissionEnterpriseView()) > 0) {
            return false;
        }
        if (comparePermission(targetPermissionMap.get("permissionEnterpriseEdit"), operatorPermission.getPermissionEnterpriseEdit()) > 0) {
            return false;
        }
        if (comparePermission(targetPermissionMap.get("permissionEnterpriseDelete"), operatorPermission.getPermissionEnterpriseDelete()) > 0) {
            return false;
        }
        if (comparePermission(targetPermissionMap.get("permissionResearchView"), operatorPermission.getPermissionResearchView()) > 0) {
            return false;
        }
        if (comparePermission(targetPermissionMap.get("permissionResearchEdit"), operatorPermission.getPermissionResearchEdit()) > 0) {
            return false;
        }
        if (comparePermission(targetPermissionMap.get("permissionResearchDelete"), operatorPermission.getPermissionResearchDelete()) > 0) {
            return false;
        }
        if (comparePermission(targetPermissionMap.get("permissionPatentAudit"), operatorPermission.getPermissionPatentAudit()) > 0) {
            return false;
        }
        if (comparePermission(targetPermissionMap.get("permissionPatentReview"), operatorPermission.getPermissionPatentReview()) > 0) {
            return false;
        }
        if (comparePermission(targetPermissionMap.get("permissionNoticePublish"), operatorPermission.getPermissionNoticePublish()) > 0) {
            return false;
        }
        if (comparePermission(targetPermissionMap.get("permissionStatisticsView"), operatorPermission.getPermissionStatisticsView()) > 0) {
            return false;
        }
        if (comparePermission(targetPermissionMap.get("permissionSystemConfig"), operatorPermission.getPermissionSystemConfig()) > 0) {
            return false;
        }
        if (comparePermission(targetPermissionMap.get("permissionIntentionAudit"), operatorPermission.getPermissionIntentionAudit()) > 0) {
            return false;
        }
        if (comparePermission(targetPermissionMap.get("permissionLogView"), operatorPermission.getPermissionLogView()) > 0) {
            return false;
        }

        return true;
    }

    /**
     * ============================================
     * 删除角色（同时删除关联的权限配置）
     * ============================================
     *
     * 【后端逻辑】
     * 1. 验证操作者是管理员
     * 2. 检查角色是否存在
     * 3. 检查是否为系统保留角色（超级管理员角色、空白角色），系统保留角色不能删除
     * 4. 检查管理员是否删除自己的角色，不能删除自己的角色
     * 5. 将使用该角色的管理员换为空白角色
     * 6. 检查权限继承（只能删除权限范围 <= 自己权限的角色）
     * 7. 删除角色关联的权限配置记录
     * 8. 删除角色记录
     * 9. @Transactional注解保证所有操作原子性
     *
     * 【权限继承规则】
     * - 管理员只能删除权限范围 <= 自己权限的角色
     * - 超级管理员可以删除任意角色
     *
     * 【业务场景】
     * - 删除不再需要的角色
     * - 删除角色后，使用该角色的管理员自动换为空白角色
     *
     * 【参数】
     * - roleId: 角色ID（必填）
     * - operatorUserId: 操作管理员ID（必填）
     *
     * 【返回】
     * - 成功：{ "code": 2003, "message": "角色删除成功", "data": null }
     * - 失败：见【错误码】
     *
     * 【错误码】
     * - 4002 FORBIDDEN: 只有管理员才能删除角色
     * - 4003 NOT_FOUND: 角色不存在
     * - 4005 CONFLICT: 不能删除系统保留角色（超级管理员角色、空白角色）/ 不能删除自己的角色
     * - 6009 PERMISSION_DENIED: 权限不足，无法删除超出自己权限范围的角色
     */
    @Transactional
    public void deleteRole(String roleId, String operatorUserId) {
        logger.info("删除角色，roleId: {}, operatorUserId: {}", roleId, operatorUserId);

        if (!checkIsAdmin(operatorUserId)) {
            logger.warn("非管理员尝试删除角色，operatorUserId: {}", operatorUserId);
            throw new ForbiddenException("只有管理员才能删除角色");
        }

        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            logger.warn("角色不存在，roleId: {}", roleId);
            throw new NotFoundException("角色不存在");
        }

        if (isSystemReservedRole(role)) {
            logger.warn("不能删除系统保留角色，roleId: {}, roleCode: {}", roleId, role.getRoleCode());
            throw new ConflictException("不能删除系统保留角色（超级管理员角色、空白角色）");
        }

        Admin operatorAdmin = getAdminByUserId(operatorUserId);
        if (operatorAdmin != null && operatorAdmin.getRoleId() != null && operatorAdmin.getRoleId().equals(roleId)) {
            logger.warn("管理员不能删除自己的角色，operatorUserId: {}, roleId: {}", operatorUserId, roleId);
            throw new ConflictException("不能删除自己的角色");
        }

        if (!checkPermissionInheritanceForDelete(operatorUserId, roleId)) {
            logger.warn("管理员尝试删除超出自己权限范围的角色，operatorUserId: {}", operatorUserId);
            throw new PermissionDeniedException("权限不足，无法删除超出自己权限范围的角色");
        }

        String blankRoleId = getBlankRoleId();
        if (blankRoleId != null) {
            LambdaQueryWrapper<Admin> adminWrapper = new LambdaQueryWrapper<>();
            adminWrapper.eq(Admin::getRoleId, roleId);
            List<Admin> adminsWithThisRole = adminMapper.selectList(adminWrapper);
            for (Admin admin : adminsWithThisRole) {
                admin.setRoleId(blankRoleId);
                admin.setUpdateTime(LocalDateTime.now());
                adminMapper.updateById(admin);
                logger.info("已将管理员{}的角色从{}换为空白角色", admin.getUserId(), roleId);
            }
        }

        LambdaQueryWrapper<RolePermission> permissionWrapper = new LambdaQueryWrapper<>();
        permissionWrapper.eq(RolePermission::getRoleId, roleId);
        rolePermissionMapper.delete(permissionWrapper);

        roleMapper.deleteById(roleId);

        logger.info("角色删除成功，roleId: {}", roleId);
    }

    /**
     * ============================================
     * 检查是否为系统保留角色
     * ============================================
     *
     * 【系统保留角色】
     * - SUPER_ADMIN（超级管理员角色）
     * - BLANK（空白角色）
     *
     * 【参数】
     * - role: 角色对象
     *
     * 【返回】
     * - true: 是系统保留角色，不能删除
     * - false: 不是系统保留角色，可以删除
     */
    private boolean isSystemReservedRole(Role role) {
        if (role == null || role.getRoleCode() == null) {
            return false;
        }
        return "SUPER_ADMIN".equals(role.getRoleCode()) || "BLANK".equals(role.getRoleCode());
    }

    /**
     * ============================================
     * 根据userId获取Admin对象
     * ============================================
     */
    private Admin getAdminByUserId(String userId) {
        LambdaQueryWrapper<Admin> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Admin::getUserId, userId);
        return adminMapper.selectOne(wrapper);
    }

    /**
     * ============================================
     * 获取空白角色ID
     * ============================================
     */
    private String getBlankRoleId() {
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Role::getRoleCode, "BLANK");
        Role role = roleMapper.selectOne(wrapper);
        return role != null ? role.getId() : null;
    }

    /**
     * ============================================
     * 检查删除角色时的权限继承
     * ============================================
     *
     * 【后端逻辑】
     * 1. 如果是超级管理员，直接返回true
     * 2. 获取操作管理员的权限配置
     * 3. 获取目标角色的权限配置
     * 4. 逐项比较目标角色的权限是否都在操作管理员权限范围内
     *
     * 【参数】
     * - operatorUserId: 操作管理员ID
     * - roleId: 目标角色ID
     *
     * 【返回】
     * - true: 操作员有权删除该角色
     * - false: 操作员权限不足
     */
    private boolean checkPermissionInheritanceForDelete(String operatorUserId, String roleId) {
        // 超级管理员可以删除任意角色
        if (checkSuperAdmin(operatorUserId)) {
            return true;
        }

        // 获取操作管理员的权限配置
        RolePermission operatorPermission = getOperatorPermission(operatorUserId);
        if (operatorPermission == null) {
            logger.warn("操作管理员权限配置不存在，operatorUserId: {}", operatorUserId);
            return false;
        }

        // 获取目标角色的权限配置
        LambdaQueryWrapper<RolePermission> targetWrapper = new LambdaQueryWrapper<>();
        targetWrapper.eq(RolePermission::getRoleId, roleId);
        RolePermission targetPermission = rolePermissionMapper.selectOne(targetWrapper);

        if (targetPermission == null) {
            // 角色没有权限配置（可能还没设置过权限），允许删除
            return true;
        }

        // 逐项比较权限：目标角色的权限不能超过操作者权限
        if (comparePermission(targetPermission.getPermissionEnterpriseView(), operatorPermission.getPermissionEnterpriseView()) > 0) {
            return false;
        }
        if (comparePermission(targetPermission.getPermissionEnterpriseEdit(), operatorPermission.getPermissionEnterpriseEdit()) > 0) {
            return false;
        }
        if (comparePermission(targetPermission.getPermissionEnterpriseDelete(), operatorPermission.getPermissionEnterpriseDelete()) > 0) {
            return false;
        }
        if (comparePermission(targetPermission.getPermissionResearchView(), operatorPermission.getPermissionResearchView()) > 0) {
            return false;
        }
        if (comparePermission(targetPermission.getPermissionResearchEdit(), operatorPermission.getPermissionResearchEdit()) > 0) {
            return false;
        }
        if (comparePermission(targetPermission.getPermissionResearchDelete(), operatorPermission.getPermissionResearchDelete()) > 0) {
            return false;
        }
        if (comparePermission(targetPermission.getPermissionPatentAudit(), operatorPermission.getPermissionPatentAudit()) > 0) {
            return false;
        }
        if (comparePermission(targetPermission.getPermissionPatentReview(), operatorPermission.getPermissionPatentReview()) > 0) {
            return false;
        }
        if (comparePermission(targetPermission.getPermissionNoticePublish(), operatorPermission.getPermissionNoticePublish()) > 0) {
            return false;
        }
        if (comparePermission(targetPermission.getPermissionStatisticsView(), operatorPermission.getPermissionStatisticsView()) > 0) {
            return false;
        }
        if (comparePermission(targetPermission.getPermissionSystemConfig(), operatorPermission.getPermissionSystemConfig()) > 0) {
            return false;
        }
        if (comparePermission(targetPermission.getPermissionIntentionAudit(), operatorPermission.getPermissionIntentionAudit()) > 0) {
            return false;
        }
        if (comparePermission(targetPermission.getPermissionLogView(), operatorPermission.getPermissionLogView()) > 0) {
            return false;
        }

        return true;
    }

    /**
     * ============================================
     * 根据角色ID获取权限配置
     * ============================================
     *
     * 【后端逻辑】
     * 根据roleId查询角色的权限配置
     *
     * 【参数】
     * - roleId: 角色ID
     *
     * 【返回】
     * - RolePermission对象
     * - 如果不存在，返回null
     */
    public RolePermission getPermissionByRoleId(String roleId) {
        LambdaQueryWrapper<RolePermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RolePermission::getRoleId, roleId);
        return rolePermissionMapper.selectOne(wrapper);
    }

    /**
     * ============================================
     * 填充权限配置
     * ============================================
     *
     * 【后端逻辑】
     * 将Map中的权限值设置到RolePermission对象
     * 如果Map为null或某个权限项不存在，默认设为0
     *
     * 【参数】
     * - permission: 权限配置对象
     * - permissionMap: 权限Map（key为权限名，value为权限值0或1）
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
    private void fillPermission(RolePermission permission, Map<String, Integer> permissionMap) {
        if (permissionMap == null) {
            // 默认所有权限为0（无权限）
            permission.setPermissionEnterpriseView(0);
            permission.setPermissionEnterpriseEdit(0);
            permission.setPermissionEnterpriseDelete(0);
            permission.setPermissionResearchView(0);
            permission.setPermissionResearchEdit(0);
            permission.setPermissionResearchDelete(0);
            permission.setPermissionPatentAudit(0);
            permission.setPermissionPatentReview(0);
            permission.setPermissionNoticePublish(0);
            permission.setPermissionStatisticsView(0);
            permission.setPermissionSystemConfig(0);
            permission.setPermissionIntentionAudit(0);
            permission.setPermissionLogView(0);
            return;
        }

        // 从Map中获取权限值，如果不存在则默认0
        permission.setPermissionEnterpriseView(permissionMap.getOrDefault("permissionEnterpriseView", 0));
        permission.setPermissionEnterpriseEdit(permissionMap.getOrDefault("permissionEnterpriseEdit", 0));
        permission.setPermissionEnterpriseDelete(permissionMap.getOrDefault("permissionEnterpriseDelete", 0));
        permission.setPermissionResearchView(permissionMap.getOrDefault("permissionResearchView", 0));
        permission.setPermissionResearchEdit(permissionMap.getOrDefault("permissionResearchEdit", 0));
        permission.setPermissionResearchDelete(permissionMap.getOrDefault("permissionResearchDelete", 0));
        permission.setPermissionPatentAudit(permissionMap.getOrDefault("permissionPatentAudit", 0));
        permission.setPermissionPatentReview(permissionMap.getOrDefault("permissionPatentReview", 0));
        permission.setPermissionNoticePublish(permissionMap.getOrDefault("permissionNoticePublish", 0));
        permission.setPermissionStatisticsView(permissionMap.getOrDefault("permissionStatisticsView", 0));
        permission.setPermissionSystemConfig(permissionMap.getOrDefault("permissionSystemConfig", 0));
        permission.setPermissionIntentionAudit(permissionMap.getOrDefault("permissionIntentionAudit", 0));
        permission.setPermissionLogView(permissionMap.getOrDefault("permissionLogView", 0));
    }
}