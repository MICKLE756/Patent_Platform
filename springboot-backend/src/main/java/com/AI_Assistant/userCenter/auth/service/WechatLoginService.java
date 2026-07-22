package com.AI_Assistant.userCenter.auth.service;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.AI_Assistant.userCenter.auth.config.WechatConfig;
import com.AI_Assistant.userCenter.user.entity.User;
import com.AI_Assistant.userCenter.user.mapper.UserMapper;
import com.AI_Assistant.userCenter.user.service.UserService;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

@Service
public class WechatLoginService {

    private static final Logger logger = LoggerFactory.getLogger(WechatLoginService.class);

    @Autowired
    private WechatConfig wechatConfig;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserService userService;

    public String getOpenidFromWechat(String code) {
        try {
            String url = String.format("%s?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code",
                    wechatConfig.getLoginUrl(),
                    wechatConfig.getAppId(),
                    wechatConfig.getAppSecret(),
                    code);
            logger.info(url);

            logger.info("微信登录请求URL: {}", url);
            String result = HttpUtil.get(url);
            logger.info("微信登录响应: {}", result);

            JSONObject json = JSONUtil.parseObj(result);

            String errcode = json.getStr("errcode");
            if (errcode != null && !"0".equals(errcode)) {
                String errmsg = json.getStr("errmsg");
                logger.error("微信API调用失败: errcode={}, errmsg={}", errcode, errmsg);
                return null;
            }

            String openid = json.getStr("openid");
            if (openid == null || openid.isEmpty()) {
                logger.error("微信API返回的openid为空");
                return null;
            }

            return openid;
        } catch (Exception e) {
            logger.error("微信登录异常", e);
            return null;
        }
    }

    public User createNewUser(String openid) {
        User user = new User();
        user.setWechatOpenid(openid);
        user.setUsername("wx_" + System.currentTimeMillis());
        // 使用MD5加密初始密码，确保账密登录时能正确验证
        String initialPassword = "12345678";
        user.setPassword(userService.encryptPassword(initialPassword));
        user.setUserType(0);
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());

        userMapper.insert(user);
        logger.info("新用户创建成功，openid: {}, username: {}", openid, user.getUsername());
        return user;
    }
}