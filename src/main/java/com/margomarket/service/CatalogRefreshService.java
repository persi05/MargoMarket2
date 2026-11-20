package com.margomarket.service;

import jakarta.annotation.PreDestroy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CatalogRefreshService {
    private final CatalogStore store;
    private final CatalogSource source;
    private final ExecutorService worker =
            Executors.newSingleThreadExecutor(
                    r -> {
                        var t = new Thread(r, "catalog-refresh");
                        t.setDaemon(true);
                        return t;
                    });

    public CatalogStore.Status start() {
        UUID job = UUID.randomUUID();
        if (store.claim(job)) {
            try {
                worker.submit(
                        () -> {
                            try {
                                store.complete(
                                        job, source.fetch(pages -> store.progress(job, pages)));
                            } catch (Exception error) {
                                log.warn("Catalog refresh failed, job {}", job, error);
                                store.fail(job);
                            }
                        });
            } catch (RejectedExecutionException error) {
                store.fail(job);
            }
        }
        return store.status();
    }

    @PreDestroy
    void shutdown() {
        worker.shutdownNow();
    }
}
