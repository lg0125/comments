package com.chris.comments.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chris.comments.dto.Result;
import com.chris.comments.entity.Shop;
import com.chris.comments.mapper.ShopMapper;
import com.chris.comments.service.IShopService;
import com.chris.comments.utils.cache.CacheClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.chris.comments.utils.constant.RedisConstant.*;

public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryByIdV1(Long id) {
        // 1.从redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);

        // 2.判断缓存是否命中
        if (StrUtil.isNotBlank(shopJson)) {
            // 3. 命中,直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }

        // 4. 缺失,根据id查询DB
        Shop shop = getById(id);

        // 5. 判断商铺是否存在
        if (shop == null) {
            // 6. 不存在,返回错误
            return Result.fail("商铺不存在!");
        }

        // 7. 存在,写入redis并返回结果
        stringRedisTemplate.opsForValue().set(
                CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop),
                CACHE_SHOP_TTL, TimeUnit.MINUTES
        );
        return Result.ok(shop);
    }

    @Override
    public Result queryByIdV2(Long id) {
        // 1.从redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);

        // 2.判断缓存是否命中
        if (StrUtil.isNotBlank(shopJson)) {
            // 3. 命中,直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }

        // 判断命中的是否为空值
        if (shopJson != null) {
            // 返回一个错误信息
            return Result.fail("商铺信息不存在!");
        }

        // 4. 缺失,根据id查询DB
        Shop shop = getById(id);

        // 5. 判断商铺是否存在
        if (shop == null) {
            // 6. 不存在,将空值写入redis并返回错误
            stringRedisTemplate.opsForValue().set(
                    CACHE_SHOP_KEY + id, "",
                    CACHE_NULL_TTL, TimeUnit.MINUTES
            );
            return Result.fail("商铺不存在!");
        }

        // 7. 存在,写入redis并返回结果
        stringRedisTemplate.opsForValue().set(
                CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop),
                CACHE_SHOP_TTL, TimeUnit.MINUTES
        );
        return Result.ok(shop);
    }

    @Override
    public Result queryByIdV3(Long id) {
        // 缓存工具类 解决 缓存穿透

        Shop shop = cacheClient.queryWithPassThrough(
                CACHE_SHOP_KEY, id,
                Shop.class, this::getById,
                CACHE_SHOP_TTL, TimeUnit.MINUTES
        );

        if (shop == null) {
            return Result.fail("店铺不存在！");
        }

        return Result.ok(shop);
    }

    @Override
    public Result queryByIdV4(Long id) {
        // 缓存工具类 互斥锁解决缓存击穿

        Shop shop = cacheClient.queryWithMutex(
                CACHE_SHOP_KEY, id,
                Shop.class, this::getById,
                CACHE_SHOP_TTL, TimeUnit.MINUTES
        );

        if (shop == null) {
            return Result.fail("店铺不存在！");
        }

        return Result.ok(shop);
    }

    @Override
    public Result queryByIdV5(Long id) {
        // 逻辑过期解决缓存击穿

        Shop shop = cacheClient.queryWithLogicalExpire(
                CACHE_SHOP_KEY, id,
                Shop.class, this::getById,
                CACHE_SHOP_TTL, TimeUnit.MINUTES
        );

        if (shop == null) {
            return Result.fail("店铺不存在！");
        }

        return Result.ok(shop);
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("商铺id不能为空");
        }

        // 1.更新数据库
        updateById(shop);

        // 2. 删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);

        return Result.ok();
    }
}
