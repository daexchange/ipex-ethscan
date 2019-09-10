package ai.turbochain.ipex.wallet.component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.web3j.utils.Convert;

import ai.turbochain.ipex.wallet.entity.Account;
import ai.turbochain.ipex.wallet.entity.Coin;
import ai.turbochain.ipex.wallet.entity.Deposit;
import ai.turbochain.ipex.wallet.service.AccountService;
import ai.turbochain.ipex.wallet.service.EthService;
import io.api.etherscan.core.impl.EtherScanApi;
import io.api.etherscan.model.proxy.BlockProxy;
import io.api.etherscan.util.BasicUtils;

@Component
public class EthWatcher extends Watcher {
	private Logger logger = LoggerFactory.getLogger(EthWatcher.class);
	@Autowired
	private EtherScanApi etherScanApi;
	@Autowired
	private EthService ethService;
	@Autowired
	private AccountService accountService;
	@Autowired
	private ExecutorService executorService;

	@Autowired
	private Coin coin;
	
	@Override
	public List<Deposit> replayBlock(Long startBlockNumber, Long endBlockNumber) {
		List<Deposit> deposits = new ArrayList<>();
		try {
			for (Long i = startBlockNumber; i <= endBlockNumber; i++) {
				BlockProxy blockProxy = etherScanApi.proxy().block(i).get();
				blockProxy.getTransactions().stream().forEach(transaction -> {
					if (StringUtils.isNotEmpty(transaction.getTo())
							&& accountService.isAddressExist(transaction.getTo())
							&& !transaction.getFrom().equalsIgnoreCase(getCoin().getIgnoreFromAddress())) {

						Deposit deposit = new Deposit();
						deposit.setTxid(transaction.getHash());
						deposit.setBlockHeight(transaction.getBlockNumber());
						deposit.setBlockHash(transaction.getBlockHash());
						deposit.setAmount(Convert.fromWei(BasicUtils.parseHex(transaction.getValue()).toString(),
								Convert.Unit.ETHER));
						deposit.setAddress(transaction.getTo());
						afterDeposit(deposit);
						deposits.add(deposit);
						logger.info("received coin {} at height {}", Convert
								.fromWei(BasicUtils.parseHex(transaction.getValue()).toString(), Convert.Unit.ETHER),
								transaction.getBlockNumber());
						// 同步余额
						try {
							ethService.syncAddressBalance(deposit.getAddress());
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
					// 如果是地址簿里转出去的地址，需要同步余额
					if (StringUtils.isNotEmpty(transaction.getTo())
							&& accountService.isAddressExist(transaction.getFrom())) {
						logger.info("sync address:{} balance", transaction.getFrom());
						try {
							ethService.syncAddressBalance(transaction.getFrom());
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				});
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return deposits;
	}

	/**
	 * 充值成功后的操作
	 */
	public void afterDeposit(Deposit deposit) {
		executorService.execute(new Runnable() {
			public void run() {
				depositCoin(deposit);
			}
		});
	}

	/**
	 * 充值ETH转账到withdraw账户
	 * 
	 * @param deposit
	 */
	public void depositCoin(Deposit deposit) {
		try {
			BigDecimal fee = ethService.getMinerFee(coin.getGasLimit());
			Account account = accountService.findByAddress(deposit.getAddress());
			logger.info("充值ETH转账到withdraw账户:from={},to={},amount={},sync={}", deposit.getAddress(),
					coin.getWithdrawAddress(), deposit.getAmount().subtract(fee), true);
			ethService.transfer(coin.getKeystorePath() + "/" + account.getWalletFile(), account.getPassword(),
					coin.getWithdrawAddress(), deposit.getAmount().subtract(fee), true, "");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public Long getNetworkBlockHeight() {
		try {
			return etherScanApi.proxy().blockNoLast();
		} catch (Exception e) {
			e.printStackTrace();
			return 0L;
		}
	}
}
