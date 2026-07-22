package com.AI_Assistant.userCenter.admin.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.AI_Assistant.common.constant.RedisConstants;
import com.AI_Assistant.common.exception.ConflictException;
import com.AI_Assistant.common.exception.ForbiddenException;
import com.AI_Assistant.common.exception.InternalServerException;
import com.AI_Assistant.common.exception.NotFoundException;
import com.AI_Assistant.common.exception.PermissionDeniedException;
import com.AI_Assistant.common.util.UserCacheService;
import com.AI_Assistant.userCenter.admin.dto.AdminAccount;
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
 * 管理员账号服务
 * ============================================
 *
 * 【服务职责】
 * 本服务负责管理员账号的增删改查操作，包括：
 * 1. 管理员账号的创建（将普通用户升级为管理员）
 * 2. 管理员账号的注销（保留用户，降级为普通用户）
 * 3. 管理员信息的更新（姓名、角色、状态）
 * 4. 权限继承检查（确保管理员只能分配自己拥有的权限）
 *
 * 【核心概念】
 * - 用户类型(userType)：0=普通用户，3=管理员
 * - 空白角色(BLANK)：所有权限为0，默认分配给新管理员
 * - 超级管理员(SUPER_ADMIN)：所有权限为1，系统最高权限
 *
 * 【数据库表】
 * - user表：存储用户基础信息（用户名、密码、用户类型等）
 * - admin表：存储管理员扩展信息（真实姓名、角色ID等）
 * - role表：存储角色信息
 * - role_permission表：存储角色权限配置
 */
@Service
public class AdminService {

    private static final Logger logger = LoggerFactory.getLogger(AdminService.class);

    @Autowired
    private AdminMapper adminMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private RolePermissionMapper rolePermissionMapper;

    @Autowired
    private UserCacheService userCacheService;

    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    /**
     * ============================================
     * 获取管理员账号信息（包含绑定的角色信息）
     * ============================================
     *
     * 【后端逻辑】
     * 1. 根据userId查询用户基础信息
     * 2. 查询用户的管理员扩展信息
     * 3. 组装完整的AdminAccount对象
     *
     * 【业务场景】
     * - 用户登录后获取个人信息
     * - 管理员查看自己的账号信息
     *
     * 【参数】
     * - userId: 用户ID
     *
     * 【返回】
     * - AdminAccount对象，包含用户基础信息和管理员扩展信息
     * - 如果用户不存在或不是管理员，返回null
     */
    public AdminAccount getAdminAccount(String userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return null;
        }

