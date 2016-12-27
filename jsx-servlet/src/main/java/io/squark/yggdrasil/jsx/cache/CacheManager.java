package io.squark.yggdrasil.jsx.cache;

import org.apache.commons.jcs.access.CacheAccess;
import org.apache.commons.jcs.access.behavior.ICacheAccess;
import org.apache.commons.jcs.access.exception.CacheException;
import org.apache.commons.jcs.auxiliary.disk.indexed.IndexedDiskCacheAttributes;
import org.apache.commons.jcs.auxiliary.disk.indexed.IndexedDiskCacheFactory;
import org.apache.commons.jcs.engine.CompositeCacheAttributes;
import org.apache.commons.jcs.engine.ElementAttributes;
import org.apache.commons.jcs.engine.behavior.ICacheElement;
import org.apache.commons.jcs.engine.behavior.ICompositeCacheAttributes;
import org.apache.commons.jcs.engine.behavior.IElementAttributes;
import org.apache.commons.jcs.engine.control.CompositeCache;
import org.apache.commons.jcs.engine.control.CompositeCacheManager;
import org.apache.commons.jcs.engine.memory.lru.LRUMemoryCache;
import org.apache.commons.jcs.engine.stats.behavior.ICacheStats;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * timewise
 * <p>
 * Created by Erik Håkansson on 2016-12-07.
 * Copyright 2016
 */
@ApplicationScoped
public class CacheManager implements ICacheAccess<CacheKey, CacheObject> {

    private CacheAccess<CacheKey, CacheObject> cache;

    @PostConstruct
    public void setup() {
        Properties props = new Properties();

        props.put("jcs.default", "defaultCache");
        props.put("jcs.default.cacheattributes", CompositeCacheAttributes.class.getName());
        props.put("jcs.default.cacheattributes.MaxObjects", "1000");
        props.put("jcs.default.cacheattributes.MemoryCacheName", LRUMemoryCache.class.getName());
        props.put("jcs.default.cacheattributes.UseMemoryShrinker", "true");
        props.put("jcs.default.cacheattributes.MaxMemoryIdleTimeSeconds", "3600");
        props.put("jcs.default.cacheattributes.ShrinkerIntervalSeconds", "60");
        props.put("jcs.default.cacheattributes.MaxSpoolPerRun", "500");
        props.put("jcs.default.elementattributes", ElementAttributes.class.getName());
        props.put("jcs.default.elementattributes.IsEternal", "false");
        props.put("jcs.default.elementattributes.MaxLife", "21600");
        props.put("jcs.default.elementattributes.IdleTime", "1800");
        props.put("jcs.default.elementattributes.IsSpool", "true");
        props.put("jcs.default.elementattributes.IsRemote", "false");
        props.put("jcs.default.elementattributes.IsLateral", "true");

        props.put("jcs.region.jsxCache", "defaultCache");
        props.put("jcs.region.jsxCache.cacheattributes", CompositeCacheAttributes.class.getName());
        props.put("jcs.region.jsxCache.cacheattributes.MaxObjects", "1000");
        props.put("jcs.region.jsxCache.cacheattributes.MemoryCacheName", LRUMemoryCache.class.getName());
        props.put("jcs.region.jsxCache.cacheattributes.UseMemoryShrinker", "true");
        props.put("jcs.region.jsxCache.cacheattributes.MaxMemoryIdleTimeSeconds", "3600");
        props.put("jcs.region.jsxCache.cacheattributes.ShrinkerIntervalSeconds", "60");
        props.put("jcs.region.jsxCache.cacheattributes.MaxSpoolPerRun", "500");
        props.put("jcs.region.jsxCache.cacheattributes.DiskUsagePatternName", "UPDATE");
        props.put("jcs.region.jsxCache.elementattributes", ElementAttributes.class.getName());
        props.put("jcs.region.jsxCache.elementattributes.IsEternal", "false");

        props.put("jcs.auxiliary.defaultCache", IndexedDiskCacheFactory.class.getName());
        props.put("jcs.auxiliary.defaultCache.attributes", IndexedDiskCacheAttributes.class.getName());
        props.put("jcs.auxiliary.defaultCache.attributes.DiskPath", "jcs_swap");
        props.put("jcs.auxiliary.defaultCache.attributes.MaxPurgatorySize", "10000000");
        props.put("jcs.auxiliary.defaultCache.attributes.MaxKeySize", "1000000");
        props.put("jcs.auxiliary.defaultCache.attributes.OptimizeAtRemoveCount", "300000");
        props.put("jcs.auxiliary.defaultCache.attributes.ShutdownSpoolTimeLimit", "60");

        CompositeCacheManager ccm = CompositeCacheManager.getUnconfiguredInstance();
        ccm.configure(props);

        cache = new CacheAccess<>(ccm.getCache("jsxCache"));
        cache.clear();
    }


    @Override
    public CacheObject get(CacheKey name) {
        return cache.get(name);
    }

    @Override
    public Map<CacheKey, CacheObject> getMatching(String pattern) {
        return cache.getMatching(pattern);
    }

    @Override
    public void putSafe(CacheKey key, CacheObject value) {
        cache.putSafe(key, value);
    }

    @Override
    public void put(CacheKey name, CacheObject obj) {
        cache.put(name, obj);
    }

    @Override
    public void put(CacheKey key, CacheObject val, IElementAttributes attr) {
        cache.put(key, val, attr);
    }

    @Override
    public ICacheElement<CacheKey, CacheObject> getCacheElement(CacheKey name) {
        return cache.getCacheElement(name);
    }

    @Override
    public Map<CacheKey, ICacheElement<CacheKey, CacheObject>> getCacheElements(Set<CacheKey> names) {
        return cache.getCacheElements(names);
    }

    @Override
    public Map<CacheKey, ICacheElement<CacheKey, CacheObject>> getMatchingCacheElements(String pattern) {
        return cache.getMatchingCacheElements(pattern);
    }

    @Override
    public void remove(CacheKey name) {
        cache.remove(name);
    }

    @Override
    public void resetElementAttributes(CacheKey name, IElementAttributes attr) {
        cache.resetElementAttributes(name, attr);
    }

    @Override
    public IElementAttributes getElementAttributes(CacheKey name) {
        return cache.getElementAttributes(name);
    }

    @Override
    public void dispose() {
        cache.dispose();
    }

    @Override
    public void clear() throws CacheException {
        cache.clear();
    }

    @Override
    public IElementAttributes getDefaultElementAttributes() throws CacheException {
        return cache.getDefaultElementAttributes();
    }

    @Override
    public void setDefaultElementAttributes(IElementAttributes attr) throws CacheException {
        cache.setDefaultElementAttributes(attr);
    }

    @Override
    public ICompositeCacheAttributes getCacheAttributes() {
        return cache.getCacheAttributes();
    }

    @Override
    public void setCacheAttributes(ICompositeCacheAttributes cattr) {
        cache.setCacheAttributes(cattr);
    }

    @Override
    public int freeMemoryElements(int numberToFree) throws CacheException {
        return cache.freeMemoryElements(numberToFree);
    }

    @Override
    public ICacheStats getStatistics() {
        return cache.getStatistics();
    }

    @Override
    public String getStats() {
        return cache.getStats();
    }

    public CompositeCache<CacheKey, CacheObject> getCacheControl() {
        return cache.getCacheControl();
    }
}
