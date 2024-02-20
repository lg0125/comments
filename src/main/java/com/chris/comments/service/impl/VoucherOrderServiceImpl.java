package com.chris.comments.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chris.comments.dto.Result;
import com.chris.comments.entity.VoucherOrder;
import com.chris.comments.mapper.VoucherOrderMapper;
import com.chris.comments.service.IVoucherOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Override
    public Result seckillVoucher(Long voucherId) {
        return null;
    }
}
