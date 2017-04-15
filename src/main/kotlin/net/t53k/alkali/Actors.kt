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
import kotlin.concurrent.thread
import kotlin.reflect.KClass

object PoisonPill

class ActorSystem {
    private val _actors = mutableMapOf<String, ThreadActorReference>()
    private val _currentActor = ThreadLocal<ActorReference>()

    fun <T> actor(name: String, actorClass: KClass<T>): ActorReference where T : Actor = actor(name, actorClass.java)

    fun <T> actor(name: String, actorClass: Class<T>): ActorReference where T : Actor = actor(name, actorClass.newInstance())

    @Synchronized
    fun <T> actor(name: String, actor: T): ActorReference where T : Actor {
        if (_actors.contains(name)) {
            throw IllegalArgumentException("actor '$name' already exists")
        }
        val actorRef = actor.start(this)
        _actors.put(name, actorRef)
        return actorRef
    }

    fun get(name: String) = _actors[name]

    fun current(): ActorReference? = _currentActor.get()

    fun current(actor: ActorReference) {
        _currentActor.set(actor)
    }

    fun waitForShutdown() {
        _actors.forEach { it.value.waitForShutdown() }
    }
}

data class ActorMessageWrapper(val message: Any, val sender: ActorReference?)

interface ActorReference {
    fun send(message: Any)
}

class ThreadActorReference(val system: ActorSystem, private val actor: Actor): ActorReference {
    override fun send(message: Any) {
        actor.send(message, system.current())
    }

    fun waitForShutdown() {
        actor.waitForShutdown()
    }
}

abstract class Actor {
    private val _inbox = LinkedBlockingQueue<ActorMessageWrapper>()
    private var _running = true
    private var _self: ThreadActorReference? = null
    private var _sender: ActorReference? = null
    private var _thread: Thread? = null

    @Synchronized
    internal fun start(system: ActorSystem): ThreadActorReference {
        if(_self != null) throw IllegalStateException("actor already started!")
        _self = ThreadActorReference(system, this)
        _thread = thread(start = true) {
            mainLoop()
        }
        return _self!!
    }

    internal fun waitForShutdown() { _thread?.join() }

    internal fun send(message: Any, sender: ActorReference?) {
        _inbox.offer(ActorMessageWrapper(message, sender))
    }

    private fun mainLoop() {
        system().current(self())
        while (_running) {
            val (message, sender) = _inbox.take()
            _sender = sender
            when (message) {
                PoisonPill -> stop()
                else -> receive(message)
            }
        }
    }

    protected fun stop() {
        _running = false
    }

    protected fun system() = self().system

    protected fun sender() = _sender

    protected fun self() =  _self!!

    protected abstract fun receive(message: Any)
}

