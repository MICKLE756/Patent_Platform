package com.AI_Assistant.backend.patent.controller;

import java.util.HashMap;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.AI_Assistant.backend.patent.dto.PatentBindRequest;
import com.AI_Assistant.backend.patent.dto.PatentBindResponse;
import com.AI_Assistant.backend.patent.dto.PatentCreateRequest;
import com.AI_Assistant.backend.patent.dto.PatentDTO;
import com.AI_Assistant.backend.patent.dto.PatentSearchRequest;
import com.AI_Assistant.backend.patent.dto.PatentUnbindRequest;
import com.AI_Assistant.backend.patent.service.PatentService;
import com.AI_Assistant.common.Result;
import com.AI_Assistant.common.dto.PageResult;
import com.AI_Assistant.common.exception.BadRequestException;
import com.AI_Assistant.common.exception.NotFoundException;
import com.AI_Assistant.common.exception.UnauthorizedException;
import com.AI_Assistant.common.util.UserContext;

/**
 * 专利控制器
 * 提供专利数据的查询、搜索和统计功能
 *
 * @author AI_Assistant
 * @version 1.0.0
 */
@RestController
@RequestMapping("/api/v1")
public class PatentController {

    @Autowired
    private PatentService patentService;

    /**
     * 搜索专利列表（支持多条件筛选和分页）
     *
     * 【功能说明】
     * - 支持按关键词搜索（标题、摘要、发明人、申请人、技术领域）
     * - 支持按专利类型、法律状态、有效性筛选
     * - 支持分页查询，返回分页元信息（总记录数、总页数等）
     *
     * 【筛选条件】
     * - keyword: 关键词搜索（模糊匹配）
     * - patentType: 专利类型（发明/实用新型/外观设计）
     * - legalStatus: 法律状态（授权/公开/实质审查等）
     * - validity: 有效性（有效/无效/终止）
     *
     * 【分页参数】
     * - pageNum: 页码（默认1，从1开始）
     * - pageSize: 每页大小（默认10）
     *
     * @return 分页专利列表
     */
    @GetMapping("/patents")
    public Result<PageResult<PatentDTO>> searchPatents(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String patentType,
            @RequestParam(required = false) String legalStatus,
            @RequestParam(required = false) String validity,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {

        PatentSearchRequest request = PatentSearchRequest.builder()
                .keyword(keyword)
                .patentType(patentType)
                .legalStatus(legalStatus)
                .validity(validity)
                .pageNum(pageNum)
                .pageSize(pageSize)
                .build();

        PageResult<PatentDTO> patents = patentService.searchPatents(request);
        return Result.ok(patents);
    }

    /**
     * 根据专利ID获取专利详情
     *
     * @param patentId 专利ID
     * @return 专利详情
     */
    @GetMapping("/patents/{patentId}")
    public Result<PatentDTO> getPatent(@PathVariable String patentId) {
        PatentDTO patent = patentService.getPatentById(patentId);

        if (patent == null) {
            throw new NotFoundException("专利不存在");
        }

        // 记录浏览次数（浏览统计功能已移至BoundPatent表）
        String userId = UserContext.getUserId();
        if (userId != null) {
            patentService.recordView(patentId, userId);
        }

        return Result.ok(patent);
    }

    /**
     * 获取专利统计信息
     *
     * @return 统计数据（总数）
     */
    @GetMapping("/patents/statistics")
    public Result<Map<String, Object>> getPatentStatistics() {
        Map<String, Object> stats = Map.of(
                "totalCount", patentService.getTotalCount()
        );
        return Result.ok(stats);
    }

    /**
     * 获取用户已绑定专利数量
     *
     * @return 已绑定专利数量
     */
    @GetMapping("/patents/bound/count")
    // [已修复 - 问题3] 移除userId参数，强制使用当前登录用户
    public Result<Map<String, Object>> getBoundPatentCount() {
        String userId = UserContext.getUserId();
        if (userId == null) {
            throw new UnauthorizedException("用户未登录");
        }

        int count = patentService.getBoundPatentCount(userId);
        return Result.ok(Map.of("count", count));
    }

    /**
     * 获取用户未绑定专利数量
     *
     * @return 未绑定专利数量
     */
    @GetMapping("/patents/unbound/count")
    // [已修复 - 问题3] 移除userId参数，强制使用当前登录用户
    public Result<Map<String, Object>> getUnboundPatentCount() {
        String userId = UserContext.getUserId();
        if (userId == null) {
            throw new UnauthorizedException("用户未登录");
        }

        int count = patentService.getUnboundPatentCount(userId);
        return Result.ok(Map.of("count", count));
    }

    /**
     * 分页获取用户已绑定专利
     *
     * @param pageNum 页码（默认1）
     * @param pageSize 每页大小（默认10）
     * @return 分页专利列表
     */
    @GetMapping("/patents/bound/page")
    // [已修复 - 问题3] 移除userId参数，强制使用当前登录用户
    public Result<PageResult<PatentDTO>> getBoundPatentsPage(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        String userId = UserContext.getUserId();
        if (userId == null) {
            throw new UnauthorizedException("用户未登录");
        }

        PageResult<PatentDTO> result = patentService.getBoundPatentsPage(userId, pageNum, pageSize);
        return Result.ok(result);
    }

    /**
     * 分页获取用户未绑定专利
     *
     * @param pageNum 页码（默认1）
     * @param pageSize 每页大小（默认10）
     * @return 分页专利列表
     */
    @GetMapping("/patents/unbound/page")
    // [已修复 - 问题3] 移除userId参数，强制使用当前登录用户
    public Result<PageResult<PatentDTO>> getUnboundPatentsPage(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        String userId = UserContext.getUserId();
        if (userId == null) {
            throw new UnauthorizedException("用户未登录");
        }

        PageResult<PatentDTO> result = patentService.getUnboundPatentsPage(userId, pageNum, pageSize);
        return Result.ok(result);
    }

    /**
     * 查询未绑定的专利（科研团队用户查询允许绑定的专利）
     *
     * 【业务流程说明】
     * 1. 科研团队用户调用此接口查询专利库中匹配的专利
     * 2. 不一定是初次登录，任何时候都可以调用此接口查询
     * 3. 后端逻辑：
     *    - 从当前登录用户的科研团队信息中获取团队名称（发明人）和所属单位
     *    - 根据发明人姓名和所属单位在专利库中查找匹配的专利
     *    - 查询已绑定专利数据表（bound_patent）中该用户的已绑定专利
     *    - 返回两者的差集（未绑定的专利）
     *
     * 【前端处理逻辑】
     * - 如果返回的专利列表不为空：展示专利列表供用户选择确认
     * - 如果返回的专利列表为空数组：科研团队账户可以显示"绑定专利"按钮
     *
     * 【使用场景】
     * - 用户初次登录后查询
     * - 用户点击"绑定专利"按钮时查询
     * - 用户解绑专利后重新查询
     *
     * @return 未绑定的专利列表
     */
    @GetMapping("/patents/unbound")
    public Result<List<PatentDTO>> getUnboundPatents() {
        String userId = UserContext.getUserId();
        if (userId == null) {
            throw new UnauthorizedException("用户未登录");
        }
        
        List<PatentDTO> patents = patentService.getUnboundPatents(userId);
        return Result.ok(patents);
    }

    /**
     * 绑定专利到用户
     *
     * 【业务流程说明】
     * 1. 用户在前端选择确认专利后调用此接口
     * 2. 后端将选中的专利绑定到当前用户
     * 3. 在 bound_patent 表中创建绑定记录（包含浏览数、搜索数、点击数字段，初始值为0）
     *
     * 【前端调用时机】
     * - 用户在专利列表中选择若干专利后点击"确认绑定"按钮
     * - 需传入用户选择的专利ID列表
     *
     * 【注意事项】
     * - 用户必须已登录（通过 UserContext 获取用户ID）
     * - 支持两种用户类型：enterprise（企业用户）、research_team（科研团队用户）
     * - 已绑定的专利会被自动跳过，不会重复绑定
     *
     * @param request 绑定请求（包含专利ID列表、用户类型）
     * @return 绑定结果（success: 是否成功, message: 提示信息）
     */
    @PostMapping("/patents/bind")
    // [已修复 - 问题61] 添加@Validated进行参数校验
    // [已修复 - 问题3] 移除request.getUserId()，强制使用当前登录用户
    public Result<PatentBindResponse> bindPatents(@RequestBody @Validated PatentBindRequest request) {
        String userId = UserContext.getUserId();
        if (userId == null) {
            throw new UnauthorizedException("用户未登录");
        }

        String userType = request.getUserType() != null ? request.getUserType() : "research_team";

        boolean success = patentService.bindPatents(request.getPatentIds(), userId, userType);

        PatentBindResponse response = success ? PatentBindResponse.success() : PatentBindResponse.failure();

        return Result.ok(response);
    }

    /**
     * 获取用户已绑定的专利列表
     *
     * @return 已绑定的专利列表
     */
    @GetMapping("/patents/bound")
    // [已修复 - 问题3] 移除userId参数，强制使用当前登录用户
    public Result<List<PatentDTO>> getBoundPatents() {
        String userId = UserContext.getUserId();
        if (userId == null) {
            throw new UnauthorizedException("用户未登录");
        }

        List<PatentDTO> patents = patentService.getBoundPatents(userId);
        return Result.ok(patents);
    }

    /**
     * 解绑专利（删除用户与专利的绑定关系）
     *
     * 【业务流程说明】
     * 1. 用户在前端选择要解绑的专利后调用此接口
     * 2. 后端从 bound_patent 表中删除对应的绑定记录
     * 3. 解绑后，该专利可以被其他用户绑定
     *
     * 【前端调用时机】
     * - 用户在"已绑定专利"列表中选择若干专利后点击"解除绑定"按钮
     * - 需传入要解绑的专利ID列表
     *
     * 【注意事项】
     * - 用户必须已登录（通过 UserContext 获取用户ID）
     * - 解绑后，其他用户可以绑定该专利
     * - 解绑不会影响该专利在其他用户下的绑定记录
     * - 不需要处理意向留言（因为可能有其他人来绑定这个专利）
     *
     * @param request 解绑请求（包含专利ID列表）
     * @return 解绑结果（success: 是否成功, message: 提示信息, unboundCount: 解绑数量, patents: 解绑后用户剩余的专利列表）
     */
    @PostMapping("/patents/unbind")
    public Result<Map<String, Object>> unbindPatents(@RequestBody PatentUnbindRequest request) {
        String userId = UserContext.getUserId();
        if (userId == null) {
            throw new UnauthorizedException("用户未登录");
        }

        List<String> patentIds = request.getPatentIds();
        if (patentIds == null || patentIds.isEmpty()) {
            throw new BadRequestException("请选择要解绑的专利");
        }

        int unboundCount = patentService.unbindPatents(patentIds, userId);
        List<PatentDTO> remainingPatents = patentService.getBoundPatents(userId);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "解绑成功");
        result.put("unboundCount", unboundCount);
        result.put("patents", remainingPatents);

        return Result.ok(result);
    }

