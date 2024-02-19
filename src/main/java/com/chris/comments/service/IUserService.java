package com.chris.comments.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chris.comments.dto.LoginFormDTO;
import com.chris.comments.dto.Result;
import com.chris.comments.entity.User;

import javax.servlet.http.HttpSession;

public interface IUserService extends IService<User> {
    Result sendCodeV1(String phone, HttpSession session);

    Result loginV1(LoginFormDTO loginForm, HttpSession session);

    Result loginV2(LoginFormDTO loginForm, HttpSession session);
}
