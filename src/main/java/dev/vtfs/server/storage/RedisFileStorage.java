package dev.vtfs.server.storage;

import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@Component
public class RedisFileStorage {
    private final RedisTemplate<String, byte[]> redis;

    public RedisFileStorage(RedisTemplate<String, byte[]> redis) {
        this.redis = redis;
    }

    private String key(Long inodeId) {
        return "vtfs:file:" + inodeId;
    }

    public void create(Long inodeId) {
        redis.opsForValue().set(key(inodeId),new byte[0]);
    }

    public byte[] read(Long inodeId, long offset, int length) {
        if (offset < 0 || length < 0 ||offset > Long.MAX_VALUE-length) {
            throw new IllegalArgumentException("invalid range");
        }
        String key = key(inodeId);

        if (!Boolean.TRUE.equals(redis.hasKey(key))) {
            throw new IllegalArgumentException("invalid key");
        }
        if (length == 0) {
            return new byte[0];
        }
        byte[] redisKey = key.getBytes(StandardCharsets.UTF_8);
        return redis.execute((RedisCallback<byte[]>) connection -> connection.stringCommands()
                .getRange(redisKey, offset, offset+length-1));

    }

    public void write(Long inodeId, long offset, byte[] data) {
        if (offset < 0 || data == null) {
            throw new IllegalArgumentException("invalid range");
        }
        String key = key(inodeId);
        if (!Boolean.TRUE.equals(redis.hasKey(key))) {
            throw new IllegalArgumentException("invalid key");
        }
        if (data.length == 0) {
            return;
        }
        redis.opsForValue().set(key,data,offset);
    }

    public void truncate(Long inodeId, long size) {
        if (size < 0 || size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("invalid range");
        }
        String key = key(inodeId);

        if (!Boolean.TRUE.equals(redis.hasKey(key))) {
            throw new IllegalStateException("File content not found in Redis");
        }

        byte[] data = redis.opsForValue().get(key);

        if (data == null) {
             throw new IllegalStateException("File content not found in Redis");
        }
        redis.opsForValue().set(key, Arrays.copyOf(data, (int)size));
    }

    public void delete(Long inodeId) {
        redis.delete(key(inodeId));
    }


}
