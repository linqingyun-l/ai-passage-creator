package org.example.aipassagecreator.service;

import org.example.aipassagecreator.domain.VO.StatisticsVO;
import org.example.aipassagecreator.domain.VO.UserStatisticsVO;

/**
 * 统计服务
 */
public interface StatisticsService {

    /**
     * 获取系统统计数据
     *
     * @return 统计数据
     */
    StatisticsVO getStatistics();

    /**
     * 获取用户统计数据
     *
     * @param userId 用户ID
     * @return 用户统计数据
     */
    UserStatisticsVO getUserStatistics(Long userId);
}