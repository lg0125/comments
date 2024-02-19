package com.chris.comments.controller;

import com.chris.comments.entity.ShopType;
import com.chris.comments.dto.Result;
import com.chris.comments.service.IShopTypeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {
    @Resource
    private IShopTypeService typeService;

    @GetMapping("list")
    public Result queryTypeList() {
        List<ShopType> typeList = typeService.query().orderByAsc("sort").list();
        return Result.ok(typeList);
    }
}
