package org.example.aipassagecreator.service;


import com.mybatisflex.core.service.IService;
import jakarta.servlet.http.HttpServletRequest;
import org.example.aipassagecreator.domain.DTO.User.UserLoginRequest;
import org.example.aipassagecreator.domain.PO.User;
import org.example.aipassagecreator.domain.VO.LoginUserVO;

/**
* @author LWK
* @description 针对表【user(用户)】的数据库操作Service
* @createDate 2026-03-23 14:46:03
*/
public interface UserService extends IService<User> {

    Long userRegister(String userAccount, String userPassword, String checkPassword);

    LoginUserVO userLogin(UserLoginRequest request,HttpServletRequest httpServletRequest);

    LoginUserVO getLoginUser(HttpServletRequest request);

    void userLogout(HttpServletRequest request);
}
