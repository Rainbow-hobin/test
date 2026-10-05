package com.sky.controller.user;

import com.sky.constant.JwtClaimsConstant;
import com.sky.context.BaseContext;
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
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/info")
    @ApiOperation("获取当前用户信息")
    public Result<UserLoginVO> info() {
        Long userId = BaseContext.getCurrentId();
        User user = userService.getById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }
        UserLoginVO vo = UserLoginVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .name(user.getName())
                .role(user.getRole())
                .token(null)
                .phone(user.getPhone())
                .sex(user.getSex())
                .avatar(user.getAvatar())
                .build();
        return Result.success(vo);
    }

    @PutMapping("/info")
    @ApiOperation("更新当前用户信息")
    public Result update(@RequestBody User user) {
        Long userId = BaseContext.getCurrentId();
        user.setId(userId);
        // 不允许通过这些字段提权或改关键信息
        user.setUsername(null);
        user.setPassword(null);
        user.setRole(null);
        user.setStatus(null);
        user.setIdNumber(null);
        user.setCreateTime(null);
        userService.update(user);
        return Result.success();
    }

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
