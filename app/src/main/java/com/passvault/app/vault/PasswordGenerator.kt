package com.passvault.app.vault

import java.security.SecureRandom
import kotlin.math.ln
import kotlin.math.log2

data class GeneratorOptions(
    val length: Int = 20,
    val upper: Boolean = true,
    val lower: Boolean = true,
    val digits: Boolean = true,
    val symbols: Boolean = true,
    val excludeAmbiguous: Boolean = false,
)

object PasswordGenerator {
    private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    private const val DIGITS = "0123456789"
    private const val SYMBOLS = "!@#$%^&*()-_=+[]{};:,.<>/?~"
    private const val AMBIGUOUS = "Il1O0o|`'\""

    private val random = SecureRandom()

    private fun sets(o: GeneratorOptions): List<String> = buildList {
        if (o.upper) add(UPPER)
        if (o.lower) add(LOWER)
        if (o.digits) add(DIGITS)
        if (o.symbols) add(SYMBOLS)
    }.map { set -> if (o.excludeAmbiguous) set.filterNot { it in AMBIGUOUS } else set }
        .filter { it.isNotEmpty() }

    /** Generates a password containing at least one character from each selected set. */
    fun generate(o: GeneratorOptions): String {
        val sets = sets(o).ifEmpty { listOf(LOWER) }
        val length = o.length.coerceIn(sets.size, 256)
        val pool = sets.joinToString("")
        val chars = CharArray(length)
        sets.forEachIndexed { i, set -> chars[i] = set[random.nextInt(set.length)] }
        for (i in sets.size until length) chars[i] = pool[random.nextInt(pool.length)]
        // Fisher–Yates shuffle so the guaranteed characters are not at the front.
        for (i in length - 1 downTo 1) {
            val j = random.nextInt(i + 1)
            val t = chars[i]; chars[i] = chars[j]; chars[j] = t
        }
        return String(chars)
    }

    fun entropyBits(o: GeneratorOptions): Double {
        val pool = sets(o).sumOf { it.length }
        return if (pool <= 1) 0.0 else o.length * log2(pool.toDouble())
    }

    /** Rough entropy estimate for an arbitrary (user-typed) password. */
    fun estimateBits(password: String): Double {
        if (password.isEmpty()) return 0.0
        var pool = 0
        if (password.any { it.isLowerCase() }) pool += 26
        if (password.any { it.isUpperCase() }) pool += 26
        if (password.any { it.isDigit() }) pool += 10
        if (password.any { !it.isLetterOrDigit() }) pool += 33
        val unique = password.toSet().size
        // Penalise heavy repetition.
        val effectiveLength = password.length * (unique.toDouble() / password.length).coerceAtLeast(0.3)
        return effectiveLength * (ln(pool.coerceAtLeast(2).toDouble()) / ln(2.0))
    }
}
