package me.goosbanny.goosboards.interaction.economy;

import java.util.UUID;

/**
 * Guard interface for atomic, idempotent player economy charges.
 */
public interface EconomyGuard {

    /**
     * Attempts to charge a player an amount atomically and idempotently.
     *
     * @param playerId       the player UUID
     * @param amount         the positive amount to withdraw
     * @param idempotencyKey a unique transaction/click key to prevent double spending
     * @return ChargeResult indicating success with receipt UUID, or failure with reason
     */
    ChargeResult tryCharge(UUID playerId, double amount, String idempotencyKey);

    /**
     * Attempts to refund a previously charged amount if a subsequent action fails.
     *
     * @param playerId the player UUID
     * @param amount   the positive amount to refund
     * @return true if refund succeeded, false otherwise
     */
    default boolean refund(UUID playerId, double amount) {
        return false;
    }
}
