package ua.co.tensa.modules.runtime;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulerModuleTest {
    @Test
    void isVisibleRequiredAndReloadDoesNotTouchOwnerRuntime() throws Exception {
        SchedulerModule.ENTRY.enable();
        try (ModuleScheduler owner = SchedulerModule.create("scheduler-module-test")) {
            CountDownLatch ran = new CountDownLatch(1);
            assertThat(SchedulerModule.ENTRY.id()).isEqualTo("scheduler");
            assertThat(SchedulerModule.ENTRY.title()).isEqualTo("Task Scheduler");
            assertThat(SchedulerModule.ENTRY.required()).isTrue();
            assertThat(SchedulerModule.ENTRY.tryReload()).isTrue();

            owner.schedule(ModuleScheduler.job("still-alive", ran::countDown).build());
            assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue();
        } finally {
            SchedulerModule.ENTRY.disable();
        }
    }
}
