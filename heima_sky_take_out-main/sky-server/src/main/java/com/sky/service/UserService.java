package com.sky.service;

import com.sky.dto.UserLoginDTO;
import com.sky.dto.UserRegisterDTO;
import com.sky.entity.User;

public interface UserService {

    /**
     * 账号密码登录
     * @param userLoginDTO
     * @return
     */
    User login(UserLoginDTO userLoginDTO);

    /**
     * 注册（用户/商家）
     * @param userRegisterDTO
     */
    void register(UserRegisterDTO userRegisterDTO);
}
