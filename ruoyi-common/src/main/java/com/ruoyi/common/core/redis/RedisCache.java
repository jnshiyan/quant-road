package com.ruoyi.common.core.redis;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.BoundSetOperations;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

/**
 * spring redis 工具类
 *
 * @author ruoyi
 **/
@SuppressWarnings(value = { "unchecked", "rawtypes" })
@Component
public class RedisCache
{
    @Autowired(required = false)
    public RedisTemplate redisTemplate;

    @Value("${ruoyi.cache.type:local}")
    private String cacheType;

    private final Map<String, LocalCacheValue> localCache = new ConcurrentHashMap<>();

    private boolean useLocalCache()
    {
        return !"redis".equalsIgnoreCase(cacheType) || redisTemplate == null;
    }

    private void evictIfExpired(String key)
    {
        LocalCacheValue cacheValue = localCache.get(key);
        if (cacheValue != null && cacheValue.isExpired())
        {
            localCache.remove(key);
        }
    }

    /**
     * 缓存基本的对象，Integer、String、实体类等
     *
     * @param key 缓存的键值
     * @param value 缓存的值
     */
    public <T> void setCacheObject(final String key, final T value)
    {
        if (useLocalCache())
        {
            localCache.put(key, new LocalCacheValue(value, -1L));
            return;
        }
        redisTemplate.opsForValue().set(key, value);
    }

    /**
     * 缓存基本的对象，Integer、String、实体类等
     *
     * @param key 缓存的键值
     * @param value 缓存的值
     * @param timeout 时间
     * @param timeUnit 时间颗粒度
     */
    public <T> void setCacheObject(final String key, final T value, final Integer timeout, final TimeUnit timeUnit)
    {
        if (useLocalCache())
        {
            long expireAt = System.currentTimeMillis() + timeUnit.toMillis(timeout);
            localCache.put(key, new LocalCacheValue(value, expireAt));
            return;
        }
        redisTemplate.opsForValue().set(key, value, timeout, timeUnit);
    }

    /**
     * 设置有效时间
     *
     * @param key Redis键
     * @param timeout 超时时间
     * @return true=设置成功；false=设置失败
     */
    public boolean expire(final String key, final long timeout)
    {
        return expire(key, timeout, TimeUnit.SECONDS);
    }

    /**
     * 设置有效时间
     *
     * @param key Redis键
     * @param timeout 超时时间
     * @param unit 时间单位
     * @return true=设置成功；false=设置失败
     */
    public boolean expire(final String key, final long timeout, final TimeUnit unit)
    {
        if (useLocalCache())
        {
            evictIfExpired(key);
            LocalCacheValue cacheValue = localCache.get(key);
            if (cacheValue == null)
            {
                return false;
            }
            cacheValue.setExpireAt(System.currentTimeMillis() + unit.toMillis(timeout));
            return true;
        }
        return redisTemplate.expire(key, timeout, unit);
    }

    /**
     * 获取有效时间
     *
     * @param key Redis键
     * @return 有效时间
     */
    public long getExpire(final String key)
    {
        if (useLocalCache())
        {
            evictIfExpired(key);
            LocalCacheValue cacheValue = localCache.get(key);
            if (cacheValue == null || cacheValue.getExpireAt() < 0)
            {
                return -1L;
            }
            return Math.max(0L, cacheValue.getExpireAt() - System.currentTimeMillis());
        }
        return redisTemplate.getExpire(key);
    }

    /**
     * 判断 key是否存在
     *
     * @param key 键
     * @return true 存在 false不存在
     */
    public Boolean hasKey(String key)
    {
        if (useLocalCache())
        {
            evictIfExpired(key);
            return localCache.containsKey(key);
        }
        return redisTemplate.hasKey(key);
    }

    /**
     * 获得缓存的基本对象。
     *
     * @param key 缓存键值
     * @return 缓存键值对应的数据
     */
    public <T> T getCacheObject(final String key)
    {
        if (useLocalCache())
        {
            evictIfExpired(key);
            LocalCacheValue cacheValue = localCache.get(key);
            return cacheValue == null ? null : (T) cacheValue.getValue();
        }
        ValueOperations<String, T> operation = redisTemplate.opsForValue();
        return operation.get(key);
    }

    /**
     * 删除单个对象
     *
     * @param key
     */
    public boolean deleteObject(final String key)
    {
        if (useLocalCache())
        {
            return localCache.remove(key) != null;
        }
        return redisTemplate.delete(key);
    }

    /**
     * 删除集合对象
     *
     * @param collection 多个对象
     * @return
     */
    public boolean deleteObject(final Collection collection)
    {
        if (useLocalCache())
        {
            boolean changed = false;
            for (Object key : collection)
            {
                changed = localCache.remove(String.valueOf(key)) != null || changed;
            }
            return changed;
        }
        return redisTemplate.delete(collection) > 0;
    }

    /**
     * 缓存List数据
     *
     * @param key 缓存的键值
     * @param dataList 待缓存的List数据
     * @return 缓存的对象
     */
    public <T> long setCacheList(final String key, final List<T> dataList)
    {
        if (useLocalCache())
        {
            localCache.put(key, new LocalCacheValue(dataList, -1L));
            return dataList == null ? 0 : dataList.size();
        }
        Long count = redisTemplate.opsForList().rightPushAll(key, dataList);
        return count == null ? 0 : count;
    }

    /**
     * 获得缓存的list对象
     *
     * @param key 缓存的键值
     * @return 缓存键值对应的数据
     */
    public <T> List<T> getCacheList(final String key)
    {
        if (useLocalCache())
        {
            evictIfExpired(key);
            LocalCacheValue cacheValue = localCache.get(key);
            return cacheValue == null ? Collections.emptyList() : (List<T>) cacheValue.getValue();
        }
        return redisTemplate.opsForList().range(key, 0, -1);
    }

