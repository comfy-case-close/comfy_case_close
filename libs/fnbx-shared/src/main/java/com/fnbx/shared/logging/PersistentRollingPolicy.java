package com.fnbx.shared.logging;

import ch.qos.logback.core.rolling.SizeAndTimeBasedFileNamingAndTriggeringPolicy;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import ch.qos.logback.core.rolling.helper.ArchiveRemover;
import ch.qos.logback.core.rolling.helper.SizeAndTimeBasedArchiveRemover;
import ch.qos.logback.core.util.FileSize;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/** Size/day rollover with serialized retention for Logback 1.5's mutable rolling calendar. */
public class PersistentRollingPolicy<E> extends TimeBasedRollingPolicy<E> {
    private FileSize maxFileSize;

    public void setMaxFileSize(FileSize value) { maxFileSize = value; }

    @Override public void start() {
        if (maxFileSize == null || (!isUnboundedTotalSizeCap() && totalSizeCap.getSize() < maxFileSize.getSize())) {
            addError("maxFileSize is required and must not exceed totalSizeCap");
            return;
        }
        // Logback 1.5's Usage enum is package-private. The public one-argument
        // constructor accepts null for embedded use (only DIRECT triggers its deprecation warning).
        var naming = new SizeAndTimeBasedFileNamingAndTriggeringPolicy<E>(null) {
            @Override protected ArchiveRemover createArchiveRemover() {
                return new SizeAndTimeBasedArchiveRemover(new ch.qos.logback.core.rolling.helper.FileNamePattern(
                        getFileNamePattern(), getContext()), rc) {
                    @Override public Future<?> cleanAsynchronously(Instant now) {
                        // The library shares rc with the rollover thread. Waiting here prevents
                        // concurrent calendar mutation from selecting recent archives for deletion.
                        Future<?> cleanup = super.cleanAsynchronously(now);
                        try { cleanup.get(); }
                        catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            addError("Interrupted while cleaning log archives", interrupted);
                        } catch (ExecutionException failure) {
                            addError("Could not clean log archives", failure.getCause());
                        }
                        return cleanup;
                    }
                };
            }
        };
        naming.setMaxFileSize(maxFileSize);
        setTimeBasedFileNamingAndTriggeringPolicy(naming);
        super.start();
    }
}
