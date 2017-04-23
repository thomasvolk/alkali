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

class WatchingTest {
    class DummyActor : Actor() {
        override fun receive(message: Any) {
        }
    }
    class Reaper(val actors: Set<ActorReference>, val starter: (actors: Set<ActorReference>) -> Unit): Actor() {
        private val actorsLeft = actors.toMutableList()

        override fun before() {
            actors.forEach{ self() watch it }
            starter(actors)

        }
        override fun receive(message: Any) {
            when(message) {
                Terminated -> {
                    sender()?.let { actorsLeft.remove(it) }
                    if(actorsLeft.size == 0) {
                        system().shutdown()
                    }
                }
            }
        }
    }
    @Test
    fun reaper() {
        actorTest {
            val watchedActors = (1..10).map { testSystem().actor("d$it", DummyActor::class) }
            testSystem().actor("reaper", Reaper(watchedActors.toHashSet(), { actors ->
                val router = testSystem().actor("router", RoundRobinRouter(actors.toList()))
                router send Broadcast(PoisonPill)
            }))
        }
    }
}