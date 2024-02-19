package com.chris.comments.utils.interceptor;

import lombok.NonNull;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 1.判断是否需要拦截（ThreadLocal中是否有用户）
        // System.out.println(UserHolderV2.getUser());
        if (UserHolderV2.getUser() == null) {
            // 没有，需要拦截，设置状态码
            response.setStatus(401);
            // 拦截
            return false;
        }

        // 有用户，则放行
        return true;
    }
}
