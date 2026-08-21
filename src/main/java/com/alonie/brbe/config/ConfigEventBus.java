package com.alonie.brbe.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Push-based configuration change notification — replaces the
 * {@code configChanged} volatile flag that was polled every render frame.
 *
 * <p>Modules subscribe to specific event types and are notified immediately
 * when a change occurs.  This eliminates the per-frame polling overhead and
 * lets each module invalidate its own caches independently.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   events.subscribe(ConfigChanged.class, e -> layout.invalidate());
 *   events.subscribe(PartialCraftingChanged.class, e -> index.rebuild());
 * }</pre>
 */
public final class ConfigEventBus {

    private final Map<Class<?>, List<Consumer<?>>> listeners = new ConcurrentHashMap<>();

    /**
     * Set to {@code true} whenever a config change requires a UI rebuild.
     * Consumed by the render loop via {@link #consumeConfigChange()}.
     */
    private final AtomicBoolean configChangePending = new AtomicBoolean(false);

    /**
     * Called from the render loop: returns whether a UI rebuild is pending
     * and atomically resets the flag.  Use this instead of polling a
     * volatile {@code configChanged} field.
     */
    public boolean consumeConfigChange() {
        return configChangePending.getAndSet(false);
    }

    /**
     * Non-consuming check: returns whether a config-change-driven UI rebuild
     * is pending without resetting the flag.  The flag is consumed later by
     * {@link #consumeConfigChange()} inside the updateCollections pipeline.
     */
    public boolean hasPendingConfigChange() {
        return configChangePending.get();
    }

    /**
     * Request a UI rebuild on the next render frame.  Safe to call from
     * any thread (config save listener, settings button click handler).
     */
    public void requestConfigRefresh() {
        configChangePending.set(true);
    }

    /**
     * Subscribe to events of the given type.  The listener is called
     * synchronously on the publishing thread (typically the render thread).
     */
    @SuppressWarnings("unchecked")
    public <T> void subscribe(Class<T> eventType, Consumer<T> listener) {
        listeners.computeIfAbsent(eventType, k -> new ArrayList<>()).add(listener);
    }

    /**
     * Publish an event to all subscribers of its type.  Subscribers are
     * notified in registration order.
     */
    @SuppressWarnings("unchecked")
    public <T> void publish(T event) {
        List<Consumer<?>> subs = listeners.get(event.getClass());
        if (subs == null) return;
        for (Consumer<?> sub : subs) {
            try {
                ((Consumer<T>) sub).accept(event);
            } catch (Exception e) {
                // Swallow per-listener errors so one broken subscriber
                // doesn't prevent others from receiving the event.
                e.printStackTrace();
            }
        }
    }

    /** Remove all subscribers.  Called on mod shutdown. */
    public void clear() {
        listeners.clear();
    }

    // -- Standard event types -------------------------------------------------

    /** Published when any config value changes (full config snapshot). */
    public static final class ConfigChanged {
        private final BrbeConfig config;

        public ConfigChanged(BrbeConfig config) {
            this.config = config;
        }

        public BrbeConfig config() { return config; }
    }

    /** Published when {@code partialCraftingEnabled} or
     * {@code partialMarkingEnabled} toggles. */
    public static final class PartialCraftingChanged {
        private final boolean enabled;
        private final boolean markingEnabled;

        public PartialCraftingChanged(boolean enabled, boolean markingEnabled) {
            this.enabled = enabled;
            this.markingEnabled = markingEnabled;
        }

        public boolean enabled() { return enabled; }
        public boolean markingEnabled() { return markingEnabled; }
    }

    /** Published when {@code enablePinning} toggles. */
    public static final class PinningChanged {
        private final boolean enabled;

        public PinningChanged(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean enabled() { return enabled; }
    }

    /** Published when the recipe book visibility config changes. */
    public static final class BookVisibilityChanged {
        private final boolean enableBook;

        public BookVisibilityChanged(boolean enableBook) {
            this.enableBook = enableBook;
        }

        public boolean enableBook() { return enableBook; }
    }
}