    // ========== 专利增删改接口 ==========

    /**
     * 新增专利
     *
     * @param request 专利创建请求
     * @return 新增的专利信息
     */
    @PostMapping("/patents/create")
    // [已修复 - 问题61] 添加@Validated进行参数校验
    public Result<PatentDTO> createPatent(@RequestBody @Validated PatentCreateRequest request) {
        PatentDTO patent = patentService.createPatent(request);
        return Result.ok(patent);
    }

    /**
     * 更新专利
     *
     * @param patentId 专利ID
     * @param request 专利更新请求
     * @return 更新后的专利信息
     */
    @PutMapping("/patents/{patentId}")
    // [已修复 - 问题61] 添加@Validated进行参数校验
    public Result<PatentDTO> updatePatent(
            @PathVariable String patentId,
            @RequestBody @Validated PatentCreateRequest request) {
        PatentDTO patent = patentService.updatePatent(patentId, request);
        if (patent == null) {
            throw new NotFoundException("专利不存在");
        }
        return Result.ok(patent);
    }

    /**
     * 删除专利
     *
     * @param patentId 专利ID
     * @return 删除结果
     */
    @DeleteMapping("/patents/{patentId}")
    public Result<Map<String, Object>> deletePatent(@PathVariable String patentId) {
        boolean success = patentService.deletePatent(patentId);
        if (!success) {
            throw new NotFoundException("专利不存在");
        }
        return Result.ok(Map.of(
                "success", true,
                "message", "专利删除成功"
        ));
    }
}