    /**
     * 缓存Set
     *
     * @param key 缓存键值
     * @param dataSet 缓存的数据
     * @return 缓存数据的对象
     */
    public <T> BoundSetOperations<String, T> setCacheSet(final String key, final Set<T> dataSet)
    {
        if (useLocalCache())
        {
            localCache.put(key, new LocalCacheValue(dataSet, -1L));
            return null;
        }
        BoundSetOperations<String, T> setOperation = redisTemplate.boundSetOps(key);
        Iterator<T> it = dataSet.iterator();
        while (it.hasNext())
        {
            setOperation.add(it.next());
        }
        return setOperation;
    }

    /**
     * 获得缓存的set
     *
     * @param key
     * @return
     */
    public <T> Set<T> getCacheSet(final String key)
    {
        if (useLocalCache())
        {
            evictIfExpired(key);
            LocalCacheValue cacheValue = localCache.get(key);
            return cacheValue == null ? Collections.emptySet() : (Set<T>) cacheValue.getValue();
        }
        return redisTemplate.opsForSet().members(key);
    }

    /**
     * 缓存Map
     *
     * @param key
     * @param dataMap
     */
    public <T> void setCacheMap(final String key, final Map<String, T> dataMap)
    {
        if (useLocalCache())
        {
            localCache.put(key, new LocalCacheValue(dataMap, -1L));
            return;
        }
        if (dataMap != null) {
            redisTemplate.opsForHash().putAll(key, dataMap);
        }
    }

    /**
     * 获得缓存的Map
     *
     * @param key
     * @return
     */
    public <T> Map<String, T> getCacheMap(final String key)
    {
        if (useLocalCache())
        {
            evictIfExpired(key);
            LocalCacheValue cacheValue = localCache.get(key);
            return cacheValue == null ? Collections.emptyMap() : (Map<String, T>) cacheValue.getValue();
        }
        return redisTemplate.opsForHash().entries(key);
    }

    /**
     * 往Hash中存入数据
     *
     * @param key Redis键
     * @param hKey Hash键
     * @param value 值
     */
    public <T> void setCacheMapValue(final String key, final String hKey, final T value)
    {
        if (useLocalCache())
        {
            evictIfExpired(key);
            Map<String, T> map = (Map<String, T>) getCacheMap(key);
            Map<String, T> mutableMap = new ConcurrentHashMap<>(map);
            mutableMap.put(hKey, value);
            localCache.put(key, new LocalCacheValue(mutableMap, -1L));
            return;
        }
        redisTemplate.opsForHash().put(key, hKey, value);
    }

    /**
     * 获取Hash中的数据
     *
     * @param key Redis键
     * @param hKey Hash键
     * @return Hash中的对象
     */
    public <T> T getCacheMapValue(final String key, final String hKey)
    {
        if (useLocalCache())
        {
            return (T) getCacheMap(key).get(hKey);
        }
        HashOperations<String, String, T> opsForHash = redisTemplate.opsForHash();
        return opsForHash.get(key, hKey);
    }

    /**
     * 获取多个Hash中的数据
     *
     * @param key Redis键
     * @param hKeys Hash键集合
     * @return Hash对象集合
     */
    public <T> List<T> getMultiCacheMapValue(final String key, final Collection<Object> hKeys)
    {
        if (useLocalCache())
        {
            Map<String, T> map = getCacheMap(key);
            return hKeys.stream().map(item -> map.get(String.valueOf(item))).toList();
        }
        return redisTemplate.opsForHash().multiGet(key, hKeys);
    }

    /**
     * 删除Hash中的某条数据
     *
     * @param key Redis键
     * @param hKey Hash键
     * @return 是否成功
     */
    public boolean deleteCacheMapValue(final String key, final String hKey)
    {
        if (useLocalCache())
        {
            Map<String, Object> map = new ConcurrentHashMap<>(getCacheMap(key));
            boolean removed = map.remove(hKey) != null;
            localCache.put(key, new LocalCacheValue(map, -1L));
            return removed;
        }
        return redisTemplate.opsForHash().delete(key, hKey) > 0;
    }

    /**
     * 获得缓存的基本对象列表
     *
     * @param pattern 字符串前缀
     * @return 对象列表
     */
    public Collection<String> keys(final String pattern)
    {
        if (useLocalCache())
        {
            String regex = "^" + pattern.replace("*", ".*") + "$";
            Pattern compiled = Pattern.compile(regex);
            return localCache.keySet().stream()
                    .filter(key -> {
                        evictIfExpired(key);
                        return compiled.matcher(key).matches();
                    })
                    .toList();
        }
        return redisTemplate.keys(pattern);
    }

    public boolean isLocalMode()
    {
        return useLocalCache();
    }

    public long size()
    {
        if (useLocalCache())
        {
            localCache.keySet().forEach(this::evictIfExpired);
            return localCache.size();
        }
        Long size = (Long) redisTemplate.execute((RedisCallback<Long>) connection -> connection.dbSize());
        return size == null ? 0L : size;
    }

    private static class LocalCacheValue
    {
        private final Object value;
        private volatile long expireAt;

        private LocalCacheValue(Object value, long expireAt)
        {
            this.value = value;
            this.expireAt = expireAt;
        }

        public Object getValue()
        {
            return value;
        }

        public long getExpireAt()
        {
            return expireAt;
        }

        public void setExpireAt(long expireAt)
        {
            this.expireAt = expireAt;
        }

        public boolean isExpired()
        {
            return expireAt >= 0 && System.currentTimeMillis() > expireAt;
        }
    }
}
