package com.AI_Assistant.userCenter.researchTeam.controller;

import java.util.List;

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

import com.AI_Assistant.backend.patent.dto.PatentDTO;
import com.AI_Assistant.backend.patent.service.PatentService;
import com.AI_Assistant.common.Result;
import com.AI_Assistant.common.dto.PageResult;
import com.AI_Assistant.common.exception.ForbiddenException;
import com.AI_Assistant.common.exception.NotFoundException;
import com.AI_Assistant.common.util.UserContext;
import com.AI_Assistant.userCenter.researchTeam.dto.BindResearchTeamRequest;
import com.AI_Assistant.userCenter.researchTeam.dto.ResearchTeamAccount;
import com.AI_Assistant.userCenter.researchTeam.dto.ResearchTeamUserVO;
import com.AI_Assistant.userCenter.researchTeam.dto.UpdateResearchTeamRequest;
import com.AI_Assistant.userCenter.researchTeam.service.ResearchTeamService;
import com.AI_Assistant.userCenter.user.entity.User;

/**
 * 科研团队控制器
 * 
 * 登录验证由 LoginRequiredInterceptor 统一处理
 */
@RestController
@RequestMapping("/api/v1")
public class ResearchTeamController {

    private static final Logger logger = LoggerFactory.getLogger(ResearchTeamController.class);

    @Autowired
    private ResearchTeamService researchTeamService;

    @Autowired
    private PatentService patentService;

    /**
     * 分页获取科研团队用户列表
     * 权限验证由 PermissionInterceptor 统一处理（需要 RESEARCH_VIEW 权限，即管理员）
     */
    @GetMapping("/researchTeamUsers")
    public Result<PageResult<ResearchTeamUserVO>> getResearchTeamUsers(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        
        logger.info("管理员查询科研团队用户列表，pageNum: {}, pageSize: {}", pageNum, pageSize);
        PageResult<ResearchTeamUserVO> pageResult = researchTeamService.getResearchTeamUserList(pageNum, pageSize);
        return Result.ok(pageResult);
    }

    /**
     * 获取单个科研团队用户信息
     * 
     * 【权限说明】
     * - 管理员用户（user_type = 3）可查看任意科研团队用户信息
     * - 科研团队用户（user_type = 2）只能查看自己的信息
     */
    @GetMapping("/researchTeamUsers/{researchTeamUserId}")
    public Result<ResearchTeamUserVO> getResearchTeamUser(@PathVariable String researchTeamUserId) {
        
        User currentUser = UserContext.getUser();
        
        // 权限检查：管理员可以查看任意科研团队用户，普通科研团队用户只能查看自己
        if (!UserContext.isAdmin() && !currentUser.getId().equals(researchTeamUserId)) {
            logger.warn("科研团队用户尝试查看其他科研团队用户信息，当前用户ID: {}, 目标用户ID: {}", 
                    currentUser.getId(), researchTeamUserId);
            throw new ForbiddenException("权限不足：只能查看自己的科研团队信息");
        }

        logger.info("查询科研团队用户信息，researchTeamUserId: {}", researchTeamUserId);
        ResearchTeamUserVO team = researchTeamService.getResearchTeamUserById(researchTeamUserId);
        if (team == null) {
            throw new NotFoundException("科研团队用户不存在");
        }
        return Result.ok(team);
    }

    /**
     * 绑定科研团队账号
     * 权限验证：登录用户只能为自己绑定科研团队账号
     */
    @PostMapping("/researchTeamUsers")
    // [已修复 - 问题61] 添加@Validated进行参数校验
    public Result<ResearchTeamAccount> bindResearchTeam(@RequestBody @Validated BindResearchTeamRequest request) {
        
        User currentUser = UserContext.getUser();
        
        // userId 从当前会话获取，确保用户只能为自己绑定科研团队账号
        logger.info("绑定科研团队账号，userId: {}", currentUser.getId());
        return researchTeamService.bindResearchTeam(request);
    }

    /**
     * 更新科研团队信息
     * 
     * 【权限说明】
     * - 用户只能更新自己的科研团队信息
     */
    @PutMapping("/researchTeamUsers/{researchTeamUserId}")
    // [已修复 - 问题61] 添加@Validated进行参数校验
    public Result<ResearchTeamAccount> updateResearchTeamUser(
            @PathVariable String researchTeamUserId,
            @RequestBody @Validated UpdateResearchTeamRequest request) {
        
        User currentUser = UserContext.getUser();
        
        // 用户只能更新自己的科研团队信息
        if (!currentUser.getId().equals(researchTeamUserId)) {
            logger.warn("用户尝试更新其他科研团队信息，当前用户ID: {}, 目标用户ID: {}", 
                    currentUser.getId(), researchTeamUserId);
            throw new ForbiddenException("权限不足：只能更新自己的科研团队信息");
        }

        logger.info("更新科研团队信息，researchTeamUserId: {}", researchTeamUserId);
        return researchTeamService.updateResearchTeam(researchTeamUserId, request);
    }

    /**
     * 注销科研团队用户身份
     * 查询该科研团队账号对应的意向留言列表，若有尚未审核完毕的，将其状态改为拒绝
     * 
     * 【权限说明】
     * - 用户只能注销自己的科研团队账号
     */
    @DeleteMapping("/researchTeamUsers/{researchTeamUserId}")
    public Result<Void> deleteResearchTeamUser(@PathVariable String researchTeamUserId) {
        
        User currentUser = UserContext.getUser();
        
        // 用户只能删除自己的科研团队账号
        if (!currentUser.getId().equals(researchTeamUserId)) {
            logger.warn("用户尝试删除其他科研团队账号，当前用户ID: {}, 目标用户ID: {}", 
                    currentUser.getId(), researchTeamUserId);
            throw new ForbiddenException("权限不足：只能注销自己的科研团队账号");
        }

        logger.info("注销科研团队用户身份，researchTeamUserId: {}", researchTeamUserId);
        researchTeamService.unbindResearchTeam();
        return Result.ok("注销科研团队账号成功");
    }

    /**
     * 获取科研团队绑定的专利列表
     * 
     * 【权限说明】
     * - 管理员用户（user_type = 3）可查看任意科研团队的专利列表
     * - 科研团队用户（user_type = 2）只能查看自己绑定的专利列表
     */
    @GetMapping("/researchTeamUsers/{researchTeamUserId}/patents")
    public Result<List<PatentDTO>> getResearchTeamPatents(@PathVariable String researchTeamUserId) {
        
        User currentUser = UserContext.getUser();
        
        // 权限检查：管理员可以查看任意科研团队的专利，普通科研团队用户只能查看自己的
        if (!UserContext.isAdmin() && !currentUser.getId().equals(researchTeamUserId)) {
            logger.warn("科研团队用户尝试查看其他科研团队的专利列表，当前用户ID: {}, 目标用户ID: {}", 
                    currentUser.getId(), researchTeamUserId);
            throw new ForbiddenException("权限不足：只能查看自己绑定的专利列表");
        }

        logger.info("查询科研团队绑定的专利列表，researchTeamUserId: {}", researchTeamUserId);
        List<PatentDTO> patents = patentService.getBoundPatents(researchTeamUserId);
        return Result.ok(patents);
    }
}