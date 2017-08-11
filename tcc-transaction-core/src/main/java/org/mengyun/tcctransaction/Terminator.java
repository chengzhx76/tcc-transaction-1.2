package org.mengyun.tcctransaction;

import org.apache.log4j.Logger;
import org.mengyun.tcctransaction.api.TransactionContext;
import org.mengyun.tcctransaction.api.TransactionContextEditor;
import org.mengyun.tcctransaction.support.FactoryBuilder;
import org.mengyun.tcctransaction.utils.StringUtils;

import java.io.Serializable;
import java.lang.reflect.Method;

/**
 * 终结者.
 * Created by changmingxie on 10/30/15.
 */
public class Terminator implements Serializable {

    private static final long serialVersionUID = -164958655471605778L;

    static final Logger LOG = Logger.getLogger(Terminator.class.getSimpleName());

    public Terminator() {

    }

    /**
     * 根据调用上下文，获取目标方法并执行方法调用.
     * @param invocationContext
     * @return
     */
    public Object invoke(TransactionContext transactionContext, InvocationContext invocationContext, Class<? extends TransactionContextEditor> transactionContextEditorClass) {


        if (StringUtils.isNotEmpty(invocationContext.getMethodName())) {

            LOG.debug("-->根据调用上下文，获取目标方法并执行方法调用 " + invocationContext.getTargetClass().getName() + "." + invocationContext.getMethodName());

            try {

                Object target = FactoryBuilder.factoryOf(invocationContext.getTargetClass()).getInstance();

                Method method = null;

                // 找到要调用的目标方法
                method = target.getClass().getMethod(invocationContext.getMethodName(), invocationContext.getParameterTypes());

                FactoryBuilder.factoryOf(transactionContextEditorClass).getInstance().set(transactionContext, target, method, invocationContext.getArgs());

                // 调用服务方法，被再次被TccTransactionContextAspect和ResourceCoordinatorInterceptor拦截，但因为事务状态已经不再是TRYING了，所以直接执行远程服务
                return method.invoke(target, invocationContext.getArgs());// 调用服务方法

            } catch (Exception e) {
                throw new SystemException(e);
            }
        }
        return null;
    }
}
