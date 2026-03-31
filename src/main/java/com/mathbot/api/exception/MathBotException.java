package com.mathbot.api.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class MathBotException extends RuntimeException {

    private final ErrorCode errorCode;
    private final HttpStatus httpStatus;

    public MathBotException(ErrorCode errorCode, String message, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public static MathBotException notFound(ErrorCode code, String message) {
        return new MathBotException(code, message, HttpStatus.NOT_FOUND);
    }

    public static MathBotException unauthorized(ErrorCode code, String message) {
        return new MathBotException(code, message, HttpStatus.UNAUTHORIZED);
    }

    public static MathBotException forbidden(ErrorCode code, String message) {
        return new MathBotException(code, message, HttpStatus.FORBIDDEN);
    }

    public static MathBotException conflict(ErrorCode code, String message) {
        return new MathBotException(code, message, HttpStatus.CONFLICT);
    }

    public static MathBotException badRequest(ErrorCode code, String message) {
        return new MathBotException(code, message, HttpStatus.BAD_REQUEST);
    }

    public static MathBotException serviceUnavailable(ErrorCode code, String message) {
        return new MathBotException(code, message, HttpStatus.SERVICE_UNAVAILABLE);
    }
}
