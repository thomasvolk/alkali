package net.t53k.alkali

import net.t53k.alkali.test.actorTestBuilder
import org.junit.Assert.*
import org.junit.Test

class ActorReferenceTest {
    class DummyActor : Actor() {
        override fun receive(message: Any) {
        }
    }

    @Test
    fun compare() {
        val system = ActorSystem()
        try {
            val test1 = system.actor("test1", DummyActor::class)
            val test1Clone = ActorReference(system, DummyActor(), "test1")
            assertEquals("ActorReference(actor='test1')", test1.toString())
            assertTrue(test1 == test1)
            assertTrue(test1 == test1Clone)
            val test2 = system.actor("test2", DummyActor::class)
            assertFalse(test1 == test2)
        } finally {
            system.shutdown()
            system.waitForShutdown()
        }
    }
}