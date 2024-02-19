package com.chris.comments.controller;

import com.chris.comments.dto.LoginFormDTO;
import com.chris.comments.dto.Result;
import com.chris.comments.service.IUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {
    @Resource
    private IUserService userService;

    /**
     * 发送手机验证码
     */
    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        // 发送短信验证码并保存验证码
        // return userService.sendCodeV1(phone, session); // session方式
        return userService.sendCodeV2(phone, session); // redis方式
    }

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session){
        // 实现登录功能
        // return userService.loginV1(loginForm, session); // session 方式
        // return userService.loginV2(loginForm, session); // session 方式
        return userService.loginV3(loginForm, session);
    }
}
