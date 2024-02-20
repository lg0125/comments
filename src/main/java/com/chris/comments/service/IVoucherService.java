package com.chris.comments.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chris.comments.dto.Result;
import com.chris.comments.entity.Voucher;

public interface IVoucherService extends IService<Voucher> {
    Result queryVoucherOfShop(Long shopId);

    void addSeckillVoucher(Voucher voucher);
}
