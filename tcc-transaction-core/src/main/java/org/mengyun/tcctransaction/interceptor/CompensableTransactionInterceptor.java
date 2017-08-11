package org.mengyun.tcctransaction.interceptor;

import com.alibaba.fastjson.JSON;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.log4j.Logger;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.mengyun.tcctransaction.NoExistedTransactionException;
import org.mengyun.tcctransaction.SystemException;
import org.mengyun.tcctransaction.Transaction;
import org.mengyun.tcctransaction.TransactionManager;
import org.mengyun.tcctransaction.api.Compensable;
import org.mengyun.tcctransaction.api.Propagation;
import org.mengyun.tcctransaction.api.TransactionContext;
import org.mengyun.tcctransaction.api.TransactionStatus;
import org.mengyun.tcctransaction.common.MethodType;
import org.mengyun.tcctransaction.support.FactoryBuilder;
import org.mengyun.tcctransaction.utils.CompensableMethodUtils;
import org.mengyun.tcctransaction.utils.ReflectionUtils;
import org.mengyun.tcctransaction.utils.TransactionUtils;

import java.lang.reflect.Method;
import java.util.Set;

/**
 * 可补偿事务拦截器。
 * Created by changmingxie on 10/30/15.
 */
public class CompensableTransactionInterceptor {

    static final Logger logger = Logger.getLogger(CompensableTransactionInterceptor.class.getSimpleName());


    private TransactionManager transactionManager;

    private Set<Class<? extends Exception>> delayCancelExceptions;


    public void setTransactionManager(TransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    public void setDelayCancelExceptions(Set<Class<? extends Exception>> delayCancelExceptions) {
        this.delayCancelExceptions = delayCancelExceptions;
    }

    /**
     * 拦截补偿方法.
     * @param pjp
     * @return
     * @throws Throwable
     */
    public Object interceptCompensableMethod(ProceedingJoinPoint pjp) throws Throwable {

        Method method = CompensableMethodUtils.getCompensableMethod(pjp);

        Compensable compensable = method.getAnnotation(Compensable.class);
        Propagation propagation = compensable.propagation();
        // 从拦截方法的参数中获取事务上下文
        TransactionContext transactionContext = FactoryBuilder.factoryOf(compensable.transactionContextEditor()).getInstance().get(pjp.getTarget(), method, pjp.getArgs());

        boolean isTransactionActive = transactionManager.isTransactionActive();

        if (!TransactionUtils.isLegalTransactionContext(isTransactionActive, propagation, transactionContext)) {
            throw new SystemException("no active compensable transaction while propagation is mandatory for method " + method.getName());
        }

        // 计算可补偿事务方法类型
        MethodType methodType = CompensableMethodUtils.calculateMethodType(propagation, isTransactionActive, transactionContext);

        logger.debug("-->可补偿事务拦截器 methodType:" + methodType.toString());

        switch (methodType) {
            case ROOT:
                return rootMethodProceed(pjp);// 主事务方法的处理
            case PROVIDER:
                return providerMethodProceed(pjp, transactionContext); // 服务提供者事务方法处理
            default:
                return pjp.proceed(); // 其他的方法都是直接执行
        }
    }

    /**
     * 主事务方法的处理.
     * @param pjp
     * @return
     * @throws Throwable
     */
    private Object rootMethodProceed(ProceedingJoinPoint pjp) throws Throwable {
        logger.debug("-->根方法处理");
        Object returnValue = null;

        Transaction transaction = null;

        try {

            transaction = transactionManager.begin(); // 事务开始（创建事务日志记录，并在当前线程缓存该事务日志记录）

            try {
                logger.debug("-->根方法处理 try begin");
                returnValue = pjp.proceed();// Try (开始执行被拦截的方法)
                logger.debug("-->根方法处理 try end");
            } catch (Throwable tryingException) {

                if (isDelayCancelException(tryingException)) {

                } else {
                    logger.warn(String.format("compensable transaction trying failed. transaction content:%s", JSON.toJSONString(transaction)), tryingException);

                    transactionManager.rollback();
                }

                throw tryingException;
            }

            logger.info("-->可补偿事务拦截器 begin commit()");
            transactionManager.commit();

        } finally {
            transactionManager.cleanAfterCompletion(transaction);
        }

        return returnValue;
    }

    private Object providerMethodProceed(ProceedingJoinPoint pjp, TransactionContext transactionContext) throws Throwable {
        logger.debug("-->生产者方法处理 事务状态:" + TransactionStatus.valueOf(transactionContext.getStatus()).toString());
        Transaction transaction = null;
        try {

            switch (TransactionStatus.valueOf(transactionContext.getStatus())) {
                case TRYING:
                    logger.debug("-->生产者方法处理 try begin");
                    // 基于全局事务ID扩展创建新的分支事务，并存于当前线程的事务局部变量中.
                    transaction = transactionManager.propagationNewBegin(transactionContext);
                    logger.debug("-->生产者方法处理 try end");
                    return pjp.proceed();
                case CONFIRMING:
                    try {
                        logger.debug("-->生产者方法处理 确认 begin");
                        // 找出存在的事务并处理.
                        transaction = transactionManager.propagationExistBegin(transactionContext);
                        transactionManager.commit();
                        logger.debug("-->生产者方法处理 确认 end");
                    } catch (NoExistedTransactionException excepton) {
                        //the transaction has been commit,ignore it.
                    }
                    break;
                case CANCELLING:

                    try {
                        logger.debug("-->生产者方法处理 取消 begin");
                        transaction = transactionManager.propagationExistBegin(transactionContext);
                        transactionManager.rollback();
                        logger.debug("-->生产者方法处理 取消 end");
                    } catch (NoExistedTransactionException exception) {
                        //the transaction has been rollback,ignore it.
                    }
                    break;
            }

        } finally {
            transactionManager.cleanAfterCompletion(transaction);
        }

        Method method = ((MethodSignature) (pjp.getSignature())).getMethod();

        return ReflectionUtils.getNullValue(method.getReturnType());
    }

    private boolean isDelayCancelException(Throwable throwable) {

        if (delayCancelExceptions != null) {
            for (Class delayCancelException : delayCancelExceptions) {

                Throwable rootCause = ExceptionUtils.getRootCause(throwable);

                if (delayCancelException.isAssignableFrom(throwable.getClass())
                        || (rootCause != null && delayCancelException.isAssignableFrom(rootCause.getClass()))) {
                    return true;
                }
            }
        }

        return false;
    }

}
