package org.example.aipassagecreator.mapper;


import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.example.aipassagecreator.domain.PO.User;

/**
* @author LWK
* @description 针对表【user(用户)】的数据库操作Mapper
* @createDate 2026-03-23 14:46:03
* @Entity generator.domain.User
*/
public interface UserMapper extends BaseMapper<User> {
    /**
     * 原子扣减用户配额
     * 使用 quota > 0 条件确保并发安全，避免超扣
     *
     * @param userId 用户ID
     * @return 影响行数，1表示成功，0表示配额不足
     */
    @Update("UPDATE user SET quota = quota - 1 WHERE id = #{userId} AND quota > 0")
    int decrementQuota(@Param("userId") Long userId);
}




