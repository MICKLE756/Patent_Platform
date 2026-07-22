package com.AI_Assistant.common.util;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.AI_Assistant.backend.patent.dto.PatentDTO;
import com.AI_Assistant.backend.patent.mapper.BoundPatentMapper;
import com.AI_Assistant.common.constant.RedisConstants;
import com.AI_Assistant.userCenter.user.dto.UserFullInfoDTO;
import com.AI_Assistant.userCenter.user.entity.User;

import cn.hutool.json.JSONUtil;

/**
 * 用户完整信息缓存服务
 * 负责构建和缓存用户的完整信息（包含基础用户信息、企业/科研团队额外信息、绑定专利列表）
 */
@Component
public class UserCacheService {

    private static final Logger logger = LoggerFactory.getLogger(UserCacheService.class);

    @Autowired
    private BoundPatentMapper boundPatentMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 构建并缓存用户完整信息（旧方法，已废弃）
     * 使用 buildAndCacheFullUserInfo(UserFullInfoDTO fullInfo, String token) 替代
     *
     * @param user 当前用户
     * @param userId 用户ID
     * @param token 登录token
     */
    @Deprecated
    public void buildAndCacheFullUserInfo(User user, String userId, String token) {
        logger.warn("buildAndCacheFullUserInfo(User, String, String) 已废弃，请使用 buildAndCacheFullUserInfo(UserFullInfoDTO, String)");
    }

    /**
     * 构建并缓存用户完整信息（新方法）
     * 由调用方负责构建 UserFullInfoDTO，避免循环依赖
     *
     * @param fullInfo 用户完整信息
     * @param token 登录token
     */
    public void buildAndCacheFullUserInfo(UserFullInfoDTO fullInfo, String token) {
        if (fullInfo == null || fullInfo.getUser() == null || token == null) {
            logger.warn("用户信息或token为空，无法构建完整用户信息缓存");
            return;
        }

        try {
            String userId = fullInfo.getUser().getId();

            // 存入 Redis 缓存
            String tokenKey = RedisConstants.LOGIN_TOKEN_PREFIX + token;
            String userJson = JSONUtil.toJsonStr(fullInfo);
            redisTemplate.opsForValue().set(tokenKey, userJson,
                    RedisConstants.LOGIN_TOKEN_EXPIRE_SECONDS, java.util.concurrent.TimeUnit.SECONDS);

            // 存储 userId -> token（用于单点登录）
            String userTokenKey = RedisConstants.LOGIN_USER_PREFIX + userId;
            redisTemplate.opsForValue().set(userTokenKey, token,
                    RedisConstants.LOGIN_TOKEN_EXPIRE_SECONDS, java.util.concurrent.TimeUnit.SECONDS);

            logger.info("用户完整信息已缓存，userId: {}, userType: {}", userId, fullInfo.getUser().getUserType());
        } catch (Exception e) {
            logger.error("构建用户完整信息缓存失败，userId: {}", fullInfo.getUser().getId(), e);
        }
    }

    /**
     * 构建用户完整信息（旧方法，已废弃）
     * 数据构建由调用方负责，此方法不再实现
     *
     * @param user 当前用户
     * @return 完整用户信息DTO
     */
    @Deprecated
    public UserFullInfoDTO buildFullUserInfo(User user) {
        logger.warn("buildFullUserInfo(User) 已废弃，请在调用方构建 UserFullInfoDTO");
        if (user == null) {
            return null;
        }
        return UserFullInfoDTO.builder().user(user).build();
    }

    /**
     * 更新 Redis 缓存中的用户完整信息（旧方法，已废弃）
     * 使用 updateFullUserCache(UserFullInfoDTO fullInfo) 替代
     *
     * @param user 当前用户
     */
    @Deprecated
    public void updateFullUserCache(User user) {
        logger.warn("updateFullUserCache(User) 已废弃，请使用 updateFullUserCache(UserFullInfoDTO)");
    }

