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

import net.t53k.alkali.test.actorTestBuilder
import org.junit.Test

object Start
object Stop
object Ping
data class Pong(val id: Int)

class PingActor: Actor() {
    private var lastPongId = 0
    private var starter: ActorReference? = null
    override fun receive(message: Any) {
        when(message) {
            Start -> {
                system().get("pong")!!.send(Ping)
                starter = sender()
            }
            Stop -> {
                sender()?.send(PoisonPill)
                self().send(PoisonPill)
                starter!!.send(lastPongId)
            }
            is Pong -> {
                lastPongId = message.id
                sender()?.send(Ping)
            }
        }
    }
}

class PongActor: Actor() {
    private var count = 0
    override fun receive(message: Any) {
        when(message) {
            Ping -> {
                count++
                if(count < 100) sender()?.send(Pong(count))
                else sender()?.send(Stop)
            }
        }
    }
}


class PingPongTest {
    @Test
    fun pingPong() {
        actorTestBuilder().test {
            val ping = testSystem().actor("ping", PingActor::class)
            testSystem().actor("pong", PongActor::class)
            ping.send(Start)
            expectMessage(99)
        }.build().run()
    }
}