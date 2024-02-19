package com.chris.comments.utils.interceptor;

import com.chris.comments.dto.UserDTO;
import com.chris.comments.entity.User;
import lombok.NonNull;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

public class LoginInterceptorV2 implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        HttpSession session = request.getSession();

        Object user = session.getAttribute("user");

        if(user == null) {
            response.setStatus(401);
            return false;
        }

        UserHolderV2.saveUser((UserDTO) user);

        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler, Exception ex) {
        UserHolderV2.removeUser();
    }
}
