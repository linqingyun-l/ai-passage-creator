package org.example.aipassagecreator.Aspect;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.example.aipassagecreator.annotation.AuthCheck;
import org.example.aipassagecreator.domain.VO.LoginUserVO;
import org.example.aipassagecreator.domain.constant.UserRoleEnum;
import org.example.aipassagecreator.exception.BusinessException;
import org.example.aipassagecreator.exception.ErrorCode;
import org.example.aipassagecreator.service.UserService;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
public class AuthInterceptor {

    @Resource
    private UserService userService;

    @Around("@annotation(authCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        String mustRole = authCheck.mustRole();
        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
        // 获取当前登录用户
        LoginUserVO loginUser = userService.getLoginUser(request);
        UserRoleEnum mustRoleEnum = UserRoleEnum.getEnumByValue(mustRole);
        // 不需要权限，直接放行
        if (mustRoleEnum == null) {
            return joinPoint.proceed();
        }
        // 获取用户角色枚举
        UserRoleEnum userRoleEnum = UserRoleEnum.getEnumByValue(loginUser.getUserRole());
        // 要求必须有管理员权限，但当前用户没有
        if (UserRoleEnum.ADMIN.equals(mustRoleEnum)) {
            if (userRoleEnum == null || !UserRoleEnum.ADMIN.equals(userRoleEnum)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
            return joinPoint.proceed();
        }
        // 要求普通用户权限（user角色），用户已登录且有有效角色字符串即可通过
        // 只要用户有有效角色字符串（非null/空，即USER/ADMIN/VIP等）即可访问
        String userRole = loginUser.getUserRole();
        if (userRole != null && !userRole.isEmpty()) {
            return joinPoint.proceed();
        }
        // 用户角色为空或无效
        throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
    }
}