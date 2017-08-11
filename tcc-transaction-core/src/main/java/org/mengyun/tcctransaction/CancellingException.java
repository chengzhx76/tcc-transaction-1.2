package org.mengyun.tcctransaction;

/**
 * Created by changming.xie on 7/21/16.
 */
public class CancellingException extends RuntimeException {

    /**
     * 取消异常.
     * Created by changming.xie on 7/21/16.
     */
    public CancellingException(Throwable cause) {
        super(cause);
    }
}
