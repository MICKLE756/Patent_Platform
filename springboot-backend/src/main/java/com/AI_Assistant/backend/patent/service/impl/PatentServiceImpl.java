package com.AI_Assistant.backend.patent.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.AI_Assistant.backend.patent.dto.PatentCreateRequest;
import com.AI_Assistant.backend.patent.dto.PatentDTO;
import com.AI_Assistant.backend.patent.dto.PatentSearchRequest;
import com.AI_Assistant.backend.patent.entity.BoundPatent;
import com.AI_Assistant.backend.patent.entity.Patent;
import com.AI_Assistant.backend.patent.entity.PatentViewRecord;
import com.AI_Assistant.backend.patent.entity.SearchLog;
import com.AI_Assistant.backend.patent.mapper.BoundPatentMapper;
import com.AI_Assistant.backend.patent.mapper.PatentMapper;
import com.AI_Assistant.backend.patent.mapper.PatentStatisticsMapper;
import com.AI_Assistant.backend.patent.mapper.SearchLogMapper;
import com.AI_Assistant.backend.patent.service.PatentService;
import com.AI_Assistant.common.dto.PageResult;
import com.AI_Assistant.common.exception.BusinessException;
import com.AI_Assistant.common.StateCode;
import com.AI_Assistant.common.util.UserCacheService;
import com.AI_Assistant.common.util.UserContext;
import com.AI_Assistant.userCenter.researchTeam.entity.ResearchTeam;
import com.AI_Assistant.userCenter.researchTeam.mapper.ResearchTeamMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 专利服务实现类
 * 从数据库patent表加载专利数据，提供专利查询、搜索和统计功能
 *
 * @author AI_Assistant
 * @version 1.0.0
 */
@Service
public class PatentServiceImpl implements PatentService {

    private static final Logger logger = LoggerFactory.getLogger(PatentServiceImpl.class);

    @Autowired
    private BoundPatentMapper boundPatentMapper;

    @Autowired
    private ResearchTeamMapper researchTeamMapper;

    @Autowired
    private SearchLogMapper searchLogMapper;

    @Autowired
    private PatentMapper patentMapper;

    @Autowired
    private PatentStatisticsMapper patentStatisticsMapper;

    @Autowired
    private com.AI_Assistant.backend.patent.mapper.PatentViewRecordMapper patentViewRecordMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private UserCacheService userCacheService;

    @Autowired
    private com.AI_Assistant.backend.statistics.service.StatisticsService statisticsService;

    /**
     * 将Patent实体转换为PatentDTO
     */
    private PatentDTO convertToDTO(Patent patent) {
        if (patent == null) {
            return null;
        }
        return PatentDTO.builder()
                .patentId(patent.getPatentId())
                .title(patent.getTitle())
                .applicant(patent.getApplicant())
                .currentOwner(patent.getCurrentOwner())
                .inventors(patent.getInventors())
                .patentType(patent.getPatentType())
                .legalStatus(patent.getLegalStatus())
                .validity(patent.getValidity())
                .applicationDate(patent.getApplicationDate())
                .publicationDate(patent.getPublicationDate())
                .grantDate(patent.getGrantDate())
                .estimatedExpiryDate(patent.getEstimatedExpiryDate())
                .abstractText(patent.getAbstractText())
                .technicalEffectSentences(patent.getTechnicalEffectSentences())
                .techField(patent.getTechField())
                .technicalStability(patent.getTechnicalStability())
                .technicalAdvancement(patent.getTechnicalAdvancement())
                .sourceLink(patent.getSourceLink())
                .build();
    }

    /**
     * 将Patent列表转换为PatentDTO列表
     */
    private List<PatentDTO> convertToDTOList(List<Patent> patents) {
        if (patents == null || patents.isEmpty()) {
            return new ArrayList<>();
        }
        return patents.stream()
                .map(this::convertToDTO)
                .filter(p -> p != null)
                .collect(Collectors.toList());
    }

    @Override
    public PageResult<PatentDTO> searchPatents(PatentSearchRequest request) {
        // 记录搜索日志（处理空请求）
        if (request != null) {
            recordSearchLog(request);
        }

        // 如果请求为空，返回第一页数据（最多10条）
        if (request == null) {
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<Patent> page = 
                    new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10);
            com.baomidou.mybatisplus.core.metadata.IPage<Patent> resultPage = patentMapper.selectPage(page, null);
            return PageResult.of(convertToDTOList(resultPage.getRecords()), 1, 10, resultPage.getTotal());
        }

        // 分页参数
        int pageNum = request.getPageNum() != null && request.getPageNum() > 0 ? request.getPageNum() : 1;
        int pageSize = request.getPageSize() != null && request.getPageSize() > 0 ? request.getPageSize() : 10;
        
        // 设置最大单页限制，防止请求过大
        pageSize = Math.min(pageSize, 50);

        // 【MyBatis-Plus分页】使用数据库分页查询
        Page<Patent> page = new Page<>(pageNum, pageSize);
        IPage<Patent> resultPage = patentMapper.selectByConditionsPage(
                page,
                request.getKeyword(),
                request.getPatentType(),
                request.getLegalStatus(),
                request.getValidity()
        );

