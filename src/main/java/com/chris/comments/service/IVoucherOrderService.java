package com.chris.comments.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chris.comments.dto.Result;
import com.chris.comments.entity.VoucherOrder;

public interface IVoucherOrderService extends IService<VoucherOrder> {
    Result seckillVoucher(Long voucherId);

    Result createVoucherOrderV2(Long voucherId);

    Result createVoucherOrderV1(Long voucherId);
}
