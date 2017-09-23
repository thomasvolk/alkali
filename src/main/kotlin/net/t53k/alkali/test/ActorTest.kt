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
package net.t53k.alkali.test

import net.t53k.alkali.Actor
import net.t53k.alkali.ActorReference
import net.t53k.alkali.ActorSystem
import net.t53k.alkali.ActorSystemBuilder
import kotlin.concurrent.thread

fun actorTestBuilder() = ActorTestBuilder()

fun actorTest(test: ActorTest.TestRunActor.(ActorReference) -> Unit) {
    actorTestBuilder().test(test).build().run()
}

private val TEST_LOOP_DELAY_MS: Long = 1

fun ActorSystem.waitForShutdown(timeout: Long) {
    var doWait = true
    thread(start = true) {
        Thread.sleep(timeout)
        doWait = false
    }
    while(doWait && isActive()) {
        Thread.sleep(TEST_LOOP_DELAY_MS)
    }
    if (isActive()) {
        shutdown()
        waitForShutdown()
        throw RuntimeException("timeout $timeout reached!")
    }
}

class ActorTestBuilder {
    private lateinit var _test: (ActorTest.TestRunActor.(ActorReference) -> Unit)
    private var _timeout: Long = 2000
    private var deadletterHandler: (Any) -> Unit = {}
    private var mainHandler: ActorSystem.(Any) -> Unit = {}

    fun test(test: ActorTest.TestRunActor.(ActorReference) -> Unit): ActorTestBuilder {
        _test = test
        return this
    }

    fun timeout(timeout: Long): ActorTestBuilder {
        _timeout = timeout
        return this
    }

    fun deadLetterHandler(handler: (Any) -> Unit): ActorTestBuilder {
        deadletterHandler = handler
        return this
    }

    fun mainHandler(handler: ActorSystem.(Any) -> Unit): ActorTestBuilder {
        mainHandler = handler
        return this
    }

    fun build(): ActorTest {
        return ActorTest(_test, _timeout, mainHandler, deadletterHandler)
    }
}

class ActorTest(val test: TestRunActor.(ActorReference) -> Unit, val timeout: Long, val mainHandler: ActorSystem.(Any) -> Unit, val deadletterHandler: (Any) -> Unit) {
    class TestRunActor(val test: TestRunActor.(ActorReference) -> Unit): Actor() {
        private var messageHandler: (Any) -> (Unit) = { }

        override fun before() {
            test(self())
        }

        override fun receive(message: Any) {
            messageHandler(message)
            system().shutdown()
        }

        fun testSystem() = system()

        infix fun onMessage(messageHandler: (Any) -> (Unit)) {
            this.messageHandler = messageHandler
        }
    }

    fun run() {
        val system = ActorSystemBuilder().onDefaultActorMessage(mainHandler).onDeadLetterMessage(deadletterHandler).build()
        try {
            system.actor(this.toString(), TestRunActor(test))
        } finally {
            system.waitForShutdown(timeout)
        }
    }
}
