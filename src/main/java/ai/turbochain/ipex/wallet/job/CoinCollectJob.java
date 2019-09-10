package ai.turbochain.ipex.wallet.job;


import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ai.turbochain.ipex.wallet.service.AccountService;
import ai.turbochain.ipex.wallet.service.EthService;
import ai.turbochain.ipex.wallet.util.AccountReplay;

@Component
public class CoinCollectJob {
    private Logger logger = LoggerFactory.getLogger(CoinCollectJob.class);
    @Autowired
    private AccountService accountService;
    @Autowired
    private EthService ethService;

    /**
     * 同步ETH地址余额
     */
    @Scheduled(cron = "0 0 17 * * ?")
    public void rechargeMinerFee(){
        AccountReplay accountReplay = new AccountReplay(accountService,100);
        accountReplay.run(account -> {
            logger.info("process account:{}",account);
            try {
                //查询余额
                BigDecimal ethBalance = ethService.getBalance(account.getAddress());
                //同步账户余额
                accountService.updateBalance(account.getAddress(),ethBalance);
            }
            catch (Exception e){
                e.printStackTrace();
            }
        });
    }
}
