package cn.ninth.novel.trigger.http;

import java.util.Locale;

/** 识别响应写出阶段由客户端主动断开导致的异常。 */
final class ClientDisconnectDetector {

    private static final String SERVLET_OUTPUT_STREAM_WRITE_FAILURE =
            "servletoutputstream failed to write";

    private ClientDisconnectDetector() {
    }

    static boolean isClientDisconnected(Throwable exception) {
        Throwable current = exception;
        for (int depth = 0; current != null && depth < 32; depth++) {
            String type = current.getClass().getSimpleName();
            String message = current.getMessage();
            if ("ClientAbortException".equals(type)
                    || "AsyncRequestNotUsableException".equals(type)
                    || message != null && message.toLowerCase(Locale.ROOT)
                    .contains(SERVLET_OUTPUT_STREAM_WRITE_FAILURE)) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return false;
    }
}
