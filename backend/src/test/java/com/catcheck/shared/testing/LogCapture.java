package com.catcheck.shared.testing;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.List;

/** Ghi lại log của một logger trong lúc chạy {@code action}, để test kiểm tra mức log và không lộ PII. */
public final class LogCapture {

    public interface Action {
        void run() throws Exception;
    }

    private LogCapture() {
    }

    public static List<ILoggingEvent> capture(Class<?> loggerClass, Action action) throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(loggerClass);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            action.run();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
        return appender.list;
    }

    /** Message of every event plus the class name and message of every throwable in its cause chain. */
    public static String text(List<ILoggingEvent> events) {
        StringBuilder out = new StringBuilder();
        for (ILoggingEvent event : events) {
            out.append(event.getLevel()).append(' ').append(event.getFormattedMessage()).append('\n');
            for (IThrowableProxy p = event.getThrowableProxy(); p != null; p = p.getCause()) {
                out.append("  ").append(p.getClassName()).append(": ").append(p.getMessage()).append('\n');
            }
        }
        return out.toString();
    }
}
