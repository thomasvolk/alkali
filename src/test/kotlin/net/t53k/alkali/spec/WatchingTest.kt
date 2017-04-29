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
import net.t53k.alkali.PoisonPill
import net.t53k.alkali.Terminated
import net.t53k.alkali.actors.Reaper
import net.t53k.alkali.router.Broadcast
import net.t53k.alkali.router.RoundRobinRouter
import net.t53k.alkali.test.actorTest
import org.junit.Test

class WatchingTest {
    class DummyActor : Actor() {
        override fun receive(message: Any) {
        }
    }

    @Test
    fun reaper() {
        (1..50).forEach {
            actorTest { testActor ->
                testSystem().actor("reaper", Reaper( {
                    val actors = (1..it).map { actor("d$it", DummyActor::class) }
                    val router = testSystem().actor("router", RoundRobinRouter(actors))
                    router send Broadcast(PoisonPill)
                }))
            }
        }
    }

}