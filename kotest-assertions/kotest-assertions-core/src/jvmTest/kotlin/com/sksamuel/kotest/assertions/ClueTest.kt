package com.sksamuel.kotest.assertions

import io.kotest.assertions.asClue
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.Matcher
import io.kotest.matchers.MatcherResult
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.util.*

class ClueTest : FreeSpec({

   "withClue()" - {
      fun withClueEcho(other: String) = object : Matcher<String> {
         override fun test(value: String) = MatcherResult(
            false,
            { "Should have the details of '$value' and $other" },
            {
               "Should have the details of '$value' and $other"
            })
      }

      "should prepend clue to message with a newline" {
         val ex = shouldThrow<AssertionError> {
            withClue("a clue:") { "1" shouldBe withClueEcho("here are the details!") }
         }
         ex.message shouldBe "a clue:\nShould have the details of '1' and here are the details!"
      }

      "should add clues correctly with multiple/softAssert" {
         val ex = shouldThrow<AssertionError> {
            withClue("outer clue:") {
               assertSoftly {
                  "1" shouldBe withClueEcho("the details!")
                  withClue("inner clue:") { "2" shouldBe "1" }
               }
            }
         }
         ex.message.apply {
            shouldContain("outer clue:\nShould have the details of '1' and the details!")
            shouldContain("inner clue:\nexpected:<\"1\"> but was:<\"2\">")
         }
      }

      "should show all available nested clue contexts" {
         withClue("clue outer:") {
            shouldThrow<AssertionError> { "1" shouldBe "2" }.message shouldBe "clue outer:\nexpected:<\"2\"> but was:<\"1\">"
            withClue("clue inner:") {
               shouldThrow<AssertionError> { "3" shouldBe "4" }.message shouldBe "clue outer:\nclue inner:\nexpected:<\"4\"> but was:<\"3\">"
            }
            shouldThrow<AssertionError> { "5" shouldBe "6" }.message shouldBe "clue outer:\nexpected:<\"6\"> but was:<\"5\">"
         }
         //And resets completely when leaving final clue block
         shouldThrow<AssertionError> { "7" shouldBe "8" }.message shouldBe "expected:<\"8\"> but was:<\"7\">"
      }

      "should only invoke the lazy if an assertion fails" {
         val expected = UUID.randomUUID()
         var state: String? = null
         val clue: Lazy<UUID> = lazy {
            expected.also { state = it.toString() }
         }

         state.shouldBeNull()

         withClue(clue) {
            1 + 1 shouldBe 2
         }

         state.shouldBeNull()

         withClue(clue) {
            shouldThrow<AssertionError> { "1" shouldBe "2" }.message shouldStartWith expected.toString()
         }

         state shouldBe expected.toString()
      }

      "clue can be nullable" {
         val ex = shouldThrow<AssertionError> {
            withClue(null) { 1 shouldBe 2 }
         }
         ex.message shouldBe "null\nexpected:<2> but was:<1>"
      }
   }
   "asClue()" - {
      "should prepend clue to message with a newline" {
         val ex = shouldThrow<AssertionError> {
            "a clue:".asClue { "1" shouldBe "2" }
         }
         ex.message shouldBe "a clue:\nexpected:<\"2\"> but was:<\"1\">"
      }

      "should add clues correctly with multiple/softAssert" {
         val ex = shouldThrow<AssertionError> {
            "outer clue:".asClue {
               assertSoftly {
                  "1" shouldBe "the details"
                  "inner clue:".asClue { "2" shouldBe "1" }
               }
            }
         }
         ex.message.apply {
            shouldContain("outer clue:\nexpected:<\"the details\"> but was:<\"1\">")
            shouldContain("outer clue:\ninner clue:\nexpected:<\"1\"> but was:<\"2\">")
         }
      }

      "should show all available nested clue contexts" {
         data class MyData(val a: Int, val b: String)
         MyData(10, "clue object").asClue {
            shouldThrow<AssertionError> { it.b shouldBe "2" }.message shouldBe """MyData(a=10, b=clue object)
|expected:<"2"> but was:<"clue object">""".trimMargin()
         }

         data class HttpResponse(val status: Int, val body: String)
         val response = HttpResponse(404, "not found")
         response.asClue {
            shouldThrow<AssertionError> {it.status shouldBe 200}.message shouldBe "HttpResponse(status=404, body=not found)\nexpected:<200> but was:<404>"
            MyData(20, "nest it").asClue { inner ->
               shouldThrow<AssertionError> {it.status shouldBe 200}.message shouldBe "HttpResponse(status=404, body=not found)\nMyData(a=20, b=nest it)\nexpected:<200> but was:<404>"
               shouldThrow<AssertionError> {inner.a shouldBe 10}.message shouldBe "HttpResponse(status=404, body=not found)\nMyData(a=20, b=nest it)\nexpected:<10> but was:<20>"
            }
            //after nesting, everything looks as before
            shouldThrow<AssertionError> { it.status shouldBe 200 }.message shouldBe "HttpResponse(status=404, body=not found)\nexpected:<200> but was:<404>"
         }
      }

      "clue can be nullable" {
         val ex = shouldThrow<AssertionError> {
            null.asClue { 1 shouldBe 2 }
         }
         ex.message shouldBe "null\nexpected:<2> but was:<1>"
      }

      "clue should work for withTimeout" {
         shouldThrow<AssertionError> {
            withClue("timey timey") {
               withTimeout(2) {
                  delay(1000)
               }
            }
         }.message shouldBe "timey timey\nTimed out waiting for 2 ms"
      }

      "clue should work where expected or actual is null" {
         shouldThrow<AssertionError> {
            withClue("A expected is null value") {
               "hello" shouldBe null
            }
         }.message shouldBe "A expected is null value\nExpected null but actual was \"hello\""
         shouldThrow<AssertionError> {
            withClue("A actual is null value") {
               null shouldBe "hello"
            }
         }.message shouldBe "A actual is null value\nExpected \"hello\" but actual was null"
      }
   }

})
