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
import net.t53k.alkali.ActorSystem
import kotlin.concurrent.thread

fun actorTestBuilder() = ActorTestBuilder()

fun actorTest(test: ActorTest.TestRunActor.() -> Unit) {
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
    var _test: (ActorTest.TestRunActor.() -> Unit)? = null
    var _timeout: Long = 2000

    fun test(test: ActorTest.TestRunActor.() -> Unit): ActorTestBuilder {
        _test = test
        return this
    }

    fun timeout(timeout: Long): ActorTestBuilder {
        _timeout = timeout
        return this
    }

    fun build(): ActorTest {
        return ActorTest(_test!!, _timeout)
    }

}

class ActorTest(val test: TestRunActor.() -> Unit, val timeout: Long) {
    class TestRunActor(val test: TestRunActor.() -> Unit): Actor() {
        private var expectedMessage: Any? = null

        override fun before() {
            test()
        }

        override fun receive(message: Any) {
            if (expectedMessage != message) {
                throw RuntimeException("$expectedMessage != $message")
            }
            system().shutdown()
        }

        fun testSystem() = system()

        fun expectMessage(msg: Any) {
            expectedMessage = msg
        }
    }

    fun run() {
        val system = ActorSystem()
        try {
            system.actor(this.toString(), TestRunActor(test))
        } finally {
            system.waitForShutdown(timeout)
        }
    }
}