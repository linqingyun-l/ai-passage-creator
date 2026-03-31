package org.example.aipassagecreator.controller;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.example.aipassagecreator.common.BaseResponse;
import org.example.aipassagecreator.common.ResultUtils;
import org.example.aipassagecreator.domain.DTO.User.UserLoginRequest;
import org.example.aipassagecreator.domain.DTO.User.UserRegisterRequest;
import org.example.aipassagecreator.domain.VO.LoginUserVO;
import org.example.aipassagecreator.exception.ErrorCode;
import org.example.aipassagecreator.exception.ThrowUtils;
import org.example.aipassagecreator.service.UserService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
public class UserController {
    @Resource
    private UserService userService;
    //用户注册
    @PostMapping("/register")
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest request){
        ThrowUtils.throwIf(request==null, ErrorCode.PARAMS_ERROR);
        String userPassword = request.getUserPassword();
        String checkPassword = request.getCheckPassword();
        String userAccount = request.getUserAccount();
        Long userID=userService.userRegister(userAccount, userPassword, checkPassword);
        return ResultUtils.success(userID);
    }
    //用户登录
    @PostMapping("/login")
    public BaseResponse<LoginUserVO> userLogin(@RequestBody UserLoginRequest request,HttpServletRequest httpServletRequest){
        ThrowUtils.throwIf(request==null, ErrorCode.PARAMS_ERROR);
        LoginUserVO userVO=userService.userLogin(request,httpServletRequest);
        return ResultUtils.success(userVO);
    }
    //获取当前用户
    @GetMapping("/get/current")
    public BaseResponse<LoginUserVO> getCurrentUser(HttpServletRequest request){
        ThrowUtils.throwIf(request==null, ErrorCode.PARAMS_ERROR);
        LoginUserVO loginUser =userService.getLoginUser(request);
        return ResultUtils.success(loginUser);
    }
    //用户登出
    @GetMapping("/logout")
    public BaseResponse<String> userLogout(HttpServletRequest request){
        ThrowUtils.throwIf(request==null, ErrorCode.PARAMS_ERROR);
        userService.userLogout(request);
        return ResultUtils.success("退出登录成功");
    }
}
