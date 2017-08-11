package org.mengyun.tcctransaction;

import org.apache.log4j.Logger;
import org.mengyun.tcctransaction.api.TransactionContext;
import org.mengyun.tcctransaction.api.TransactionContextEditor;
import org.mengyun.tcctransaction.api.TransactionStatus;
import org.mengyun.tcctransaction.api.TransactionXid;

import java.io.Serializable;

/**
 * 事务参与者.
 * Created by changmingxie on 10/27/15.
 */
public class Participant implements Serializable {

    static final Logger LOG = Logger.getLogger(Participant.class.getSimpleName());

    private static final long serialVersionUID = 4127729421281425247L;

    private TransactionXid xid;

    private InvocationContext confirmInvocationContext;

    private InvocationContext cancelInvocationContext;

    private Terminator terminator = new Terminator();

    Class<? extends TransactionContextEditor> transactionContextEditorClass;

    public Participant() {

    }

    public Participant(TransactionXid xid, InvocationContext confirmInvocationContext, InvocationContext cancelInvocationContext, Class<? extends TransactionContextEditor> transactionContextEditorClass) {
        this.xid = xid;
        this.confirmInvocationContext = confirmInvocationContext;
        this.cancelInvocationContext = cancelInvocationContext;
        this.transactionContextEditorClass = transactionContextEditorClass;
    }

    public Participant(InvocationContext confirmInvocationContext, InvocationContext cancelInvocationContext, Class<? extends TransactionContextEditor> transactionContextEditorClass) {
        this.confirmInvocationContext = confirmInvocationContext;
        this.cancelInvocationContext = cancelInvocationContext;
        this.transactionContextEditorClass = transactionContextEditorClass;
    }

    public void setXid(TransactionXid xid) {
        this.xid = xid;
    }

    /**
     * 回滚参与者事务（在Transaction中被调用）
     */
    public void rollback() {
        LOG.debug("-->回滚参与者事务");
        terminator.invoke(new TransactionContext(xid, TransactionStatus.CANCELLING.getId()), cancelInvocationContext, transactionContextEditorClass);
    }

    /**
     * 提交参与者事务（在Transaction中被调用）.
     */
    public void commit() {
        LOG.debug("-->提交参与者事务");
        terminator.invoke(new TransactionContext(xid, TransactionStatus.CONFIRMING.getId()), confirmInvocationContext, transactionContextEditorClass);
    }

    public Terminator getTerminator() {
        return terminator;
    }

    public TransactionXid getXid() {
        return xid;
    }

    public InvocationContext getConfirmInvocationContext() {
        return confirmInvocationContext;
    }

    public InvocationContext getCancelInvocationContext() {
        return cancelInvocationContext;
    }

}
