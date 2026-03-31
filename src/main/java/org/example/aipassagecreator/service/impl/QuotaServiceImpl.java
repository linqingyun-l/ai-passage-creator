package org.example.aipassagecreator.service.impl;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.aipassagecreator.domain.PO.User;
import org.example.aipassagecreator.domain.VO.LoginUserVO;
import org.example.aipassagecreator.exception.BusinessException;
import org.example.aipassagecreator.exception.ErrorCode;
import org.example.aipassagecreator.mapper.UserMapper;
import org.example.aipassagecreator.service.QuotaService;
import org.example.aipassagecreator.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static org.example.aipassagecreator.domain.constant.UserConstant.ADMIN_ROLE;

@Deprecated
@Service
@Slf4j
public class QuotaServiceImpl implements QuotaService {

    @Resource
    private UserService userService;

    @Resource
    private UserMapper userMapper;


    @Override
    public boolean hasQuota(LoginUserVO user) {
        return false;
    }

    @Override
    public void consumeQuota(LoginUserVO user) {

    }

    @Override
    public void checkAndConsumeQuota(LoginUserVO user) {

    }
}