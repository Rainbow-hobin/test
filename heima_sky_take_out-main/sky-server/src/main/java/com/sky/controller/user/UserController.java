package com.sky.controller.user;

import com.sky.constant.JwtClaimsConstant;
import com.sky.dto.UserLoginDTO;
import com.sky.dto.UserRegisterDTO;
import com.sky.entity.User;
import com.sky.properties.JwtProperties;
import com.sky.result.Result;
import com.sky.service.UserService;
import com.sky.utils.JwtUtil;
import com.sky.vo.UserLoginVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@Api(tags = "C端用户相关接口")
@RequestMapping("/user/user")
@Slf4j
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtProperties jwtProperties;

    @PostMapping("/login")
    @ApiOperation("账号密码登录")
    public Result<UserLoginVO> login(@RequestBody UserLoginDTO userLoginDTO) {
        // 账号密码登录
        User user = userService.login(userLoginDTO);

        // 为用户生成jwt
        Map<String, Object> cliams = new HashMap<>();
        cliams.put(JwtClaimsConstant.USER_ID, user.getId());
        cliams.put(JwtClaimsConstant.USERNAME, user.getUsername());
        cliams.put(JwtClaimsConstant.NAME, user.getName());
        cliams.put(JwtClaimsConstant.ROLE, user.getRole());
        String token = JwtUtil.createJWT(jwtProperties.getUserSecretKey(), jwtProperties.getUserTtl(), cliams);

        UserLoginVO build = UserLoginVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .name(user.getName())
                .role(user.getRole())
                .token(token)
                .build();

        return Result.success(build);
    }

    @PostMapping("/register")
    @ApiOperation("注册（用户/商家）")
    public Result register(@RequestBody UserRegisterDTO userRegisterDTO) {
        userService.register(userRegisterDTO);
        return Result.success();
    }
}
