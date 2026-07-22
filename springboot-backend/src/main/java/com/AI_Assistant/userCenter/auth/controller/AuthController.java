package com.AI_Assistant.userCenter.auth.controller;

import java.time.LocalDateTime;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.AI_Assistant.backend.statistics.service.StatisticsService;
import com.AI_Assistant.common.Result;
import com.AI_Assistant.common.exception.BadRequestException;
import com.AI_Assistant.common.exception.UserDisabledException;
import com.AI_Assistant.common.util.UserCacheService;
import com.AI_Assistant.common.util.UserContext;
import com.AI_Assistant.userCenter.admin.service.AdminService;
import com.AI_Assistant.userCenter.auth.config.WechatConfig;
import com.AI_Assistant.userCenter.auth.dto.LoginResponse;
import com.AI_Assistant.userCenter.auth.service.TokenService;
import com.AI_Assistant.userCenter.auth.service.WechatLoginService;
import com.AI_Assistant.userCenter.enterprise.service.EnterpriseService;
import com.AI_Assistant.userCenter.researchTeam.service.ResearchTeamService;
import com.AI_Assistant.userCenter.user.dto.UserFullInfoDTO;
import com.AI_Assistant.userCenter.user.entity.User;
import com.AI_Assistant.userCenter.user.mapper.UserMapper;
import com.AI_Assistant.userCenter.user.service.UserService;

import cn.hutool.json.JSONUtil;

