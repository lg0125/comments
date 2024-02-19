package com.chris.comments.service.impl;


import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chris.comments.dto.Result;
import com.chris.comments.entity.User;
import com.chris.comments.mapper.UserMapper;
import com.chris.comments.service.IUserService;
import com.chris.comments.utils.regex.RegexUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.concurrent.TimeUnit;

import static com.chris.comments.utils.constant.RedisConstant.LOGIN_CODE_KEY;
import static com.chris.comments.utils.constant.RedisConstant.LOGIN_CODE_TTL;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCodeV1(String phone, HttpSession session) {
        // 1.校验手机号
        if (RegexUtil.isPhoneInvalid(phone))
            // 2.如果不符合，返回错误信息
            return Result.fail("手机号格式错误！");

        // 3.符合，生成验证码
        String code = RandomUtil.randomNumbers(6);

        // 4.保存验证码到 session
        session.setAttribute("code", code);

        // 5.发送验证码
        log.debug("发送短信验证码成功，验证码：{}", code);

        // 返回ok
        return Result.ok();
    }
}
