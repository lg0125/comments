package com.chris.comments.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chris.comments.dto.Result;
import com.chris.comments.entity.ShopType;
import com.chris.comments.mapper.ShopTypeMapper;
import com.chris.comments.service.IShopTypeService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;


@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
        // 1.从redis查询缓存

        // 2.判断缓存是否命中

        // 3. 命中,直接返回

        // 4. 缺失,根据id查询DB

        // 5. 判断商铺是否存在

        // 6. 不存在,返回错误

        // 7. 存在,写入redis并返回结果

        return Result.ok();
    }
}
