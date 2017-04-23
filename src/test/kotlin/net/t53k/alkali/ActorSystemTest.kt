/*
 * Copyright 2017 Thomas Volk
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package net.t53k.alkali

import net.t53k.alkali.test.actorTest
import net.t53k.alkali.test.actorTestBuilder
import org.junit.Test

class ActorSystemTest {
    class DummyActor : Actor() {
        override fun receive(message: Any) {
        }
    }

    @Test(expected = RuntimeException::class)
    fun actorTestTimeout() {
        actorTestBuilder().timeout(100).test {
            testSystem().actor("runForEver", DummyActor::class)
        }.build().run()
    }

    @Test(expected = IllegalArgumentException::class)
    fun actorsCannotHaveTheSameName() {
        val system = ActorSystem()
        try {
            system.actor("dummy", DummyActor::class)
            system.actor("dummy", DummyActor::class)
        } finally {
            system.shutdown()
            system.waitForShutdown()
        }
    }

    @Test(expected = IllegalStateException::class)
    fun inactiveSystemError() {
        val system = ActorSystem()
        system.shutdown()
        system.waitForShutdown()
        system.actor("dummy", DummyActor::class)
    }


    @Test
    fun actorCannotCallWaitForShutdown() {
        class CannotCallWaitForShutdownError: Actor() {
            override fun receive(message: Any) {
                system().waitForShutdown()
            }

            override fun onException(e: Exception) {
                system().shutdown()
            }
        }
        actorTest {
            val test = testSystem().actor("error", CannotCallWaitForShutdownError::class)
            test send "dummy"
        }
    }
}