package com.school.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.school.common.security.BizException;
import com.school.common.util.Ids;
import com.school.system.dto.DictCreateRequest;
import com.school.system.dto.DictUpdateRequest;
import com.school.system.entity.SysDict;
import com.school.system.mapper.SysDictMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 数据字典管理（M0505）。
 */
@Service
public class DictService {

    private final SysDictMapper dictMapper;

    public DictService(SysDictMapper dictMapper) {
        this.dictMapper = dictMapper;
    }

    public List<SysDict> list(String dictCode) {
        LambdaQueryWrapper<SysDict> qw = new LambdaQueryWrapper<>();
        if (dictCode != null && !dictCode.isBlank()) {
            qw.eq(SysDict::getDictCode, dictCode);
        }
        qw.orderByAsc(SysDict::getDictCode).orderByAsc(SysDict::getSort);
        return dictMapper.selectList(qw);
    }

    public SysDict create(DictCreateRequest req) {
        Long exists = dictMapper.selectCount(new LambdaQueryWrapper<SysDict>()
                .eq(SysDict::getDictCode, req.dictCode()).eq(SysDict::getItemCode, req.itemCode()));
        if (exists != null && exists > 0) {
            throw BizException.conflict("字典项编码已存在");
        }
        SysDict dict = new SysDict();
        dict.setId(Ids.next("dict"));
        dict.setDictCode(req.dictCode());
        dict.setItemCode(req.itemCode());
        dict.setItemName(req.itemName());
        dict.setSort(req.sort() == null ? 0 : req.sort());
        dict.setEnabled(req.enabled() == null || req.enabled());
        dict.setIsCurrent(req.isCurrent());
        dictMapper.insert(dict);
        return dictMapper.selectById(dict.getId());
    }

    public SysDict update(String id, DictUpdateRequest req) {
        SysDict dict = require(id);
        if (req.itemName() != null) {
            dict.setItemName(req.itemName());
        }
        if (req.sort() != null) {
            dict.setSort(req.sort());
        }
        if (req.enabled() != null) {
            dict.setEnabled(req.enabled());
        }
        if (req.isCurrent() != null) {
            dict.setIsCurrent(req.isCurrent());
        }
        dictMapper.updateById(dict);
        return dictMapper.selectById(id);
    }

    public void delete(String id) {
        require(id);
        dictMapper.deleteById(id);
    }

    private SysDict require(String id) {
        SysDict dict = dictMapper.selectById(id);
        if (dict == null) {
            throw BizException.notFound();
        }
        return dict;
    }
}
