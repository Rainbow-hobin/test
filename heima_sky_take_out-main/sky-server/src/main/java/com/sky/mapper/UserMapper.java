package com.sky.mapper;

import com.sky.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface UserMapper {

    /**
     * 根据用户名查询用户
     * @param username
     * @return
     */
    @Select("select * from user where username = #{username}")
    User getByUsername(String username);

    /**
     * 插入数据
     * @param user
     */
    void insert(User user);

    @Select("select * from user where id = #{id}")
    User getById(Long userId);

    void update(User user);

    /**
     * 根据动态条件统计用户数量
     * @param begin 开始时间
     * @param end 结束时间
     * @return
     */
    Integer countByMap(@Param("begin") LocalDateTime begin, @Param("end") LocalDateTime end);
}
