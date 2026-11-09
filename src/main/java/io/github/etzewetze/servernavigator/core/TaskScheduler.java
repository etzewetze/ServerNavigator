package io.github.etzewetze.servernavigator.core;

import java.time.Duration;

public interface TaskScheduler {
    interface Task { void cancel(); }
    Task repeat(Runnable action, Duration interval);
}
