package net.t53k.alkali

import net.t53k.alkali.actors.Reaper
import net.t53k.alkali.test.actorTest
import org.junit.Assert
import org.junit.Test

class ActorTest {
    companion object {
        val STOP_CMD = "Stop"
        val STOP_ANSWER = "STOP_ANSWER"
    }
    class EchoStop: Actor() {
        override fun receive(message: Any) {
            when(message) {
                STOP_CMD -> {
                    sender() send STOP_ANSWER
                    stop()
                }
                else -> sender() send message
            }
        }
    }
    class ErrorActor : Actor() {
        override fun receive(message: Any) {
            throw RuntimeException()
        }
    }

    @Test
    fun stopEchoTest() {
        actorTest {
            val actor = testSystem().actor("test", EchoStop())
            actor send STOP_CMD
            onMessage {
                Assert.assertEquals(STOP_ANSWER, it)
            }
        }
    }

    @Test
    fun errorTest() {
        actorTest {
            actor("reaper", Reaper({
                val actor = actor("test", ErrorActor())
                actor send "Hello"
            }))
        }
    }
}