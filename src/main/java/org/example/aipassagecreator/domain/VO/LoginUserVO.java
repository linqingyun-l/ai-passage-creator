package org.example.aipassagecreator.domain.VO;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class LoginUserVO implements Serializable {
    private Long id;
    private String userAccount;
    private String userName;
    private String userAvatar;
    private String userProfile;
    private String userRole;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    /**
     * 成为会员时间
     */
    private LocalDateTime vipTime;
    /**
     * 创作额度（剩余可创建文章数）
     */
    private Integer quota;

}
