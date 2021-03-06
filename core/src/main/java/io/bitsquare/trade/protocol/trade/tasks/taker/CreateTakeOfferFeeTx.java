/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.trade.protocol.trade.tasks.taker;

import io.bitsquare.arbitration.Arbitrator;
import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.FeePolicy;
import io.bitsquare.btc.WalletService;
import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.ArbitrationSelectionRule;
import io.bitsquare.trade.protocol.trade.tasks.TradeTask;
import io.bitsquare.user.User;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;

public class CreateTakeOfferFeeTx extends TradeTask {
    private static final Logger log = LoggerFactory.getLogger(CreateTakeOfferFeeTx.class);

    public CreateTakeOfferFeeTx(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            User user = processModel.getUser();
            NodeAddress selectedArbitratorNodeAddress = ArbitrationSelectionRule.select(user.getAcceptedArbitratorAddresses(), processModel.getOffer());
            log.debug("selectedArbitratorAddress " + selectedArbitratorNodeAddress);
            Arbitrator selectedArbitrator = user.getAcceptedArbitratorByAddress(selectedArbitratorNodeAddress);
            checkNotNull(selectedArbitrator, "selectedArbitrator must not be null at CreateTakeOfferFeeTx");
            WalletService walletService = processModel.getWalletService();
            String id = model.getOffer().getId();
            AddressEntry addressEntry = walletService.getOrCreateAddressEntry(id, AddressEntry.Context.OFFER_FUNDING);
            AddressEntry reservedForTradeAddressEntry = walletService.getOrCreateAddressEntry(id, AddressEntry.Context.RESERVED_FOR_TRADE);
            AddressEntry changeAddressEntry = walletService.getOrCreateAddressEntry(AddressEntry.Context.AVAILABLE);
            Address fundingAddress = addressEntry.getAddress();
            Address reservedForTradeAddress = reservedForTradeAddressEntry.getAddress();
            Address changeAddress = changeAddressEntry.getAddress();
            Transaction createTakeOfferFeeTx = processModel.getTradeWalletService().createTradingFeeTx(
                    fundingAddress,
                    reservedForTradeAddress,
                    changeAddress,
                    processModel.getFundsNeededForTrade(),
                    processModel.getUseSavingsWallet(),
                    FeePolicy.getTakeOfferFee(),
                    selectedArbitrator.getBtcAddress());

            processModel.setTakeOfferFeeTx(createTakeOfferFeeTx);
            trade.setTakeOfferFeeTxId(createTakeOfferFeeTx.getHashAsString());

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
