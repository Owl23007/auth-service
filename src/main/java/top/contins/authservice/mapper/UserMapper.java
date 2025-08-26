package top.contins.authservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import top.contins.authservice.model.po.UserPo;

/**
 * 用户数据访问接口
 */
@Mapper
public interface UserMapper extends BaseMapper<UserPo> {
}
