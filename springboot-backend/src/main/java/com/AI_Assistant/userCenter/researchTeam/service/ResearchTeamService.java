package com.AI_Assistant.userCenter.researchTeam.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.AI_Assistant.asyncMessage.intentionMessage.entity.IntentionMessage;
import com.AI_Assistant.asyncMessage.intentionMessage.mapper.IntentionMessageMapper;
import com.AI_Assistant.backend.patent.dto.PatentDTO;
import com.AI_Assistant.backend.patent.service.PatentService;
import com.AI_Assistant.backend.statistics.service.StatisticsService;
import com.AI_Assistant.common.Result;
import com.AI_Assistant.common.dto.PageResult;
import com.AI_Assistant.common.exception.NotFoundException;
import com.AI_Assistant.common.util.UserCacheService;
import com.AI_Assistant.common.util.UserContext;
import com.AI_Assistant.userCenter.researchTeam.dto.BindResearchTeamRequest;
import com.AI_Assistant.userCenter.researchTeam.dto.ResearchTeamAccount;
import com.AI_Assistant.userCenter.researchTeam.dto.ResearchTeamUserVO;
import com.AI_Assistant.userCenter.researchTeam.dto.UpdateResearchTeamRequest;
import com.AI_Assistant.userCenter.researchTeam.entity.ResearchTeam;
import com.AI_Assistant.userCenter.researchTeam.mapper.ResearchTeamMapper;
import com.AI_Assistant.userCenter.user.dto.UserFullInfoDTO;
import com.AI_Assistant.userCenter.user.entity.User;
import com.AI_Assistant.userCenter.user.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import cn.hutool.core.util.IdUtil;

/**
 * 科研团队服务类
 */
@Service
public class ResearchTeamService {

    private static final Logger logger = LoggerFactory.getLogger(ResearchTeamService.class);

    private static final String BIND_LOCK_PREFIX = "lock:bind:research_team:";
    private static final long LOCK_EXPIRE_SECONDS = 10;

    /**
     * [已修复 - 问题60] Lua脚本：原子释放锁（仅当锁值匹配时才删除）
     */
    private static final String RELEASE_LOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "    return redis.call('del', KEYS[1]) " +
            "else " +
            "    return 0 " +
            "end";

    /**
     * [已修复 - 问题60] Lua脚本：原子续期锁（仅当锁值匹配时才刷新过期时间）
     */
    private static final String RENEW_LOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "    return redis.call('expire', KEYS[1], ARGV[2]) " +
            "else " +
            "    return 0 " +
            "end";

    private static final DefaultRedisScript<Long> RELEASE_LOCK_REDIS_SCRIPT;
    private static final DefaultRedisScript<Long> RENEW_LOCK_REDIS_SCRIPT;

    static {
        RELEASE_LOCK_REDIS_SCRIPT = new DefaultRedisScript<>(RELEASE_LOCK_SCRIPT, Long.class);
        RENEW_LOCK_REDIS_SCRIPT = new DefaultRedisScript<>(RENEW_LOCK_SCRIPT, Long.class);
    }

    @Autowired
    private ResearchTeamMapper researchTeamMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private IntentionMessageMapper intentionMessageMapper;

    @Autowired
    private PatentService patentService;

    @Autowired
    private StatisticsService statisticsService;

    @Autowired
    @Lazy
    private UserCacheService userCacheService;

    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    /**
     * 获取科研团队账号信息（包含绑定的专利列表）
     * 登录时调用此方法，减少前端请求次数
     * 
     * @param userId 用户ID
     * @return 科研团队账号信息，包含绑定的专利列表
     */
    public ResearchTeamAccount getResearchTeamAccount(String userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return null;
        }

