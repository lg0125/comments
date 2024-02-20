package com.chris.comments.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chris.comments.dto.Result;
import com.chris.comments.entity.SeckillVoucher;
import com.chris.comments.entity.Voucher;
import com.chris.comments.mapper.VoucherMapper;
import com.chris.comments.service.ISeckillVoucherService;
import com.chris.comments.service.IVoucherService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import static com.chris.comments.utils.constant.RedisConstant.SECKILL_STOCK_KEY;

@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryVoucherOfShop(Long shopId) {
        return null;
    }

    @Override
    public void addSeckillVoucher(Voucher voucher) {
        // 保存优惠券
        save(voucher);

        // 保存秒杀信息
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(voucher.getStock());
        seckillVoucher.setBeginTime(voucher.getBeginTime());
        seckillVoucher.setEndTime(voucher.getEndTime());

        seckillVoucherService.save(seckillVoucher);

        // 保存秒杀库存到Redis中
        stringRedisTemplate.opsForValue().set(
                SECKILL_STOCK_KEY + voucher.getId(),
                voucher.getStock().toString()
        );
    }
}
