package org.practice.fundgateway.common.permission;

/** 表示请求超出当前显式权限上下文。 */
public class PermissionDeniedException extends RuntimeException {

    /** 使用可审计的拒绝原因创建权限异常。 */
    public PermissionDeniedException(String message) {
        super(message);
    }
}
