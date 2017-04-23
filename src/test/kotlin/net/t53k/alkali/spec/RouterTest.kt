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
package net.t53k.alkali.spec

import net.t53k.alkali.Actor
import net.t53k.alkali.ActorReference
import net.t53k.alkali.router.Broadcast
import net.t53k.alkali.router.RoundRobinRouter
import net.t53k.alkali.test.actorTest
import org.junit.Assert
import org.junit.Test

class RouterTest {
    class Worker: Actor() {
        object Stop
        override fun receive(message: Any) {
            system().find("aggregator")!! send message
        }
    }

    class Aggregator(val workerCount: Int): Actor() {
        object Register
        private lateinit var _receiver: ActorReference
        private val _messages = mutableListOf<String>()
        private var _stopCount = 0
        override fun receive(message: Any) {
            when(message) {
                Register -> _receiver = sender()!!
                is Int -> {
                    val name = sender()!!.name
                    val num = String.format("%02d", message)
                    _messages.add("$name-$num")
                }
                Worker.Stop -> {
                    _stopCount += 1
                    if(_stopCount == workerCount) {
                        _messages.sort()
                        _receiver send _messages.joinToString(separator = "#")
                    }
                }
            }
        }

    }
    @Test
    fun routing() {
        (1..50).forEach { workerCount ->
            actorTest {
                val messageCount = 12
                val system = testSystem()
                val aggregator = system.actor("aggregator", Aggregator(workerCount))
                val router = system.actor("router", RoundRobinRouter(
                        (1..workerCount).map { system.actor(String.format("w%02d", it), Worker::class) }
                ));
                aggregator send Aggregator.Register
                for (i in 1..messageCount) {
                    router send i
                }
                router send Broadcast(Worker.Stop)
                val expected = expectedResult(messageCount, workerCount)

                onMessage { message ->
                    Assert.assertEquals(expected, message)
                }
            }
        }
    }

    private fun expectedResult(messageCount: Int, workerCount: Int): String {
        val expected = mutableListOf<String>()
        var worker = 1
        for (m in 1..messageCount) {
            expected.add(String.format("w%02d-%02d", worker, m))
            if (worker < workerCount) {
                worker += 1
            } else {
                worker = 1
            }
        }
        expected.sort()
        return expected.joinToString("#")
    }
}
