package org.example.aipassagecreator.service.impl;


import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.example.aipassagecreator.domain.DTO.User.UserLoginRequest;
import org.example.aipassagecreator.domain.PO.User;
import org.example.aipassagecreator.domain.VO.LoginUserVO;
import org.example.aipassagecreator.domain.constant.UserConstant;
import org.example.aipassagecreator.exception.BusinessException;
import org.example.aipassagecreator.exception.ErrorCode;
import org.example.aipassagecreator.exception.ThrowUtils;
import org.example.aipassagecreator.mapper.UserMapper;
import org.example.aipassagecreator.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;

import static org.example.aipassagecreator.domain.constant.UserConstant.USER_LOGIN_STATE;

/**
* @author LWK
* @description 针对表【user(用户)】的数据库操作Service实现
* @createDate 2026-03-23 14:46:02
*/
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
    implements UserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Long userRegister(String userAccount, String userPassword, String checkPassword) {
        // 1. 校验参数
        if (StrUtil.hasBlank(userAccount, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 3) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号长度过短");
        }
        if (userPassword.length() < 8 || checkPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码长度过短");
        }
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        }
        // 2. 查询用户是否已存在
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq("userAccount", userAccount);
        long count = this.mapper.selectCountByQuery(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号重复");
        }
        // 3. 加密密码
        String encryptPassword = getEncryptPassword(userPassword);
        // 4. 创建用户，插入数据库
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryptPassword);
        user.setUserName("无名");
        user.setUserRole(UserConstant.DEFAULT_ROLE);
        boolean saveResult = this.save(user);
        if (!saveResult) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "注册失败，数据库错误");
        }
        return user.getId();
    }

    @Override
    public LoginUserVO userLogin(UserLoginRequest request,HttpServletRequest httpServletRequest) {
        String userAccount = request.getUserAccount();
        String userPassword = request.getUserPassword();
        String captcha = stringRedisTemplate.opsForValue().get(request.getUuid());
        // 1. 校验参数
        if (StrUtil.hasBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (StrUtil.isBlank(captcha)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "验证码已过期");
        }
        if (!captcha.equals(request.getCaptcha())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "验证码错误");
        }
        //2.删除验证码
        stringRedisTemplate.delete(request.getUuid());
        // 3. 加密
        String encryptPassword = getEncryptPassword(userPassword);
        // 4. 查询用户是否存在
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq("userAccount", userAccount);
        queryWrapper.eq("userPassword", encryptPassword);
        User user = this.mapper.selectOneByQuery(queryWrapper);
        if (user == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
        }
        // 5. 记录用户的登录态
        httpServletRequest.getSession().setAttribute(USER_LOGIN_STATE, user);
        // 6. 返回脱敏的用户信息
        return this.getLoginUserVO(user);
    }

    public LoginUserVO getLoginUserVO(User user) {
        ThrowUtils.throwIf(user == null, ErrorCode.PARAMS_ERROR);
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtils.copyProperties(user, loginUserVO);
        return loginUserVO;
    }

    @Override
    public LoginUserVO getLoginUser(HttpServletRequest request) {
        // 先判断用户是否登录
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User currentUser = (User) userObj;
        if (currentUser == null || currentUser.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        // 从数据库查询当前用户信息（保证数据最新）
        long userId = currentUser.getId();
        currentUser = this.getById(userId);
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return getLoginUserVO(currentUser);
    }

    @Override
    public void userLogout(HttpServletRequest request) {
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        if (userObj == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "用户未登录");
        }
        request.getSession().removeAttribute(USER_LOGIN_STATE);
    }
    public String getEncryptPassword(String userPassword) {
        // 盐值，混淆密码
        final String SALT = "lin";
        return DigestUtils.md5DigestAsHex((userPassword + SALT).getBytes(StandardCharsets.UTF_8));
    }

}




