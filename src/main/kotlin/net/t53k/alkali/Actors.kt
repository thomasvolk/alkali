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
    private val _actors = mutableMapOf<String, ActorReference>()
    private val _currentActor = ThreadLocal<ActorReference>()
    private var _active = true

    fun <T> actor(name: String, actorClass: KClass<T>): ActorReference where T : Actor = actor(name, actorClass.java)

    fun <T> actor(name: String, actorClass: Class<T>): ActorReference where T : Actor = actor(name, actorClass.newInstance())

    @Synchronized
    fun <T> actor(name: String, actor: T): ActorReference where T : Actor {
        passIfActive()
        if (_actors.contains(name)) {
            throw IllegalArgumentException("actor '$name' already exists")
        }
        val actorRef = actor.start(name, this)
        _actors.put(name, actorRef)
        return actorRef
    }

    fun find(name: String) = _actors[name]

    fun currentActor(): ActorReference? = _currentActor.get()

    internal fun currentActor(actor: ActorReference) {
        _currentActor.set(actor)
    }

    fun waitForShutdown() {
        if(currentActor() != null) { throw IllegalStateException("an actor of this system can not wait for shutdown")}
        _actors.forEach { it.value.waitForShutdown() }
    }

    @Synchronized
    fun shutdown() {
        passIfActive()
        _actors.forEach { it.value.send(PoisonPill) }
        _active = false
    }

    private fun passIfActive() {
        if(!isActive()) { throw IllegalStateException("ActorSystem is not active!") }
    }

    fun isActive() = _active
}

data class ActorMessageWrapper(val message: Any, val sender: ActorReference?)

class ActorReference(val system: ActorSystem, private val actor: Actor, val name: String) {
    infix fun send(message: Any) {
        actor.send(message, system.currentActor())
    }

    fun waitForShutdown() {
        actor.waitForShutdown()
    }

    fun name() = name
}

abstract class Actor {
    private val _inbox = LinkedBlockingQueue<ActorMessageWrapper>()
    private var _running = true
    private var _self: ActorReference? = null
    private var _sender: ActorReference? = null
    private var _thread: Thread? = null

    @Synchronized
    internal fun start(name: String, system: ActorSystem): ActorReference {
        if(_self != null) throw IllegalStateException("actor already started!")
        _self = ActorReference(system, this, name)
        _thread = thread(start = true) {
            system().currentActor(self())
            before()
            try {
                mainLoop()
            } finally {
                after()
            }
        }
        return _self!!
    }

    open protected fun after() {
    }

    open protected fun before() {
    }

    internal fun waitForShutdown() { _thread?.join() }

    internal fun send(message: Any, sender: ActorReference?) {
        _inbox.offer(ActorMessageWrapper(message, sender))
    }

    private fun mainLoop() {
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

