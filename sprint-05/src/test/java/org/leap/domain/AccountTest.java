package org.leap.domain;

import org.junit.jupiter.api.Test;
import org.leap.domain.enums.*;
import org.leap.exceptions.*;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class AccountTest {

    private Account activeAccount() {
        return new Account(
                1L,
                "ACC-001",
                "Abinaya",
                "K",
                "EUR",
                new BigDecimal("1000.00"),
                AccountStatus.ACTIVE,
                0L
        );
    }

    @Test
    void shouldCreateAccountWithCorrectDetails() {
        Account account = new Account(
                1L,
                "ACC-001",
                "Abinaya",
                "K",
                "EUR",
                new BigDecimal("1000.00"),
                AccountStatus.ACTIVE,
                0L
        );

        assertEquals(1L, account.getAccountId());
        assertEquals("ACC-001", account.getAccountReference());
        assertEquals("Abinaya", account.getFirstName());
        assertEquals("K", account.getLastName());
        assertEquals("EUR", account.getCurrency());
        assertEquals(new BigDecimal("1000.00"), account.getCashBalance());
        assertEquals(AccountStatus.ACTIVE, account.getStatus());
        assertEquals(0L, account.getVersion());
    }

    @Test
    void activeAccountShouldBeAllowedToTrade() {
        Account account = activeAccount();

        assertTrue(account.isActive());
        assertTrue(account.canTrade());
    }

    @Test
    void suspendedAccountShouldNotBeAllowedToTrade() {
        Account account = activeAccount();
        account.suspend();

        assertEquals(AccountStatus.SUSPENDED, account.getStatus());
        assertFalse(account.canTrade());
    }

    @Test
    void closedAccountShouldNotBeAllowedToTrade() {
        Account account = activeAccount();
        account.close();

        assertEquals(AccountStatus.CLOSED, account.getStatus());
        assertFalse(account.canTrade());
    }

    @Test
    void suspendedAccountShouldBeReversible() {
        Account account = activeAccount();

        account.suspend();
        assertFalse(account.canTrade());

        account.activate();

        assertEquals(AccountStatus.ACTIVE, account.getStatus());
        assertTrue(account.canTrade());
    }

    @Test
    void shouldCreditAccount() {
        Account account = activeAccount();

        account.credit(new BigDecimal("250.00"));

        assertEquals(
                new BigDecimal("1250.00"),
                account.getCashBalance()
        );
    }

    @Test
    void shouldDebitAccount() {
        Account account = activeAccount();

        account.debit(new BigDecimal("250.00"));

        assertEquals(
                new BigDecimal("750.00"),
                account.getCashBalance()
        );
    }

    @Test
    void shouldAllowDebitWhenBalanceExactlyEqualsAmount() {
        Account account = activeAccount();

        account.debit(new BigDecimal("1000.00"));

        assertEquals(
                new BigDecimal("0.00"),
                account.getCashBalance()
        );
    }

    @Test
    void shouldRejectDebitThatWouldMakeBalanceNegative() {
        Account account = activeAccount();

        assertThrows(
                InsufficientFundsException.class,
                () -> account.debit(new BigDecimal("1000.01"))
        );

        assertEquals(
                new BigDecimal("1000.00"),
                account.getCashBalance()
        );
    }

    @Test
    void shouldReportAffordabilityWhenAmountIsAvailable() {
        Account account = activeAccount();

        assertTrue(
                account.canAfford(new BigDecimal("1000.00"))
        );
    }

    @Test
    void shouldReportNotAffordableWhenAmountIsTooLarge() {
        Account account = activeAccount();

        assertFalse(
                account.canAfford(new BigDecimal("1000.01"))
        );
    }

    @Test
    void shouldNotChangeBalanceWhenDebitFails() {
        Account account = activeAccount();

        assertThrows(
                InsufficientFundsException.class,
                () -> account.debit(new BigDecimal("2000.00"))
        );

        assertEquals(
                new BigDecimal("1000.00"),
                account.getCashBalance()
        );
    }

    @Test
    void shouldPreserveMoneyPrecision() {
        Account account = new Account(
                1L,
                "ACC-001",
                "Abinaya",
                "K",
                "EUR",
                new BigDecimal("0.10"),
                AccountStatus.ACTIVE,
                0L
        );

        account.credit(new BigDecimal("0.10"));

        assertEquals(
                new BigDecimal("0.20"),
                account.getCashBalance()
        );
    }

    @Test
    void shouldNotDriftAfterManyOperations() {
        Account account = new Account(
                1L,
                "ACC-001",
                "Abinaya",
                "K",
                "EUR",
                new BigDecimal("0.00"),
                AccountStatus.ACTIVE,
                0L
        );

        for (int i = 0; i < 1000; i++) {
            account.credit(new BigDecimal("0.10"));
            account.debit(new BigDecimal("0.10"));
        }

        assertEquals(
                new BigDecimal("0.00"),
                account.getCashBalance()
        );
    }

    @Test
    void accountIdAndAccountReferenceShouldBeDifferentIdentifiers() {
        Account account = activeAccount();

        assertEquals(1L, account.getAccountId());
        assertEquals("ACC-001", account.getAccountReference());

        assertNotEquals(
                String.valueOf(account.getAccountId()),
                account.getAccountReference()
        );
    }

    @Test
    void shouldExposeOptimisticLockVersion() {
        Account account = activeAccount();

        assertEquals(0L, account.getVersion());

        account.setVersion(5L);

        assertEquals(5L, account.getVersion());
    }

    @Test
    void shouldRejectNegativeCredit() {
        Account account = activeAccount();

        assertThrows(
                IllegalArgumentException.class,
                () -> account.credit(new BigDecimal("-1.00"))
        );
    }

    @Test
    void shouldRejectZeroCredit() {
        Account account = activeAccount();

        assertThrows(
                IllegalArgumentException.class,
                () -> account.credit(BigDecimal.ZERO)
        );
    }

    @Test
    void shouldRejectNegativeDebit() {
        Account account = activeAccount();

        assertThrows(
                IllegalArgumentException.class,
                () -> account.debit(new BigDecimal("-1.00"))
        );
    }

}