package ua.co.tensa.placeholders;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceholderManagerTest {
    @Test
    void renderingUnrelatedTextDoesNotCallAbsentPlaceholderResolvers() {
        AtomicInteger calls = new AtomicInteger();
        PlaceholderManager.register("audit_expensive", player -> {
            calls.incrementAndGet();
            return "value";
        });
        try {
            assertThat(PlaceholderManager.resolveText(null, "<green>hello</green>"))
                    .isEqualTo("<green>hello</green>");
            assertThat(calls).hasValue(0);
        } finally {
            PlaceholderManager.unregister("audit_expensive");
        }
    }

    @Test
    void asyncLocalResolutionEvaluatesEachPresentPlaceholderOnlyOnce() {
        AtomicInteger calls = new AtomicInteger();
        PlaceholderManager.register("audit_counter", player -> Integer.toString(calls.incrementAndGet()));
        try {
            assertThat(PlaceholderManager.resolveTextAsync(null, "%audit_counter%").join())
                    .isEqualTo("1");
            assertThat(calls).hasValue(1);
        } finally {
            PlaceholderManager.unregister("audit_counter");
        }
    }
}
