package io.github.xianreallyhotzzh.dsh.cases.orchestration;

/** 用例编排的受检异常边界。 */
public class CaseExecutionException extends RuntimeException {

    public CaseExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
