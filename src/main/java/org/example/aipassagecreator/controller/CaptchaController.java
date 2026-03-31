package org.example.aipassagecreator.controller;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.CircleCaptcha;
import cn.hutool.core.lang.UUID;
import jakarta.annotation.Resource;


import org.example.aipassagecreator.common.BaseResponse;
import org.example.aipassagecreator.common.ResultUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;


@RestController
@RequestMapping("/captcha")
public class CaptchaController {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    //获取验证码
    @GetMapping("/get")
    public BaseResponse<HashMap<String,String>> getCaptcha() {
        String uuid = UUID.randomUUID().toString();
        CircleCaptcha circleCaptcha = CaptchaUtil.createCircleCaptcha(120, 50, 4, 4);
        String captcha = circleCaptcha.getCode();
        //保存验证码到redis
        stringRedisTemplate.opsForValue().set(uuid, captcha);
        //封装验证码
        HashMap<String, String> captchaMap = new HashMap<>();
        captchaMap.put("image", "data:image/png;base64," + circleCaptcha.getImageBase64());
        captchaMap.put("uuid", uuid);
        //返回验证码
        return ResultUtils.success(captchaMap);
    }
}
