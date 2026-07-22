package com.AI_Assistant.common.interceptor;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.AI_Assistant.common.constant.RedisConstants;
import com.AI_Assistant.common.exception.UserDisabledException;
import com.AI_Assistant.common.util.UserContext;
import com.AI_Assistant.userCenter.researchTeam.entity.ResearchTeam;
import com.AI_Assistant.userCenter.user.dto.UserFullInfoDTO;
import com.AI_Assistant.userCenter.user.entity.User;
import com.AI_Assistant.userCenter.user.service.UserService;

import cn.hutool.json.JSONUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 通用 Token 拦截器
 * 拦截所有请求，解析 Token 并将用户信息存入 ThreadLocal
 * 如果没有 Token 则直接放行
 */
@Component
public class TokenInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(TokenInterceptor.class);

    private static final String TOKEN_PREFIX = "Bearer ";

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private UserService userService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = extractToken(request);
        
        if (token == null || token.isEmpty()) {
            return true;
        }

        try {
            // 先检查是否是无效 Token（缓存穿透防护）
            String invalidTokenKey = RedisConstants.LOGIN_INVALID_TOKEN_PREFIX + token;
            if (Boolean.TRUE.equals(redisTemplate.hasKey(invalidTokenKey))) {
                logger.debug("无效 Token，已缓存，跳过查询");
                return true;
            }

            String redisKey = RedisConstants.LOGIN_TOKEN_PREFIX + token;
            String userJson = redisTemplate.opsForValue().get(redisKey);

            if (userJson != null) {
                // 先反序列化为 UserFullInfoDTO，再从中提取 User
                UserFullInfoDTO fullUserInfo = JSONUtil.toBean(userJson, UserFullInfoDTO.class);
                User user = fullUserInfo.getUser();
                
                if (user != null) {
                    // 检查用户是否被禁用
                    if (user.getStatus() != null && user.getStatus() == 0) {
                        logger.warn("用户已被禁用，拒绝登录，userId: {}", user.getId());
                        // 清除该用户的所有 Redis 登录缓存
                        clearUserLoginCache(user.getId(), token, redisKey, invalidTokenKey);
                        throw new UserDisabledException("账号已被禁用");
                    }
                    
                    UserContext.setUser(user);
                    
                    // 刷新 Redis 过期时间（滑动过期）
                    redisTemplate.expire(redisKey, RedisConstants.LOGIN_TOKEN_EXPIRE_SECONDS, TimeUnit.SECONDS);
                    
                    // 同时刷新 userId→token 映射的过期时间，保证单点登录控制有效
                    String userTokenKey = RedisConstants.LOGIN_USER_PREFIX + user.getId();
                    redisTemplate.expire(userTokenKey, RedisConstants.LOGIN_TOKEN_EXPIRE_SECONDS, TimeUnit.SECONDS);
                    
                    logger.debug("Token 验证成功，用户ID: {}，已刷新过期时间", user.getId());
                    
                    // 如果是科研团队用户，从完整用户信息中提取科研团队和专利绑定信息
                    if (user.getUserType() != null && user.getUserType() == 2) {
                        // 设置已绑定专利ID列表
                        if (fullUserInfo.getBoundPatentIds() != null) {
                            UserContext.setBoundPatentIds(fullUserInfo.getBoundPatentIds());
                            logger.debug("已从完整缓存加载已绑定专利列表，userId: {}, 数量: {}", user.getId(), fullUserInfo.getBoundPatentIds().size());
                        }
                        // 设置科研团队信息
                        if (fullUserInfo.getResearchTeamAccount() != null) {
                            ResearchTeam team = ResearchTeam.builder()
                                    .id(fullUserInfo.getResearchTeamAccount().getId())
                                    .userId(user.getId())
                                    .teamName(fullUserInfo.getResearchTeamAccount().getTeamName())
                                    .contactName(fullUserInfo.getResearchTeamAccount().getContactName())
                                    .institution(fullUserInfo.getResearchTeamAccount().getInstitution())
                                    .teamCode(fullUserInfo.getResearchTeamAccount().getTeamCode())
                                    .researchDomain(fullUserInfo.getResearchTeamAccount().getResearchDomain())
                                    .build();
                            UserContext.setResearchTeam(team);
                            logger.debug("已从完整缓存加载科研团队信息，userId: {}, teamName: {}", user.getId(), team.getTeamName());
                        }
                    }
                }
            } else {
                // Token 无效，缓存到无效 Token 集合（缓存穿透防护）
                redisTemplate.opsForValue().set(invalidTokenKey, "1", RedisConstants.LOGIN_INVALID_TOKEN_EXPIRE_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
                logger.debug("Token 无效，已缓存到无效 Token 集合");
            }
        } catch (UserDisabledException e) {
            // [已修复 - 问题38] 业务异常重新抛出，确保被禁用用户无法访问系统
            // [已修复 - 问题18] 异常路径上先清理 ThreadLocal，防止内存泄漏
            UserContext.clear();
            throw e;
        } catch (Exception e) {
            // [已修复 - 问题18] 技术异常路径上先清理 ThreadLocal，防止内存泄漏
            UserContext.clear();
            logger.error("解析 Redis 中的用户信息失败", e);
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserContext.clear();
    }

    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith(TOKEN_PREFIX)) {
            return authHeader.substring(TOKEN_PREFIX.length());
        }
        return null;
    }

    /**
     * 清除用户的所有 Redis 登录缓存
     * 当用户被禁用时调用，强制用户下线
     */
    private void clearUserLoginCache(String userId, String token, String tokenKey, String invalidTokenKey) {
        try {
            // 1. 删除 token 缓存
            redisTemplate.delete(tokenKey);
            // 2. 删除 userId→token 映射
            String userTokenKey = RedisConstants.LOGIN_USER_PREFIX + userId;
            redisTemplate.delete(userTokenKey);
            // 3. 缓存到无效 Token 集合，防止重复查询
            redisTemplate.opsForValue().set(invalidTokenKey, "1", RedisConstants.LOGIN_INVALID_TOKEN_EXPIRE_SECONDS, TimeUnit.SECONDS);
            logger.info("已清除被禁用用户的登录缓存，userId: {}", userId);
        } catch (Exception e) {
            logger.error("清除用户登录缓存失败，userId: {}", userId, e);
        }
    }
}
