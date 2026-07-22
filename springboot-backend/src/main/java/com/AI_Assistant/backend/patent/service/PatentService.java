package com.AI_Assistant.backend.patent.service;

import java.util.List;

import com.AI_Assistant.backend.patent.dto.PatentCreateRequest;
import com.AI_Assistant.backend.patent.dto.PatentDTO;
import com.AI_Assistant.backend.patent.dto.PatentSearchRequest;
import com.AI_Assistant.common.dto.PageResult;

/**
 * 专利服务接口
 * 定义专利数据的查询、搜索和统计操作
 *
 * @author AI_Assistant
 * @version 1.0.0
 */
public interface PatentService {

    /**
     * 搜索专利列表（支持多条件筛选和分页）
     *
     * @param request 搜索请求（包含关键词、筛选条件和分页参数）
     * @return 分页专利列表
     */
    PageResult<PatentDTO> searchPatents(PatentSearchRequest request);

    /**
     * 根据专利ID获取专利详情
     *
     * @param patentId 专利ID
     * @return 专利详情，不存在返回null
     */
    PatentDTO getPatentById(String patentId);

    /**
     * 根据成熟度等级名称获取专利列表
     *
     * @param maturity 成熟度等级名称（如：量产级、中试级、原型级、实验室级）
     * @return 专利列表
     * 
     * TODO: 如需启用成熟度筛选功能，请执行以下修改：
     * 1. 在 patent 表中添加 maturity 字段（VARCHAR(50)）
     * 2. 在 Patent 实体类中添加 maturity 字段
     * 3. 在 PatentDTO 中添加 maturity 字段
     * 4. 在 PatentMapper 中添加按成熟度查询的方法
     * 5. 修改此方法实现，调用 mapper 的查询方法
     * 6. 在 PatentSearchRequest 中添加 maturity 参数支持搜索
     */
    List<PatentDTO> getPatentsByMaturity(String maturity);

    /**
     * 获取专利总数
     *
     * @return 专利总数
     */
    int getTotalCount();

    /**
     * 获取用户已绑定专利数量
     *
     * @param userId 用户ID
     * @return 已绑定专利数量
     */
    int getBoundPatentCount(String userId);

    /**
     * 获取用户未绑定专利数量
     *
     * @param userId 用户ID
     * @return 未绑定专利数量
     */
    int getUnboundPatentCount(String userId);

    /**
     * 获取总已绑定专利数量（所有用户）
     *
     * @return 总已绑定专利数量
     */
    int getTotalBoundPatentCount();

    /**
     * 获取总未绑定专利数量（专利库中未被任何用户绑定的专利）
     *
     * @return 总未绑定专利数量
     */
    int getTotalUnboundPatentCount();

    /**
     * 分页获取用户已绑定专利
     *
     * @param userId 用户ID
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @return 分页专利列表
     */
    PageResult<PatentDTO> getBoundPatentsPage(String userId, int pageNum, int pageSize);

    /**
     * 分页获取用户未绑定专利
     *
     * @param userId 用户ID
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @return 分页专利列表
     */
    PageResult<PatentDTO> getUnboundPatentsPage(String userId, int pageNum, int pageSize);

    /**
     * 记录专利浏览次数
     *
     * @param patentId 专利ID
     * @param viewerId 浏览者用户ID
     */
    void recordView(String patentId, String viewerId);

    /**
     * 查询未绑定的专利（根据当前登录用户的科研团队信息匹配）
     * 用户登录后调用，返回专利库中匹配但尚未绑定的专利
     *
     * @param userId 用户ID
     * @return 未绑定的专利列表
     */
    List<PatentDTO> getUnboundPatents(String userId);

    /**
     * 绑定专利到用户
     *
     * @param patentIds 专利ID列表
     * @param userId 用户ID
     * @param userType 用户类型
     * @return 绑定结果
     */
    boolean bindPatents(List<String> patentIds, String userId, String userType);

    /**
     * 获取用户已绑定的专利列表
     *
     * @param userId 用户ID
     * @return 已绑定的专利列表
     */
    List<PatentDTO> getBoundPatents(String userId);

    /**
     * 解绑专利（删除用户与专利的绑定关系）
     *
     * @param patentIds 要解绑的专利ID列表
     * @param userId 用户ID
     * @return 解绑的专利数量
     */
    int unbindPatents(List<String> patentIds, String userId);

    /**
     * 解绑用户所有专利（用于机构变更时自动解绑所有专利）
     *
     * @param userId 用户ID
     * @return 解绑的专利数量
     */
    int unbindAllPatents(String userId);

    /**
     * 获取专利的统计数据（浏览数、搜索数、点击数）
     *
     * @param patentId 专利ID
     * @return 统计数据，如果不存在返回默认值(0,0,0)
     */
    int[] getPatentStatistics(String patentId);

    /**
     * 批量获取专利的统计数据（解决N+1查询问题）
     *
     * @param patentIds 专利ID列表
     * @return 专利统计数据Map，key为patentId，value为统计数组[viewCount, searchCount, clickCount]
     */
    java.util.Map<String, int[]> batchGetPatentStatistics(java.util.List<String> patentIds);

    /**
     * 记录专利搜索次数
     *
     * @param patentId 专利ID
     */
    void recordSearch(String patentId);

    /**
     * 记录专利点击次数
     *
     * @param patentId 专利ID
     */
    void recordClick(String patentId);

    /**
     * 获取热门专利列表（按浏览次数排序，直接从BoundPatent表查询）
     *
     * @param limit 返回数量限制
     * @return 热门专利列表，包含专利ID、名称和统计数据
     */
    java.util.List<java.util.Map<String, Object>> getHotBoundPatents(int limit);

    // ========== 专利增删改接口 ==========

    /**
     * 新增专利
     *
     * @param request 专利创建请求
     * @return 新增的专利DTO
     */
    PatentDTO createPatent(PatentCreateRequest request);

    /**
     * 更新专利
     *
     * @param patentId 专利ID
     * @param request 专利更新请求
     * @return 更新后的专利DTO，如果专利不存在返回null
     */
    PatentDTO updatePatent(String patentId, PatentCreateRequest request);

    /**
     * 删除专利
     *
     * @param patentId 专利ID
     * @return 是否删除成功
     */
    boolean deletePatent(String patentId);
}