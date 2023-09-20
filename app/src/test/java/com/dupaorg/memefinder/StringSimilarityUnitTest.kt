package com.dupaorg.memefinder

import info.debatty.java.stringsimilarity.WeightedLevenshtein
import io.kotest.core.spec.style.StringSpec
import io.kotest.data.blocking.forAll
import io.kotest.matchers.shouldBe

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
interface LowerIsBetter {
    fun measure(s1: String, s2: String): Double
}

class MyWeightedLevenshtein : LowerIsBetter {

    private val wl = WeightedLevenshtein { c1, c2 ->
        matchChars(
            c1,
            c2,
            listOf(
                Pair('r', 'p'),
                Pair('e', 'f'),
                Pair('a', 'ą'),
                Pair('e', 'ę'),
            ),
        )
    }
    private fun matchChars(c1: Char, c2: Char, pairs: List<Pair<Char, Char>>): Double {
        return pairs.fold(1.0) { acc, (first, second) ->
            if (first == c1 && second == c2 || first == c2 && second == c1) 0.1 else acc
        }
    }

    override fun measure(s1: String, s2: String): Double {
        return wl.distance(s1, s2)
    }
}

class StringSimilarityProperties :
    StringSpec({
        val myWl: LowerIsBetter = MyWeightedLevenshtein()
        "is case insensitive" {
            forAll<String> { s ->
                myWl.measure(s, s.lowercase()) shouldBe myWl.measure(s, s.uppercase())
            }
        }
        "lowest score for the same strings" {
            forAll<String> { s -> myWl.measure(s, s) shouldBe 0 }
        }
        "input order does not matter" {
            forAll<String, String> { s1, s2 -> myWl.measure(s1, s2) shouldBe myWl.measure(s2, s1) }
        }
    })