        // 手动查询总数（解决@Select注解分页插件无法正确计算total的问题）
        int total = patentMapper.countByConditions(
                request.getKeyword(),
                request.getPatentType(),
                request.getLegalStatus(),
                request.getValidity()
        );

        List<PatentDTO> pageDTOs = convertToDTOList(resultPage.getRecords());

        // 批量获取统计数据并设置到专利DTO中
        List<String> pagePatentIds = pageDTOs.stream()
                .map(PatentDTO::getPatentId)
                .collect(Collectors.toList());
        Map<String, int[]> statsMap = batchGetPatentStatistics(pagePatentIds);

        for (PatentDTO patent : pageDTOs) {
            int[] stats = statsMap.getOrDefault(patent.getPatentId(), new int[]{0, 0, 0});
            patent.setViewCount(stats[0]);
            patent.setSearchCount(stats[1]);
            patent.setClickCount(stats[2]);
        }

        // 返回分页结果（使用手动查询的总数）
        return PageResult.of(pageDTOs, pageNum, pageSize, (long) total);
    }

    @Override
    public PatentDTO getPatentById(String patentId) {
        // ===== 调试日志：查询数据库 =====
        Patent patent = patentMapper.selectByPatentIdResultMap(patentId);
        logger.info("===== [DEBUG] 数据库查询 Patent 实体 =====");
        logger.info("patentId: {}", patentId);
        logger.info("Patent 对象: {}", patent);
        if (patent != null) {
            logger.info("Patent.getAbstractText(): [{}]", patent.getAbstractText());
            logger.info("Patent.title: [{}]", patent.getTitle());
        }
        logger.info("===== 调试结束 =====\n");

        if (patent != null) {
            PatentDTO dto = convertToDTO(patent);

            // ===== 调试日志：转换为 DTO =====
            logger.info("===== [DEBUG] 转换 PatentDTO =====");
            logger.info("dto 对象: {}", dto);
            logger.info("dto.abstractText: [{}]", dto.getAbstractText());
            logger.info("dto.title: [{}]", dto.getTitle());
            logger.info("===== 调试结束 =====\n");

            // 设置统计数据
            int[] stats = getPatentStatistics(patentId);
            dto.setViewCount(Integer.valueOf(stats[0]));
            dto.setSearchCount(Integer.valueOf(stats[1]));
            dto.setClickCount(Integer.valueOf(stats[2]));

            // ===== 调试日志：返回前端前 =====
            logger.info("===== [DEBUG] 返回给前端的 DTO =====");
            logger.info("最终 dto.abstractText: [{}]", dto.getAbstractText());
            logger.info("===== 调试结束 =====\n");

            return dto;
        }
        return null;
    }

    @Override
    public List<PatentDTO> getPatentsByMaturity(String maturity) {
        // TODO: 如需启用成熟度筛选功能，请执行以下修改：
        // 1. 在 patent 表中添加 maturity 字段（VARCHAR(50)）
        // 2. 在 Patent 实体类中添加 maturity 字段
        // 3. 在 PatentDTO 中添加 maturity 字段
        // 4. 在 PatentMapper 中添加按成熟度查询的方法
        // 5. 修改此方法实现：
        //    List<Patent> patents = patentMapper.selectByMaturity(maturity);
        //    return patents.stream().map(this::convertToDTO).collect(Collectors.toList());
        // 6. 在 PatentSearchRequest 中添加 maturity 参数支持搜索
        
        // 当前专利表中没有成熟度字段，此方法暂时返回空列表
        logger.warn("getPatentsByMaturity方法已弃用，请使用关键词搜索代替");
        return new ArrayList<>();
    }

    @Override
    public int getTotalCount() {
        return patentMapper.countAll();
    }

    @Override
    public int getBoundPatentCount(String userId) {
        try {
            List<BoundPatent> boundPatents = boundPatentMapper.selectByUserId(userId);
            return boundPatents != null ? boundPatents.size() : 0;
        } catch (Exception e) {
            logger.warn("获取已绑定专利数量失败，userId: {}", userId, e);
            return 0;
        }
    }

    @Override
    public int getUnboundPatentCount(String userId) {
        List<PatentDTO> unboundPatents = getUnboundPatents(userId);
        return unboundPatents != null ? unboundPatents.size() : 0;
    }

    @Override
    public int getTotalBoundPatentCount() {
        try {
            Long count = boundPatentMapper.selectCount(null);
            return count != null ? count.intValue() : 0;
        } catch (Exception e) {
            logger.warn("获取总已绑定专利数量失败", e);
            return 0;
        }
    }

    @Override
    public int getTotalUnboundPatentCount() {
        try {
            int totalPatents = patentMapper.countAll();
            Long boundCount = boundPatentMapper.selectCount(null);
            return totalPatents - (boundCount != null ? boundCount.intValue() : 0);
        } catch (Exception e) {
            logger.warn("获取总未绑定专利数量失败", e);
            return 0;
        }
    }

    @Override
    public PageResult<PatentDTO> getBoundPatentsPage(String userId, int pageNum, int pageSize) {
        try {
            // 1. 查询该用户已绑定的专利ID列表
            List<BoundPatent> boundPatents = boundPatentMapper.selectByUserId(userId);
            if (boundPatents == null || boundPatents.isEmpty()) {
                return PageResult.of(new ArrayList<>(), pageNum, pageSize, 0L);
            }

            // 获取专利ID列表
            List<String> boundPatentIds = boundPatents.stream()
                    .map(BoundPatent::getPatentId)
                    .collect(Collectors.toList());

            // 2. 批量获取统一统计数据（从 patent_statistics 表）
            Map<String, int[]> statisticsMap = batchGetPatentStatisticsFromNewTable(boundPatentIds);

            // 3. 【MyBatis-Plus分页】使用数据库分页查询专利详情
            Page<Patent> page = new Page<>(pageNum, pageSize);
            LambdaQueryWrapper<Patent> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.in(Patent::getPatentId, boundPatentIds);
            IPage<Patent> resultPage = patentMapper.selectPage(page, queryWrapper);

            // 4. 将专利详情与统一统计数据合并
            List<PatentDTO> pageDTOs = resultPage.getRecords().stream()
                    .map(patent -> {
                        PatentDTO dto = convertToDTO(patent);
                        // 使用统一统计数据
                        int[] stats = statisticsMap.getOrDefault(patent.getPatentId(), new int[]{0, 0, 0});
                        dto.setViewCount(Integer.valueOf(stats[0]));
                        dto.setSearchCount(Integer.valueOf(stats[1]));
                        dto.setClickCount(Integer.valueOf(stats[2]));
                        return dto;
                    })
                    .collect(Collectors.toList());

            // 5. 返回分页结果（使用boundPatents总数作为total）
            return PageResult.of(pageDTOs, pageNum, pageSize, (long) boundPatents.size());
        } catch (Exception e) {
            logger.error("分页获取已绑定专利失败，userId: {}", userId, e);
            return PageResult.of(new ArrayList<>(), pageNum, pageSize, 0L);
        }
    }

    @Override
    public PageResult<PatentDTO> getUnboundPatentsPage(String userId, int pageNum, int pageSize) {
        try {
            // 1. 查询该用户已绑定的专利ID列表（用于判断绑定状态）
            // 【优化】优先从缓存获取，缓存未命中时查询数据库
            final List<String> userBoundPatentIds;
            List<String> cachedBoundPatentIds = UserContext.getBoundPatentIds();
            
            if (cachedBoundPatentIds != null) {
                // 缓存命中，直接使用
                userBoundPatentIds = cachedBoundPatentIds;
                logger.debug("已绑定专利缓存命中，userId: {}, 数量: {}", userId, userBoundPatentIds.size());
            } else {
                // 缓存未命中，查询数据库
                List<BoundPatent> userBoundPatents = boundPatentMapper.selectByUserId(userId);
                if (userBoundPatents != null && !userBoundPatents.isEmpty()) {
                    userBoundPatentIds = userBoundPatents.stream()
                            .map(BoundPatent::getPatentId)
                            .collect(Collectors.toList());
                } else {
                    userBoundPatentIds = new ArrayList<>();
                }
                logger.debug("已绑定专利缓存未命中，从数据库查询，userId: {}, 数量: {}", userId, userBoundPatentIds.size());
            }

            // 2. 获取科研团队信息（发明人、所属单位）
            // 【优化】优先从缓存获取，缓存未命中时查询数据库
            ResearchTeam researchTeam = UserContext.getResearchTeam();
            
            if (researchTeam == null) {
                // 缓存未命中，查询数据库
                LambdaQueryWrapper<ResearchTeam> teamWrapper = new LambdaQueryWrapper<>();
                teamWrapper.eq(ResearchTeam::getUserId, userId);
                researchTeam = researchTeamMapper.selectOne(teamWrapper);
                logger.debug("科研团队信息缓存未命中，从数据库查询，userId: {}", userId);
            } else {
                logger.debug("科研团队信息缓存命中，userId: {}, teamName: {}", userId, researchTeam.getTeamName());
            }

            if (researchTeam == null) {
                logger.warn("科研团队信息不存在，userId: {}", userId);
                return PageResult.of(new ArrayList<>(), pageNum, pageSize, 0L);
            }

            String inventor = researchTeam.getTeamName();
            String applicant = researchTeam.getInstitution();

            if ((inventor == null || inventor.isEmpty()) && (applicant == null || applicant.isEmpty())) {
                logger.warn("发明人和所属单位均为空，userId: {}", userId);
                return PageResult.of(new ArrayList<>(), pageNum, pageSize, 0L);
            }

            // 3. 【MyBatis-Plus分页 + 数据库层精确匹配】
            // 使用正则表达式在数据库层进行精确匹配，避免应用层二次过滤
            Page<Patent> page = new Page<>(pageNum, pageSize);
            IPage<Patent> resultPage = patentMapper.selectPatentsByInventorOrApplicant(page, inventor, applicant);

            // 4. 获取当前页专利的统一统计数据
            List<String> currentPagePatentIds = resultPage.getRecords().stream()
                    .map(Patent::getPatentId)
                    .collect(Collectors.toList());
            Map<String, int[]> statisticsMap = batchGetPatentStatisticsFromNewTable(currentPagePatentIds);

            // 5. 转换为DTO并设置绑定状态
            List<PatentDTO> dtoList = resultPage.getRecords().stream()
                    .map(patent -> {
                        PatentDTO dto = convertToDTO(patent);
                        String pid = patent.getPatentId();
                        
                        // 设置绑定状态（只判断当前用户是否绑定）
                        boolean isBound = userBoundPatentIds.contains(pid);
                        dto.setIsBound(isBound);
                        
                        // 设置统一统计数据（从 patent_statistics 表获取）
                        int[] stats = statisticsMap.getOrDefault(pid, new int[]{0, 0, 0});
                        dto.setViewCount(Integer.valueOf(stats[0]));
                        dto.setSearchCount(Integer.valueOf(stats[1]));
                        dto.setClickCount(Integer.valueOf(stats[2]));
                        
                        return dto;
                    })
                    .collect(Collectors.toList());

            // 6. 返回分页结果
            return PageResult.of(dtoList, pageNum, pageSize, resultPage.getTotal());

        } catch (Exception e) {
            logger.error("分页获取可绑定专利失败，userId: {}", userId, e);
            return PageResult.of(new ArrayList<>(), pageNum, pageSize, 0L);
        }
    }

    @Override
    // [已修复 - 问题24] 移除@Transactional，浏览记录写入不应影响主业务
    // 整个方法体用try-catch包裹，确保浏览统计失败不影响专利详情查询
    public void recordView(String patentId, String viewerId) {
        try {
            // 创建浏览记录
            PatentViewRecord viewRecord = new PatentViewRecord();
            viewRecord.setPatentId(patentId);
            viewRecord.setViewerId(viewerId);
            viewRecord.setViewTime(LocalDateTime.now());
            viewRecord.setStayDuration(0);

            // 保存浏览记录
            patentViewRecordMapper.insert(viewRecord);
            logger.debug("专利浏览记录已保存，patentId: {}, viewerId: {}", patentId, viewerId);

            // 更新统一统计数据表（新表）
            try {
                patentStatisticsMapper.incrementViewCount(patentId);
                logger.debug("专利浏览次数已更新，patentId: {}", patentId);
            } catch (Exception e) {
                // 如果统计记录不存在，先初始化再更新
                try {
                    patentStatisticsMapper.initStatistics(patentId);
                    patentStatisticsMapper.incrementViewCount(patentId);
                } catch (Exception initEx) {
                    logger.debug("初始化或更新专利统计失败，patentId: {}, error: {}", patentId, initEx.getMessage());
                }
            }
        } catch (Exception e) {
            // [已修复 - 问题24] 浏览记录写入失败不影响主业务，仅记录日志
            logger.warn("保存专利浏览记录失败，patentId: {}, viewerId: {}", patentId, viewerId, e);
        }
    }

    @Override
    public List<PatentDTO> getUnboundPatents(String userId) {
        logger.info("查询未绑定专利，userId: {}", userId);

        // 先从数据库中获取科研团队信息（团队名称=发明人，所属单位）
        ResearchTeam researchTeam = null;
        try {
            LambdaQueryWrapper<ResearchTeam> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ResearchTeam::getUserId, userId);
            researchTeam = researchTeamMapper.selectOne(wrapper);
        } catch (Exception e) {
            logger.warn("查询科研团队信息失败，userId: {}", userId, e);
            return new ArrayList<>();
        }

        if (researchTeam == null) {
            logger.warn("科研团队信息不存在，userId: {}", userId);
            return new ArrayList<>();
        }

        // 团队名称对应专利的发明人，所属单位对应专利的申请人
        String inventor = researchTeam.getTeamName();
        String applicant = researchTeam.getInstitution();

        logger.debug("发明人(团队名称): {}, 所属单位: {}", inventor, applicant);

        // 如果发明人或所属单位为空，返回空列表
        if ((inventor == null || inventor.isEmpty()) && (applicant == null || applicant.isEmpty())) {
            logger.warn("发明人和所属单位均为空，userId: {}", userId);
            return new ArrayList<>();
        }

        // 从数据库专利表中查询匹配的专利（初步匹配，使用模糊查询）
        List<Patent> matchedPatents = patentMapper.selectByInventorOrApplicant(inventor, applicant);

        if (matchedPatents.isEmpty()) {
            logger.info("未找到匹配的专利，发明人: {}, 所属单位: {}", inventor, applicant);
            return new ArrayList<>();
        }

        // 【精确匹配】在应用层对发明人进行精确匹配
        // 由于专利的发明人通常有多位（以分隔符连接），用户仅是其中之一
        // 需要确保用户发明人精确匹配专利发明人列表中的某一项，而不是模糊匹配导致误匹配
        // 例如：专利发明人 "王五；赵六" 应该匹配用户发明人 "王五"，而不应该匹配 "王"
        final String inventorName = inventor.trim();
        List<PatentDTO> matchedDTOs = matchedPatents.stream()
                .filter(patent -> {
                    if (patent.getInventors() == null || patent.getInventors().isEmpty()) {
                        return false;
                    }
                    // 【分隔符说明】当前专利数据中发明人使用中文冒号分隔
                    // 如果后续数据源的分隔符有变化，在此添加新的分隔符即可
                    // 例如：split("[；,]") 或 split("[；,，、\\s]") 等
                    String[] patentInventors = patent.getInventors().split("[；]");
                    for (String pi : patentInventors) {
                        if (pi.trim().equals(inventorName)) {
                            return true;
                        }
                    }
                    return false;
                })
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        // 查询该用户已绑定的专利ID列表
        List<String> boundPatentIds = new ArrayList<>();
        try {
            List<BoundPatent> boundPatents = boundPatentMapper.selectByUserId(userId);
            if (boundPatents != null && !boundPatents.isEmpty()) {
                boundPatentIds = boundPatents.stream()
                        .map(BoundPatent::getPatentId)
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            logger.warn("查询已绑定专利失败，userId: {}", userId, e);
        }

        logger.debug("已绑定专利数量: {}", boundPatentIds.size());

        // 返回未绑定的专利
        List<String> finalBoundPatentIds = boundPatentIds;
        List<PatentDTO> unboundPatents = matchedDTOs.stream()
                .filter(p -> !finalBoundPatentIds.contains(p.getPatentId()))
                .collect(Collectors.toList());

        logger.info("查询到未绑定专利数量: {}", unboundPatents.size());
        return unboundPatents;
    }

    @Override
    @Transactional
    public boolean bindPatents(List<String> patentIds, String userId, String userType) {
        try {
            for (String patentId : patentIds) {
                // 检查当前用户是否已绑定（允许其他用户绑定同一专利）
                BoundPatent existing = boundPatentMapper.selectByPatentIdAndUserId(patentId, userId);
                if (existing != null) {
                    // 当前用户已绑定，跳过
                    continue;
                }

                // 获取专利信息（使用XML ResultMap处理TEXT类型）
                Patent patent = patentMapper.selectByPatentIdResultMap(patentId);
                if (patent == null) {
                    logger.warn("专利不存在，patentId: {}", patentId);
                    continue;
                }

                // 创建绑定记录（不再存储统计数据，统计数据存储在统一表中）
                BoundPatent boundPatent = new BoundPatent();
                boundPatent.setPatentId(patentId);
                boundPatent.setInventor(patent.getInventors());
                boundPatent.setPatentName(patent.getTitle());
                // 统计数据已移至 patent_statistics 表，这里不再设置
                boundPatent.setUserId(userId);
                boundPatent.setUserType(userType);
                boundPatent.setBindTime(LocalDateTime.now());

                boundPatentMapper.insert(boundPatent);
                
                // 初始化统一统计数据（如果不存在）
                patentStatisticsMapper.initStatistics(patentId);
            }
            logger.info("专利绑定成功，userId: {}, patentIds: {}", userId, patentIds);
            
            // 【缓存一致性】绑定成功后，更新缓存
            updateBoundPatentsCache(userId);
            
            return true;
        } catch (Exception e) {
            logger.error("专利绑定失败", e);
            return false;
        }
    }

    @Override
    public List<PatentDTO> getBoundPatents(String userId) {
        try {
            List<BoundPatent> boundPatents = boundPatentMapper.selectByUserId(userId);
            
            if (boundPatents.isEmpty()) {
                return new ArrayList<>();
            }
            
            // 获取所有绑定专利的ID
            List<String> patentIds = boundPatents.stream()
                    .map(BoundPatent::getPatentId)
                    .collect(Collectors.toList());
            
            // 【优化】批量查询专利详情（1次查询替代N次循环查询，使用XML ResultMap处理TEXT类型）
            List<Patent> patents = patentMapper.selectBatchPatentsByIdsResultMap(patentIds);
            Map<String, Patent> patentMap = patents.stream()
                    .collect(Collectors.toMap(Patent::getPatentId, p -> p));
            
            // 批量获取统一统计数据
            Map<String, int[]> statisticsMap = batchGetPatentStatisticsFromNewTable(patentIds);
            
            return boundPatents.stream()
                    .map(bp -> {
                        Patent patent = patentMap.get(bp.getPatentId());  // 从Map获取，O(1)复杂度
                        if (patent != null) {
                            PatentDTO dto = convertToDTO(patent);
                            // 设置统一统计数据（从 patent_statistics 表获取）
                            int[] stats = statisticsMap.getOrDefault(bp.getPatentId(), new int[]{0, 0, 0});
                            dto.setViewCount(Integer.valueOf(stats[0]));
                            dto.setSearchCount(Integer.valueOf(stats[1]));
                            dto.setClickCount(Integer.valueOf(stats[2]));
                            return dto;
                        }
                        return null;
                    })
                    .filter(p -> p != null)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("获取绑定专利失败，userId: {}", userId, e);
            return new ArrayList<>();
        }
    }

    @Override
    @Transactional
    public int unbindPatents(List<String> patentIds, String userId) {
        int count = 0;
        try {
            for (String patentId : patentIds) {
                BoundPatent existing = boundPatentMapper.selectByPatentIdAndUserId(patentId, userId);
                if (existing != null) {
                    boundPatentMapper.deleteById(existing.getId());
                    count++;
                    logger.debug("专利解绑成功，patentId: {}, userId: {}", patentId, userId);
                }
            }
            logger.info("专利解绑完成，共解绑 {} 条记录，userId: {}", count, userId);
            
            // 【缓存一致性】解绑成功后，更新缓存
            if (count > 0) {
                updateBoundPatentsCache(userId);
            }
        } catch (Exception e) {
            logger.error("专利解绑失败，userId: {}", userId, e);
        }
        return count;
    }

    @Override
    @Transactional
    public int unbindAllPatents(String userId) {
        try {
            // 查询用户所有绑定的专利
            List<BoundPatent> boundPatents = boundPatentMapper.selectByUserId(userId);
            
            if (boundPatents == null || boundPatents.isEmpty()) {
                logger.info("用户没有绑定任何专利，无需解绑，userId: {}", userId);
                return 0;
            }

            // 获取所有专利ID
            List<String> patentIds = boundPatents.stream()
                    .map(BoundPatent::getPatentId)
                    .collect(Collectors.toList());

            // 批量删除绑定记录
            int count = 0;
            for (BoundPatent bp : boundPatents) {
                boundPatentMapper.deleteById(bp.getId());
                count++;
            }

            logger.info("用户所有专利解绑完成，共解绑 {} 条记录，userId: {}", count, userId);

            // 【缓存一致性】解绑成功后，更新缓存
            updateBoundPatentsCache(userId);

            return count;
        } catch (Exception e) {
            logger.error("批量解绑用户所有专利失败，userId: {}", userId, e);
            throw e;
        }
    }

    @Override
    public int[] getPatentStatistics(String patentId) {
        // 从统一统计数据表获取统计数据
        try {
            Map<String, Object> stats = patentStatisticsMapper.getStatisticsByPatentId(patentId);
            if (stats != null) {
                int viewCount = stats.get("view_count") != null ? ((Number) stats.get("view_count")).intValue() : 0;
                int searchCount = stats.get("search_count") != null ? ((Number) stats.get("search_count")).intValue() : 0;
                int clickCount = stats.get("click_count") != null ? ((Number) stats.get("click_count")).intValue() : 0;
                return new int[]{viewCount, searchCount, clickCount};
            }
        } catch (Exception e) {
            logger.warn("获取专利统计数据失败，patentId: {}", patentId, e);
        }
        return new int[]{0, 0, 0};
    }

    @Override
    public Map<String, int[]> batchGetPatentStatistics(List<String> patentIds) {
        // 委托给新的方法从统一统计数据表获取数据
        return batchGetPatentStatisticsFromNewTable(patentIds);
    }

    @Override
    public void recordSearch(String patentId) {
        try {
            // 更新统一统计数据表（新表）
            patentStatisticsMapper.incrementSearchCount(patentId);
            logger.debug("专利搜索次数已更新，patentId: {}", patentId);
        } catch (Exception e) {
            // 如果统计记录不存在，先初始化再更新
            try {
                patentStatisticsMapper.initStatistics(patentId);
                patentStatisticsMapper.incrementSearchCount(patentId);
            } catch (Exception initEx) {
                logger.debug("初始化或更新专利搜索统计失败，patentId: {}", patentId);
            }
        }
    }

    @Override
    public void recordClick(String patentId) {
        try {
            // 更新统一统计数据表（新表）
            patentStatisticsMapper.incrementClickCount(patentId);
            logger.debug("专利点击次数已更新，patentId: {}", patentId);
        } catch (Exception e) {
            // 如果统计记录不存在，先初始化再更新
            try {
                patentStatisticsMapper.initStatistics(patentId);
                patentStatisticsMapper.incrementClickCount(patentId);
            } catch (Exception initEx) {
                logger.debug("初始化或更新专利点击统计失败，patentId: {}", patentId);
            }
        }
    }

    @Override
    public List<Map<String, Object>> getHotBoundPatents(int limit) {
        List<Map<String, Object>> result = new ArrayList<>();

        try {
            // 从统一统计数据表查询热门专利
            List<Map<String, Object>> hotStats = patentStatisticsMapper.getHotPatentStatistics(limit);
            
            for (Map<String, Object> stat : hotStats) {
                String patentId = (String) stat.get("patent_id");
                Patent patent = patentMapper.selectByPatentIdResultMap(patentId);
                
                // 如果专利不存在，跳过（可能已被删除）
                if (patent == null) {
                    logger.warn("热门专利不存在，跳过，patentId: {}", patentId);
                    continue;
                }
                
                Map<String, Object> item = new HashMap<>();
                item.put("patentId", patentId);
                item.put("patentName", patent.getTitle());
                item.put("inventor", patent.getInventors());
                item.put("viewCount", stat.get("view_count") != null ? stat.get("view_count") : 0);
                item.put("searchCount", stat.get("search_count") != null ? stat.get("search_count") : 0);
                item.put("clickCount", stat.get("click_count") != null ? stat.get("click_count") : 0);
                result.add(item);
            }
        } catch (Exception e) {
            logger.warn("获取热门专利列表失败", e);
        }

        return result;
    }

    /**
     * 从新的专利统计数据表批量获取统计数据
     * 
     * @param patentIds 专利ID列表
     * @return 统计数据Map，key为patentId，value为统计数组[viewCount, searchCount, clickCount]
     */
    private Map<String, int[]> batchGetPatentStatisticsFromNewTable(List<String> patentIds) {
        Map<String, int[]> result = new HashMap<>();

        if (patentIds == null || patentIds.isEmpty()) {
            return result;
        }

        try {
            List<Map<String, Object>> statsList = patentStatisticsMapper.getStatisticsByPatentIds(patentIds);
            
            for (Map<String, Object> stat : statsList) {
                String patentId = (String) stat.get("patent_id");
                int viewCount = stat.get("view_count") != null ? ((Number) stat.get("view_count")).intValue() : 0;
                int searchCount = stat.get("search_count") != null ? ((Number) stat.get("search_count")).intValue() : 0;
                int clickCount = stat.get("click_count") != null ? ((Number) stat.get("click_count")).intValue() : 0;
                
                result.put(patentId, new int[]{viewCount, searchCount, clickCount});
            }
        } catch (Exception e) {
            logger.warn("批量获取专利统计数据失败", e);
        }

        return result;
    }

    /**
     * 记录搜索日志
     */
    private void recordSearchLog(PatentSearchRequest request) {
        try {
            // 只有当有搜索条件时才记录日志
            boolean hasSearchCondition = (request.getKeyword() != null && !request.getKeyword().isEmpty())
                    || (request.getPatentType() != null && !request.getPatentType().isEmpty())
                    || (request.getLegalStatus() != null && !request.getLegalStatus().isEmpty())
                    || (request.getValidity() != null && !request.getValidity().isEmpty());

            if (!hasSearchCondition) {
                return;
            }

            SearchLog log = new SearchLog();
            log.setKeyword(request.getKeyword());
            log.setSearchTime(LocalDateTime.now());

            searchLogMapper.insert(log);
            logger.debug("搜索日志已记录，keyword: {}", request.getKeyword());

            // 清除相关统计缓存，确保数据一致性
            clearStatisticsCache();
        } catch (Exception e) {
            logger.warn("记录搜索日志失败", e);
        }
    }

    /**
     * 清除统计相关的Redis缓存，并异步重建
     */
    private void clearStatisticsCache() {
        try {
            statisticsService.clearAndRebuildHotSearchKeywordsCache();
        } catch (Exception e) {
            logger.warn("清除Redis缓存失败", e);
        }
    }

    // ========== 专利增删改实现 ==========

    @Override
    @Transactional
    public PatentDTO createPatent(PatentCreateRequest request) {
        logger.info("新增专利，patentId: {}, title: {}", request.getPatentId(), request.getTitle());

        // 校验必填字段
        if (request.getPatentId() == null || request.getPatentId().isEmpty()) {
            logger.warn("专利ID不能为空");
            // [已修复 - 问题36] 使用BusinessException替代IllegalArgumentException
            throw new BusinessException(StateCode.BAD_REQUEST, "专利ID不能为空");
        }
        if (request.getTitle() == null || request.getTitle().isEmpty()) {
            logger.warn("专利标题不能为空");
            // [已修复 - 问题36] 使用BusinessException替代IllegalArgumentException
            throw new BusinessException(StateCode.BAD_REQUEST, "专利标题不能为空");
        }

        // 检查专利ID是否已存在（使用XML ResultMap处理TEXT类型）
        Patent existing = patentMapper.selectByPatentIdResultMap(request.getPatentId());
        if (existing != null) {
            logger.warn("专利ID已存在，patentId: {}", request.getPatentId());
            // [已修复 - 问题36] 使用BusinessException替代IllegalArgumentException
            throw new BusinessException(StateCode.BAD_REQUEST, "专利ID已存在");
        }

        // 创建专利实体
        Patent patent = new Patent();
        patent.setPatentId(request.getPatentId());
        patent.setTitle(request.getTitle());
        patent.setApplicant(request.getApplicant());
        patent.setCurrentOwner(request.getCurrentOwner());
        patent.setInventors(request.getInventors());
        patent.setPatentType(request.getPatentType());
        patent.setLegalStatus(request.getLegalStatus());
        patent.setValidity(request.getValidity());
        patent.setApplicationDate(request.getApplicationDate());
        patent.setPublicationDate(request.getPublicationDate());
        patent.setGrantDate(request.getGrantDate());
        patent.setEstimatedExpiryDate(request.getEstimatedExpiryDate());
        patent.setAbstractText(request.getAbstractText());
        patent.setTechnicalEffectSentences(request.getTechnicalEffectSentences());
        patent.setTechField(request.getTechField());
        patent.setTechnicalStability(request.getTechnicalStability());
        patent.setTechnicalAdvancement(request.getTechnicalAdvancement());
        patent.setSourceLink(request.getSourceLink());

        // 保存专利
        patentMapper.insert(patent);
        logger.info("专利新增成功，patentId: {}", patent.getPatentId());

        return convertToDTO(patent);
    }

    @Override
    @Transactional
    public PatentDTO updatePatent(String patentId, PatentCreateRequest request) {
        logger.info("更新专利，patentId: {}", patentId);

        // 查询专利是否存在（使用XML ResultMap处理TEXT类型）
        Patent existing = patentMapper.selectByPatentIdResultMap(patentId);
        if (existing == null) {
            logger.warn("专利不存在，patentId: {}", patentId);
            return null;
        }

        // 更新专利信息（只更新非空字段）
        if (request.getTitle() != null && !request.getTitle().isEmpty()) {
            existing.setTitle(request.getTitle());
        }
        if (request.getApplicant() != null) {
            existing.setApplicant(request.getApplicant());
        }
        if (request.getCurrentOwner() != null) {
            existing.setCurrentOwner(request.getCurrentOwner());
        }
        if (request.getInventors() != null) {
            existing.setInventors(request.getInventors());
        }
        if (request.getPatentType() != null) {
            existing.setPatentType(request.getPatentType());
        }
        if (request.getLegalStatus() != null) {
            existing.setLegalStatus(request.getLegalStatus());
        }
        if (request.getValidity() != null) {
            existing.setValidity(request.getValidity());
        }
        if (request.getApplicationDate() != null) {
            existing.setApplicationDate(request.getApplicationDate());
        }
        if (request.getPublicationDate() != null) {
            existing.setPublicationDate(request.getPublicationDate());
        }
        if (request.getGrantDate() != null) {
            existing.setGrantDate(request.getGrantDate());
        }
        if (request.getEstimatedExpiryDate() != null) {
            existing.setEstimatedExpiryDate(request.getEstimatedExpiryDate());
        }
        if (request.getAbstractText() != null) {
            existing.setAbstractText(request.getAbstractText());
        }
        if (request.getTechnicalEffectSentences() != null) {
            existing.setTechnicalEffectSentences(request.getTechnicalEffectSentences());
        }
        if (request.getTechField() != null) {
            existing.setTechField(request.getTechField());
        }
        if (request.getTechnicalStability() != null) {
            existing.setTechnicalStability(request.getTechnicalStability());
        }
        if (request.getTechnicalAdvancement() != null) {
            existing.setTechnicalAdvancement(request.getTechnicalAdvancement());
        }
        if (request.getSourceLink() != null) {
            existing.setSourceLink(request.getSourceLink());
        }

        // 更新专利
        patentMapper.updateById(existing);
        logger.info("专利更新成功，patentId: {}", patentId);

        return convertToDTO(existing);
    }

    @Override
    @Transactional
    public boolean deletePatent(String patentId) {
        logger.info("删除专利，patentId: {}", patentId);

        // 查询专利是否存在（使用XML ResultMap处理TEXT类型）
        Patent existing = patentMapper.selectByPatentIdResultMap(patentId);
        if (existing == null) {
            logger.warn("专利不存在，patentId: {}", patentId);
            return false;
        }

        // 检查是否有绑定记录
        List<BoundPatent> boundPatents = boundPatentMapper.selectByPatentId(patentId);
        if (boundPatents != null && !boundPatents.isEmpty()) {
            logger.warn("专利存在绑定记录，无法删除，patentId: {}, 绑定数量: {}", patentId, boundPatents.size());
            throw new IllegalStateException("专利存在绑定记录，无法删除。请先解绑所有绑定记录后再删除专利。");
        }

        // 删除专利
        patentMapper.deleteById(patentId);
        logger.info("专利删除成功，patentId: {}", patentId);

        return true;
    }
    
    /**
     * 更新用户已绑定专利的缓存
     * 在绑定/解绑操作后调用，保证缓存一致性
     * 
     * @param userId 用户ID
     */
    private void updateBoundPatentsCache(String userId) {
        try {
            // 获取当前已绑定的专利ID列表
            List<PatentDTO> boundPatents = getBoundPatents(userId);
            List<String> boundPatentIds = boundPatents != null ? 
                    boundPatents.stream().map(PatentDTO::getPatentId).collect(Collectors.toList()) : null;
            
            // 使用 UserCacheService 更新缓存
            userCacheService.updateBoundPatentsCache(userId, boundPatentIds);
            logger.info("已通过 UserCacheService 更新完整用户缓存，userId: {}", userId);
        } catch (Exception e) {
            logger.error("更新已绑定专利缓存失败，userId: {}", userId, e);
        }
    }
}