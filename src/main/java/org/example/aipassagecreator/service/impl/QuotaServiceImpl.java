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
import static org.example.aipassagecreator.domain.constant.UserConstant.VIP_ROLE;


@Service
@Slf4j
public class QuotaServiceImpl implements QuotaService {

    @Resource
    private UserService userService;

    @Resource
    private UserMapper userMapper;


    /**
     * 检查用户是否有足够的配额
     * @param user 用户
     * @return
     */
    @Override
    public boolean hasQuota(LoginUserVO user) {
        // 管理员和 VIP 用户无限配额
        if (isAdmin(user) || isVip(user)) {
            return true;
        }
        // 从数据库查询最新配额，避免使用缓存的旧数据
        User latestUser = userMapper.selectOneById(user.getId());
        return latestUser != null && latestUser.getQuota() != null && latestUser.getQuota() > 0;
    }

    /**
     * 消耗配额（扣减1次）
     * @param user 用户
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void consumeQuota(LoginUserVO user) {
        // 管理员和 VIP 用户不消耗配额
        if (isAdmin(user) || isVip(user)) {
            return;
        }

        // 使用原子更新：UPDATE user SET quota = quota - 1 WHERE id = ? AND quota > 0
        // 通过影响行数判断是否成功，避免并发问题
        int affectedRows = userMapper.decrementQuota(user.getId());

        if (affectedRows > 0) {
            log.info("用户配额已消耗, userId={}", user.getId());
        } else {
            log.warn("用户配额扣减失败（可能配额不足或并发冲突）, userId={}", user.getId());
        }
    }

    @Override
    public void checkAndConsumeQuota(LoginUserVO user) {

    }

    /**
     * 判断是否为 VIP
     */
    private boolean isVip(LoginUserVO user) {
        return VIP_ROLE.equals(user.getUserRole());
    }
    /**
     * 判断是否为管理员
     */
    private boolean isAdmin(LoginUserVO user) {
        return ADMIN_ROLE.equals(user.getUserRole());
    }
}