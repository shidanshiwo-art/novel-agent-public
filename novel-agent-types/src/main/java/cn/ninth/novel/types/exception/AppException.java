package cn.ninth.novel.types.exception;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class AppException extends RuntimeException {

    private static final long serialVersionUID = 5317680961212299217L;

    /** 异常码 */
    private String code;

    /** 异常信息 */
    private String info;

    /** 仅供日志排查的内部详情，不参与用户响应。 */
    private String internalDetail;

    /** 是否允许将 info 作为用户提示返回。 */
    private boolean userMessage;

    public AppException(String code) {
        this.code = code;
    }

    public AppException(String code, Throwable cause) {
        this.code = code;
        super.initCause(cause);
    }

    /**
     * 创建明确允许返回前端的用户提示。
     */
    public static AppException user(String code, String message) {
        return user(code, message, null);
    }

    /**
     * 创建明确允许返回前端的用户提示，并保留异常原因供日志排查。
     */
    public static AppException user(
            String code,
            String message,
            Throwable cause
    ) {
        return new AppException(code, message, cause, true);
    }

    /**
     * 创建仅供日志排查的内部详情，不允许穿透到 API info 或 SSE content。
     */
    public static AppException internal(String code, String detail) {
        return internal(code, detail, null);
    }

    /**
     * 创建仅供日志排查的内部详情，并保留异常原因供日志排查。
     */
    public static AppException internal(
            String code,
            String detail,
            Throwable cause
    ) {
        return new AppException(code, detail, cause, false);
    }

    /**
     * 供结构化模型响应等内部异常子类使用。
     */
    protected AppException(
            String code,
            String message,
            Throwable cause,
            boolean userMessage
    ) {
        this.code = code;
        this.userMessage = userMessage;
        if (userMessage) {
            this.info = message;
        } else {
            this.internalDetail = message;
        }
        super.initCause(cause);
    }

    public String getUserMessage() {
        return userMessage && info != null && !info.isBlank() ? info : null;
    }

    @Override
    public String toString() {
        return "AppException{" +
                "code='" + code + '\'' +
                ", info='" + info + '\'' +
                ", internalDetail='" + internalDetail + '\'' +
                '}';
    }

}
