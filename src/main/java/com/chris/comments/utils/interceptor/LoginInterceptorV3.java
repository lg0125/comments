package com.chris.comments.utils.interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.chris.comments.dto.UserDTO;
import com.chris.comments.utils.constant.RedisConstant;
import lombok.NonNull;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class LoginInterceptorV3 implements HandlerInterceptor {
    private final StringRedisTemplate stringRedisTemplate;

    public LoginInterceptorV3(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        // 1.获取request的header的token
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            // 没有，需要拦截，设置状态码
            response.setStatus(401);
            // 拦截
            return false;
        }

        // 2.基于token获取redis的用户
        String key = RedisConstant.LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);

        // 3.判断用户是否存在
        if(userMap.isEmpty()) {
            // 4. 没有，需要拦截，设置状态码
            response.setStatus(401);
            // 拦截
            return false;
        }

        // 5.将查询到的Hash数据转换成UserDTO对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);

        // 6.保存用户信息在ThreadLocal
        UserHolderV2.saveUser(userDTO);

        // 7.刷新token有效期
        stringRedisTemplate.expire(key, RedisConstant.LOGIN_USER_TTL, TimeUnit.MINUTES);

        // 8.放行
        return true;
    }
}
