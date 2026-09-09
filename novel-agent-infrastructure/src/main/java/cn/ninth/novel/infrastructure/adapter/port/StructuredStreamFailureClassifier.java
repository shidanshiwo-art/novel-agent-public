package cn.ninth.novel.infrastructure.adapter.port;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

final class StructuredStreamFailureClassifier {

    private StructuredStreamFailureClassifier() {
    }

    static String classify(Throwable exception, boolean receivedFirstChunk) {
        if (isConnectTimeout(exception)) {
            return "CONNECT_TIMEOUT";
        }
        if (isTimeout(exception)) {
            return receivedFirstChunk ? "STREAM_READ_TIMEOUT" : "STREAM_START_TIMEOUT";
        }
        return "STREAM_FAILED";
    }

    static String exceptionType(Throwable exception) {
        return exception == null ? null : exception.getClass().getSimpleName();
    }

    static String rootCauseType(Throwable exception) {
        if (exception == null) {
            return null;
        }
        Throwable root = exception;
        for (int depth = 0; depth < 32 && root.getCause() != null && root.getCause() != root; depth++) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName();
    }

    private static boolean isConnectTimeout(Throwable exception) {
        for (Throwable current : causes(exception)) {
            String type = current.getClass().getSimpleName().toLowerCase();
            String message = message(current);
            if (type.contains("connecttimeout")
                    || (isTimeout(current) && (type.contains("connect") || message.contains("connect")))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTimeout(Throwable exception) {
        for (Throwable current : causes(exception)) {
            String type = current.getClass().getSimpleName().toLowerCase();
            String message = message(current);
            if (current instanceof SocketTimeoutException
                    || current instanceof TimeoutException
                    || type.contains("timeoutexception")
                    || message.contains("timed out")
                    || message.contains("timeout")) {
                return true;
            }
        }
        return false;
    }

    private static String message(Throwable exception) {
        return exception.getMessage() == null ? "" : exception.getMessage().toLowerCase();
    }

    private static Iterable<Throwable> causes(Throwable exception) {
        List<Throwable> causes = new ArrayList<>();
        Throwable current = exception;
        for (int depth = 0; current != null && depth < 32; depth++) {
            causes.add(current);
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return causes;
    }
}
