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

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

object PoisonPill
object Terminated
object Watch
data class Forward(val message: Any)
data class DeadLetter(val message: Any)

internal class AskingActor(private val returnChannel: LinkedBlockingQueue<Any>, private val target: ActorReference, val message: Any): Actor() {
    override fun receive(message: Any) = returnChannel.put(message)
    override fun before() {
        target send message
    }
}

class AskTimeoutException(msg: String): RuntimeException(msg)

internal class NameSpace(val name: String) {
    companion object {
        val system = NameSpace("_system")
    }
    fun name(actorName: String) = "$name/$actorName"
    fun hasNameSpace(actorName: String) = actorName.startsWith(name)
}

class ActorSystemBuilder {
    private var defaultActorHandler: ActorSystem.(Any) -> Unit = {}
    private var deadLetterHandler: (Any) -> Unit = {}
    fun onDefaultActorMessage(defaultActorHandler: ActorSystem.(Any) -> Unit): ActorSystemBuilder {
        this.defaultActorHandler = defaultActorHandler
        return this
    }
    fun onDeadLetterMessage(deadLetterHandler: (Any) -> Unit): ActorSystemBuilder {
        this.deadLetterHandler = deadLetterHandler
        return this
    }
    fun build(): ActorSystem = ActorSystem(defaultActorHandler, deadLetterHandler)
}

class ActorSystem(defaultActorHandler: ActorSystem.(Any) -> Unit = {}, deadLetterHandler: (Any) -> Unit = {}): ActorFactory {
    private class DefaultActor(val defaultActorHandler: ActorSystem.(Any) -> Unit): Actor() {
        override fun receive(message: Any) {
            defaultActorHandler(system(), message)
        }
    }
    private class DeadLetterActor(val deadLetterHandler: (Any) -> Unit): Actor()  {
        override fun receive(message: Any) {
            deadLetterHandler((message as DeadLetter).message)
        }

    }
    private data class ActorWrapper(val reference: ActorReference, private val actor: Actor){
        fun waitForShutdown() {
            actor.waitForShutdown()
        }
    }
    private val _actors = mutableMapOf<String, ActorWrapper>()
    private val _currentActor = ThreadLocal<ActorReference>()
    private var _active = true
    private var _deadLetterActor: ActorWrapper
    private val MAIN_ACTOR_NAME = NameSpace.system.name("main")
    private val DEAD_LETTER_ACTOR_NAME = NameSpace.system.name("deadLetter")
    private val WAIT_FOR_SHUTDOWN_INTERVALL = 10L


    init {
        currentActor(_actor(MAIN_ACTOR_NAME, DefaultActor(defaultActorHandler)))
        val deadLetterActor = DeadLetterActor(deadLetterHandler)
        _deadLetterActor = ActorWrapper(_start(DEAD_LETTER_ACTOR_NAME, deadLetterActor), deadLetterActor)
    }

    @Synchronized
    override fun <T> actor(name: String, actor: T): ActorReference where T : Actor {
        if(NameSpace.system.hasNameSpace(name)) {
            throw IllegalArgumentException("actor name can not start with '${NameSpace.system.name}' !")
        }
        return _actor(name, actor)
    }

    @Synchronized
    private fun <T> _actor(name: String, actor: T): ActorReference where T : Actor {
        if (_actors.contains(name)) {
            throw IllegalArgumentException("actor '$name' already exists")
        }
        val actorRef = _start(name, actor)
        _actors.put(name, ActorWrapper(actorRef, actor))
        return actorRef
    }

    private fun <T> _start(name: String, actor: T): ActorReference where T : Actor {
        passIfActive()
        return actor.start(name, this)
    }

    @Synchronized
    internal fun <T> actor(actor: T): ActorReference where T : Actor {
        return _start(NameSpace.system.name("anonymous"), actor)
    }

    @Synchronized
    fun find(name: String): ActorReference? = _actors[name]?.reference

    internal fun ask(target: ActorReference, message: Any, timeout: Long): Any {
        val returnChannel = LinkedBlockingQueue<Any>()
        val askingActor = actor(AskingActor(returnChannel, target, message))
        try {
            return returnChannel.poll(timeout, TimeUnit.MILLISECONDS) ?: throw AskTimeoutException("timeout $timeout ms reached!")
        }
        finally {
            askingActor send PoisonPill
        }
    }

    fun currentActor(): ActorReference = _currentActor.get()

    internal fun currentActor(actor: ActorReference) {
        _currentActor.set(actor)
    }

    fun waitForShutdown() {
        if(currentActor().name != MAIN_ACTOR_NAME) { throw IllegalStateException("an actor from the same system can not wait system shutdown")}
        while (isActive()) {
            Thread.sleep(WAIT_FOR_SHUTDOWN_INTERVALL)
        }
        _actors.forEach { it.value.waitForShutdown() }
        _deadLetterActor.waitForShutdown()
    }

    @Synchronized
    fun shutdown() {
        passIfActive()
        _actors.forEach { it.value.reference send PoisonPill }
        _deadLetterActor.reference send PoisonPill
        _active = false
    }

    private fun passIfActive() {
        if(!isActive()) { throw IllegalStateException("ActorSystem is not active!") }
    }

    fun isActive() = _active

    internal fun deadLetter(message: Any) {
        if(message !is DeadLetter) {
            _deadLetterActor.reference send DeadLetter(message)
        }
    }
}


