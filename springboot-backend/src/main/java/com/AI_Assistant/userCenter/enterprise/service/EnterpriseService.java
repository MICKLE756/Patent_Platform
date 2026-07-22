package com.AI_Assistant.userCenter.enterprise.service;

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
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.AI_Assistant.asyncMessage.intentionMessage.entity.IntentionMessage;
import com.AI_Assistant.asyncMessage.intentionMessage.mapper.IntentionMessageMapper;
import com.AI_Assistant.backend.statistics.service.StatisticsService;
import com.AI_Assistant.common.Result;
import com.AI_Assistant.common.dto.PageResult;
import com.AI_Assistant.common.exception.BadRequestException;
import com.AI_Assistant.common.util.UserCacheService;
import com.AI_Assistant.common.util.UserContext;
import com.AI_Assistant.userCenter.enterprise.dto.BindEnterpriseRequest;
import com.AI_Assistant.userCenter.enterprise.dto.EnterpriseAccount;
import com.AI_Assistant.userCenter.enterprise.dto.EnterpriseUserVO;
import com.AI_Assistant.userCenter.enterprise.dto.UpdateEnterpriseRequest;
import com.AI_Assistant.userCenter.enterprise.entity.Enterprise;
import com.AI_Assistant.userCenter.enterprise.mapper.EnterpriseMapper;
import com.AI_Assistant.userCenter.user.dto.UserFullInfoDTO;
import com.AI_Assistant.userCenter.user.entity.User;
import com.AI_Assistant.userCenter.user.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import cn.hutool.core.util.IdUtil;

@Service
public class EnterpriseService {

    private static final Logger logger = LoggerFactory.getLogger(EnterpriseService.class);

    private static final String BIND_LOCK_PREFIX = "lock:bind:enterprise:";
    private static final long LOCK_EXPIRE_SECONDS = 10;

    /**
     * [已修复 - 问题59] Lua脚本：原子释放锁（仅当锁值匹配时才删除）
     */
    private static final String RELEASE_LOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "    return redis.call('del', KEYS[1]) " +
            "else " +
            "    return 0 " +
            "end";

