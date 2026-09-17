package org.leap.executor.exec;

import java.math.BigDecimal;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.leap.domain.Account;
import org.leap.domain.Instrument;
import org.leap.domain.Order;
import org.leap.domain.enums.OrderSide;
import org.leap.domain.enums.OrderStatus;
import org.leap.executor.db.AccountRepository;
import org.leap.executor.db.InstrumentRepository;
import org.leap.executor.db.OrderRepository;
import org.leap.executor.fauxnance.FauxnanceClient;
import org.leap.executor.fauxnance.FauxnanceException;
import org.leap.pricing.FillDecision;
import org.leap.pricing.FillRule;
import org.leap.pricing.Quote;

/**
 * Per-message flow: load the order, re-check what may have changed since
 * acceptance, price it against a live quote, and hand the decision to
 * settlement. Every rejection resolves the order (never leaves it at
 * {@code NEW}); nothing here talks to Kafka — that is {@code
 * OrderEventConsumer}'s job.
 */
public class ExecutionService {

    private static final Logger log = Logger.getLogger(ExecutionService.class.getName());

    private final OrderRepository orderRepository;
    private final InstrumentRepository instrumentRepository;
    private final AccountRepository accountRepository;
    private final FauxnanceClient fauxnanceClient;
    private final SettlementService settlementService;

    public ExecutionService(
            OrderRepository orderRepository,
            InstrumentRepository instrumentRepository,
            AccountRepository accountRepository,
            FauxnanceClient fauxnanceClient,
            SettlementService settlementService) {
        this.orderRepository = orderRepository;
        this.instrumentRepository = instrumentRepository;
        this.accountRepository = accountRepository;
        this.fauxnanceClient = fauxnanceClient;
        this.settlementService = settlementService;
    }

    public void execute(long orderId) {
        OrderStatus status = orderRepository.findStatus(orderId);

        if (status == null) {
            log.log(Level.WARNING, "orderId={0} not found, nothing to execute", orderId);
            return;
        }

        // Duplicate-delivery defense: idempotency at acceptance is already
        // guaranteed by the DB unique constraint on orders.idempotency_key;
        // this guards the second delivery of the *same* Kafka message.
        if (status != OrderStatus.NEW) {
            log.log(Level.WARNING, "DUPLICATE_DELIVERY orderId={0} status={1} — already processed, skipping",
                    new Object[]{orderId, status});
            return;
        }

        Order order = orderRepository.findById(orderId);

        Instrument instrument = instrumentRepository.findById(order.getInstrumentId());
        if (instrument == null || !instrument.isTradable()) {
            settle(orderId, FillDecision.reject("INSTRUMENT_NOT_TRADABLE"));
            return;
        }

        // Account existence is already guaranteed at acceptance; a missing
        // row here would be a data anomaly, handled the same defensive way
        // as "no longer active" rather than inventing a new reason for it.
        Account account = accountRepository.findById(order.getAccountId());
        if (account == null || !account.isActive()) {
            settle(orderId, FillDecision.reject("ACCOUNT_SUSPENDED"));
            return;
        }

        if (order.getSide() == OrderSide.BUY) {
            BigDecimal orderValue = order.getQuantity().multiply(order.getLimitPrice());
            if (!account.canAfford(orderValue)) {
                settle(orderId, FillDecision.reject("INSUFFICIENT_FUNDS"));
                return;
            }
        }

        Quote quote;
        try {
            quote = fauxnanceClient.getQuote(instrument.getTicker());
        } catch (FauxnanceException e) {
            log.log(Level.WARNING, "NO_PRICE_AVAILABLE orderId=" + orderId + " symbol=" + instrument.getTicker(), e);
            settle(orderId, FillDecision.reject("NO_PRICE_AVAILABLE"));
            return;
        }

        settle(orderId, FillRule.decide(order, quote));
    }

    private void settle(long orderId, FillDecision decision) {
        settlementService.settle(orderId, decision);
    }
}
