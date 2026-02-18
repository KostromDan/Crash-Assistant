package dev.kostromdan.mods.crash_assistant.common_config.scripts.script_utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.util.MessageSupplier;
import org.apache.logging.log4j.util.Supplier;

public class Logger {
    private static final org.apache.logging.log4j.Logger STATIC_LOGGER = LogManager.getLogger("Script");

    public static void info(Marker marker, Message message) { STATIC_LOGGER.info(marker, message); }
    public static void info(Marker marker, Message message, Throwable throwable) { STATIC_LOGGER.info(marker, message, throwable); }
    public static void info(Marker marker, MessageSupplier messageSupplier) { STATIC_LOGGER.info(marker, messageSupplier); }
    public static void info(Marker marker, MessageSupplier messageSupplier, Throwable throwable) { STATIC_LOGGER.info(marker, messageSupplier, throwable); }
    public static void info(Marker marker, CharSequence message) { STATIC_LOGGER.info(marker, message); }
    public static void info(Marker marker, CharSequence message, Throwable throwable) { STATIC_LOGGER.info(marker, message, throwable); }
    public static void info(Marker marker, Object message) { STATIC_LOGGER.info(marker, message); }
    public static void info(Marker marker, Object message, Throwable throwable) { STATIC_LOGGER.info(marker, message, throwable); }
    public static void info(Marker marker, String message) { STATIC_LOGGER.info(marker, message); }
    public static void info(Marker marker, String message, Object... params) { STATIC_LOGGER.info(marker, message, params); }
    public static void info(Marker marker, String message, Supplier<?>... paramSuppliers) { STATIC_LOGGER.info(marker, message, paramSuppliers); }
    public static void info(Marker marker, String message, Throwable throwable) { STATIC_LOGGER.info(marker, message, throwable); }
    public static void info(Marker marker, Supplier<?> messageSupplier) { STATIC_LOGGER.info(marker, messageSupplier); }
    public static void info(Marker marker, Supplier<?> messageSupplier, Throwable throwable) { STATIC_LOGGER.info(marker, messageSupplier, throwable); }
    public static void info(Message message) { STATIC_LOGGER.info(message); }
    public static void info(Message message, Throwable throwable) { STATIC_LOGGER.info(message, throwable); }
    public static void info(MessageSupplier messageSupplier) { STATIC_LOGGER.info(messageSupplier); }
    public static void info(MessageSupplier messageSupplier, Throwable throwable) { STATIC_LOGGER.info(messageSupplier, throwable); }
    public static void info(CharSequence message) { STATIC_LOGGER.info(message); }
    public static void info(CharSequence message, Throwable throwable) { STATIC_LOGGER.info(message, throwable); }
    public static void info(Object message) { STATIC_LOGGER.info(message); }
    public static void info(Object message, Throwable throwable) { STATIC_LOGGER.info(message, throwable); }
    public static void info(String message) { STATIC_LOGGER.info(message); }
    public static void info(String message, Object... params) { STATIC_LOGGER.info(message, params); }
    public static void info(String message, Supplier<?>... paramSuppliers) { STATIC_LOGGER.info(message, paramSuppliers); }
    public static void info(String message, Throwable throwable) { STATIC_LOGGER.info(message, throwable); }
    public static void info(Supplier<?> messageSupplier) { STATIC_LOGGER.info(messageSupplier); }
    public static void info(Supplier<?> messageSupplier, Throwable throwable) { STATIC_LOGGER.info(messageSupplier, throwable); }
    public static void info(Marker marker, String message, Object p0) { STATIC_LOGGER.info(marker, message, p0); }
    public static void info(Marker marker, String message, Object p0, Object p1) { STATIC_LOGGER.info(marker, message, p0, p1); }
    public static void info(Marker marker, String message, Object p0, Object p1, Object p2) { STATIC_LOGGER.info(marker, message, p0, p1, p2); }
    public static void info(Marker marker, String message, Object p0, Object p1, Object p2, Object p3) { STATIC_LOGGER.info(marker, message, p0, p1, p2, p3); }
    public static void info(Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4) { STATIC_LOGGER.info(marker, message, p0, p1, p2, p3, p4); }
    public static void info(Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5) { STATIC_LOGGER.info(marker, message, p0, p1, p2, p3, p4, p5); }
    public static void info(Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6) { STATIC_LOGGER.info(marker, message, p0, p1, p2, p3, p4, p5, p6); }
    public static void info(Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7) { STATIC_LOGGER.info(marker, message, p0, p1, p2, p3, p4, p5, p6, p7); }
    public static void info(Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8) { STATIC_LOGGER.info(marker, message, p0, p1, p2, p3, p4, p5, p6, p7, p8); }
    public static void info(Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8, Object p9) { STATIC_LOGGER.info(marker, message, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9); }
    public static void info(String message, Object p0) { STATIC_LOGGER.info(message, p0); }
    public static void info(String message, Object p0, Object p1) { STATIC_LOGGER.info(message, p0, p1); }
    public static void info(String message, Object p0, Object p1, Object p2) { STATIC_LOGGER.info(message, p0, p1, p2); }
    public static void info(String message, Object p0, Object p1, Object p2, Object p3) { STATIC_LOGGER.info(message, p0, p1, p2, p3); }
    public static void info(String message, Object p0, Object p1, Object p2, Object p3, Object p4) { STATIC_LOGGER.info(message, p0, p1, p2, p3, p4); }
    public static void info(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5) { STATIC_LOGGER.info(message, p0, p1, p2, p3, p4, p5); }
    public static void info(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6) { STATIC_LOGGER.info(message, p0, p1, p2, p3, p4, p5, p6); }
    public static void info(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7) { STATIC_LOGGER.info(message, p0, p1, p2, p3, p4, p5, p6, p7); }
    public static void info(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8) { STATIC_LOGGER.info(message, p0, p1, p2, p3, p4, p5, p6, p7, p8); }
    public static void info(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8, Object p9) { STATIC_LOGGER.info(message, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9); }

