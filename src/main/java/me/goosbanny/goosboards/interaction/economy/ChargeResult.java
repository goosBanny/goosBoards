package me.goosbanny.goosboards.interaction.economy;

/**
 * Result of an atomic economy charge operation.
 *
 * @param success         true if the transaction succeeded, false otherwise
 * @param receiptOrReason a transaction receipt UUID if successful, or error reason if failed
 */
public record ChargeResult(boolean success, String receiptOrReason) {}
