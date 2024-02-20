package com.chris.comments.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chris.comments.dto.Result;
import com.chris.comments.entity.Shop;

public interface IShopService extends IService<Shop> {
    Result queryByIdV1(Long id);

    Result update(Shop shop);

    Result queryByIdV2(Long id);

    Result queryByIdV3(Long id);

    Result queryByIdV4(Long id);

    Result queryByIdV5(Long id);
}
