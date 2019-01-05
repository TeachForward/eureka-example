package com.teachforward.eureka.monitoring;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.health.CurrentHealthStatus;
import io.micronaut.health.HealthStatus;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import io.micronaut.management.health.monitor.HealthMonitorTask;
import io.micronaut.runtime.ApplicationConfiguration;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.scheduling.annotation.Scheduled;
import io.reactivex.Flowable;
import io.reactivex.MaybeObserver;
import io.reactivex.disposables.Disposable;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

//@Singleton
//@Replaces(HealthMonitorTask.class)
//@Requires(beans = EmbeddedServer.class)
//@Requires(property = ApplicationConfiguration.APPLICATION_NAME)
//@Requires(property = "micronaut.health.monitor.enabled", value = "true", defaultValue = "true")
public class MyHealthMonitorTask {


    private static final Logger LOG = LoggerFactory.getLogger(MyHealthMonitorTask.class);

    private final CurrentHealthStatus currentHealthStatus;
    private final HealthIndicator[] healthIndicators;

    /**
     * @param currentHealthStatus The current health status
     * @param healthIndicators    Health indicators
     */
    public MyHealthMonitorTask(CurrentHealthStatus currentHealthStatus, HealthIndicator... healthIndicators) {
        this.currentHealthStatus = currentHealthStatus;
        this.healthIndicators = healthIndicators;
    }

    /**
     * Start the continuous health monitor.
     */
    @Scheduled(
            fixedDelay = "${micronaut.health.monitor.interval:1m}",
            initialDelay = "${micronaut.health.monitor.initial-delay:1m}")
    void monitor() {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting health monitor check");
        }
        List<Publisher<HealthResult>> healthResults = Arrays
                .stream(healthIndicators)
                .map(HealthIndicator::getResult)
                .collect(Collectors.toList());

        Flowable<HealthResult> resultFlowable = Flowable
                .merge(healthResults)
                .filter(healthResult -> {
                            LOG.debug("Checking Health Results for name: {}", healthResult.getName());
                            LOG.debug("Checking Health Results for detail: {}", healthResult.getDetails());
                            LOG.debug("Checking Health Results : {}", healthResult.getStatus());
                            HealthStatus status = healthResult.getStatus();
                            return status.equals(HealthStatus.DOWN) || !status.getOperational().orElse(true);
                        }
                );

        resultFlowable.firstElement().subscribe(new MaybeObserver<HealthResult>() {
            @Override
            public void onSubscribe(Disposable d) {

            }

            @Override
            public void onSuccess(HealthResult healthResult) {
                HealthStatus status = healthResult.getStatus();
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Health monitor check failed with status {}", status);
                }
                currentHealthStatus.update(status);
            }

            @Override
            public void onError(Throwable e) {
                if (LOG.isErrorEnabled()) {
                    LOG.error("Health monitor check failed with exception: " + e.getMessage(), e);
                }

                currentHealthStatus.update(HealthStatus.DOWN.describe("Error occurred running health check: " + e.getMessage()));
            }

            @Override
            public void onComplete() {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Health monitor check passed.");
                }

                currentHealthStatus.update(HealthStatus.UP);
            }
        });
    }


}


