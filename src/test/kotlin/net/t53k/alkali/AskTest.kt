package net.t53k.alkali

import org.junit.Assert
import org.junit.Test


class AskTest {
    class AnswerActor : Actor() {
        override fun receive(message: Any) {
            sender() send "ECHO::$message"
        }
    }

    class NoAnswerActor : Actor() {
        override fun receive(message: Any) {}
    }

    private fun test(test: (ActorSystem) -> Unit) {
        val system = ActorSystemBuilder().build()
        try {
            test(system)
        }
        finally {
            system.shutdown()
            system.waitForShutdown()
        }
    }

    @Test
    fun ask() {
        test { system ->
            val actor = system.actor("answer", AnswerActor::class)
            val answer = actor.ask("hello", 1000)
            Assert.assertEquals("ECHO::hello", answer)
        }
    }

    @Test(expected = AskTimeoutException::class)
    fun askTimeout() {
        test { system ->
            val actor = system.actor("answer", NoAnswerActor::class)
            val answer = actor.ask("hello", 100)
            Assert.assertEquals("ECHO::hello", answer)
        }
    }

}