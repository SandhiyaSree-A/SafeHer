package com.safeher.app.util

object PhoneUtils {

    /** 9876543210 / 09876543210 / 919876543210 / 0091... / +91 98765 43210  ->  +919876543210 */
    fun normalizeIndian(raw: String): String {
        val cleaned = raw.filter { it.isDigit() || it == '+' }
        val digits = cleaned.filter { it.isDigit() }
        return when {
            cleaned.startsWith("+") -> "+$digits"
            digits.startsWith("00") -> "+${digits.drop(2)}"
            digits.length == 10 -> "+91$digits"
            digits.length == 11 && digits.startsWith("0") -> "+91${digits.drop(1)}"
            digits.length == 12 && digits.startsWith("91") -> "+$digits"
            else -> cleaned
        }
    }

    fun isValidIndianMobile(number: String): Boolean =
        Regex("^\\+91[6-9]\\d{9}$").matches(number)

    /** Indian mobile, or any international number written with a + prefix. */
    fun isValidPhone(number: String): Boolean =
        isValidIndianMobile(number) || Regex("^\\+\\d{8,15}$").matches(number)
}