    public static void warn(Marker marker, Message message) { STATIC_LOGGER.warn(marker, message); }
    public static void warn(Marker marker, Message message, Throwable throwable) { STATIC_LOGGER.warn(marker, message, throwable); }
    public static void warn(Marker marker, MessageSupplier messageSupplier) { STATIC_LOGGER.warn(marker, messageSupplier); }
    public static void warn(Marker marker, MessageSupplier messageSupplier, Throwable throwable) { STATIC_LOGGER.warn(marker, messageSupplier, throwable); }
    public static void warn(Marker marker, CharSequence message) { STATIC_LOGGER.warn(marker, message); }
    public static void warn(Marker marker, CharSequence message, Throwable throwable) { STATIC_LOGGER.warn(marker, message, throwable); }
    public static void warn(Marker marker, Object message) { STATIC_LOGGER.warn(marker, message); }
    public static void warn(Marker marker, Object message, Throwable throwable) { STATIC_LOGGER.warn(marker, message, throwable); }
    public static void warn(Marker marker, String message) { STATIC_LOGGER.warn(marker, message); }
    public static void warn(Marker marker, String message, Object... params) { STATIC_LOGGER.warn(marker, message, params); }
    public static void warn(Marker marker, String message, Supplier<?>... paramSuppliers) { STATIC_LOGGER.warn(marker, message, paramSuppliers); }
    public static void warn(Marker marker, String message, Throwable throwable) { STATIC_LOGGER.warn(marker, message, throwable); }
    public static void warn(Marker marker, Supplier<?> messageSupplier) { STATIC_LOGGER.warn(marker, messageSupplier); }
    public static void warn(Marker marker, Supplier<?> messageSupplier, Throwable throwable) { STATIC_LOGGER.warn(marker, messageSupplier, throwable); }
    public static void warn(Message message) { STATIC_LOGGER.warn(message); }
    public static void warn(Message message, Throwable throwable) { STATIC_LOGGER.warn(message, throwable); }
    public static void warn(MessageSupplier messageSupplier) { STATIC_LOGGER.warn(messageSupplier); }
    public static void warn(MessageSupplier messageSupplier, Throwable throwable) { STATIC_LOGGER.warn(messageSupplier, throwable); }
    public static void warn(CharSequence message) { STATIC_LOGGER.warn(message); }
    public static void warn(CharSequence message, Throwable throwable) { STATIC_LOGGER.warn(message, throwable); }
    public static void warn(Object message) { STATIC_LOGGER.warn(message); }
    public static void warn(Object message, Throwable throwable) { STATIC_LOGGER.warn(message, throwable); }
    public static void warn(String message) { STATIC_LOGGER.warn(message); }
    public static void warn(String message, Object... params) { STATIC_LOGGER.warn(message, params); }
    public static void warn(String message, Supplier<?>... paramSuppliers) { STATIC_LOGGER.warn(message, paramSuppliers); }
    public static void warn(String message, Throwable throwable) { STATIC_LOGGER.warn(message, throwable); }
    public static void warn(Supplier<?> messageSupplier) { STATIC_LOGGER.warn(messageSupplier); }
    public static void warn(Supplier<?> messageSupplier, Throwable throwable) { STATIC_LOGGER.warn(messageSupplier, throwable); }
    public static void warn(Marker marker, String message, Object p0) { STATIC_LOGGER.warn(marker, message, p0); }
    public static void warn(Marker marker, String message, Object p0, Object p1) { STATIC_LOGGER.warn(marker, message, p0, p1); }
    public static void warn(Marker marker, String message, Object p0, Object p1, Object p2) { STATIC_LOGGER.warn(marker, message, p0, p1, p2); }
    public static void warn(Marker marker, String message, Object p0, Object p1, Object p2, Object p3) { STATIC_LOGGER.warn(marker, message, p0, p1, p2, p3); }
    public static void warn(Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4) { STATIC_LOGGER.warn(marker, message, p0, p1, p2, p3, p4); }
    public static void warn(Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5) { STATIC_LOGGER.warn(marker, message, p0, p1, p2, p3, p4, p5); }
    public static void warn(Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6) { STATIC_LOGGER.warn(marker, message, p0, p1, p2, p3, p4, p5, p6); }
    public static void warn(Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7) { STATIC_LOGGER.warn(marker, message, p0, p1, p2, p3, p4, p5, p6, p7); }
    public static void warn(Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8) { STATIC_LOGGER.warn(marker, message, p0, p1, p2, p3, p4, p5, p6, p7, p8); }
    public static void warn(Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8, Object p9) { STATIC_LOGGER.warn(marker, message, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9); }
    public static void warn(String message, Object p0) { STATIC_LOGGER.warn(message, p0); }
    public static void warn(String message, Object p0, Object p1) { STATIC_LOGGER.warn(message, p0, p1); }
    public static void warn(String message, Object p0, Object p1, Object p2) { STATIC_LOGGER.warn(message, p0, p1, p2); }
    public static void warn(String message, Object p0, Object p1, Object p2, Object p3) { STATIC_LOGGER.warn(message, p0, p1, p2, p3); }
    public static void warn(String message, Object p0, Object p1, Object p2, Object p3, Object p4) { STATIC_LOGGER.warn(message, p0, p1, p2, p3, p4); }
    public static void warn(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5) { STATIC_LOGGER.warn(message, p0, p1, p2, p3, p4, p5); }
    public static void warn(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6) { STATIC_LOGGER.warn(message, p0, p1, p2, p3, p4, p5, p6); }
    public static void warn(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7) { STATIC_LOGGER.warn(message, p0, p1, p2, p3, p4, p5, p6, p7); }
    public static void warn(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8) { STATIC_LOGGER.warn(message, p0, p1, p2, p3, p4, p5, p6, p7, p8); }
    public static void warn(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8, Object p9) { STATIC_LOGGER.warn(message, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9); }

