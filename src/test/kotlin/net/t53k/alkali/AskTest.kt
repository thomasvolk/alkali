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