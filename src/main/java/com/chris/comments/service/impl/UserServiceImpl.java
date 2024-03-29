package com.chris.comments.service.impl;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chris.comments.dto.LoginFormDTO;
import com.chris.comments.dto.Result;
import com.chris.comments.dto.UserDTO;
import com.chris.comments.entity.User;
import com.chris.comments.mapper.UserMapper;
import com.chris.comments.service.IUserService;
import com.chris.comments.utils.regex.RegexUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.chris.comments.utils.constant.RedisConstant.*;
import static com.chris.comments.utils.constant.SystemConstant.USER_NICK_NAME_PREFIX;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private User createUserWithPhone(String phone) {
        // 1.创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));

        // 2.保存用户
        save(user);

        return user;
    }

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

    @Override
    public Result sendCodeV2(String phone, HttpSession session) {
        // 1.校验手机号
        if (RegexUtil.isPhoneInvalid(phone))
            // 2.如果不符合，返回错误信息
            return Result.fail("手机号格式错误！");

        // 3.符合，生成验证码
        String code = RandomUtil.randomNumbers(6);

        // 4.保存验证码到 session
        stringRedisTemplate.opsForValue().set(
                LOGIN_CODE_KEY + phone, code,
                LOGIN_CODE_TTL, TimeUnit.MINUTES
        );

        // 5.发送验证码
        log.debug("发送短信验证码成功，验证码：{}", code);

        // 返回ok
        return Result.ok();
    }

    @Override
    public Result loginV1(LoginFormDTO loginForm, HttpSession session) {
        // 1.校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtil.isPhoneInvalid(phone))
            // 2.如果不符合，返回错误信息
            return Result.fail("手机号格式错误！");

        // 3.从redis获取验证码并校验
        Object cacheCode = session.getAttribute("code");
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.toString().equals(code))
            // 不一致，报错
            return Result.fail("验证码错误");

        // 4.一致，根据手机号查询用户 select * from tb_user where phone = ?
        User user = query().eq("phone", phone).one();

        // 5.判断用户是否存在
        if (user == null)
            // 6.不存在，创建新用户并保存
            user = createUserWithPhone(phone);

        // 7.保存用户信息到 redis中
        session.setAttribute("user", user);

        // 8.返回token
        return Result.ok();
    }

    @Override
    public Result loginV2(LoginFormDTO loginForm, HttpSession session) {
        // 1.校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtil.isPhoneInvalid(phone))
            // 2.如果不符合，返回错误信息
            return Result.fail("手机号格式错误！");

        // 3.从redis获取验证码并校验
        Object cacheCode = session.getAttribute("code");
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.toString().equals(code))
            // 不一致，报错
            return Result.fail("验证码错误");

        // 4.一致，根据手机号查询用户 select * from tb_user where phone = ?
        User user = query().eq("phone", phone).one();

        // 5.判断用户是否存在
        if (user == null)
            // 6.不存在，创建新用户并保存
            user = createUserWithPhone(phone);

        // 7.保存用户信息到 redis中
        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));

        // 8.返回token
        return Result.ok();
    }

    @Override
    public Result loginV3(LoginFormDTO loginForm, HttpSession session) {
        // 1.校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtil.isPhoneInvalid(phone))
            // 2.如果不符合，返回错误信息
            return Result.fail("手机号格式错误！");

        // 3.从redis获取验证码并校验
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.equals(code))
            // 不一致，报错
            return Result.fail("验证码错误");

        // 4.一致，根据手机号查询用户 select * from tb_user where phone = ?
        User user = query().eq("phone", phone).one();

        // 5.判断用户是否存在
        if (user == null)
            // 6.不存在，创建新用户并保存
            user = createUserWithPhone(phone);

        // 7.保存用户信息到 redis中
        // 7.1.随机生成token，作为登录令牌
        String token = UUID.randomUUID().toString(true);
        // 7.2.将User对象转为HashMap存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(
                userDTO,
                new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor(
                                (fieldName, fieldValue) -> fieldValue.toString()
                        )
        );
        // 7.3.存储
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        // 7.4.设置token有效期
        stringRedisTemplate.expire(
                tokenKey,
                LOGIN_USER_TTL, TimeUnit.MINUTES
        );

        // 8.返回token
        return Result.ok(token);
    }
}
