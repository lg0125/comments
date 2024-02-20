package com.chris.comments.utils.lock;

import cn.hutool.core.lang.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

public class SimpleRedisLockV1 implements ILock {
    private String name;
    private StringRedisTemplate stringRedisTemplate;
    public SimpleRedisLockV1(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-"; // 解决Redis分布式锁的误删问题



    @Override
    public boolean tryLock(long timeoutSec) {
        // 获取线程标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();

        // 获取锁
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(
                KEY_PREFIX + name, threadId,
                timeoutSec, TimeUnit.SECONDS
        );

        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
        // 获取线程标识 解决Redis分布式锁的误删问题
        String threadId = ID_PREFIX + Thread.currentThread().getId();

        // 获取锁中的线程标识 解决Redis分布式锁的误删问题
        String id       = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);

        // 判断标识是否一致
        if(threadId.equals(id)) { // 解决Redis分布式锁的误删问题
            // 释放锁
            stringRedisTemplate.delete(KEY_PREFIX + name);
        }
    }
}
