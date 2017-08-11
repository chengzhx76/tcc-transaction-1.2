package org.mengyun.tcctransaction;

import org.apache.log4j.Logger;
import org.mengyun.tcctransaction.api.TransactionContext;
import org.mengyun.tcctransaction.api.TransactionStatus;
import org.mengyun.tcctransaction.api.TransactionXid;
import org.mengyun.tcctransaction.common.TransactionType;

import java.util.Deque;
import java.util.LinkedList;

/**
 * 事务管理器.
 * Created by changmingxie on 10/26/15.
 */
public class TransactionManager {

    static final Logger logger = Logger.getLogger(TransactionManager.class.getSimpleName());
    static final Logger LOG = Logger.getLogger(TransactionManager.class.getSimpleName());

    /**
     * 事务配置器
     */
    private TransactionRepository transactionRepository;

    /**
     * 定义当前线程的事务局部变量.
     */
    private static final ThreadLocal<Deque<Transaction>> CURRENT = new ThreadLocal<Deque<Transaction>>();

    public void setTransactionRepository(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    /**
     * 事务开始（创建事务日志记录，并将该事务日志记录存入当前线程的事务局部变量中）
     */
    public Transaction begin() {
        LOG.debug("-->事务开始");
        // 创建事务，事务类型为根事务ROOT
        Transaction transaction = new Transaction(TransactionType.ROOT);
        LOG.debug("-->事务类型:" + transaction.getTransactionType().toString() + ", 事务状态:" + transaction.getStatus().toString());
        // 创建事务记录,写入事务日志库
        transactionRepository.create(transaction);
        registerTransaction(transaction);
        return transaction;
    }

    public Transaction propagationNewBegin(TransactionContext transactionContext) {

        Transaction transaction = new Transaction(transactionContext);
        LOG.debug("-->扩展创建新的分支事务 TransactionXid：" + TransactionXid.byteArrayToUUID(transaction.getXid().getGlobalTransactionId()).toString()
                + "|" + TransactionXid.byteArrayToUUID(transaction.getXid().getBranchQualifier()).toString());
        transactionRepository.create(transaction);

        registerTransaction(transaction);
        return transaction;
    }

    /**
     * 找出存在的事务并处理.
     * @param transactionContext
     * @return
     * @throws NoExistedTransactionException
     */
    public Transaction propagationExistBegin(TransactionContext transactionContext) throws NoExistedTransactionException {
        Transaction transaction = transactionRepository.findByXid(transactionContext.getXid());

        // 如果找到了事物
        if (transaction != null) {
            LOG.debug("-->找出存在的事务 TransactionXid：" + TransactionXid.byteArrayToUUID(transaction.getXid().getGlobalTransactionId()).toString()
                    + "|" + TransactionXid.byteArrayToUUID(transaction.getXid().getBranchQualifier()).toString());
            // 更改事务状态为transactionContext中的状态
            transaction.changeStatus(TransactionStatus.valueOf(transactionContext.getStatus()));
            registerTransaction(transaction);
            return transaction;
        } else {
            throw new NoExistedTransactionException();
        }
    }
    /**
     * 提交.
     */
    public void commit() {

        LOG.debug("-->提交");
        // 获取本地线程上事务队列中的时间最久的事务
        Transaction transaction = getCurrentTransaction();
        // 更改事务状态为CONFIRMING
        transaction.changeStatus(TransactionStatus.CONFIRMING);
        LOG.debug("-->更新事务状态为 CONFIRMING");
        // 更新事务持久化
        transactionRepository.update(transaction);

        try {
            LOG.info("-->事务开始提交");
            // 调用事务的commit
            transaction.commit();
            // 如果上面的commit没有抛出任何异常就说明事务成功，就从事务日志中删除这个事务
            transactionRepository.delete(transaction);
        } catch (Throwable commitException) {
            // 事务commit过程抛出了异常
            logger.error("compensable transaction confirm failed.", commitException);
            // 转为抛出ConfirmingException异常，这样会导致事务在事务日志中不被删除，recovery会去处理长时间没有被删除的事务
            throw new ConfirmingException(commitException);
        }
    }

    public Transaction getCurrentTransaction() {
        if (isTransactionActive()) {
            return CURRENT.get().peek();
        }
        return null;
    }

    /**
     * 获取当前事务.
     * @return
     */
    public boolean isTransactionActive() {
        Deque<Transaction> transactions = CURRENT.get();
        return transactions != null && !transactions.isEmpty();
    }

    /**
     * 回滚事务.
     */
    public void rollback() {
        // 回滚事务
        Transaction transaction = getCurrentTransaction();
        // 更改事务状态为CANCELLING
        transaction.changeStatus(TransactionStatus.CANCELLING);
        // 更新事务持久化日志
        transactionRepository.update(transaction);

        try {
            LOG.info("-->开始回滚事务");
            // 调用事务的rollback
            transaction.rollback();
            // 没有异常，就从事务日志中删除这个事务
            transactionRepository.delete(transaction);
        } catch (Throwable rollbackException) {
            logger.error("compensable transaction rollback failed.", rollbackException);
            // 否则事务异常，抛出CancellingException
            throw new CancellingException(rollbackException);
        }
    }

    private void registerTransaction(Transaction transaction) {

        if (CURRENT.get() == null) {
            CURRENT.set(new LinkedList<Transaction>());
        }

        CURRENT.get().push(transaction);
    }

    public void cleanAfterCompletion(Transaction transaction) {
        if (isTransactionActive() && transaction != null) {
            Transaction currentTransaction = getCurrentTransaction();
            if (currentTransaction == transaction) {
                CURRENT.get().pop();
            } else {
                throw new SystemException("Illegal transaction when clean after completion");
            }
        }
    }


    public void enlistParticipant(Participant participant) {
        Transaction transaction = this.getCurrentTransaction();
        transaction.enlistParticipant(participant);
        transactionRepository.update(transaction);
    }
}
