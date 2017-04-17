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

import net.t53k.alkali.router.Broadcast
import net.t53k.alkali.router.RoundRobinRouter
import net.t53k.alkali.test.actorTest
import org.junit.Test

class Worker: Actor() {
    object Stop
    override fun receive(message: Any) {
        system().get("aggregator")!!.send(message)
    }
}

class Aggregator(val workerCount: Int): Actor() {
    object Register
    private var _receiver: ActorReference? = null
    private val _messages = mutableListOf<String>()
    private var _stopCount = 0
    override fun receive(message: Any) {
       when(message) {
           Register -> _receiver = sender()
           is Int -> {
               val name = sender()!!.name()
               val num = String.format("%02d", message)
               _messages.add("$num-$name")
           }
           Worker.Stop -> {
               _stopCount += 1
               if(_stopCount == workerCount) {
                   _messages.sort()
                   _receiver!!.send(_messages.joinToString(separator = "#"))
               }
           }
       }
    }

}

class RouterTest {
    @Test
    fun routing() {
        actorTest {
            val WORKER_COUNT = 4
            val system = testSystem()
            val aggregator = system.actor("aggregator", Aggregator(WORKER_COUNT))
            val router = system.actor("router", RoundRobinRouter(listOf(
                    system.actor("w1", Worker::class),
                    system.actor("w2", Worker::class),
                    system.actor("w3", Worker::class),
                    system.actor("w4", Worker::class)
            )));
            aggregator.send(Aggregator.Register)
            for(i in 1..12) {
                router.send(i)
            }
            router.send(Broadcast(Worker.Stop))
            expectMessage("01-w1#02-w2#03-w3#04-w4#05-w1#06-w2#07-w3#08-w4#09-w1#10-w2#11-w3#12-w4")
        }
    }
}
