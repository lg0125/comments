package com.chris.comments.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chris.comments.dto.Result;
import com.chris.comments.entity.SeckillVoucher;
import com.chris.comments.entity.VoucherOrder;
import com.chris.comments.mapper.VoucherOrderMapper;
import com.chris.comments.service.ISeckillVoucherService;
import com.chris.comments.service.IVoucherOrderService;
import com.chris.comments.utils.id.RedisIdWorker;
import com.chris.comments.utils.interceptor.UserHolderV2;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Override
    @Transactional
    public Result seckillVoucher(Long voucherId) {
        return seckillVoucherV1(voucherId);
    }


    private Result seckillVoucherV1(Long voucherId) {
        // 1.查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);

        // 2.判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            // 尚未开始
            return Result.fail("秒杀尚未开始！");
        }

        // 3.判断秒杀是否已经结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            // 尚未开始
            return Result.fail("秒杀已经结束！");
        }

        // 4.判断库存是否充足
        if (voucher.getStock() < 1) {
            // 库存不足
            return Result.fail("库存不足！");
        }

        // 5.扣减库存
        boolean isSuccess = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .update();
        if (!isSuccess) {
            // 扣减失败
            return Result.fail("库存不足！");
        }

        // 6.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 6.1.订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        // 6.2.用户id
        Long userId = UserHolderV2.getUser().getId();
        voucherOrder.setUserId(userId);
        // 6.3.代金券id
        voucherOrder.setVoucherId(voucherId);
        // 6.4 写入DB
        save(voucherOrder);

        // 7.返回订单id
        return Result.ok(orderId);
    }
}
