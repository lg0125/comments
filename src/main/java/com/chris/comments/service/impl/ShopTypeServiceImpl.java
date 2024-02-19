package com.chris.comments.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chris.comments.entity.ShopType;
import com.chris.comments.mapper.ShopTypeMapper;
import com.chris.comments.service.IShopTypeService;
import org.springframework.stereotype.Service;


@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

}
