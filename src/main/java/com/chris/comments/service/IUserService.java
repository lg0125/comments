package com.chris.comments.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chris.comments.dto.Result;
import com.chris.comments.entity.User;

import javax.servlet.http.HttpSession;

public interface IUserService extends IService<User> {
    Result sendCodeV1(String phone, HttpSession session);

}
