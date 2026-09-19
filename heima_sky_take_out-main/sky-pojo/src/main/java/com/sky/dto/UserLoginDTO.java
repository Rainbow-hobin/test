package com.sky.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * C端用户登录（账号密码）
 */
@Data
public class UserLoginDTO implements Serializable {

    private String username;
    private String password;

    /**
     * 登录角色：1用户 2商家（登录页选择）
     */
    private Integer role;

}
