package com.sky.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 网页版用户注册
 */
@Data
public class UserRegisterDTO implements Serializable {

    private String username;

    private String password;

    private String name;

    private String phone;

    /**
     * 注册角色：1用户 2商家
     */
    private Integer role;

}
