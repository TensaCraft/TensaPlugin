package ua.co.tensa.modules.discord;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

final class BoundedWorkerQueue<T> implements AutoCloseable {
    @FunctionalInterface
    interface Handler<T> {
        void handle(T item) throws Exception;
    }

    private final ArrayBlockingQueue<T> queue;
    private final Handler<T> handler;
    private final BiConsumer<T, Exception> failureHandler;
    private final AtomicBoolean running = new AtomicBoolean();
    private final Thread worker;

    BoundedWorkerQueue(String threadName, int capacity, Handler<T> handler, BiConsumer<T, Exception> failureHandler) {
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.handler = Objects.requireNonNull(handler, "handler");
        this.failureHandler = Objects.requireNonNull(failureHandler, "failureHandler");
        this.worker = Thread.ofPlatform().name(threadName).daemon(true).unstarted(this::run);
    }

    void start() {
        if (running.compareAndSet(false, true)) {
            worker.start();
        }
    }

    boolean offer(T item) {
        return item != null && running.get() && queue.offer(item);
    }

    boolean isRunning() {
        return running.get() && worker.isAlive();
    }

    int size() {
        return queue.size();
    }

    void close(Duration timeout) {
        running.set(false);
        long waitMillis = Math.max(1L, timeout.toMillis());
        try {
            worker.join(waitMillis);
            if (worker.isAlive()) {
                worker.interrupt();
                worker.join(Math.min(1_000L, waitMillis));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            worker.interrupt();
        } finally {
            queue.clear();
        }
    }

    @Override
    public void close() {
        close(Duration.ofSeconds(3));
    }

    private void run() {
        while (running.get() || !queue.isEmpty()) {
            T item = null;
            try {
                item = queue.poll(200, TimeUnit.MILLISECONDS);
                if (item != null) {
                    handler.handle(item);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                failureHandler.accept(item, e);
            }
        }
    }
}