    /**
     * 更新 Redis 缓存中的用户完整信息（新方法）
     * 由调用方负责构建 UserFullInfoDTO，避免循环依赖
     *
     * @param fullInfo 用户完整信息
     */
    public void updateFullUserCache(UserFullInfoDTO fullInfo) {
        if (fullInfo == null || fullInfo.getUser() == null || fullInfo.getUser().getId() == null) {
            logger.warn("用户信息为空，无法更新缓存");
            return;
        }

        try {
            String userId = fullInfo.getUser().getId();

            // 查询用户当前的 token
            String userTokenKey = RedisConstants.LOGIN_USER_PREFIX + userId;
            String token = redisTemplate.opsForValue().get(userTokenKey);

            // 如果 userId→token 映射过期，尝试通过扫描 token→userInfo 查找有效 token
            if (token == null) {
                token = findValidTokenByUserId(userId);
                // 如果找到有效 token，重建 userId→token 映射（使用与 token→userInfo 一致的过期时间）
                if (token != null) {
                    redisTemplate.opsForValue().set(userTokenKey, token, 
                            RedisConstants.LOGIN_TOKEN_EXPIRE_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
                }
            }

            if (token != null) {
                // 更新 Redis 缓存
                String tokenKey = RedisConstants.LOGIN_TOKEN_PREFIX + token;
                String userJson = JSONUtil.toJsonStr(fullInfo);
                redisTemplate.opsForValue().set(tokenKey, userJson,
                        RedisConstants.LOGIN_TOKEN_EXPIRE_SECONDS, java.util.concurrent.TimeUnit.SECONDS);

                // 同步更新 userId→token 映射的过期时间，确保两者同步过期
                redisTemplate.expire(userTokenKey, RedisConstants.LOGIN_TOKEN_EXPIRE_SECONDS, java.util.concurrent.TimeUnit.SECONDS);

                // 同步更新 ThreadLocal
                UserContext.setUser(fullInfo.getUser());

                // 如果是科研团队用户，同步更新科研团队信息到 ThreadLocal
                if (fullInfo.getUser().getUserType() != null && fullInfo.getUser().getUserType() == 2 && fullInfo.getResearchTeamAccount() != null) {
                    // 从 researchTeamAccount 构建 ResearchTeam 对象
                    com.AI_Assistant.userCenter.researchTeam.entity.ResearchTeam team =
                            com.AI_Assistant.userCenter.researchTeam.entity.ResearchTeam.builder()
                                    .id(fullInfo.getResearchTeamAccount().getId())
                                    .userId(userId)
                                    .teamName(fullInfo.getResearchTeamAccount().getTeamName())
                                    .contactName(fullInfo.getResearchTeamAccount().getContactName())
                                    .institution(fullInfo.getResearchTeamAccount().getInstitution())
                                    .teamCode(fullInfo.getResearchTeamAccount().getTeamCode())
                                    .researchDomain(fullInfo.getResearchTeamAccount().getResearchDomain())
                                    .build();
                    UserContext.setResearchTeam(team);
                }

                // 如果是科研团队用户，同步更新已绑定专利ID列表到 ThreadLocal
                if (fullInfo.getUser().getUserType() != null && fullInfo.getUser().getUserType() == 2 && fullInfo.getBoundPatentIds() != null) {
                    UserContext.setBoundPatentIds(fullInfo.getBoundPatentIds());
                }

                logger.debug("已更新 Redis 缓存中的用户完整信息，userId: {}", userId);
            }
        } catch (Exception e) {
            logger.warn("更新 Redis 用户完整缓存失败，userId: {}", fullInfo.getUser().getId(), e);
        }
    }

    /**
     * 通过 userId 查找有效的 token
     * 当 userId→token 映射过期但 token→userInfo 仍有效时使用
     * 使用 SCAN 命令避免阻塞 Redis
     *
     * @param userId 用户ID
     * @return 有效的 token，如果未找到返回 null
     */
    private String findValidTokenByUserId(String userId) {
        try {
            // 使用 SCAN 命令遍历所有 login:token:* 键（非阻塞）
            ScanOptions scanOptions = ScanOptions.scanOptions()
                    .match(RedisConstants.LOGIN_TOKEN_PREFIX + "*")
                    .count(100)
                    .build();

            Set<String> scannedKeys = new HashSet<>();
            try (Cursor<String> cursor = redisTemplate.scan(scanOptions)) {
                while (cursor.hasNext()) {
                    scannedKeys.add(cursor.next());
                }
            }

            if (scannedKeys.isEmpty()) {
                return null;
            }

            // 遍历查找属于该用户的 token
            for (String tokenKey : scannedKeys) {
                String userJson = redisTemplate.opsForValue().get(tokenKey);
                if (userJson != null) {
                    try {
                        UserFullInfoDTO fullInfo = JSONUtil.toBean(userJson, UserFullInfoDTO.class);
                        if (fullInfo != null && fullInfo.getUser() != null && userId.equals(fullInfo.getUser().getId())) {
                            // 提取 token（去掉前缀）
                            return tokenKey.substring(RedisConstants.LOGIN_TOKEN_PREFIX.length());
                        }
                    } catch (Exception e) {
                        // 解析失败，跳过
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("通过 userId 查找 token 失败，userId: {}", userId, e);
        }
        return null;
    }

    /**
     * 更新用户已绑定专利缓存（旧方法，已废弃）
     * 使用 updateBoundPatentsCache(String userId, List<String> boundPatentIds) 替代
     *
     * @param userId 用户ID
     */
    @Deprecated
    public void updateBoundPatentsCache(String userId) {
        logger.warn("updateBoundPatentsCache(String) 已废弃，请使用 updateBoundPatentsCache(String, List)");
    }

    /**
     * 更新用户已绑定专利缓存（新方法）
     * 由调用方负责提供已绑定专利ID列表，避免循环依赖
     *
     * @param userId 用户ID
     * @param boundPatentIds 已绑定专利ID列表
     */
    public void updateBoundPatentsCache(String userId, List<String> boundPatentIds) {
        if (userId == null) {
            return;
        }

        try {
            // 查询用户当前的 token
            String userTokenKey = RedisConstants.LOGIN_USER_PREFIX + userId;
            String token = redisTemplate.opsForValue().get(userTokenKey);

            // 如果 userId→token 映射过期，尝试通过扫描 token→userInfo 查找有效 token
            if (token == null) {
                token = findValidTokenByUserId(userId);
            }

            if (token != null) {
                String tokenKey = RedisConstants.LOGIN_TOKEN_PREFIX + token;
                String userJson = redisTemplate.opsForValue().get(tokenKey);

                if (userJson != null) {
                    UserFullInfoDTO fullInfo = JSONUtil.toBean(userJson, UserFullInfoDTO.class);

                    // 更新已绑定专利列表
                    fullInfo.setBoundPatentIds(boundPatentIds);

                    // 同步更新 ThreadLocal
                    UserContext.setBoundPatentIds(boundPatentIds);

                    // 更新 Redis 缓存
                    String newUserJson = JSONUtil.toJsonStr(fullInfo);
                    redisTemplate.opsForValue().set(tokenKey, newUserJson,
                            RedisConstants.LOGIN_TOKEN_EXPIRE_SECONDS, java.util.concurrent.TimeUnit.SECONDS);

                    // 同步更新 userId→token 映射的过期时间，确保两者同步过期
                    redisTemplate.expire(userTokenKey, RedisConstants.LOGIN_TOKEN_EXPIRE_SECONDS, java.util.concurrent.TimeUnit.SECONDS);

                    logger.debug("已更新 Redis 缓存中的已绑定专利信息，userId: {}", userId);
                }
            }
        } catch (Exception e) {
            logger.warn("更新已绑定专利缓存失败，userId: {}", userId, e);
        }
    }

    /**
     * 从缓存获取用户完整信息
     *
     * @param token 登录token
     * @return 用户完整信息DTO
     */
    public UserFullInfoDTO getFullUserInfo(String token) {
        if (token == null) {
            return null;
        }

        try {
            String tokenKey = RedisConstants.LOGIN_TOKEN_PREFIX + token;
            String userJson = redisTemplate.opsForValue().get(tokenKey);

            if (userJson != null) {
                return JSONUtil.toBean(userJson, UserFullInfoDTO.class);
            }
        } catch (Exception e) {
            logger.error("从缓存获取用户完整信息失败", e);
        }
        return null;
    }
}
