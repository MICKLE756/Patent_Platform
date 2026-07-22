package com.AI_Assistant.userCenter.enterprise.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.AI_Assistant.common.Result;
import com.AI_Assistant.common.dto.PageResult;
import com.AI_Assistant.common.exception.ForbiddenException;
import com.AI_Assistant.common.exception.NotFoundException;
import com.AI_Assistant.common.util.UserContext;
import com.AI_Assistant.userCenter.enterprise.dto.BindEnterpriseRequest;
import com.AI_Assistant.userCenter.enterprise.dto.EnterpriseAccount;
import com.AI_Assistant.userCenter.enterprise.dto.EnterpriseUserVO;
import com.AI_Assistant.userCenter.enterprise.dto.UpdateEnterpriseRequest;
import com.AI_Assistant.userCenter.enterprise.service.EnterpriseService;
import com.AI_Assistant.userCenter.user.entity.User;

/**
 * 企业用户控制器
 * 
 * 登录验证由 LoginRequiredInterceptor 统一处理
 */
@RestController
@RequestMapping("/api/v1")
public class EnterpriseController {

    private static final Logger logger = LoggerFactory.getLogger(EnterpriseController.class);

    @Autowired
    private EnterpriseService enterpriseService;

    /**
     * 分页获取企业用户列表
     * 权限验证由 PermissionInterceptor 统一处理（需要 ENTERPRISE_VIEW 权限，即管理员）
     */
    @GetMapping("/enterpriseUsers")
    public Result<PageResult<EnterpriseUserVO>> getEnterpriseUsers(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        
        logger.info("管理员查询企业用户列表，pageNum: {}, pageSize: {}", pageNum, pageSize);
        PageResult<EnterpriseUserVO> pageResult = enterpriseService.getEnterpriseUserList(pageNum, pageSize);
        return Result.ok(pageResult);
    }

    /**
     * 获取单个企业用户信息
     * 
     * 【权限说明】
     * - 管理员用户（user_type = 3）可查看任意企业用户信息
     * - 企业用户（user_type = 1）只能查看自己的信息
     * 
     * @param enterpriseUserId 企业用户ID
     * @return 企业用户信息
     */
    @GetMapping("/enterpriseUsers/{enterpriseUserId}")
    public Result<EnterpriseUserVO> getEnterpriseUser(@PathVariable String enterpriseUserId) {
        
        User currentUser = UserContext.getUser();
        
        // 权限检查：管理员可以查看任意企业用户，普通企业用户只能查看自己
        if (!UserContext.isAdmin() && !currentUser.getId().equals(enterpriseUserId)) {
            logger.warn("企业用户尝试查看其他企业用户信息，当前用户ID: {}, 目标用户ID: {}", 
                    currentUser.getId(), enterpriseUserId);
            throw new ForbiddenException("权限不足：只能查看自己的企业信息");
        }

        logger.info("查询企业用户信息，enterpriseUserId: {}", enterpriseUserId);
        EnterpriseUserVO enterprise = enterpriseService.getEnterpriseUserById(enterpriseUserId);
        if (enterprise == null) {
            throw new NotFoundException("企业用户不存在");
        }
        return Result.ok(enterprise);
    }

    /**
     * 注销企业用户身份
     * 查询该企业账号对应的意向留言列表，若有尚未审核完毕的，将其状态改为拒绝
     * 
     * 【权限说明】
     * - 用户只能注销自己的企业账号
     */
    @DeleteMapping("/enterpriseUsers/{enterpriseUserId}")
    public Result<Void> deleteEnterpriseUser(@PathVariable String enterpriseUserId) {
        
        User currentUser = UserContext.getUser();
        
        // 用户只能删除自己的企业账号
        if (!currentUser.getId().equals(enterpriseUserId)) {
            logger.warn("用户尝试删除其他企业账号，当前用户ID: {}, 目标用户ID: {}", 
                    currentUser.getId(), enterpriseUserId);
            throw new ForbiddenException("权限不足：只能注销自己的企业账号");
        }

        logger.info("注销企业用户身份，enterpriseUserId: {}", enterpriseUserId);
        enterpriseService.unbindEnterprise();
        return Result.ok("注销企业账号成功");
    }

    /**
     * 更新企业信息
     * 仅修改企业特有的信息（companyName, contactName, businessLicense, companyAddress, companyIntro）
     * 账号通用信息（如用户名、联系方式等）应在 user 模块中修改
     * 
     * 【权限说明】
     * - 用户只能更新自己的企业信息
     */
    @PutMapping("/enterpriseUsers/{enterpriseUserId}")
    // [已修复 - 问题61] 添加@Validated进行参数校验
    public Result<EnterpriseAccount> updateEnterpriseUser(
            @PathVariable String enterpriseUserId, 
            @RequestBody @Validated UpdateEnterpriseRequest request) {
        
        User currentUser = UserContext.getUser();
        
        // 用户只能更新自己的企业信息
        if (!currentUser.getId().equals(enterpriseUserId)) {
            logger.warn("用户尝试更新其他企业信息，当前用户ID: {}, 目标用户ID: {}", 
                    currentUser.getId(), enterpriseUserId);
            throw new ForbiddenException("权限不足：只能更新自己的企业信息");
        }

        logger.info("更新企业信息，enterpriseUserId: {}", enterpriseUserId);
        return enterpriseService.updateEnterprise(enterpriseUserId, request);
    }

    /**
     * 绑定企业账号
     * 权限验证：登录用户只能为自己绑定企业账号
     */
    @PostMapping("/enterpriseUsers")
    // [已修复 - 问题61] 添加@Validated进行参数校验
    public Result<EnterpriseAccount> bindEnterprise(@RequestBody @Validated BindEnterpriseRequest request) {
        
        User currentUser = UserContext.getUser();
        
        // userId 从当前会话获取，确保用户只能为自己绑定企业账号
        logger.info("绑定企业账号，userId: {}", currentUser.getId());
        return enterpriseService.bindEnterprise(request);
    }
}