@RestController
@RequestMapping("/api/v1")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private WechatLoginService wechatLoginService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserService userService;

    @Autowired
    private EnterpriseService enterpriseService;

    @Autowired
    private ResearchTeamService researchTeamService;

    @Autowired
    private AdminService adminService;

    @Autowired
    private WechatConfig wechatConfig;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private StatisticsService statisticsService;

    @Autowired
    private UserCacheService userCacheService;

    @PostMapping("/auth/wechat-login")
    public Result<?> wechatLogin(@RequestBody Map<String, String> request) {
        String code = request.get("code");
        logger.info("收到微信登录请求，code: {}", code != null ? "***" : "null");

        if (code == null || code.isEmpty()) {
            logger.error("登录失败：code为空");
            throw new BadRequestException("登录失败：缺少code参数");
        }

        String openid = wechatLoginService.getOpenidFromWechat(code);
        if (openid == null) {
            logger.error("登录失败：微信API调用失败，无法获取openid");
            throw new BadRequestException("微信登录失败");
        }

        logger.info("获取到openid: {}", openid);

        User user = userService.getUserByOpenid(openid);
        boolean isNewUser = false;
        if (user == null) {
            logger.info("用户不存在，已创建用户");
            user = wechatLoginService.createNewUser(openid);
            isNewUser = true;
        } else {
            logger.info("用户已存在，用户信息：" + JSONUtil.toJsonStr(user));
            
            if (!userService.isUserEnabled(user)) {
                logger.warn("用户账号已被禁用，userId: {}", user.getId());
                throw new UserDisabledException("用户账号已被禁用");
            }
        }

        // 更新最后登录时间（用于统计活跃用户）
        user.setLastLoginTime(LocalDateTime.now());
        userMapper.updateById(user);

        // 记录统计数据
        if (isNewUser) {
            statisticsService.recordNewUser();
        }
        statisticsService.recordUserLogin(user.getId());

        Object account = getAccountByUserType(user);
        String token = tokenService.generateToken(user);

        // 构建用户完整信息（包含企业/科研团队额外信息、已绑定专利列表）
        UserFullInfoDTO fullInfo = UserFullInfoDTO.builder()
                .user(user)
                .build();
        
        Integer userType = user.getUserType();
        if (userType != null) {
            switch (userType) {
                case 1 -> fullInfo.setEnterpriseAccount((com.AI_Assistant.userCenter.enterprise.dto.EnterpriseAccount) account);
                case 2 -> {
                    com.AI_Assistant.userCenter.researchTeam.dto.ResearchTeamAccount teamAccount = 
                            (com.AI_Assistant.userCenter.researchTeam.dto.ResearchTeamAccount) account;
                    fullInfo.setResearchTeamAccount(teamAccount);
                    if (teamAccount != null && teamAccount.getPatents() != null) {
                        fullInfo.setBoundPatents(teamAccount.getPatents());
                        java.util.List<String> boundPatentIds = teamAccount.getPatents().stream()
                                .map(com.AI_Assistant.backend.patent.dto.PatentDTO::getPatentId)
                                .collect(java.util.stream.Collectors.toList());
                        fullInfo.setBoundPatentIds(boundPatentIds);
                    }
                }
                default -> {}
            }
        }
        
        // 缓存用户完整信息
        userCacheService.buildAndCacheFullUserInfo(fullInfo, token);

        LoginResponse response = LoginResponse.builder()
                .token(token)
                .userId(user.getId())
                .userType(user.getUserType())
                .account(account)
                .build();

        logger.info("登录成功，userId: {}, userType: {}", user.getId(), user.getUserType());
        return Result.ok("登录成功", response);
    }

    /**
     * 账密登录接口
     * @param request 包含 username 和 password
     * @return 登录响应
     */
    @PostMapping("/auth/login")
    public Result<?> login(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");
        
        logger.info("收到账密登录请求，username: {}", username);

        if (username == null || username.isEmpty()) {
            logger.error("登录失败：用户名为空");
            throw new BadRequestException("登录失败：缺少用户名");
        }

        if (password == null || password.isEmpty()) {
            logger.error("登录失败：密码为空，username: {}", username);
            throw new BadRequestException("登录失败：缺少密码");
        }

        User user = userService.getUserByUsername(username);
        if (user == null) {
            logger.warn("登录失败：用户不存在，username: {}", username);
            throw new BadRequestException("用户名或密码错误");
        }

        if (!userService.isUserEnabled(user)) {
            logger.warn("用户账号已被禁用，userId: {}", user.getId());
            throw new UserDisabledException("用户账号已被禁用");
        }

        if (!userService.verifyPassword(password, user.getPassword())) {
            logger.warn("登录失败：密码错误，username: {}", username);
            throw new BadRequestException("用户名或密码错误");
        }

        // 更新最后登录时间
        user.setLastLoginTime(LocalDateTime.now());
        userMapper.updateById(user);

        // 记录统计数据
        statisticsService.recordUserLogin(user.getId());

        // 获取用户账户信息
        Object account = getAccountByUserType(user);
        
        // 生成Token
        String token = tokenService.generateToken(user);

        // 构建用户完整信息（包含企业/科研团队额外信息、已绑定专利列表）
        UserFullInfoDTO fullInfo = UserFullInfoDTO.builder()
                .user(user)
                .build();
        
        Integer userType = user.getUserType();
        if (userType != null) {
            switch (userType) {
                case 1 -> fullInfo.setEnterpriseAccount((com.AI_Assistant.userCenter.enterprise.dto.EnterpriseAccount) account);
                case 2 -> {
                    com.AI_Assistant.userCenter.researchTeam.dto.ResearchTeamAccount teamAccount = 
                            (com.AI_Assistant.userCenter.researchTeam.dto.ResearchTeamAccount) account;
                    fullInfo.setResearchTeamAccount(teamAccount);
                    if (teamAccount != null && teamAccount.getPatents() != null) {
                        fullInfo.setBoundPatents(teamAccount.getPatents());
                        java.util.List<String> boundPatentIds = teamAccount.getPatents().stream()
                                .map(com.AI_Assistant.backend.patent.dto.PatentDTO::getPatentId)
                                .collect(java.util.stream.Collectors.toList());
                        fullInfo.setBoundPatentIds(boundPatentIds);
                    }
                }
                default -> {}
            }
        }
        
        // 缓存用户完整信息
        userCacheService.buildAndCacheFullUserInfo(fullInfo, token);

        LoginResponse response = LoginResponse.builder()
                .token(token)
                .userId(user.getId())
                .userType(user.getUserType())
                .account(account)
                .build();

        // 如果是管理员登录，查询并输出权限信息（仅调试，不影响返回数据）
        if (user.getUserType() != null && user.getUserType() == 3) {
            logAdminPermissions(user.getId());
        }

        logger.info("账密登录成功，userId: {}, userType: {}", user.getId(), user.getUserType());
        return Result.ok("登录成功", response);
    }

    @PostMapping("/auth/logout")
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        // 获取当前用户ID用于日志
        String userId = UserContext.getUserId();
        
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring(7);
            tokenService.invalidateToken(token);
        }
        
        // 立即清除 ThreadLocal 中的用户信息
        UserContext.clear();
        
        if (userId != null) {
            logger.info("用户已退出登录，用户ID: {}", userId);
        } else {
            logger.info("用户已退出登录");
        }
        
        return Result.ok("退出成功");
    }

    @GetMapping("/auth/wechat-config")
    public Result<Map<String, Object>> checkWechatConfig() {
        Map<String, Object> config = Map.of(
                "appId", wechatConfig.getAppId() != null ? "已配置" : "未配置",
                "appSecret", wechatConfig.getAppSecret() != null ? "已配置" : "未配置",
                "loginUrl", wechatConfig.getLoginUrl()
        );
        return Result.ok("配置信息", config);
    }

    private Object getAccountByUserType(User user) {
        Integer userType = user.getUserType();
        String userId = user.getId();

        return switch (userType) {
            case 1 -> enterpriseService.getEnterpriseAccount(userId);
            case 2 -> researchTeamService.getResearchTeamAccount(userId);
            case 3 -> adminService.getAdminAccount(userId);
            default -> userService.getUserDTOById(userId);
        };
    }
    
    /**
     * 调试方法：查询并输出管理员权限信息（仅输出日志，不影响返回数据）
     */
    private void logAdminPermissions(String userId) {
        try {
            // 调用 AdminService 获取权限信息
            Map<String, Object> permissions = adminService.getAdminPermissionsInfo(userId);
            if (permissions == null) {
                logger.warn("【DEBUG】管理员权限信息不存在，userId: {}", userId);
                return;
            }
            
            // 输出权限信息日志
            logger.info("========== 【DEBUG】管理员登录权限信息 ==========");
            logger.info("管理员ID: {}", permissions.get("userId"));
            logger.info("用户名: {}", permissions.get("username"));
            logger.info("管理员姓名: {}", permissions.get("realName"));
            logger.info("角色ID: {}", permissions.get("roleId"));
            logger.info("角色名称: {}", permissions.get("roleName"));
            logger.info("角色编码: {}", permissions.get("roleCode"));
            logger.info("------ 权限列表 ------");
            logger.info("企业查看权限: {}", permissions.get("permissionEnterpriseView"));
            logger.info("企业编辑权限: {}", permissions.get("permissionEnterpriseEdit"));
            logger.info("企业删除权限: {}", permissions.get("permissionEnterpriseDelete"));
            logger.info("科研团队查看权限: {}", permissions.get("permissionResearchView"));
            logger.info("科研团队编辑权限: {}", permissions.get("permissionResearchEdit"));
            logger.info("科研团队删除权限: {}", permissions.get("permissionResearchDelete"));
            logger.info("专利审核权限: {}", permissions.get("permissionPatentAudit"));
            logger.info("专利查看权限: {}", permissions.get("permissionPatentReview"));
            logger.info("通知发布权限: {}", permissions.get("permissionNoticePublish"));
            logger.info("统计查看权限: {}", permissions.get("permissionStatisticsView"));
            logger.info("系统配置权限: {}", permissions.get("permissionSystemConfig"));
            logger.info("意向审核权限: {}", permissions.get("permissionIntentionAudit"));
            logger.info("日志查看权限: {}", permissions.get("permissionLogView"));
            logger.info("==========================================");
        } catch (Exception e) {
            logger.error("【DEBUG】查询管理员权限信息失败", e);
        }
    }
}