        LambdaQueryWrapper<ResearchTeam> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ResearchTeam::getUserId, userId);
        ResearchTeam team = researchTeamMapper.selectOne(wrapper);

        return buildResearchTeamAccountWithPatents(user, team);
    }

    /**
     * 构建科研团队账户信息（包含专利列表）
     */
    private ResearchTeamAccount buildResearchTeamAccountWithPatents(User user, ResearchTeam team) {
        ResearchTeamAccount account = buildResearchTeamAccount(user, team);
        
        // 查询绑定的专利列表
        List<PatentDTO> boundPatents = patentService.getBoundPatents(user.getId());
        account.setPatents(boundPatents);
        
        return account;
    }

    /**
     * 用户绑定科研团队账号
     */
    @Transactional
    public Result<ResearchTeamAccount> bindResearchTeam(BindResearchTeamRequest request) {
        logger.info("用户绑定科研团队账号请求");

        User currentUser = UserContext.getUser();
        String lockKey = BIND_LOCK_PREFIX + currentUser.getId();
        // [已修复 - 问题60] 使用UUID作为锁值，确保只有锁持有者能释放锁
        // 原代码：锁值固定为"1"，任何线程都能释放锁，存在误删风险
        String lockValue = IdUtil.simpleUUID();

        // 使用Redis分布式锁防止并发绑定
        Boolean lockAcquired = redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, LOCK_EXPIRE_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
        if (lockAcquired == null || !lockAcquired) {
            logger.warn("用户绑定科研团队账号请求过于频繁，请稍后重试，userId: {}", currentUser.getId());
            return Result.<ResearchTeamAccount>error("请求过于频繁，请稍后重试", null);
        }

        // [已修复 - 问题60] 添加锁续期机制，防止业务执行时间超过锁过期时间
        Thread renewThread = startLockRenewal(lockKey, lockValue);
        try {
            if (currentUser.getUserType() != null && currentUser.getUserType() == 2) {
                logger.warn("用户已绑定科研团队账号，userId: {}", currentUser.getId());
                return Result.<ResearchTeamAccount>error("用户已绑定科研团队账号", null);
            }

            // 检查数据库中是否已存在科研团队记录
            LambdaQueryWrapper<ResearchTeam> checkWrapper = new LambdaQueryWrapper<>();
            checkWrapper.eq(ResearchTeam::getUserId, currentUser.getId());
            ResearchTeam existingTeam = researchTeamMapper.selectOne(checkWrapper);
            if (existingTeam != null) {
                logger.warn("数据库中已存在该用户的科研团队记录，userId: {}", currentUser.getId());
                return Result.<ResearchTeamAccount>error("用户已绑定科研团队账号", null);
            }

            if (request.getTeamName() == null || request.getTeamName().trim().isEmpty()) {
                return Result.<ResearchTeamAccount>error("团队名称不能为空", null);
            }

            currentUser.setUserType(2);
            userMapper.updateById(currentUser);
            logger.info("用户类型已更新为科研团队用户，userId: {}", currentUser.getId());

            ResearchTeam team = new ResearchTeam();
            team.setId(IdUtil.simpleUUID());
            team.setUserId(currentUser.getId());
            team.setTeamName(request.getTeamName());
            team.setContactName(request.getContactName());
            team.setInstitution(request.getInstitution());
            team.setTeamCode(request.getTeamCode());
            team.setResearchDomain(request.getResearchDomain());

            researchTeamMapper.insert(team);
            logger.info("科研团队账号绑定成功，userId: {}, teamId: {}", currentUser.getId(), team.getId());

            // 更新统计聚合表
            statisticsService.recordResearchTeam();

            // 更新 Redis 缓存中的用户信息
            ResearchTeamAccount account = buildResearchTeamAccountWithPatents(currentUser, team);
            List<String> boundPatentIds = account.getPatents() != null ? 
                    account.getPatents().stream().map(PatentDTO::getPatentId).collect(Collectors.toList()) : null;
            
            UserFullInfoDTO fullInfo = UserFullInfoDTO.builder()
                    .user(currentUser)
                    .researchTeamAccount(account)
                    .boundPatents(account.getPatents())
                    .boundPatentIds(boundPatentIds)
                    .build();
            userCacheService.updateFullUserCache(fullInfo);

            return Result.ok("科研团队账号绑定成功", account);
        } catch (Exception e) {
            logger.error("科研团队账号绑定失败", e);
            throw e;
        } finally {
            // [已修复 - 问题60] 停止锁续期线程
            if (renewThread != null) {
                renewThread.interrupt();
            }
            // [已修复 - 问题60] 使用Lua脚本原子释放锁，仅当锁值匹配时才删除
            // 原代码：直接redisTemplate.delete(lockKey)，可能误删他人持有的锁
            releaseLock(lockKey, lockValue);
        }
    }

    /**
     * [已修复 - 问题60] 启动锁续期线程
     * 防止业务执行时间超过锁过期时间导致锁被自动释放
     */
    private Thread startLockRenewal(String lockKey, String lockValue) {
        Thread renewThread = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(LOCK_EXPIRE_SECONDS / 3 * 1000);
                    // 使用Lua脚本原子续期
                    Long result = redisTemplate.execute(
                            RENEW_LOCK_REDIS_SCRIPT,
                            java.util.Collections.singletonList(lockKey),
                            lockValue,
                            String.valueOf(LOCK_EXPIRE_SECONDS)
                    );
                    if (result == null || result == 0) {
                        // 锁已被释放或被其他线程持有，停止续期
                        break;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "lock-renew-" + lockKey);
        renewThread.setDaemon(true);
        renewThread.start();
        return renewThread;
    }

    /**
     * [已修复 - 问题60] 使用Lua脚本原子释放锁
     */
    private void releaseLock(String lockKey, String lockValue) {
        redisTemplate.execute(
                RELEASE_LOCK_REDIS_SCRIPT,
                java.util.Collections.singletonList(lockKey),
                lockValue
        );
    }

    /**
     * 更新科研团队信息
     * 注意：如果机构(institution)或团队名称(teamName)发生变化，会自动解除所有已绑定专利
     */
    @Transactional
    public Result<ResearchTeamAccount> updateResearchTeam(String teamUserId, UpdateResearchTeamRequest request) {
        logger.info("更新科研团队信息请求，teamUserId: {}", teamUserId);

        User currentUser = UserContext.getUser();

        if (!currentUser.getId().equals(teamUserId)) {
            logger.warn("用户无权限更新该科研团队信息，currentUserId: {}, targetUserId: {}", currentUser.getId(), teamUserId);
            return Result.<ResearchTeamAccount>error("无权限更新该科研团队信息", null);
        }

        if (currentUser.getUserType() == null || currentUser.getUserType() != 2) {
            logger.warn("用户不是科研团队用户，无法更新科研团队信息，userId: {}", currentUser.getId());
            return Result.<ResearchTeamAccount>error("用户不是科研团队用户，无法更新科研团队信息", null);
        }

        LambdaQueryWrapper<ResearchTeam> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ResearchTeam::getUserId, teamUserId);
        ResearchTeam team = researchTeamMapper.selectOne(wrapper);

        if (team == null) {
            logger.warn("科研团队信息不存在，userId: {}", teamUserId);
            return Result.<ResearchTeamAccount>error("科研团队信息不存在", null);
        }

        // 记录原始信息，用于判断是否需要解绑专利
        String originalInstitution = team.getInstitution();
        String originalTeamName = team.getTeamName();

        try {
            boolean needUnbindPatents = false;
            List<String> changedFields = new ArrayList<>();

            if (request.getTeamName() != null && !request.getTeamName().equals(originalTeamName)) {
                needUnbindPatents = true;
                changedFields.add("teamName");
                logger.info("团队名称发生变更，originalTeamName: {}, newTeamName: {}", originalTeamName, request.getTeamName());
                team.setTeamName(request.getTeamName());
            }
            if (request.getContactName() != null) {
                team.setContactName(request.getContactName());
            }
            if (request.getInstitution() != null && !request.getInstitution().equals(originalInstitution)) {
                needUnbindPatents = true;
                changedFields.add("institution");
                logger.info("机构发生变更，originalInstitution: {}, newInstitution: {}", originalInstitution, request.getInstitution());
                team.setInstitution(request.getInstitution());
            }
            if (request.getTeamCode() != null) {
                team.setTeamCode(request.getTeamCode());
            }
            if (request.getResearchDomain() != null) {
                team.setResearchDomain(request.getResearchDomain());
            }
            team.setUpdateTime(LocalDateTime.now());

            researchTeamMapper.updateById(team);
            logger.info("科研团队信息更新成功，teamId: {}", team.getId());

            // 如果关键信息发生变化，自动解除所有已绑定专利
            if (needUnbindPatents) {
                logger.info("关键信息已变更({})，自动解除所有已绑定专利，userId: {}", changedFields, teamUserId);
                int unboundCount = patentService.unbindAllPatents(teamUserId);
                logger.info("自动解除专利绑定完成，共解除 {} 条绑定记录", unboundCount);
            }

            // 从数据库重新加载用户信息，确保缓存一致性
            User updatedUser = userMapper.selectById(currentUser.getId());
            if (updatedUser != null) {
                // 更新完整用户缓存
                ResearchTeamAccount account = buildResearchTeamAccountWithPatents(updatedUser, team);
                List<String> boundPatentIds = account.getPatents() != null ? 
                        account.getPatents().stream().map(PatentDTO::getPatentId).collect(Collectors.toList()) : null;
                
                UserFullInfoDTO fullInfo = UserFullInfoDTO.builder()
                        .user(updatedUser)
                        .researchTeamAccount(account)
                        .boundPatents(account.getPatents())
                        .boundPatentIds(boundPatentIds)
                        .build();
                userCacheService.updateFullUserCache(fullInfo);
            }

            return Result.ok("科研团队信息更新成功", buildResearchTeamAccountWithPatents(currentUser, team));
        } catch (Exception e) {
            logger.error("科研团队信息更新失败", e);
            throw e;
        }
    }

    /**
     * 查询科研团队用户列表
     */
    public List<ResearchTeamUserVO> getResearchTeamUserList() {
        logger.info("查询科研团队账号列表");

        LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.eq(User::getUserType, 2);
        List<User> users = userMapper.selectList(userWrapper);

        if (users == null || users.isEmpty()) {
            logger.info("未找到科研团队用户");
            return new ArrayList<>();
        }

        List<String> userIds = users.stream().map(User::getId).collect(Collectors.toList());
        LambdaQueryWrapper<ResearchTeam> teamWrapper = new LambdaQueryWrapper<>();
        teamWrapper.in(ResearchTeam::getUserId, userIds);
        List<ResearchTeam> teams = researchTeamMapper.selectList(teamWrapper);

        Map<String, ResearchTeam> teamMap = teams.stream()
                .collect(Collectors.toMap(ResearchTeam::getUserId, Function.identity()));

        List<ResearchTeamUserVO> result = new ArrayList<>();
        for (User user : users) {
            ResearchTeam team = teamMap.get(user.getId());
            ResearchTeamUserVO vo = new ResearchTeamUserVO();
            vo.setId(user.getId());
            vo.setUsername(user.getUsername());
            vo.setWechatNickname(user.getWechatNickname());
            vo.setWechatAvatar(user.getWechatAvatar());
            vo.setStatus(user.getStatus());
            vo.setTeamName(team != null ? team.getTeamName() : null);
            vo.setInstitution(team != null ? team.getInstitution() : null);
            vo.setResearchDomain(team != null ? team.getResearchDomain() : null);
            result.add(vo);
        }

        logger.info("查询到科研团队用户数量: {}", result.size());
        return result;
    }

    /**
     * 分页查询科研团队用户列表
     */
    public PageResult<ResearchTeamUserVO> getResearchTeamUserList(Integer pageNum, Integer pageSize) {
        logger.info("分页查询科研团队账号列表，pageNum: {}, pageSize: {}", pageNum, pageSize);

        // 设置默认分页参数
        int pageNumber = pageNum != null && pageNum > 0 ? pageNum : 1;
        int pageSizeNumber = pageSize != null && pageSize > 0 ? pageSize : 10;

        // 分页查询用户
        Page<User> page = new Page<>(pageNumber, pageSizeNumber);
        LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.eq(User::getUserType, 2);
        IPage<User> userPage = userMapper.selectPage(page, userWrapper);

        List<User> users = userPage.getRecords();
        long total = userPage.getTotal();

        if (users == null || users.isEmpty()) {
            logger.info("未找到科研团队用户");
            return PageResult.of(new ArrayList<>(), pageNumber, pageSizeNumber, total);
        }

        // 查询科研团队信息
        List<String> userIds = users.stream().map(User::getId).collect(Collectors.toList());
        LambdaQueryWrapper<ResearchTeam> teamWrapper = new LambdaQueryWrapper<>();
        teamWrapper.in(ResearchTeam::getUserId, userIds);
        List<ResearchTeam> teams = researchTeamMapper.selectList(teamWrapper);

        Map<String, ResearchTeam> teamMap = teams.stream()
                .collect(Collectors.toMap(ResearchTeam::getUserId, Function.identity()));

        // 组装结果
        List<ResearchTeamUserVO> result = new ArrayList<>();
        for (User user : users) {
            ResearchTeam team = teamMap.get(user.getId());
            ResearchTeamUserVO vo = new ResearchTeamUserVO();
            vo.setId(user.getId());
            vo.setUsername(user.getUsername());
            vo.setWechatNickname(user.getWechatNickname());
            vo.setWechatAvatar(user.getWechatAvatar());
            vo.setStatus(user.getStatus());
            vo.setTeamName(team != null ? team.getTeamName() : null);
            vo.setInstitution(team != null ? team.getInstitution() : null);
            vo.setResearchDomain(team != null ? team.getResearchDomain() : null);
            result.add(vo);
        }

        logger.info("查询到科研团队用户数量: {}", result.size());
        return PageResult.of(result, pageNumber, pageSizeNumber, total);
    }

    /**
     * 查询单个科研团队用户
     */
    public ResearchTeamUserVO getResearchTeamUserById(String userId) {
        logger.info("查询单个科研团队用户，userId: {}", userId);

        User user = userMapper.selectById(userId);
        if (user == null) {
            logger.warn("用户不存在，userId: {}", userId);
            return null;
        }

        if (user.getUserType() == null || user.getUserType() != 2) {
            logger.warn("该用户不是科研团队用户，userId: {}", userId);
            return null;
        }

        LambdaQueryWrapper<ResearchTeam> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ResearchTeam::getUserId, userId);
        ResearchTeam team = researchTeamMapper.selectOne(wrapper);

        ResearchTeamUserVO vo = new ResearchTeamUserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setWechatNickname(user.getWechatNickname());
        vo.setWechatAvatar(user.getWechatAvatar());
        vo.setStatus(user.getStatus());
        vo.setTeamName(team != null ? team.getTeamName() : null);
        vo.setInstitution(team != null ? team.getInstitution() : null);
        vo.setResearchDomain(team != null ? team.getResearchDomain() : null);
        return vo;
    }

    /**
     * 用户注销科研团队账号
     */
    @Transactional
    public Result<Void> unbindResearchTeam() {
        logger.info("用户注销科研团队账号请求");

        User currentUser = UserContext.getUser();

        // 检查 research_team 表中是否存在该用户的记录（数据一致性检查）
        LambdaQueryWrapper<ResearchTeam> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ResearchTeam::getUserId, currentUser.getId());
        ResearchTeam team = researchTeamMapper.selectOne(wrapper);

        if (team == null) {
            logger.warn("用户未绑定科研团队账户，无法注销，userId: {}", currentUser.getId());
            throw new NotFoundException("用户未绑定科研团队账户");
        }

        // 调用有参方法并返回结果
        return unbindResearchTeam(currentUser);
    }

    /**
     * 用户注销科研团队账号（内部方法）
     */
    @Transactional
    public Result<Void> unbindResearchTeam(User currentUser) {
        logger.info("用户注销科研团队账号请求，userId: {}", currentUser.getId());

        try {
            // 1. 解绑所有绑定的专利
            try {
                List<PatentDTO> boundPatents = patentService.getBoundPatents(currentUser.getId());
                if (boundPatents != null && !boundPatents.isEmpty()) {
                    List<String> patentIds = boundPatents.stream()
                            .map(PatentDTO::getPatentId)
                            .collect(Collectors.toList());
                    int unboundCount = patentService.unbindPatents(patentIds, currentUser.getId());
                    logger.info("已解绑 {} 个专利", unboundCount);
                }
            } catch (Exception e) {
                logger.warn("解绑专利失败，可能专利表尚未初始化", e);
            }

            // 2. 将该团队作为接收者的待处理意向留言状态改为拒绝
            LambdaQueryWrapper<IntentionMessage> msgWrapper = new LambdaQueryWrapper<>();
            msgWrapper.eq(IntentionMessage::getReceiverId, currentUser.getId())
                      .eq(IntentionMessage::getReceiverType, "research_team");
            List<IntentionMessage> messages = intentionMessageMapper.selectList(msgWrapper);

            int rejectedCount = 0;
            Date now = new Date();
            if (messages != null && !messages.isEmpty()) {
                for (IntentionMessage message : messages) {
                    boolean needUpdate = false;
                    if ("pending".equals(message.getTeamStatus())) {
                        message.setTeamStatus("rejected");
                        message.setTeamAuditTime(now);
                        needUpdate = true;
                    }
                    if (needUpdate) {
                        intentionMessageMapper.updateById(message);
                        rejectedCount++;
                    }
                }
                logger.info("已将 {} 条待审核的意向留言状态改为拒绝", rejectedCount);
            }

            // 3. 删除科研团队信息
            LambdaQueryWrapper<ResearchTeam> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ResearchTeam::getUserId, currentUser.getId());
            ResearchTeam team = researchTeamMapper.selectOne(wrapper);

            if (team != null) {
                researchTeamMapper.deleteById(team.getId());
                logger.info("科研团队信息已删除，teamId: {}", team.getId());
            }

            // 4. 将用户类型更新为普通用户
            currentUser.setUserType(0);
            userMapper.updateById(currentUser);
            logger.info("用户类型已更新为普通用户，userId: {}", currentUser.getId());

            // 在事务提交后更新缓存，确保数据库和缓存一致性
            final User userToUpdate = currentUser;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    UserFullInfoDTO fullInfo = UserFullInfoDTO.builder()
                            .user(userToUpdate)
                            .build();
                    userCacheService.updateFullUserCache(fullInfo);
                }
            });

            return Result.ok("科研团队账号注销成功");
        } catch (Exception e) {
            logger.error("注销科研团队账号失败", e);
            throw e;
        }
    }

    /**
     * 构建科研团队账户信息
     */
    private ResearchTeamAccount buildResearchTeamAccount(User user, ResearchTeam team) {
        ResearchTeamAccount account = new ResearchTeamAccount();
        account.setId(user.getId());
        account.setUsername(user.getUsername());
        account.setUserType(user.getUserType());
        account.setWechatNickname(user.getWechatNickname());
        account.setWechatAvatar(user.getWechatAvatar());
        account.setContactPhone(user.getContactPhone());
        account.setContactEmail(user.getContactEmail());
        account.setStatus(user.getStatus());
        account.setCreateTime(user.getCreateTime());
        account.setLastLoginTime(user.getLastLoginTime());
        if (team != null) {
            account.setTeamName(team.getTeamName());
            account.setContactName(team.getContactName());
            account.setInstitution(team.getInstitution());
            account.setTeamCode(team.getTeamCode());
            account.setResearchDomain(team.getResearchDomain());
        }
        return account;
    }
}