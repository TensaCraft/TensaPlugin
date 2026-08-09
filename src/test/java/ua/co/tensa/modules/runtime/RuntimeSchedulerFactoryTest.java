package ua.co.tensa.modules.runtime;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeSchedulerFactoryTest {
    @Test
    void internalRuntimeSchedulerDoesNotDependOnPublicSchedulerModuleState() throws Exception {
        try (ModuleScheduler owner = RuntimeSchedulerFactory.create("runtime-scheduler-test")) {
            CountDownLatch ran = new CountDownLatch(1);

            owner.schedule(ModuleScheduler.job("still-alive", ran::countDown).build());

            assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(owner.snapshot().closed()).isFalse();
        }
    }
}