        LambdaQueryWrapper<Admin> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Admin::getUserId, userId);
        Admin admin = adminMapper.selectOne(wrapper);

        return buildAdminAccount(user, admin);
    }

    /**
     * ============================================
     * 根据OpenID获取管理员账号信息
     * ============================================
     *
     * 【后端逻辑】
     * 1. 根据OpenID查询用户
     * 2. 查询用户的管理员扩展信息
     * 3. 组装完整的AdminAccount对象
     *
     * 【业务场景】
     * - 微信扫码登录后获取管理员信息
     *
     * 【参数】
     * - openid: 微信OpenID
     *
     * 【返回】
     * - AdminAccount对象
     * - 如果用户不存在，返回null
     */
    public AdminAccount getAdminAccountByOpenid(String openid) {
        LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.eq(User::getWechatOpenid, openid);
        User user = userMapper.selectOne(userWrapper);
        if (user == null) {
            return null;
        }

        LambdaQueryWrapper<Admin> adminWrapper = new LambdaQueryWrapper<>();
        adminWrapper.eq(Admin::getUserId, user.getId());
        Admin admin = adminMapper.selectOne(adminWrapper);

        return buildAdminAccount(user, admin);
    }

    /**
     * ============================================
     * 获取所有管理员用户列表
     * ============================================
     *
     * 【后端逻辑】
     * 1. 查询所有userType=3的用户
     * 2. 批量查询这些用户的管理员扩展信息
     * 3. 组装完整的AdminAccount列表
     *
     * 【业务场景】
     * - 管理员管理页面展示所有管理员列表
     *
     * 【参数】
     * - 无
     *
     * 【返回】
     * - List<AdminAccount> 管理员账号列表
     */
    public List<AdminAccount> getAllAdminUsers() {
        logger.info("查询所有管理员账号列表");

        // 1. 查询所有管理员类型的用户
        LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.eq(User::getUserType, 3);
        List<User> users = userMapper.selectList(userWrapper);

        if (users == null || users.isEmpty()) {
            logger.info("未找到管理员用户");
            return new ArrayList<>();
        }

        // 2. 批量查询管理员扩展信息
        List<String> userIds = users.stream().map(User::getId).toList();
        LambdaQueryWrapper<Admin> adminWrapper = new LambdaQueryWrapper<>();
        adminWrapper.in(Admin::getUserId, userIds);
        List<Admin> admins = adminMapper.selectList(adminWrapper);

        // 3. 将 admins 转换为 Map，优化查询性能
        Map<String, Admin> adminMap = admins.stream()
                .collect(java.util.stream.Collectors.toMap(Admin::getUserId, a -> a));

        // 4. 组装结果
        List<AdminAccount> result = new ArrayList<>();
        for (User user : users) {
            Admin admin = adminMap.get(user.getId());
            result.add(buildAdminAccount(user, admin));
        }

        logger.info("查询到管理员用户数量: {}", result.size());
        return result;
    }

    /**
     * ============================================
     * 获取单个管理员用户
     * ============================================
     *
     * 【后端逻辑】
     * 1. 根据adminUserId查询用户
     * 2. 验证用户是否存在且为管理员类型
     * 3. 查询管理员扩展信息
     * 4. 组装AdminAccount对象
     *
     * 【参数】
     * - adminUserId: 管理员用户ID
     *
     * 【返回】
     * - AdminAccount对象
     * - 如果用户不存在或不是管理员，返回null
     */
    public AdminAccount getAdminUserById(String adminUserId) {
        logger.info("查询单个管理员用户，adminUserId: {}", adminUserId);

        User user = userMapper.selectById(adminUserId);
        if (user == null) {
            logger.warn("用户不存在，adminUserId: {}", adminUserId);
            return null;
        }

        if (user.getUserType() == null || user.getUserType() != 3) {
            logger.warn("该用户不是管理员用户，adminUserId: {}", adminUserId);
            return null;
        }

        LambdaQueryWrapper<Admin> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Admin::getUserId, adminUserId);
        Admin admin = adminMapper.selectOne(wrapper);

        return buildAdminAccount(user, admin);
    }

    /**
     * ============================================
     * 创建管理员用户（将已有用户升级为管理员）
     * ============================================
     *
     * 【后端逻辑】
     * 1. 验证操作管理员身份（必须是已登录的管理员）
     * 2. 检查被升级用户是否已存在（必须是已注册的普通用户）
     * 3. 如果未指定角色ID，默认分配空白角色（BLANK）
     * 4. 如果指定了角色，验证角色存在并检查权限继承
     * 5. 将用户类型从普通用户(0)升级为管理员(3)
     * 6. 创建管理员扩展信息记录（admin表）
     * 7. @Transactional注解保证操作的原子性
     *
     * 【权限继承规则】
     * - 管理员只能分配自己拥有的权限
     * - 目标角色的每个权限值都不能超过操作管理员的对应权限
     * - 超级管理员可以分配任何角色
     *
     * 【参数】
     * - operatorUserId: 操作管理员ID（必填，当前登录的管理员）
     * - username: 被升级用户的用户名（必填）
     * - realName: 真实姓名（可选）
     * - roleId: 角色ID（可选，不传则默认空白角色）
     *
     * 【返回】
     * - 成功：{ "code": 2001, "message": "管理员创建成功", "data": AdminAccount对象 }
     * - 失败：见【错误码】
     *
     * 【错误码】
     * - 4002 FORBIDDEN: 只有管理员才能创建管理员
     * - 4003 NOT_FOUND: 用户不存在或用户名错误
     * - 4005 CONFLICT: 该用户已是管理员
     * - 6009 PERMISSION_DENIED: 权限不足，无法分配该角色
     */
    @Transactional
    public AdminAccount createAdminUser(String operatorUserId, String username, String realName, String roleId) {
        logger.info("管理员创建新管理员，operatorUserId: {}, username: {}, realName: {}, roleId: {}",
                operatorUserId, username, realName, roleId);

        User operator = userMapper.selectById(operatorUserId);
        if (operator == null) {
            logger.warn("操作管理员不存在，operatorUserId: {}", operatorUserId);
            throw new ForbiddenException("操作管理员不存在");
        }

        if (operator.getUserType() == null || operator.getUserType() != 3) {
            logger.warn("非管理员用户尝试创建管理员，operatorUserId: {}, userType: {}",
                    operatorUserId, operator.getUserType());
            throw new ForbiddenException("只有管理员才能创建管理员账号");
        }

        LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.eq(User::getUsername, username);
        if (!userMapper.exists(userWrapper)) {
            logger.warn("用户不存在: {}", username);
            throw new NotFoundException("用户不存在，请先注册普通用户");
        }

        String finalRoleId = roleId;

        if (roleId == null || roleId.isEmpty()) {
            finalRoleId = getBlankRoleId();
            if (finalRoleId == null) {
                logger.error("空白角色不存在，请检查系统初始化");
                throw new InternalServerException("系统初始化异常，空白角色不存在");
            }
            logger.info("未指定角色，默认分配空白角色，roleId: {}", finalRoleId);
        } else {
            Role role = roleMapper.selectById(roleId);
            if (role == null) {
                logger.warn("角色不存在，roleId: {}", roleId);
                throw new NotFoundException("角色不存在");
            }

            boolean hasPermission = checkPermissionInheritance(operatorUserId, roleId);
            if (!hasPermission) {
                logger.warn("操作管理员权限不足，无法分配该角色，operatorUserId: {}, roleId: {}",
                        operatorUserId, roleId);
                throw new PermissionDeniedException("权限不足，无法分配该角色");
            }
        }

        User existingUser = findUserByUsername(username);

        if (existingUser != null) {
            logger.info("用户已存在，升级为管理员，userId: {}", existingUser.getId());

            if (existingUser.getUserType() == 3) {
                logger.warn("用户已是管理员，无需重复创建，userId: {}", existingUser.getId());
                throw new ConflictException("该用户已是管理员");
            }

            existingUser.setUserType(3);
            existingUser.setUpdateTime(LocalDateTime.now());
            userMapper.updateById(existingUser);

            userCacheService.updateFullUserCache(existingUser);

            Admin admin = new Admin();
            admin.setId(IdUtil.simpleUUID());
            admin.setUserId(existingUser.getId());
            admin.setRealName(realName);
            admin.setRoleId(finalRoleId);
            admin.setCreateTime(LocalDateTime.now());
            admin.setUpdateTime(LocalDateTime.now());
            adminMapper.insert(admin);

            logger.info("管理员用户升级成功，userId: {}", existingUser.getId());
            return buildAdminAccount(existingUser, admin);
        } else {
            logger.warn("用户不存在，不允许直接创建管理员账号，请先注册普通用户", username);
            throw new NotFoundException("用户不存在，请先注册普通用户");
        }
    }

    /**
     * ============================================
     * 注销管理员身份（保留用户基础信息）
     * ============================================
     *
     * 【后端逻辑】
     * 1. 验证用户是否存在且为管理员
     * 2. 删除管理员扩展信息（admin表记录）
     * 3. 将用户类型从管理员(3)降级为普通用户(0)
     * 4. 保留用户的基础信息（用户名、密码等）
     * 5. @Transactional注解保证操作的原子性
     *
     * 【业务场景】
     * - 管理员主动注销自己的管理员身份
     * - 超级管理员删除某个管理员
     *
     * 【参数】
     * - adminUserId: 要注销的管理员用户ID（必填）
     *
     * 【返回】
     * - 成功：{ "code": 2003, "message": "注销管理员身份成功", "data": null }
     * - 失败：见【错误码】
     *
     * 【错误码】
     * - 4003 NOT_FOUND: 用户不存在
     * - 4005 CONFLICT: 该用户不是管理员用户
     */
    @Transactional
    public void deleteAdminUser(String adminUserId) {
        logger.info("注销管理员身份，adminUserId: {}", adminUserId);

        User user = userMapper.selectById(adminUserId);
        if (user == null) {
            logger.warn("用户不存在，adminUserId: {}", adminUserId);
            throw new NotFoundException("用户不存在");
        }

        if (user.getUserType() == null || user.getUserType() != 3) {
            logger.warn("该用户不是管理员用户，adminUserId: {}", adminUserId);
            throw new ConflictException("该用户不是管理员用户");
        }

        LambdaQueryWrapper<Admin> adminWrapper = new LambdaQueryWrapper<>();
        adminWrapper.eq(Admin::getUserId, adminUserId);
        adminMapper.delete(adminWrapper);

        user.setUserType(0);
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);

        userCacheService.updateFullUserCache(user);

        logger.info("管理员身份注销成功，用户类型已改为普通用户，adminUserId: {}", adminUserId);
    }

    /**
     * ============================================
     * 更新管理员用户信息
     * ============================================
     *
     * 【后端逻辑】
     * 1. 验证操作管理员身份（必须是已登录的管理员）
     * 2. 验证被更新的管理员是否存在且为管理员类型
     * 3. 更新用户状态（如果指定）
     * 4. 如果要修改角色，检查权限继承（只能分配自己拥有的权限）
     * 5. 更新管理员扩展信息（真实姓名、角色等）
     * 6. @Transactional注解保证操作的原子性
     *
     * 【参数】
     * - adminUserId: 被修改的管理员ID（必填）
     * - realName: 真实姓名（可选）
     * - roleId: 角色ID（可选）
     * - status: 状态（可选，1=启用，0=禁用）
     * - operatorUserId: 操作管理员ID（必填）
     *
     * 【返回】
     * - 成功：{ "code": 2002, "message": "管理员信息更新成功", "data": AdminAccount对象 }
     * - 失败：见【错误码】
     *
     * 【错误码】
     * - 4002 FORBIDDEN: 操作管理员不存在
     * - 4003 NOT_FOUND: 用户不存在或该用户不是管理员用户
     * - 6009 PERMISSION_DENIED: 权限不足，无法分配该角色
     */
    @Transactional
    public AdminAccount updateAdminUser(String adminUserId, String realName, String roleId, Integer status, String operatorUserId) {
        logger.info("更新管理员用户信息，adminUserId: {}, operatorUserId: {}", adminUserId, operatorUserId);

        User operator = userMapper.selectById(operatorUserId);
        if (operator == null) {
            logger.warn("操作管理员不存在，operatorUserId: {}", operatorUserId);
            throw new ForbiddenException("操作管理员不存在");
        }

        if (operator.getUserType() == null || operator.getUserType() != 3) {
            logger.warn("非管理员用户尝试更新管理员，operatorUserId: {}, userType: {}",
                    operatorUserId, operator.getUserType());
            throw new ForbiddenException("只有管理员才能更新管理员账号");
        }

        User user = userMapper.selectById(adminUserId);
        if (user == null) {
            logger.warn("用户不存在，adminUserId: {}", adminUserId);
            throw new NotFoundException("用户不存在");
        }

        if (user.getUserType() == null || user.getUserType() != 3) {
            logger.warn("该用户不是管理员用户，adminUserId: {}", adminUserId);
            throw new NotFoundException("该用户不是管理员用户");
        }

        if (status != null) {
            user.setStatus(status);
            if (status == 0) {
                clearUserLoginCache(adminUserId);
            }
        }
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);

        LambdaQueryWrapper<Admin> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Admin::getUserId, adminUserId);
        Admin admin = adminMapper.selectOne(wrapper);

        if (admin != null) {
            if (realName != null) {
                admin.setRealName(realName);
            }

            if (roleId != null) {
                Role role = roleMapper.selectById(roleId);
                if (role == null) {
                    logger.warn("角色不存在，roleId: {}", roleId);
                    throw new NotFoundException("角色不存在");
                }

                boolean hasPermission = checkPermissionInheritance(operatorUserId, roleId);
                if (!hasPermission) {
                    logger.warn("操作管理员权限不足，无法分配该角色，operatorUserId: {}, roleId: {}",
                            operatorUserId, roleId);
                    throw new PermissionDeniedException("权限不足，无法分配该角色");
                }

                admin.setRoleId(roleId);
            }

            admin.setUpdateTime(LocalDateTime.now());
            adminMapper.updateById(admin);
        }

        userCacheService.updateFullUserCache(user);

        logger.info("管理员用户信息更新成功，adminUserId: {}", adminUserId);
        return buildAdminAccount(user, admin);
    }

    /**
     * ============================================
     * 检查权限继承：操作管理员是否有权限分配指定角色
     * ============================================
     *
     * 【后端逻辑】
     * 1. 获取操作管理员的角色和权限配置
     * 2. 如果操作员是超级管理员，直接返回true
     * 3. 获取目标角色的权限配置
     * 4. 逐项比较目标角色的每个权限是否都在操作管理员权限范围内
     * 5. 只有所有权限都小于等于操作管理员权限时，才返回true
     *
     * 【权限比较规则】
     * - 权限值为0表示无此权限，1表示有此权限
     * - 目标角色的每个权限都不能超过操作管理员的对应权限
     * - 例如：操作员有permissionEnterpriseView=1, permissionEnterpriseEdit=0
     *         则只能分配permissionEnterpriseView<=1且permissionEnterpriseEdit<=0的角色
     *
     * 【参数】
     * - operatorUserId: 操作管理员ID
     * - targetRoleId: 目标角色ID
     *
     * 【返回】
     * - true: 操作管理员有权限分配该角色
     * - false: 操作管理员权限不足，无法分配该角色
     */
    private boolean checkPermissionInheritance(String operatorUserId, String targetRoleId) {
        try {
            // ========== 步骤1：获取操作管理员的角色权限 ==========
            LambdaQueryWrapper<Admin> adminWrapper = new LambdaQueryWrapper<>();
            adminWrapper.eq(Admin::getUserId, operatorUserId);
            Admin operatorAdmin = adminMapper.selectOne(adminWrapper);

            if (operatorAdmin == null || operatorAdmin.getRoleId() == null) {
                logger.warn("操作管理员未分配角色，operatorUserId: {}", operatorUserId);
                return false;
            }

            // 检查是否为超级管理员（超级管理员不受权限限制）
            LambdaQueryWrapper<Role> superAdminWrapper = new LambdaQueryWrapper<>();
            superAdminWrapper.eq(Role::getRoleCode, "SUPER_ADMIN");
            Role superAdminRole = roleMapper.selectOne(superAdminWrapper);

            if (superAdminRole != null && superAdminRole.getId().equals(operatorAdmin.getRoleId())) {
                // 超级管理员可以分配任何角色
                return true;
            }

            // ========== 步骤2：获取操作管理员的权限配置 ==========
            LambdaQueryWrapper<RolePermission>
                operatorPermWrapper = new LambdaQueryWrapper<>();
            operatorPermWrapper.eq(RolePermission::getRoleId, operatorAdmin.getRoleId());
            RolePermission operatorPermission = rolePermissionMapper.selectOne(operatorPermWrapper);

            if (operatorPermission == null) {
                logger.warn("操作管理员权限配置不存在，operatorUserId: {}", operatorUserId);
                return false;
            }

            // ========== 步骤3：获取目标角色的权限配置 ==========
            LambdaQueryWrapper<RolePermission>
                targetPermWrapper = new LambdaQueryWrapper<>();
            targetPermWrapper.eq(RolePermission::getRoleId, targetRoleId);
            RolePermission targetPermission = rolePermissionMapper.selectOne(targetPermWrapper);

            if (targetPermission == null) {
                logger.warn("目标角色权限配置不存在，targetRoleId: {}", targetRoleId);
                return false;
            }

            // ========== 步骤4：逐项比较权限 ==========
            // 比较企业相关权限
            if (targetPermission.getPermissionEnterpriseView() > operatorPermission.getPermissionEnterpriseView()) {
                logger.warn("操作管理员缺少企业查看权限");
                return false;
            }
            if (targetPermission.getPermissionEnterpriseEdit() > operatorPermission.getPermissionEnterpriseEdit()) {
                logger.warn("操作管理员缺少企业编辑权限");
                return false;
            }
            if (targetPermission.getPermissionEnterpriseDelete() > operatorPermission.getPermissionEnterpriseDelete()) {
                logger.warn("操作管理员缺少企业删除权限");
                return false;
            }

            // 比较科研团队相关权限
            if (targetPermission.getPermissionResearchView() > operatorPermission.getPermissionResearchView()) {
                logger.warn("操作管理员缺少科研团队查看权限");
                return false;
            }
            if (targetPermission.getPermissionResearchEdit() > operatorPermission.getPermissionResearchEdit()) {
                logger.warn("操作管理员缺少科研团队编辑权限");
                return false;
            }
            if (targetPermission.getPermissionResearchDelete() > operatorPermission.getPermissionResearchDelete()) {
                logger.warn("操作管理员缺少科研团队删除权限");
                return false;
            }

            // 比较专利相关权限
            if (targetPermission.getPermissionPatentAudit() > operatorPermission.getPermissionPatentAudit()) {
                logger.warn("操作管理员缺少专利审核权限");
                return false;
            }
            if (targetPermission.getPermissionPatentReview() > operatorPermission.getPermissionPatentReview()) {
                logger.warn("操作管理员缺少专利查看权限");
                return false;
            }

            // 比较其他权限
            if (targetPermission.getPermissionNoticePublish() > operatorPermission.getPermissionNoticePublish()) {
                logger.warn("操作管理员缺少通知发布权限");
                return false;
            }
            if (targetPermission.getPermissionStatisticsView() > operatorPermission.getPermissionStatisticsView()) {
                logger.warn("操作管理员缺少统计查看权限");
                return false;
            }
            if (targetPermission.getPermissionSystemConfig() > operatorPermission.getPermissionSystemConfig()) {
                logger.warn("操作管理员缺少系统配置权限");
                return false;
            }
            if (targetPermission.getPermissionIntentionAudit() > operatorPermission.getPermissionIntentionAudit()) {
                logger.warn("操作管理员缺少意向审核权限");
                return false;
            }
            if (targetPermission.getPermissionLogView() > operatorPermission.getPermissionLogView()) {
                logger.warn("操作管理员缺少日志查看权限");
                return false;
            }

            return true;

        } catch (Exception e) {
            logger.error("检查权限继承失败，operatorUserId: {}, targetRoleId: {}",
                    operatorUserId, targetRoleId, e);
            return false;
        }
    }

    /**
     * 根据用户名查找用户
     */
    private User findUserByUsername(String username) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username);
        return userMapper.selectOne(wrapper);
    }

    /**
     * 获取空白角色ID
     */
    private String getBlankRoleId() {
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Role::getRoleCode, "BLANK");
        Role role = roleMapper.selectOne(wrapper);
        return role != null ? role.getId() : null;
    }

    /**
     * ============================================
     * 构建AdminAccount对象
     * ============================================
     *
     * 【后端逻辑】
     * 将User实体和Admin实体合并为AdminAccount DTO
     *
     * 【参数】
     * - user: 用户基础信息
     * - admin: 管理员扩展信息（可能为null）
     *
     * 【返回】
     * - AdminAccount对象
     */
    private AdminAccount buildAdminAccount(User user, Admin admin) {
        return AdminAccount.builder()
                .id(user.getId())
                .username(user.getUsername())
                .userType(user.getUserType())
                .wechatNickname(user.getWechatNickname())
                .wechatAvatar(user.getWechatAvatar())
                .contactPhone(user.getContactPhone())
                .contactEmail(user.getContactEmail())
                .status(user.getStatus())
                .createTime(user.getCreateTime())
                .lastLoginTime(user.getLastLoginTime())
                .realName(admin != null ? admin.getRealName() : null)
                .roleId(admin != null ? admin.getRoleId() : null)
                .build();
    }

    /**
     * ============================================
     * 获取管理员完整权限信息（用于调试）
     * ============================================
     * 返回包含角色名称和所有权限的Map
     */
    public Map<String, Object> getAdminPermissionsInfo(String userId) {
        try {
            User user = userMapper.selectById(userId);
            if (user == null) {
                logger.warn("用户不存在，userId: {}", userId);
                return null;
            }

            LambdaQueryWrapper<Admin> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Admin::getUserId, userId);
            Admin admin = adminMapper.selectOne(wrapper);

            Map<String, Object> result = new java.util.HashMap<>();
            result.put("userId", userId);
            result.put("username", user.getUsername());
            result.put("realName", admin != null ? admin.getRealName() : null);
            result.put("roleId", admin != null ? admin.getRoleId() : null);

            // 查询角色名称和权限
            if (admin != null && admin.getRoleId() != null) {
                Role role = roleMapper.selectById(admin.getRoleId());
                if (role != null) {
                    result.put("roleName", role.getRoleName());
                    result.put("roleCode", role.getRoleCode());

                    // 查询角色权限
                    LambdaQueryWrapper<RolePermission> permWrapper = new LambdaQueryWrapper<>();
                    permWrapper.eq(RolePermission::getRoleId, admin.getRoleId());
                    RolePermission permission = rolePermissionMapper.selectOne(permWrapper);
                    if (permission != null) {
                        result.put("permissionEnterpriseView", permission.getPermissionEnterpriseView());
                        result.put("permissionEnterpriseEdit", permission.getPermissionEnterpriseEdit());
                        result.put("permissionEnterpriseDelete", permission.getPermissionEnterpriseDelete());
                        result.put("permissionResearchView", permission.getPermissionResearchView());
                        result.put("permissionResearchEdit", permission.getPermissionResearchEdit());
                        result.put("permissionResearchDelete", permission.getPermissionResearchDelete());
                        result.put("permissionPatentAudit", permission.getPermissionPatentAudit());
                        result.put("permissionPatentReview", permission.getPermissionPatentReview());
                        result.put("permissionNoticePublish", permission.getPermissionNoticePublish());
                        result.put("permissionStatisticsView", permission.getPermissionStatisticsView());
                        result.put("permissionSystemConfig", permission.getPermissionSystemConfig());
                        result.put("permissionIntentionAudit", permission.getPermissionIntentionAudit());
                        result.put("permissionLogView", permission.getPermissionLogView());
                    }
                }
            }

            return result;
        } catch (Exception e) {
            logger.error("获取管理员权限信息失败，userId: {}", userId, e);
            return null;
        }
    }

    /**
     * 清除用户的所有 Redis 登录缓存
     * 当用户被禁用时调用，强制用户下线
     */
    private void clearUserLoginCache(String userId) {
        try {
            // 1. 获取 userId→token 映射
            String userTokenKey = RedisConstants.LOGIN_USER_PREFIX + userId;
            String token = redisTemplate.opsForValue().get(userTokenKey);
            
            // 2. 删除 token 缓存
            if (token != null && !token.isEmpty()) {
                String tokenKey = RedisConstants.LOGIN_TOKEN_PREFIX + token;
                redisTemplate.delete(tokenKey);
                logger.info("已删除用户 token 缓存，userId: {}, token: {}", userId, token);
            }
            
            // 3. 删除 userId→token 映射
            redisTemplate.delete(userTokenKey);
            
            logger.info("已清除被禁用用户的登录缓存，userId: {}", userId);
        } catch (Exception e) {
            logger.error("清除用户登录缓存失败，userId: {}", userId, e);
        }
    }
}