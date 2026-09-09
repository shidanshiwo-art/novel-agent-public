package cn.ninth.novel.types.response;

import cn.ninth.novel.types.enums.ResponseCode;
import cn.ninth.novel.types.exception.AppException;

public record Response<T>(String code, String info, T data) {

    public static <T> Response<T> success(T data) {
        return new Response<>(ResponseCode.SUCCESS.getCode(), ResponseCode.SUCCESS.getMessage(), data);
    }

    public static <T> Response<T> failure(ResponseCode responseCode) {
        return new Response<>(responseCode.getCode(), responseCode.getMessage(), null);
    }

    public static <T> Response<T> failure(AppException exception) {
        ResponseCode responseCode = ResponseCode.fromCode(
                exception == null ? null : exception.getCode()
        );
        return new Response<>(
                responseCode.getCode(),
                ResponseCode.messageFor(
                        responseCode.getCode(),
                        exception == null ? null : exception.getUserMessage()
                ),
                null
        );
    }
}

