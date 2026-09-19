package com.sky.service.impl;

import com.sky.constant.MessageConstant;
import com.sky.dto.UserLoginDTO;
import com.sky.dto.UserRegisterDTO;
import com.sky.entity.User;
import com.sky.exception.LoginFailedException;
import com.sky.exception.AccountNotFoundException;
import com.sky.exception.PasswordErrorException;
import com.sky.exception.AccountLockedException;
import com.sky.exception.AccountExistException;
import com.sky.mapper.UserMapper;
import com.sky.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@Slf4j
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    /**
     * 账号密码登录
     *
     * @param userLoginDTO
     * @return
     */
    @Override
    public User login(UserLoginDTO userLoginDTO) {
        String username = userLoginDTO.getUsername();
        String password = userLoginDTO.getPassword();
        Integer role = userLoginDTO.getRole();

        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new LoginFailedException(MessageConstant.LOGIN_FAILED);
        }

        // 根据用户名查询用户
        User user = userMapper.getByUsername(username);
        if (user == null) {
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }

        // 校验角色是否匹配登录页选择
        if (role != null && !role.equals(user.getRole())) {
            throw new LoginFailedException(MessageConstant.ROLE_NOT_MATCH);
        }

        // 校验状态
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new AccountLockedException(MessageConstant.ACCOUNT_DISABLED);
        }

        // 校验密码（MD5）
        String md5Password = DigestUtils.md5DigestAsHex(password.getBytes());
        if (!md5Password.equals(user.getPassword())) {
            throw new PasswordErrorException(MessageConstant.PASSWORD_ERROR);
        }

        return user;
    }

    /**
     * 注册（用户/商家）
     *
     * @param userRegisterDTO
     */
    @Override
    public void register(UserRegisterDTO userRegisterDTO) {
        String username = userRegisterDTO.getUsername();
        String password = userRegisterDTO.getPassword();

        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new LoginFailedException(MessageConstant.LOGIN_FAILED);
        }

        // 用户名唯一
        if (userMapper.getByUsername(username) != null) {
            throw new AccountExistException(MessageConstant.ALREADY_EXISTS);
        }

        Integer role = userRegisterDTO.getRole();
        if (role == null || (role != 1 && role != 2)) {
            role = 1;
        }

        User user = User.builder()
                .username(username)
                .password(DigestUtils.md5DigestAsHex(password.getBytes()))
                .name(StringUtils.hasText(userRegisterDTO.getName()) ? userRegisterDTO.getName() : username)
                .phone(userRegisterDTO.getPhone())
                .role(role)
                .status(1)
                .createTime(LocalDateTime.now())
                .build();
        userMapper.insert(user);
    }
}