    /**
     * [已修复 - 问题59] Lua脚本：原子续期锁（仅当锁值匹配时才刷新过期时间）
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
    private EnterpriseMapper enterpriseMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private IntentionMessageMapper intentionMessageMapper;

    @Autowired
    private StatisticsService statisticsService;

    @Autowired
    @Lazy
    private UserCacheService userCacheService;

    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    public EnterpriseAccount getEnterpriseAccount(String userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return null;
        }

        LambdaQueryWrapper<Enterprise> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Enterprise::getUserId, userId);
        Enterprise enterprise = enterpriseMapper.selectOne(wrapper);

        return buildEnterpriseAccount(user, enterprise);
    }

    public EnterpriseAccount getEnterpriseAccountByOpenid(String openid) {
        LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.eq(User::getWechatOpenid, openid);
        User user = userMapper.selectOne(userWrapper);
        if (user == null) {
            return null;
        }

        LambdaQueryWrapper<Enterprise> entWrapper = new LambdaQueryWrapper<>();
        entWrapper.eq(Enterprise::getUserId, user.getId());
        Enterprise enterprise = enterpriseMapper.selectOne(entWrapper);

        return buildEnterpriseAccount(user, enterprise);
    }

    @Transactional
    public Result<EnterpriseAccount> bindEnterprise(BindEnterpriseRequest request) {
        logger.info("用户绑定企业账号请求");

        User currentUser = UserContext.getUser();
        String lockKey = BIND_LOCK_PREFIX + currentUser.getId();
        String lockValue = IdUtil.simpleUUID();
        
        // 使用Redis分布式锁防止并发绑定，使用UUID作为锁值确保只有持有者能释放
        Boolean lockAcquired = redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, LOCK_EXPIRE_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
        if (lockAcquired == null || !lockAcquired) {
            logger.warn("用户绑定企业账号请求过于频繁，请稍后重试，userId: {}", currentUser.getId());
            return Result.<EnterpriseAccount>error("请求过于频繁，请稍后重试", null);
        }

        Thread renewThread = null;
        try {
            renewThread = startLockRenewal(lockKey, lockValue);

            if (currentUser.getUserType() != null && currentUser.getUserType() == 1) {
                logger.warn("用户已绑定企业账号，userId: {}", currentUser.getId());
                return Result.<EnterpriseAccount>error("用户已绑定企业账号", null);
            }

            // 检查数据库中是否已存在企业记录（防止缓存与数据库不一致）
            LambdaQueryWrapper<Enterprise> entCheckWrapper = new LambdaQueryWrapper<>();
            entCheckWrapper.eq(Enterprise::getUserId, currentUser.getId());
            Enterprise existingEnterprise = enterpriseMapper.selectOne(entCheckWrapper);
            if (existingEnterprise != null) {
                logger.warn("数据库中已存在该用户的企业记录，userId: {}", currentUser.getId());
                return Result.<EnterpriseAccount>error("用户已绑定企业账号", null);
            }

            if (request.getCompanyName() == null || request.getCompanyName().trim().isEmpty()) {
                return Result.<EnterpriseAccount>error("公司名称不能为空", null);
            }

            currentUser.setUserType(1);
            userMapper.updateById(currentUser);
            logger.info("用户类型已更新为企业用户，userId: {}", currentUser.getId());

            Enterprise enterprise = new Enterprise();
            enterprise.setId(IdUtil.simpleUUID());
            enterprise.setUserId(currentUser.getId());
            enterprise.setCompanyName(request.getCompanyName());
            enterprise.setContactName(request.getContactName());
            enterprise.setBusinessLicense(request.getBusinessLicense());
            enterprise.setCompanyAddress(request.getCompanyAddress());
            enterprise.setCompanyIntro(request.getCompanyIntro());

            enterpriseMapper.insert(enterprise);
            logger.info("企业账号绑定成功，userId: {}, enterpriseId: {}", currentUser.getId(), enterprise.getId());

            // 更新统计聚合表
            statisticsService.recordEnterprise();

            // 在事务提交后更新缓存，确保数据库和缓存一致性
            final User userToUpdate = currentUser;
            final Enterprise enterpriseToUpdate = enterprise;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    UserFullInfoDTO fullInfo = UserFullInfoDTO.builder()
                            .user(userToUpdate)
                            .enterpriseAccount(buildEnterpriseAccount(userToUpdate, enterpriseToUpdate))
                            .build();
                    userCacheService.updateFullUserCache(fullInfo);
                }
            });

            return Result.ok("企业账号绑定成功", buildEnterpriseAccount(currentUser, enterprise));
        } catch (Exception e) {
            logger.error("企业账号绑定失败", e);
            throw e;
        } finally {
            // 停止锁续期线程
            if (renewThread != null) {
                renewThread.interrupt();
            }
            // 只有锁持有者才能释放锁
            releaseLock(lockKey, lockValue);
        }
    }

    private Thread startLockRenewal(String lockKey, String lockValue) {
        Thread renewThread = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(LOCK_EXPIRE_SECONDS / 3 * 1000);
                    // [已修复 - 问题59] 使用Lua脚本原子续期，避免get-then-expire竞态条件
                    // 原代码：先get判断锁值，再expire续期，两步非原子，存在误续期风险
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

    private void releaseLock(String lockKey, String lockValue) {
        // [已修复 - 问题59] 使用Lua脚本原子释放锁，避免get-then-delete竞态条件
        // 原代码：先get判断锁值，再delete，两步非原子，可能误删他人持有的锁
        redisTemplate.execute(
                RELEASE_LOCK_REDIS_SCRIPT,
                java.util.Collections.singletonList(lockKey),
                lockValue
        );
    }

    @Transactional
    public Result<EnterpriseAccount> updateEnterprise(String enterpriseUserId, UpdateEnterpriseRequest request) {
        logger.info("更新企业信息请求，enterpriseUserId: {}", enterpriseUserId);

        User currentUser = UserContext.getUser();

        if (!currentUser.getId().equals(enterpriseUserId)) {
            logger.warn("用户无权限更新该企业信息，currentUserId: {}, targetUserId: {}", currentUser.getId(), enterpriseUserId);
            return Result.<EnterpriseAccount>error("无权限更新该企业信息", null);
        }

        if (currentUser.getUserType() == null || currentUser.getUserType() != 1) {
            logger.warn("用户不是企业用户，无法更新企业信息，userId: {}", currentUser.getId());
            return Result.<EnterpriseAccount>error("用户不是企业用户", null);
        }

        LambdaQueryWrapper<Enterprise> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Enterprise::getUserId, enterpriseUserId);
        Enterprise enterprise = enterpriseMapper.selectOne(wrapper);

        if (enterprise == null) {
            logger.warn("企业信息不存在，userId: {}", enterpriseUserId);
            return Result.<EnterpriseAccount>error("企业信息不存在", null);
        }

        try {
            if (request.getCompanyName() != null) {
                enterprise.setCompanyName(request.getCompanyName());
            }
            if (request.getContactName() != null) {
                enterprise.setContactName(request.getContactName());
            }
            if (request.getBusinessLicense() != null) {
                enterprise.setBusinessLicense(request.getBusinessLicense());
            }
            if (request.getCompanyAddress() != null) {
                enterprise.setCompanyAddress(request.getCompanyAddress());
            }
            if (request.getCompanyIntro() != null) {
                enterprise.setCompanyIntro(request.getCompanyIntro());
            }
            enterprise.setUpdateTime(LocalDateTime.now());

            enterpriseMapper.updateById(enterprise);
            logger.info("企业信息更新成功，enterpriseId: {}", enterprise.getId());
            
            // 从数据库重新加载用户信息，确保缓存一致性
            User updatedUser = userMapper.selectById(currentUser.getId());
            
            // 在事务提交后更新缓存，确保数据库和缓存一致性
            if (updatedUser != null) {
                final User userToUpdate = updatedUser;
                final Enterprise enterpriseToUpdate = enterprise;
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        UserFullInfoDTO fullInfo = UserFullInfoDTO.builder()
                                .user(userToUpdate)
                                .enterpriseAccount(buildEnterpriseAccount(userToUpdate, enterpriseToUpdate))
                                .build();
                        userCacheService.updateFullUserCache(fullInfo);
                    }
                });
            }

            return Result.ok("企业信息更新成功", buildEnterpriseAccount(currentUser, enterprise));
        } catch (Exception e) {
            logger.error("企业信息更新失败", e);
            throw e;
        }
    }

    public List<EnterpriseUserVO> getEnterpriseUserList() {
        logger.info("查询企业账号列表");

        LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.eq(User::getUserType, 1);
        List<User> users = userMapper.selectList(userWrapper);

        if (users == null || users.isEmpty()) {
            logger.info("未找到企业用户");
            return new ArrayList<>();
        }

        List<String> userIds = users.stream().map(User::getId).collect(Collectors.toList());
        LambdaQueryWrapper<Enterprise> enterpriseWrapper = new LambdaQueryWrapper<>();
        enterpriseWrapper.in(Enterprise::getUserId, userIds);
        List<Enterprise> enterprises = enterpriseMapper.selectList(enterpriseWrapper);

        Map<String, Enterprise> enterpriseMap = enterprises.stream()
                .collect(Collectors.toMap(Enterprise::getUserId, Function.identity()));

        List<EnterpriseUserVO> result = new ArrayList<>();
        for (User user : users) {
            Enterprise enterprise = enterpriseMap.get(user.getId());
            EnterpriseUserVO vo = new EnterpriseUserVO();
            vo.setId(user.getId());
            vo.setUsername(user.getUsername());
            vo.setWechatNickname(user.getWechatNickname());
            vo.setWechatAvatar(user.getWechatAvatar());
            vo.setStatus(user.getStatus());
            vo.setCompanyName(enterprise != null ? enterprise.getCompanyName() : null);
            result.add(vo);
        }

        logger.info("查询到企业用户数量: {}", result.size());
        return result;
    }

    /**
     * 分页查询企业用户列表
     *
     * @param pageNum  页码（从1开始）
     * @param pageSize 每页大小
     * @return 分页结果
     */
    public PageResult<EnterpriseUserVO> getEnterpriseUserList(Integer pageNum, Integer pageSize) {
        logger.info("分页查询企业账号列表，pageNum: {}, pageSize: {}", pageNum, pageSize);

        // 设置默认分页参数
        int pageNumber = pageNum != null && pageNum > 0 ? pageNum : 1;
        int pageSizeNumber = pageSize != null && pageSize > 0 ? pageSize : 10;

        // 分页查询用户
        Page<User> page = new Page<>(pageNumber, pageSizeNumber);
        LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.eq(User::getUserType, 1);
        IPage<User> userPage = userMapper.selectPage(page, userWrapper);

        List<User> users = userPage.getRecords();
        long total = userPage.getTotal();

        if (users == null || users.isEmpty()) {
            logger.info("未找到企业用户");
            return PageResult.of(new ArrayList<>(), pageNumber, pageSizeNumber, total);
        }

        // 查询企业信息
        List<String> userIds = users.stream().map(User::getId).collect(Collectors.toList());
        LambdaQueryWrapper<Enterprise> enterpriseWrapper = new LambdaQueryWrapper<>();
        enterpriseWrapper.in(Enterprise::getUserId, userIds);
        List<Enterprise> enterprises = enterpriseMapper.selectList(enterpriseWrapper);

        Map<String, Enterprise> enterpriseMap = enterprises.stream()
                .collect(Collectors.toMap(Enterprise::getUserId, Function.identity()));

        // 组装结果
        List<EnterpriseUserVO> result = new ArrayList<>();
        for (User user : users) {
            Enterprise enterprise = enterpriseMap.get(user.getId());
            EnterpriseUserVO vo = new EnterpriseUserVO();
            vo.setId(user.getId());
            vo.setUsername(user.getUsername());
            vo.setWechatNickname(user.getWechatNickname());
            vo.setWechatAvatar(user.getWechatAvatar());
            vo.setStatus(user.getStatus());
            vo.setCompanyName(enterprise != null ? enterprise.getCompanyName() : null);
            result.add(vo);
        }

        logger.info("查询到企业用户数量: {}", result.size());
        return PageResult.of(result, pageNumber, pageSizeNumber, total);
    }

