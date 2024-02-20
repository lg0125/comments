package com.chris.comments.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chris.comments.dto.Result;
import com.chris.comments.entity.SeckillVoucher;
import com.chris.comments.entity.VoucherOrder;
import com.chris.comments.mapper.VoucherOrderMapper;
import com.chris.comments.service.ISeckillVoucherService;
import com.chris.comments.service.IVoucherOrderService;
import com.chris.comments.utils.id.RedisIdWorker;
import com.chris.comments.utils.interceptor.UserHolderV2;
import com.chris.comments.utils.lock.SimpleRedisLockV1;
import com.chris.comments.utils.lock.SimpleRedisLockV2;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPTS_V1;
    static {
        SECKILL_SCRIPTS_V1 = new DefaultRedisScript<>();
        SECKILL_SCRIPTS_V1.setLocation(new ClassPathResource("lua/seckillV1.lua"));
        SECKILL_SCRIPTS_V1.setResultType(Long.class);
    }

    private static final DefaultRedisScript<Long> SECKILL_SCRIPTS_V2;
    static {
        SECKILL_SCRIPTS_V2 = new DefaultRedisScript<>();
        SECKILL_SCRIPTS_V2.setLocation(new ClassPathResource("lua/seckillV2.lua"));
        SECKILL_SCRIPTS_V2.setResultType(Long.class);
    }

    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);


    private IVoucherOrderService proxyV10V11;
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    private class VoucherOrderHandlerV1 implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    // 1.获取消息队列中的订单信息
                    VoucherOrder voucherOrder = orderTasks.take();
                    // 2.创建订单
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                }
            }
        }
    }
    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        Long userId     = voucherOrder.getUserId();
        // 获取锁对象
        RLock lock      = redissonClient.getLock("lock:order:" + userId);
        // 获取锁
        boolean isLock  = lock.tryLock();
        // 判断是否成功获取锁
        if (!isLock) {
            // 获取锁失败,返回
            log.error("不允许重复下单");
            return;
        }
        // 获取代理对象(事务)
        try {
           proxyV10V11.createVoucherOrder(voucherOrder);
        } finally {
            // 释放锁
            lock.unlock();
        }
    }


    @SuppressWarnings("unchecked")
    private class VoucherOrderHandlerV2 implements Runnable {
        String queueName = "stream.orders";
        @Override
        public void run() {
            while (true) {
                try {
                    // 1.获取消息队列中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS s1 >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );

                    // 2.判断订单信息是否为空
                    if (list == null || list.isEmpty()) {
                        // 如果为null，说明没有消息，继续下一次循环
                        continue;
                    }

                    // 解析数据
                    MapRecord<String, Object, Object> record    = list.get(0);
                    Map<Object, Object> values                  = record.getValue();
                    VoucherOrder voucherOrder                   = BeanUtil.fillBeanWithMap(
                            values,
                            new VoucherOrder(),
                            true
                    );

                    // 3.创建订单
                    handleVoucherOrder(voucherOrder);

                    // 4.确认消息 XACK
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void handlePendingList() {
        String queueName = "stream.orders";

        while (true) {
            try {
                // 1.获取pending-list中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 STREAMS stream.orders 0
                List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                        Consumer.from("g1", "c1"),
                        StreamReadOptions.empty().count(1),
                        StreamOffset.create(queueName, ReadOffset.from("0"))
                );

                // 2.判断订单信息是否为空
                if (list == null || list.isEmpty()) {
                    // 如果为null，说明没有异常消息，结束循环
                    break;
                }

                // 解析数据
                MapRecord<String, Object, Object> record    = list.get(0);
                Map<Object, Object> value                   = record.getValue();
                VoucherOrder voucherOrder                   = BeanUtil.fillBeanWithMap(
                        value,
                        new VoucherOrder(),
                        true
                );

                // 3.创建订单
                handleVoucherOrder(voucherOrder);

                // 4.确认消息 XACK
                stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
            } catch (Exception e) {
                log.error("处理订单异常", e);
            }
        }
    }

    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandlerV2());
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        return seckillVoucherV1(voucherId);
    }


    public Result seckillVoucherV1(Long voucherId) {
        // @Transactional

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

    public Result seckillVoucherV2(Long voucherId) {
        // @Transactional
        // 乐观锁解决超卖 CAS

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
                .setSql("stock = stock - 1") // set stock = stock - 1
                .eq("voucher_id", voucherId)
                .eq("stock", voucher.getStock()) // where id = ? and stock = ?
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

    public Result seckillVoucherV3(Long voucherId) {
        // @Transactional
        // 乐观锁解决超卖 CAS
        // 改进 提高成功率

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
                .setSql("stock = stock - 1") // set stock = stock - 1
                .eq("voucher_id", voucherId)
                .gt("stock", 0) // where id = ? and stock > 0
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

    public Result seckillVoucherV4(Long voucherId) {
        // @Transactional
        // 简易版一人一单
        // 存在线程安全问题

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

        // 5.一人一单
        Long userId = UserHolderV2.getUser().getId();
        // 5.1.查询订单
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        // 5.2.判断是否存在
        if (count > 0) {
            // 用户已经购买过了
            return Result.fail("用户已经购买过一次！");
        }

        // 6.扣减库存
        boolean isSuccess = seckillVoucherService.update()
                .setSql("stock = stock - 1") // set stock = stock - 1
                .eq("voucher_id", voucherId)
                .gt("stock", 0) // where id = ? and stock > 0
                .update();
        if (!isSuccess) {
            // 扣减失败
            return Result.fail("库存不足！");
        }

        // 7.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 7.1.订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        // 7.2.用户id
        voucherOrder.setUserId(userId);
        // 7.3.代金券id
        voucherOrder.setVoucherId(voucherId);
        // 7.4 写入DB
        save(voucherOrder);

        // 8.返回订单id
        return Result.ok(orderId);
    }

    public Result seckillVoucherV5(Long voucherId) {
        // 线程安全版一人一单
        // 此方法不需要@Transactional

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

        IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
        return proxy.createVoucherOrderV5(voucherId);
    }

    @Transactional
    public synchronized Result createVoucherOrderV5(Long voucherId) {
        // 5.一人一单
        Long userId = UserHolderV2.getUser().getId();
        // 5.1.查询订单
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        // 5.2.判断是否存在
        if (count > 0) {
            // 用户已经购买过了
            return Result.fail("用户已经购买过一次！");
        }

        // 6.扣减库存
        boolean isSuccess = seckillVoucherService.update()
                .setSql("stock = stock - 1") // set stock = stock - 1
                .eq("voucher_id", voucherId)
                .gt("stock", 0) // where id = ? and stock > 0
                .update();
        if (!isSuccess) {
            // 扣减失败
            return Result.fail("库存不足！");
        }

        // 7.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 7.1.订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        // 7.2.用户id
        voucherOrder.setUserId(userId);
        // 7.3.代金券id
        voucherOrder.setVoucherId(voucherId);
        // 7.4 写入DB
        save(voucherOrder);

        // 8.返回订单id
        return Result.ok(orderId);
    }

    public Result seckillVoucherV6(Long voucherId) {
        // 线程安全版一人一单
        // 此方法不需要@Transactional

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

        Long userId = UserHolderV2.getUser().getId();
        synchronized (userId.toString().intern()) {
            // 获取代理对象(事务)
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }
    }

    public Result seckillVoucherV7(Long voucherId) {
        // 线程安全版一人一单
        // 此方法不需要@Transactional

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

        Long userId = UserHolderV2.getUser().getId();

        // 获取锁对象
        SimpleRedisLockV1 lock = new SimpleRedisLockV1(
                "order:" + userId,
                stringRedisTemplate
        );
        // 获取锁
        boolean isLock = lock.tryLock(1000);
        // 判断是否成功获取锁
        if (!isLock) {
            // 获取锁失败,返回错误
            return Result.fail("不允许重复下单!");
        }
        // 获取代理对象(事务)
        try {
            // 获取代理对象(事务)
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } finally {
            // 释放锁
            lock.unlock();
        }
    }

    public Result seckillVoucherV8(Long voucherId) {
        // 线程安全版一人一单
        // 此方法不需要@Transactional

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

        Long userId = UserHolderV2.getUser().getId();

        // 获取锁对象
        SimpleRedisLockV2 lock = new SimpleRedisLockV2(
                "order:" + userId,
                stringRedisTemplate
        );
        // 获取锁
        boolean isLock = lock.tryLock(1000);
        // 判断是否成功获取锁
        if (!isLock) {
            // 获取锁失败,返回错误
            return Result.fail("不允许重复下单!");
        }
        // 获取代理对象(事务)
        try {
            // 获取代理对象(事务)
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } finally {
            // 释放锁
            lock.unlock();
        }
    }

    public Result seckillVoucherV9(Long voucherId) {
        // 线程安全版一人一单
        // 此方法不需要@Transactional

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

        Long userId = UserHolderV2.getUser().getId();

        // 获取锁对象
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        // 获取锁
        boolean isLock = lock.tryLock();
        // 判断是否成功获取锁
        if (!isLock) {
            // 获取锁失败,返回错误
            return Result.fail("不允许重复下单!");
        }
        // 获取代理对象(事务)
        try {
            // 获取代理对象(事务)
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } finally {
            // 释放锁
            lock.unlock();
        }
    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        // 5.一人一单
        Long userId = UserHolderV2.getUser().getId();

        // 5.1.查询订单
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        // 5.2.判断是否存在
        if (count > 0) {
            // 用户已经购买过了
            return Result.fail("用户已经购买过一次！");
        }

        // 6.扣减库存
        boolean isSuccess = seckillVoucherService.update()
                .setSql("stock = stock - 1") // set stock = stock - 1
                .eq("voucher_id", voucherId)
                .gt("stock", 0) // where id = ? and stock > 0
                .update();
        if (!isSuccess) {
            // 扣减失败
            return Result.fail("库存不足！");
        }

        // 7.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 7.1.订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        // 7.2.用户id
        voucherOrder.setUserId(userId);
        // 7.3.代金券id
        voucherOrder.setVoucherId(voucherId);
        // 7.4 写入DB
        save(voucherOrder);

        // 8.返回订单id
        return Result.ok(orderId);
    }

    @Override
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long voucherId  = voucherOrder.getVoucherId();
        Long userId     = voucherOrder.getUserId();

        // 5.一人一单
        // 5.1.查询订单
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        // 5.2.判断是否存在
        if (count > 0) {
            // 用户已经购买过了
            log.error("用户已经购买过一次！");
            return;
        }

        // 6.扣减库存
        boolean isSuccess = seckillVoucherService.update()
                .setSql("stock = stock - 1") // set stock = stock - 1
                .eq("voucher_id", voucherId)
                .gt("stock", 0) // where id = ? and stock > 0
                .update();
        if (!isSuccess) {
            // 扣减失败
            log.error("库存不足！");
            return;
        }

        // 7 写入DB
        save(voucherOrder);
    }

    public Result seckillVoucherV10(Long voucherId) {
        // 1.执行lua脚本
        Long userId     = UserHolderV2.getUser().getId();
        long orderId    = redisIdWorker.nextId("order");
        Long result     = stringRedisTemplate.execute(
                SECKILL_SCRIPTS_V1,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );

        // 2.判断结果是否为0
        int r = result.intValue();
        if (r != 0) {
            // 2.1.不为0 ，代表没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        // 2.2 为0 有购买资格 把下单信息保存到阻塞队列
        VoucherOrder voucherOrder = new VoucherOrder();
        // 2.3 订单id
        voucherOrder.setId(orderId);
        // 2.4 用户id
        voucherOrder.setUserId(userId);
        // 2.5 代金券id
        voucherOrder.setVoucherId(voucherId);
        // 2.6 放入阻塞队列
        orderTasks.add(voucherOrder);

        // 3.获取代理对象
        proxyV10V11 = (IVoucherOrderService) AopContext.currentProxy();

        // 4.返回订单id
        return Result.ok(orderId);
    }

    public Result seckillVoucherV11(Long voucherId) {
        // 1.执行lua脚本
        Long userId     = UserHolderV2.getUser().getId();
        long orderId    = redisIdWorker.nextId("order");
        Long result     = stringRedisTemplate.execute(
                SECKILL_SCRIPTS_V2,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(),String.valueOf(orderId)
        );

        // 2.判断结果是否为0
        int r = result.intValue();
        if (r != 0) {
            // 2.1.不为0 ，代表没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }

        // 3.获取代理对象
        proxyV10V11 = (IVoucherOrderService) AopContext.currentProxy();

        // 4.返回订单id
        return Result.ok(orderId);
    }
}