    public static void error(Marker marker, Message message) { STATIC_LOGGER.error(marker, message); }
    public static void error(Marker marker, Message message, Throwable throwable) { STATIC_LOGGER.error(marker, message, throwable); }
    public static void error(Marker marker, MessageSupplier messageSupplier) { STATIC_LOGGER.error(marker, messageSupplier); }
    public static void error(Marker marker, MessageSupplier messageSupplier, Throwable throwable) { STATIC_LOGGER.error(marker, messageSupplier, throwable); }
    public static void error(Marker marker, CharSequence message) { STATIC_LOGGER.error(marker, message); }
    public static void error(Marker marker, CharSequence message, Throwable throwable) { STATIC_LOGGER.error(marker, message, throwable); }
    public static void error(Marker marker, Object message) { STATIC_LOGGER.error(marker, message); }
    public static void error(Marker marker, Object message, Throwable throwable) { STATIC_LOGGER.error(marker, message, throwable); }
    public static void error(Marker marker, String message) { STATIC_LOGGER.error(marker, message); }
    public static void error(Marker marker, String message, Object... params) { STATIC_LOGGER.error(marker, message, params); }
    public static void error(Marker marker, String message, Supplier<?>... paramSuppliers) { STATIC_LOGGER.error(marker, message, paramSuppliers); }
    public static void error(Marker marker, String message, Throwable throwable) { STATIC_LOGGER.error(marker, message, throwable); }
    public static void error(Marker marker, Supplier<?> messageSupplier) { STATIC_LOGGER.error(marker, messageSupplier); }
    public static void error(Marker marker, Supplier<?> messageSupplier, Throwable throwable) { STATIC_LOGGER.error(marker, messageSupplier, throwable); }
    public static void error(Message message) { STATIC_LOGGER.error(message); }
    public static void error(Message message, Throwable throwable) { STATIC_LOGGER.error(message, throwable); }
    public static void error(MessageSupplier messageSupplier) { STATIC_LOGGER.error(messageSupplier); }
    public static void error(MessageSupplier messageSupplier, Throwable throwable) { STATIC_LOGGER.error(messageSupplier, throwable); }
    public static void error(CharSequence message) { STATIC_LOGGER.error(message); }
    public static void error(CharSequence message, Throwable throwable) { STATIC_LOGGER.error(message, throwable); }
    public static void error(Object message) { STATIC_LOGGER.error(message); }
    public static void error(Object message, Throwable throwable) { STATIC_LOGGER.error(message, throwable); }
    public static void error(String message) { STATIC_LOGGER.error(message); }
    public static void error(String message, Object... params) { STATIC_LOGGER.error(message, params); }
    public static void error(String message, Supplier<?>... paramSuppliers) { STATIC_LOGGER.error(message, paramSuppliers); }
    public static void error(String message, Throwable throwable) { STATIC_LOGGER.error(message, throwable); }
    public static void error(Supplier<?> messageSupplier) { STATIC_LOGGER.error(messageSupplier); }
    public static void error(Supplier<?> messageSupplier, Throwable throwable) { STATIC_LOGGER.error(messageSupplier, throwable); }
    public static void error(Marker marker, String message, Object p0) { STATIC_LOGGER.error(marker, message, p0); }
    public static void error(Marker marker, String message, Object p0, Object p1) { STATIC_LOGGER.error(marker, message, p0, p1); }
    public static void error(Marker marker, String message, Object p0, Object p1, Object p2) { STATIC_LOGGER.error(marker, message, p0, p1, p2); }
    public static void error(Marker marker, String message, Object p0, Object p1, Object p2, Object p3) { STATIC_LOGGER.error(marker, message, p0, p1, p2, p3); }
    public static void error(Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4) { STATIC_LOGGER.error(marker, message, p0, p1, p2, p3, p4); }
    public static void error(Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5) { STATIC_LOGGER.error(marker, message, p0, p1, p2, p3, p4, p5); }
    public static void error(Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6) { STATIC_LOGGER.error(marker, message, p0, p1, p2, p3, p4, p5, p6); }
    public static void error(Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7) { STATIC_LOGGER.error(marker, message, p0, p1, p2, p3, p4, p5, p6, p7); }
    public static void error(Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8) { STATIC_LOGGER.error(marker, message, p0, p1, p2, p3, p4, p5, p6, p7, p8); }
    public static void error(Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8, Object p9) { STATIC_LOGGER.error(marker, message, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9); }
    public static void error(String message, Object p0) { STATIC_LOGGER.error(message, p0); }
    public static void error(String message, Object p0, Object p1) { STATIC_LOGGER.error(message, p0, p1); }
    public static void error(String message, Object p0, Object p1, Object p2) { STATIC_LOGGER.error(message, p0, p1, p2); }
    public static void error(String message, Object p0, Object p1, Object p2, Object p3) { STATIC_LOGGER.error(message, p0, p1, p2, p3); }
    public static void error(String message, Object p0, Object p1, Object p2, Object p3, Object p4) { STATIC_LOGGER.error(message, p0, p1, p2, p3, p4); }
    public static void error(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5) { STATIC_LOGGER.error(message, p0, p1, p2, p3, p4, p5); }
    public static void error(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6) { STATIC_LOGGER.error(message, p0, p1, p2, p3, p4, p5, p6); }
    public static void error(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7) { STATIC_LOGGER.error(message, p0, p1, p2, p3, p4, p5, p6, p7); }
    public static void error(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8) { STATIC_LOGGER.error(message, p0, p1, p2, p3, p4, p5, p6, p7, p8); }
    public static void error(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8, Object p9) { STATIC_LOGGER.error(message, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9); }
}
