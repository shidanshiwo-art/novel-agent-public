package cn.ninth.novel.types.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Getter
public enum ResponseCode {

    SUCCESS("0000", "成功"),
    UN_ERROR("0001", "系统暂时出现异常，请稍后重试。"),
    ILLEGAL_PARAMETER("0002", "非法参数"),

    E0001("E0001","模型输出非法，请重试"),
    E0002("E0002","章节计划模型响应无效"),
    E0003("E0003","章节初稿模型响应无效"),
    E0004("E0004","章节审稿失败，请重新审稿或采用当前正文。"),
    E0005("E0005","章节改稿模型响应无效"),
    E0006("E0006","章节压缩模型响应无效"),
    E0007("E0007","章节模型结构化响应无效"),
    HUMAN_REVISE_LIMIT_EXCEEDED("E0008", "人工返修次数已达上限，请选择通过或结束"),
    ;

    private  String code;
    private  String message;

    public static ResponseCode fromCode(String code) {
        for (ResponseCode responseCode : values()) {
            if (responseCode.code.equals(code)) {
                return responseCode;
            }
        }
        return UN_ERROR;
    }

    public static String messageFor(String code, String userMessage) {
        return userMessage == null || userMessage.isBlank()
                ? fromCode(code).message
                : userMessage;
    }

}

