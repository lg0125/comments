package com.chris.comments.controller;

import com.chris.comments.dto.Result;
import com.chris.comments.entity.Shop;
import com.chris.comments.service.IShopService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/shop")
public class ShopController {
    @Resource
    public IShopService shopService;

    /**
     * 根据id查询商铺信息
     * @param id 商铺id
     * @return 商铺详情数据
     */
    @GetMapping("/{id}")
    public Result queryShopById(@PathVariable("id") Long id) {
        // return shopService.queryByIdV1(id);
        // return shopService.queryByIdV2(id);  // 解决 缓存穿透
        // return shopService.queryByIdV3(id);  // 缓存工具类 解决 缓存穿透
        // return shopService.queryByIdV4(id);     // 缓存工具类 互斥锁解决缓存击穿
        return shopService.queryByIdV5(id); // 逻辑过期解决缓存击穿
    }

    /**
     * 更新商铺信息
     * @param shop 商铺数据
     * @return 无
     */
    @PutMapping
    public Result updateShop(@RequestBody Shop shop) {
        // 写入数据库
        return shopService.update(shop);
    }
}