    public EnterpriseUserVO getEnterpriseUserById(String userId) {
        logger.info("查询单个企业用户，userId: {}", userId);

        User user = userMapper.selectById(userId);
        if (user == null) {
            logger.warn("用户不存在，userId: {}", userId);
            return null;
        }

        if (user.getUserType() == null || user.getUserType() != 1) {
            logger.warn("该用户不是企业用户，userId: {}", userId);
            return null;
        }

        LambdaQueryWrapper<Enterprise> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Enterprise::getUserId, userId);
        Enterprise enterprise = enterpriseMapper.selectOne(wrapper);

        EnterpriseUserVO vo = new EnterpriseUserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setWechatNickname(user.getWechatNickname());
        vo.setWechatAvatar(user.getWechatAvatar());
        vo.setStatus(user.getStatus());
        vo.setCompanyName(enterprise != null ? enterprise.getCompanyName() : null);
        return vo;
    }

    @Transactional
    public void unbindEnterprise() {
        logger.info("用户注销企业账号请求");

        User currentUser = UserContext.getUser();

        if (currentUser.getUserType() == null || currentUser.getUserType() != 1) {
            logger.warn("用户未绑定企业账户，无法注销，userId: {}", currentUser.getId());
            throw new BadRequestException("用户不是企业用户");
        }

        unbindEnterprise(currentUser);
    }

    @Transactional
    public Result<Void> unbindEnterprise(User currentUser) {
        logger.info("用户注销企业账号请求，userId: {}", currentUser.getId());

        try {
            LambdaQueryWrapper<IntentionMessage> msgWrapper = new LambdaQueryWrapper<>();
            msgWrapper.eq(IntentionMessage::getInitiatorId, currentUser.getId())
                      .eq(IntentionMessage::getInitiatorType, "enterprise");
            List<IntentionMessage> messages = intentionMessageMapper.selectList(msgWrapper);

            int rejectedCount = 0;
            Date now = new Date();
            if (messages != null && !messages.isEmpty()) {
                for (IntentionMessage message : messages) {
                    boolean needUpdate = false;
                    if ("pending".equals(message.getAdminStatus())) {
                        message.setAdminStatus("rejected");
                        message.setAdminAuditTime(now);
                        needUpdate = true;
                    }
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

            LambdaQueryWrapper<Enterprise> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Enterprise::getUserId, currentUser.getId());
            Enterprise enterprise = enterpriseMapper.selectOne(wrapper);

            if (enterprise != null) {
                enterpriseMapper.deleteById(enterprise.getId());
                logger.info("企业信息已删除，enterpriseId: {}", enterprise.getId());
            }

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

            return Result.ok("企业账号注销成功");
        } catch (Exception e) {
            logger.error("注销企业账号失败", e);
            throw e;
        }
    }

    private EnterpriseAccount buildEnterpriseAccount(User user, Enterprise enterprise) {
        EnterpriseAccount account = new EnterpriseAccount();
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
        if (enterprise != null) {
            account.setCompanyName(enterprise.getCompanyName());
            account.setContactName(enterprise.getContactName());
            account.setBusinessLicense(enterprise.getBusinessLicense());
            account.setCompanyAddress(enterprise.getCompanyAddress());
            account.setCompanyIntro(enterprise.getCompanyIntro());
        }
        return account;
    }
}
