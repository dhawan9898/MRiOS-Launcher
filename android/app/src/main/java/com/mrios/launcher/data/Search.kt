package com.mrios.launcher.data

import java.math.BigDecimal
import java.math.MathContext
import java.text.Normalizer

/** Small fuzzy matcher: prefix > word prefix > substring > subsequence > one typo. */
object FuzzySearch {
    private fun norm(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")

    fun score(query: String, text: String): Int {
        val q = norm(query.trim())
        if (q.isEmpty()) return 0
        val t = norm(text)
        if (t == q) return 1000
        if (t.startsWith(q)) return 900 - (t.length - q.length)
        val words = t.split(' ', '-', '_', '.', '&').filter { it.isNotEmpty() }
        if (words.any { it.startsWith(q) }) return 800 - (t.length - q.length)
        val initials = words.joinToString("") { it.take(1) }
        if (q.length >= 2 && initials.startsWith(q)) return 750
        val idx = t.indexOf(q)
        if (idx >= 0) return 650 - idx
        // Subsequence with gap penalty
        var ti = 0; var gaps = 0; var last = -1
        for (ch in q) {
            val found = t.indexOf(ch, ti)
            if (found < 0) { gaps = -1; break }
            if (last >= 0) gaps += found - last - 1
            last = found; ti = found + 1
        }
        if (gaps >= 0 && gaps <= q.length * 2) return 400 - gaps * 10
        // One typo against a word prefix
        if (q.length >= 3 && words.any { w -> w.length >= q.length - 1 && distance(q, w.take(q.length)) <= 1 }) return 200
        return 0
    }

    private fun distance(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]; dp[0] = i
            for (j in 1..b.length) {
                val tmp = dp[j]
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return dp[b.length]
    }

    fun <T> filter(query: String, items: List<T>, label: (T) -> String): List<T> =
        items.map { it to score(query, label(it)) }.filter { it.second > 0 }
            .sortedByDescending { it.second }.map { it.first }
}

/** Evaluates simple arithmetic like Spotlight: 12*(3+4)/2, 15% , 2^8. */
object Calculator {
    fun evaluate(input: String): String? {
        val s = input.replace(" ", "").replace('×', '*').replace('÷', '/').replace(',', '.')
        if (s.isEmpty() || s.none { it in "+-*/^%(" } || s.any { it !in "0123456789.+-*/^%()" }) return null
        return runCatching {
            val p = Parser(s)
            val v = p.expr()
            if (p.pos != s.length || v.isNaN() || v.isInfinite()) null
            else BigDecimal(v).round(MathContext(12)).stripTrailingZeros().toPlainString()
        }.getOrNull()
    }

    private class Parser(val s: String) {
        var pos = 0
        fun expr(): Double {
            var v = term()
            while (pos < s.length && (s[pos] == '+' || s[pos] == '-')) { val op = s[pos++]; val r = term(); v = if (op == '+') v + r else v - r }
            return v
        }
        fun term(): Double {
            var v = power()
            while (pos < s.length && (s[pos] == '*' || s[pos] == '/')) { val op = s[pos++]; val r = power(); v = if (op == '*') v * r else v / r }
            return v
        }
        fun power(): Double {
            val b = unary()
            if (pos < s.length && s[pos] == '^') { pos++; return Math.pow(b, power()) }
            return b
        }
        fun unary(): Double = if (pos < s.length && s[pos] == '-') { pos++; -unary() } else if (pos < s.length && s[pos] == '+') { pos++; unary() } else postfix()
        fun postfix(): Double {
            var v = atom()
            while (pos < s.length && s[pos] == '%') { pos++; v /= 100 }
            return v
        }
        fun atom(): Double {
            if (pos < s.length && s[pos] == '(') { pos++; val v = expr(); require(s.getOrNull(pos) == ')'); pos++; return v }
            val start = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
            require(pos > start)
            return s.substring(start, pos).toDouble()
        }
    }
}
