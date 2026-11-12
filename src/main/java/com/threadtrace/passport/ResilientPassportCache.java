package com.threadtrace.passport;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * In-memory resilient fallback cache for public passports (QA4).
 * Ensures public passport pages and JSON remain served even if PostgreSQL is down.
 */
@Component
public class ResilientPassportCache {

    private final Cache<String, CachedPublicPassport> cache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(24, TimeUnit.HOURS)
            .build();

    public void putLatest(String passportCode, PassportDtos.PublicPassportView view, String etag) {
        CachedPublicPassport entry = new CachedPublicPassport(view, etag);
        cache.put(latestKey(passportCode), entry);
        cache.put(versionKey(passportCode, view.version()), entry);
    }

    public void putVersion(String passportCode, int version, PassportDtos.PublicPassportView view, String etag) {
        cache.put(versionKey(passportCode, version), new CachedPublicPassport(view, etag));
    }

    public Optional<CachedPublicPassport> get(String passportCode, Integer version) {
        String key = version == null ? latestKey(passportCode) : versionKey(passportCode, version);
        return Optional.ofNullable(cache.getIfPresent(key));
    }

    private static String latestKey(String code) {
        return code + ":latest";
    }

    private static String versionKey(String code, int version) {
        return code + ":v" + version;
    }

    public record CachedPublicPassport(PassportDtos.PublicPassportView view, String etag) {
    }
}
