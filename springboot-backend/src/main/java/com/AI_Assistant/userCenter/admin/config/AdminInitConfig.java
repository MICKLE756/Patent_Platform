package com.AI_Assistant.userCenter.admin.config;

import com.AI_Assistant.userCenter.admin.entity.Role;
import com.AI_Assistant.userCenter.admin.entity.RolePermission;
import com.AI_Assistant.userCenter.admin.mapper.RoleMapper;
import com.AI_Assistant.userCenter.admin.mapper.RolePermissionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import cn.hutool.core.util.IdUtil;
import java.time.LocalDateTime;

/**
 * 管理员模块初始化配置
 * 
 * 功能：
 * 1. 应用启动时初始化默认角色
 * 2. 创建空白角色（无任何权限）
 * 3. 创建超级管理员角色（拥有所有权限）
 * 
 * 注意：只在首次启动时创建，已存在则跳过
 */
@Component
public class AdminInitConfig implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(AdminInitConfig.class);

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private RolePermissionMapper rolePermissionMapper;

    /**
     * 空白角色常量
     */
    public static final String BLANK_ROLE_CODE = "BLANK";
    public static final String BLANK_ROLE_NAME = "空白角色";

    /**
     * 超级管理员角色常量
     */
    public static final String SUPER_ADMIN_ROLE_CODE = "SUPER_ADMIN";
    public static final String SUPER_ADMIN_ROLE_NAME = "超级管理员";

    @Override
    public void run(String... args) throws Exception {
        logger.info("开始初始化管理员模块默认角色...");
        
        // 1. 创建空白角色（无任何权限）
        createBlankRole();
        
        // 2. 创建超级管理员角色（拥有所有权限）
        createSuperAdminRole();
        
        logger.info("管理员模块默认角色初始化完成");
    }

    /**
     * 创建空白角色（无任何权限）
     * 新创建的管理员默认分配此角色
     */
    private void createBlankRole() {
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Role::getRoleCode, BLANK_ROLE_CODE);
        
        if (roleMapper.exists(wrapper)) {
            logger.info("空白角色已存在，跳过创建");
            return;
        }

        // 创建角色
        Role role = new Role();
        role.setId(IdUtil.simpleUUID());
        role.setRoleName(BLANK_ROLE_NAME);
        role.setRoleCode(BLANK_ROLE_CODE);
        role.setDescription("空白角色，无任何权限，新管理员默认角色");
        role.setStatus(1);
        role.setCreateTime(LocalDateTime.now());
        role.setUpdateTime(LocalDateTime.now());
        roleMapper.insert(role);

        // 创建权限配置（所有权限为0）
        RolePermission permission = new RolePermission();
        permission.setId(IdUtil.simpleUUID());
        permission.setRoleId(role.getId());
        // 所有权限默认设为0（无权限）
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
        permission.setCreateTime(LocalDateTime.now());
        permission.setUpdateTime(LocalDateTime.now());
        rolePermissionMapper.insert(permission);

        logger.info("空白角色创建成功，roleId: {}", role.getId());
    }

    /**
     * 创建超级管理员角色（拥有所有权限）
     * 系统最高权限角色
     */
    private void createSuperAdminRole() {
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Role::getRoleCode, SUPER_ADMIN_ROLE_CODE);
        
        if (roleMapper.exists(wrapper)) {
            logger.info("超级管理员角色已存在，跳过创建");
            return;
        }

        // 创建角色
        Role role = new Role();
        role.setId(IdUtil.simpleUUID());
        role.setRoleName(SUPER_ADMIN_ROLE_NAME);
        role.setRoleCode(SUPER_ADMIN_ROLE_CODE);
        role.setDescription("超级管理员，拥有所有权限");
        role.setStatus(1);
        role.setCreateTime(LocalDateTime.now());
        role.setUpdateTime(LocalDateTime.now());
        roleMapper.insert(role);

        // 创建权限配置（所有权限为1）
        RolePermission permission = new RolePermission();
        permission.setId(IdUtil.simpleUUID());
        permission.setRoleId(role.getId());
        // 所有权限设为1（有权限）
        permission.setPermissionEnterpriseView(1);
        permission.setPermissionEnterpriseEdit(1);
        permission.setPermissionEnterpriseDelete(1);
        permission.setPermissionResearchView(1);
        permission.setPermissionResearchEdit(1);
        permission.setPermissionResearchDelete(1);
        permission.setPermissionPatentAudit(1);
        permission.setPermissionPatentReview(1);
        permission.setPermissionNoticePublish(1);
        permission.setPermissionStatisticsView(1);
        permission.setPermissionSystemConfig(1);
        permission.setPermissionIntentionAudit(1);
        permission.setPermissionLogView(1);
        permission.setCreateTime(LocalDateTime.now());
        permission.setUpdateTime(LocalDateTime.now());
        rolePermissionMapper.insert(permission);

        logger.info("超级管理员角色创建成功，roleId: {}", role.getId());
    }

    /**
     * 获取空白角色ID
     */
    public static String getBlankRoleId(RoleMapper roleMapper) {
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Role::getRoleCode, BLANK_ROLE_CODE);
        Role role = roleMapper.selectOne(wrapper);
        return role != null ? role.getId() : null;
    }

    /**
     * 获取超级管理员角色ID
     */
    public static String getSuperAdminRoleId(RoleMapper roleMapper) {
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Role::getRoleCode, SUPER_ADMIN_ROLE_CODE);
        Role role = roleMapper.selectOne(wrapper);
        return role != null ? role.getId() : null;
    }
}