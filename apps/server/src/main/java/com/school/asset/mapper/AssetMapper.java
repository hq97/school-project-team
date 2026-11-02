package com.school.asset.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.school.asset.entity.Asset;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 资产台账 Mapper。
 *
 * <p>库存扣减/回滚走 CAS 原子 SQL（C9），语句定义在 {@code resources/mapper/AssetMapper.xml}
 * （门禁4 规则② 禁止业务 Mapper 声明 @Update 原生注解，原子更新统一走 XML）：
 * 扣减用 {@code WHERE availableQty >= qty} 条件更新，影响行数 0 即库存不足（40903），
 * 并发下由 PostgreSQL 行锁串行化，避免超卖。</p>
 */
@Mapper
public interface AssetMapper extends BaseMapper<Asset> {

    /** 原子扣减库存（CAS），返回影响行数；0 = 库存不足。 */
    int deductStock(@Param("id") String id, @Param("qty") int qty);

    /** 回滚库存（审批驳回）。 */
    int restoreStock(@Param("id") String id, @Param("qty") int qty);
}
