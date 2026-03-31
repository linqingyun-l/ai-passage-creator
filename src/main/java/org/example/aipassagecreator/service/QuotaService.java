package org.example.aipassagecreator.service;

import org.example.aipassagecreator.domain.PO.User;
import org.example.aipassagecreator.domain.VO.LoginUserVO;

public interface QuotaService {

    /**
     * 检查用户是否有足够的配额
     *
     * @param user 用户
     * @return 是否有配额
     */
    boolean hasQuota(LoginUserVO user);

    /**
     * 消耗配额（扣减1次）
     *
     * @param user 用户
     */
    void consumeQuota(LoginUserVO user);

    /**
     * 检查并消耗配额（原子操作）
     * 如果配额不足会抛出异常
     *
     * @param user 用户
     */
    void checkAndConsumeQuota(LoginUserVO user);
}