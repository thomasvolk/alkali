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
object Terminated
object Watch
data class Forward(val message: Any)

interface ActorFactory {
    fun <T> actor(name: String, actorClass: KClass<T>): ActorReference where T : Actor = actor(name, actorClass.java)

    fun <T> actor(name: String, actorClass: Class<T>): ActorReference where T : Actor = actor(name, actorClass.newInstance())

    fun <T> actor(name: String, actor: T): ActorReference where T : Actor
}

class ActorSystem: ActorFactory {
    private val SYSTEM_NAMESPACE = "_system"
    private val _actors = mutableMapOf<String, ActorReference>()
    private val _currentActor = ThreadLocal<ActorReference>()
    private var _active = true

    @Synchronized
    override fun <T> actor(name: String, actor: T): ActorReference where T : Actor {
        if(name.startsWith(SYSTEM_NAMESPACE)) {
            throw IllegalArgumentException("actor name can not start with '$SYSTEM_NAMESPACE' !")
        }
        return _actor(name, actor)
    }

    @Synchronized
    private fun <T> _actor(name: String, actor: T): ActorReference where T : Actor {
        passIfActive()
        if (_actors.contains(name)) {
            throw IllegalArgumentException("actor '$name' already exists")
        }
        val actorRef = ActorReference(this, actor, name)
        actor.start(actorRef)
        _actors.put(name, actorRef)
        return actorRef
    }

    @Synchronized
    fun find(name: String) = _actors[name]

    fun currentActor(): ActorReference? = _currentActor.get()

    internal fun currentActor(actor: ActorReference) {
        _currentActor.set(actor)
    }

    fun waitForShutdown() {
        if(currentActor() != null) { throw IllegalStateException("an actor from the same system can not wait system shutdown")}
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

class ActorReference(internal val system: ActorSystem, private val actor: Actor, val name: String) {

    infix fun send(message: Any) {
        send(message, system.currentActor())
    }

    fun send(message: Any, sender: ActorReference?) {
        actor.send(message, sender)
    }

    infix fun watch(actorToWatchAt: ActorReference) {
        actorToWatchAt.send(Watch)
    }

    internal fun waitForShutdown() {
        actor.waitForShutdown()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as ActorReference

        if (name != other.name) return false

        return true
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

    override fun toString(): String {
        return "ActorReference(actor='$name')"
    }
}

abstract class Actor: ActorFactory  {
    private val _inbox = LinkedBlockingQueue<ActorMessageWrapper>()
    private var _running = false
    private lateinit var _self: ActorReference
    private var _sender: ActorReference? = null
    private lateinit var _thread: Thread
    private val _watchers = mutableSetOf<ActorReference>()

    override fun <T : Actor> actor(name: String, actor: T): ActorReference {
        val actorRef = system().actor(name, actor)
        self() watch actorRef
        return actorRef
    }

    @Synchronized
    internal fun start(ref: ActorReference) {
        if(_running) { throw IllegalStateException("actor already started!") }
        _running = true
        _self = ref
        _thread = thread(start = true) {
            system().currentActor(self())
            before()
            try {
                mainLoop()
            } finally {
                after()
                _watchers.forEach { it send Terminated }
            }
        }
    }

    internal fun waitForShutdown() { _thread.join() }

    internal fun send(message: Any, sender: ActorReference?) {
        if(_thread.isAlive) {
            _inbox.offer(ActorMessageWrapper(message, sender))
        } else {
            deadLetter(message, sender)
        }
    }

    open protected fun deadLetter(message: Any, sender: ActorReference?) {
    }

    open protected fun after() {
    }

    open protected fun before() {
    }

    open protected fun onException(e: Exception) {
        throw e
    }

    private fun mainLoop() {
        while (_running) {
            val (message, sender) = _inbox.take()
            _sender = sender
            when (message) {
                is Forward -> _receive(message.message)
                PoisonPill -> stop()
                Watch -> sender()?.let { _watchers += it }
                else -> _receive(message)
            }
        }
    }

    private fun _receive(message: Any) {
        try {
            receive(message)
        } catch (e: Exception) {
            onException(e)
        }
    }

    protected fun stop() {
        _running = false
    }

    protected fun system() = self().system

    protected fun sender() = _sender

    protected fun self() =  _self

    protected abstract fun receive(message: Any)
}

