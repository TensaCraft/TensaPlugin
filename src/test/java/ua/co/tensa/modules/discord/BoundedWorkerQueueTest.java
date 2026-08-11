package ua.co.tensa.modules.discord;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class BoundedWorkerQueueTest {
    @Test
    void batchAdmissionIsAllOrNothing() throws Exception {
        CountDownLatch releaseWorker = new CountDownLatch(1);
        CountDownLatch firstStarted = new CountDownLatch(1);
        try (BoundedWorkerQueue<String> queue = new BoundedWorkerQueue<>(
                "bounded-batch-test",
                2,
                item -> {
                    firstStarted.countDown();
                    releaseWorker.await(2, TimeUnit.SECONDS);
                },
                (item, failure) -> { }
        )) {
            queue.start();
            assertThat(queue.offer("running")).isTrue();
            assertThat(firstStarted.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(queue.offerAll(List.of("one", "two"))).isTrue();
            assertThat(queue.offerAll(List.of("three", "four"))).isFalse();
            assertThat(queue.size()).isEqualTo(2);
            releaseWorker.countDown();
            queue.close(Duration.ofSeconds(2));
        }
    }
}
