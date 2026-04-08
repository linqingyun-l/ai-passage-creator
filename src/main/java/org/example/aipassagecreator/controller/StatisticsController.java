package org.example.aipassagecreator.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.example.aipassagecreator.annotation.AuthCheck;
import org.example.aipassagecreator.common.BaseResponse;
import org.example.aipassagecreator.common.ResultUtils;
import org.example.aipassagecreator.domain.VO.LoginUserVO;
import org.example.aipassagecreator.domain.VO.StatisticsVO;
import org.example.aipassagecreator.domain.VO.UserStatisticsVO;
import org.example.aipassagecreator.domain.constant.UserConstant;
import org.example.aipassagecreator.service.StatisticsService;
import org.example.aipassagecreator.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统计分析控制器
 */
@RestController
@RequestMapping("/statistics")
@Slf4j
@Tag(name = "StatisticsController", description = "统计分析接口")
public class StatisticsController {

    @Resource
    private StatisticsService statisticsService;

    @Resource
    private UserService userService;

    /**
     * 获取系统统计数据（仅管理员）
     */
    @GetMapping("/overview")
    @Operation(summary = "获取系统统计数据")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<StatisticsVO> getStatistics() {
        StatisticsVO statistics = statisticsService.getStatistics();
        return ResultUtils.success(statistics);
    }

    /**
     * 获取用户统计数据
     */
    @GetMapping("/user")
    @Operation(summary = "获取用户统计数据")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<UserStatisticsVO> getUserStatistics(HttpServletRequest httpServletRequest) {
        LoginUserVO loginUser = userService.getLoginUser(httpServletRequest);
        UserStatisticsVO userStatistics = statisticsService.getUserStatistics(loginUser.getId());
        return ResultUtils.success(userStatistics);
    }
}