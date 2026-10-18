package com.katachilabs.shelves.util

/** Validation et conversion des codes EAN-13, EAN-8 et ISBN-10. */
object Isbn {
    /** Ne garde que les chiffres (et le X final d'un ISBN-10). */
    fun normalize(raw: String): String {
        val cleaned = raw.uppercase().filter { it.isDigit() || it == 'X' }
        return if (cleaned.dropLast(1).contains('X')) cleaned.filter { it.isDigit() } else cleaned
    }

    fun isValidEan13(code: String): Boolean {
        if (code.length != 13 || !code.all { it.isDigit() }) return false
        val sum = code.take(12).mapIndexed { i, c -> c.digitToInt() * if (i % 2 == 0) 1 else 3 }.sum()
        return (10 - sum % 10) % 10 == code.last().digitToInt()
    }

    fun isValidEan8(code: String): Boolean {
        if (code.length != 8 || !code.all { it.isDigit() }) return false
        val sum = code.take(7).mapIndexed { i, c -> c.digitToInt() * if (i % 2 == 0) 3 else 1 }.sum()
        return (10 - sum % 10) % 10 == code.last().digitToInt()
    }

    fun isValidIsbn10(code: String): Boolean {
        if (code.length != 10 || !code.take(9).all { it.isDigit() }) return false
        val last = code.last()
        if (!last.isDigit() && last != 'X') return false
        val sum = code.mapIndexed { i, c -> (10 - i) * if (c == 'X') 10 else c.digitToInt() }.sum()
        return sum % 11 == 0
    }

    /** Un ISBN-13 est un EAN-13 commençant par 978 ou 979. */
    fun isIsbn13(code: String): Boolean = isValidEan13(code) && (code.startsWith("978") || code.startsWith("979"))

    fun isbn10To13(isbn10: String): String {
        val body = "978" + isbn10.take(9)
        val sum = body.mapIndexed { i, c -> c.digitToInt() * if (i % 2 == 0) 1 else 3 }.sum()
        return body + ((10 - sum % 10) % 10)
    }

    /**
     * Ramène une saisie ou un scan à un code exploitable : ISBN-13 pour un ISBN-10,
     * le code tel quel pour un EAN-13 ou EAN-8 valide, `null` sinon.
     */
    fun toSearchCode(raw: String): String? {
        val code = normalize(raw)
        return when {
            isValidIsbn10(code) -> isbn10To13(code)
            isValidEan13(code) || isValidEan8(code) -> code
            else -> null
        }
    }
}
