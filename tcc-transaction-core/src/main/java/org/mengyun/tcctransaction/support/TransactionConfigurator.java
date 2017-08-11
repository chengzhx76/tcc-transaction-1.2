package org.mengyun.tcctransaction.support;

import org.mengyun.tcctransaction.TransactionManager;
import org.mengyun.tcctransaction.TransactionRepository;
import org.mengyun.tcctransaction.recover.RecoverConfig;

/**
 * 事务配置器接口
 * Created by changming.xie on 2/24/17.
 */
public interface TransactionConfigurator {

    /**
     * 获取事务管理器.
     * @return
     */
    TransactionManager getTransactionManager();

    /**
     * 获取事务库.
     * @return
     */
    TransactionRepository getTransactionRepository();

    /**
     * 获取事务恢复配置.
     * @return
     */
    RecoverConfig getRecoverConfig();
}
