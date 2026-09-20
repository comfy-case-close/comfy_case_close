package com.fnbx.shared.enums;

/**
 * Whether physical cash actually moved, and in which direction.
 *
 * <p>Independent of the other two flags on {@code platform.movement_kind}
 * ({@code affectsDifference}, {@code affectsRemaining}).
 *
 * <table>
 *   <caption>Sign of {@code signedAmount} per effect</caption>
 *   <tr><th>Value</th><th>Sign</th><th>Real example</th></tr>
 *   <tr><td>{@code CASH_OUT}</td><td>negative</td><td>Bought supplies, 523,000</td></tr>
 *   <tr><td>{@code CASH_IN}</td><td>positive</td><td>Tip left in the drawer, 34,000</td></tr>
 *   <tr><td>{@code NO_CASH_FLOW}</td><td>either</td>
 *       <td>"Opening balance keyed as 0, drawer actually held 4,228,000" -
 *           the number is wrong, no cash moved</td></tr>
 * </table>
 *
 * <p><b>Why keep the third value:</b> in Comfy's real data 21 of 51 manual
 * explanations were miscounts, POS errors or unpaid bills. Folding them into
 * {@code CASH_OUT} would make any sum over cash direction stop meaning
 * "money spent", and inflate the expense report with keying mistakes.
 */
public enum EffectType { CASH_OUT, CASH_IN, NO_CASH_FLOW }
