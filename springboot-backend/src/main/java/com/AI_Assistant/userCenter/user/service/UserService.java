package com.AI_Assistant.userCenter.user.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.AI_Assistant.common.constant.RedisConstants;
import com.AI_Assistant.common.dto.PageResult;
import com.AI_Assistant.common.exception.BadRequestException;
import com.AI_Assistant.common.exception.ConflictException;
import com.AI_Assistant.common.exception.NotFoundException;
import com.AI_Assistant.common.util.UserCacheService;
import com.AI_Assistant.userCenter.user.dto.UserDTO;
import com.AI_Assistant.userCenter.user.entity.User;
import com.AI_Assistant.userCenter.user.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserCacheService userCacheService;

    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    public User getUserById(String userId) {
        return userMapper.selectById(userId);
    }

    public User getUserByOpenid(String openid) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getWechatOpenid, openid);
        return userMapper.selectOne(wrapper);
    }

    public UserDTO getUserDTOById(String userId) {
        User user = userMapper.selectById(userId);
        return convertToDTO(user);
    }

    public UserDTO getUserDTOByOpenid(String openid) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getWechatOpenid, openid);
        User user = userMapper.selectOne(wrapper);
        return convertToDTO(user);
    }

    private UserDTO convertToDTO(User user) {
        if (user == null) {
            return null;
        }
        return UserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .userType(user.getUserType())
                .wechatNickname(user.getWechatNickname())
                .wechatAvatar(user.getWechatAvatar())
                .contactPhone(user.getContactPhone())
                .contactEmail(user.getContactEmail())
                .status(user.getStatus())
                .createTime(user.getCreateTime())
                .lastLoginTime(user.getLastLoginTime())
                .build();
    }

    /**
     * 更新用户状态（启用/禁用）
     * @param userId 用户ID
     * @param status 状态值（1=启用，0=禁用）
     * @return 操作结果
     */
    public void updateUserStatus(String userId, Integer status) {
        logger.info("更新用户状态请求，userId: {}, status: {}", userId, status);

        if (userId == null || userId.isEmpty()) {
            logger.warn("用户ID为空");
            throw new BadRequestException("用户ID不能为空");
        }

        if (status == null || (status != 0 && status != 1)) {
            logger.warn("无效的状态值: {}", status);
            throw new BadRequestException("状态值必须为0（禁用）或1（启用）");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            logger.warn("用户不存在，userId: {}", userId);
            throw new NotFoundException("用户不存在");
        }

        if (user.getStatus() != null && user.getStatus().equals(status)) {
            String statusText = status == 1 ? "启用" : "禁用";
            logger.warn("用户状态已是{}状态，无需修改，userId: {}", statusText, userId);
            throw new ConflictException("用户已是" + statusText + "状态");
        }

        user.setStatus(status);
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);

        if (status == 0) {
            clearUserLoginCache(userId);
        } else {
            userCacheService.updateFullUserCache(user);
        }

        String statusText = status == 1 ? "启用" : "禁用";
        logger.info("用户状态更新成功，userId: {}, 状态: {}", userId, statusText);
    }

    /**
     * 检查用户是否启用
     * @param user 用户对象
     * @return true=启用，false=禁用
     */
    public boolean isUserEnabled(User user) {
        return user != null && user.getStatus() != null && user.getStatus() == 1;
    }

    /**
     * 分页获取普通用户列表（userType=0）
     * @param pageNum 页码（从1开始）
     * @param pageSize 每页大小
     * @return 分页结果
     */
    public PageResult<UserDTO> getNormalUsers(int pageNum, int pageSize) {
        // 设置默认分页参数
        int pageNumber = pageNum > 0 ? pageNum : 1;
        int pageSizeNumber = pageSize > 0 ? pageSize : 10;

        // 【MyBatis-Plus分页】使用数据库分页，而非内存分页
        Page<User> page = new Page<>(pageNumber, pageSizeNumber);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUserType, 0);
        wrapper.orderByDesc(User::getCreateTime);
        IPage<User> userPage = userMapper.selectPage(page, wrapper);

        // 转换为DTO
        List<UserDTO> dtoList = userPage.getRecords().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        return PageResult.of(dtoList, pageNumber, pageSizeNumber, userPage.getTotal());
    }

    /**
     * 根据用户名查询用户
     * @param username 用户名
     * @return 用户对象，如果不存在返回null
     */
    public User getUserByUsername(String username) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username);
        return userMapper.selectOne(wrapper);
    }

    /**
     * 重置用户密码（管理员操作）
     * 将用户密码重置为默认密码
     * @param userId 用户ID
     */
    public void resetPassword(String userId) {
        logger.info("重置用户密码请求，userId: {}", userId);

        if (userId == null || userId.isEmpty()) {
            logger.warn("用户ID为空");
            throw new BadRequestException("用户ID不能为空");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            logger.warn("用户不存在，userId: {}", userId);
            throw new NotFoundException("用户不存在");
        }

        String defaultPassword = "123456";
        user.setPassword(encryptPassword(defaultPassword));
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);

        clearUserLoginCache(userId);

        logger.info("用户密码重置成功，userId: {}", userId);
    }

    /**
     * 修改用户密码（用户自己操作）
     * @param userId 用户ID
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     */
    public void updatePassword(String userId, String oldPassword, String newPassword) {
        logger.info("修改用户密码请求，userId: {}", userId);

        if (userId == null || userId.isEmpty()) {
            logger.warn("用户ID为空");
            throw new BadRequestException("用户ID不能为空");
        }

        if (oldPassword == null || oldPassword.isEmpty()) {
            logger.warn("旧密码为空");
            throw new BadRequestException("旧密码不能为空");
        }

        if (newPassword == null || newPassword.isEmpty()) {
            logger.warn("新密码为空");
            throw new BadRequestException("新密码不能为空");
        }

        if (newPassword.length() < 6) {
            logger.warn("新密码长度不足，userId: {}", userId);
            throw new BadRequestException("新密码长度至少为6位");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            logger.warn("用户不存在，userId: {}", userId);
            throw new NotFoundException("用户不存在");
        }

        if (!verifyPassword(oldPassword, user.getPassword())) {
            logger.warn("旧密码错误，userId: {}", userId);
            throw new BadRequestException("旧密码错误");
        }

        user.setPassword(encryptPassword(newPassword));
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);

        clearUserLoginCache(userId);

        logger.info("用户密码修改成功，userId: {}", userId);
    }

    /**
     * 验证密码
     * 兼容明文密码和MD5加密密码
     * @param inputPassword 用户输入的密码
     * @param storedPassword 数据库中存储的密码
     * @return true=密码正确，false=密码错误
     */
    public boolean verifyPassword(String inputPassword, String storedPassword) {
        if (inputPassword == null || storedPassword == null) {
            return false;
        }

        // 1. 先尝试 MD5 加密后比对（新的加密密码）
        String encryptedInput = cn.hutool.crypto.digest.DigestUtil.md5Hex(inputPassword);
        if (encryptedInput.equalsIgnoreCase(storedPassword)) {
            return true;
        }

        // 2. MD5 比对失败，尝试明文比对（兼容旧的明文密码）
        return inputPassword.equals(storedPassword);
    }

    /**
     * 加密密码（MD5）
     * @param password 原始密码
     * @return 加密后的密码
     */
    public String encryptPassword(String password) {
        if (password == null) {
            return null;
        }
        return cn.hutool.crypto.digest.DigestUtil.md5Hex(password);
    }

    /**
     * 清除用户的所有 Redis 登录缓存
     * 当用户被禁用时调用，强制用户下线
     */
    private void clearUserLoginCache(String userId) {
        try {
            // 1. 获取 userId→token 映射
            String userTokenKey = RedisConstants.LOGIN_USER_PREFIX + userId;
            String token = redisTemplate.opsForValue().get(userTokenKey);
            
            // 2. 删除 token 缓存
            if (token != null && !token.isEmpty()) {
                String tokenKey = RedisConstants.LOGIN_TOKEN_PREFIX + token;
                redisTemplate.delete(tokenKey);
                logger.info("已删除用户 token 缓存，userId: {}, token: {}", userId, token);
            }
            
            // 3. 删除 userId→token 映射
            redisTemplate.delete(userTokenKey);
            
            logger.info("已清除被禁用用户的登录缓存，userId: {}", userId);
        } catch (Exception e) {
            logger.error("清除用户登录缓存失败，userId: {}", userId, e);
        }
    }
}