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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class UserServiceImpl implements UserService {

    // 登录失败计数key前缀，完整key为 user:login:fail:{username}
    private static final String LOGIN_FAIL_PREFIX = "user:login:fail:";
    // 允许连续失败的最大次数
    private static final int LOGIN_FAIL_MAX_COUNT = 5;
    // 达到上限后的锁定时长
    private static final long LOGIN_LOCK_MINUTES = 10L;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

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

        // 登录失败次数过多时直接拒绝（连续失败5次锁定10分钟）
        String failKey = LOGIN_FAIL_PREFIX + username;
        String failCount = stringRedisTemplate.opsForValue().get(failKey);
        if (failCount != null && Integer.parseInt(failCount) >= LOGIN_FAIL_MAX_COUNT) {
            throw new AccountLockedException(MessageConstant.LOGIN_LOCKED);
        }

        // 根据用户名查询用户
        User user = userMapper.getByUsername(username);
        if (user == null) {
            recordLoginFailure(failKey);
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }

        // 校验角色是否匹配登录页选择
        if (role != null && !role.equals(user.getRole())) {
            recordLoginFailure(failKey);
            throw new LoginFailedException(MessageConstant.ROLE_NOT_MATCH);
        }

        // 校验状态
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new AccountLockedException(MessageConstant.ACCOUNT_DISABLED);
        }

        // 校验密码（MD5）
        String md5Password = DigestUtils.md5DigestAsHex(password.getBytes());
        if (!md5Password.equals(user.getPassword())) {
            recordLoginFailure(failKey);
            throw new PasswordErrorException(MessageConstant.PASSWORD_ERROR);
        }

        // 登录成功，清除失败计数
        stringRedisTemplate.delete(failKey);
        return user;
    }

    /**
     * 记录一次登录失败：计数+1，首次失败时设置锁定窗口过期时间
     *
     * @param failKey 失败计数redis key
     */
    private void recordLoginFailure(String failKey) {
        Long count = stringRedisTemplate.opsForValue().increment(failKey);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(failKey, LOGIN_LOCK_MINUTES, TimeUnit.MINUTES);
        }
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
