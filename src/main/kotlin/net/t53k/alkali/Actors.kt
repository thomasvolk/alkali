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
data class DeadLetter(val message: Any)

interface ActorFactory {
    fun <T> actor(name: String, actorClass: KClass<T>): ActorReference where T : Actor = actor(name, actorClass.java)

    fun <T> actor(name: String, actorClass: Class<T>): ActorReference where T : Actor = actor(name, actorClass.newInstance())

    fun <T> actor(name: String, actor: T): ActorReference where T : Actor
}

internal class NameSpace(val name: String) {
    companion object {
        val system = NameSpace("_system")
    }
    fun name(actorName: String) = "$name/$actorName"
    fun hasNameSpace(actorName: String) = actorName.startsWith(name)
}

class ActorSystem(mainHandler: (Any) -> Unit = {}, deadLetterHandler: (Any) -> Unit = {}): ActorFactory {
    private class MainActor(val mainHandler: (Any) -> Unit): Actor() {
        override fun receive(message: Any) {
            mainHandler(message)
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
    private var _mainActor: ActorReference
    private var _deadLetterActor: ActorReference
    private val MAIN_ACTOR_NAME = NameSpace.system.name("main")
    private val DEAD_LETTER_ACTOR_NAME = NameSpace.system.name("deadLetter")

    init {
        _mainActor = _start(MAIN_ACTOR_NAME, MainActor(mainHandler))
        currentActor(_mainActor)
        _deadLetterActor = _start(DEAD_LETTER_ACTOR_NAME, DeadLetterActor(deadLetterHandler))
    }

    @Synchronized
    override fun <T> actor(name: String, actor: T): ActorReference where T : Actor {
        if(NameSpace.system.hasNameSpace(name)) {
            throw IllegalArgumentException("actor name can not start with '${NameSpace.system.name}' !")
        }
        return _start(name, actor)
    }

    @Synchronized
    private fun <T> _start(name: String, actor: T): ActorReference where T : Actor {
        if (_actors.contains(name)) {
            throw IllegalArgumentException("actor '$name' already exists")
        }
        passIfActive()
        val actorRef = actor.start(name, this)
        _actors.put(name, ActorWrapper(actorRef, actor))
        return actorRef
    }

    @Synchronized
    fun find(name: String): ActorReference? = _actors[name]?.reference

    fun currentActor(): ActorReference = _currentActor.get()

    internal fun currentActor(actor: ActorReference) {
        _currentActor.set(actor)
    }

    fun waitForShutdown() {
        if(currentActor().name != MAIN_ACTOR_NAME) { throw IllegalStateException("an actor from the same system can not wait system shutdown")}
        _actors.forEach { it.value.waitForShutdown() }
    }

    @Synchronized
    fun shutdown() {
        passIfActive()
        _actors.forEach { it.value.reference.send(PoisonPill) }
        _active = false
    }

    private fun passIfActive() {
        if(!isActive()) { throw IllegalStateException("ActorSystem is not active!") }
    }

    fun isActive() = _active

    internal fun deadLetter(message: Any) {
        if(message !is DeadLetter) {
            _deadLetterActor send DeadLetter(message)
        }
    }

}

data class ActorMessageWrapper(val message: Any, val sender: ActorReference)

class ActorReference(private val system: ActorSystem, private val actor: Actor, val name: String) {

    infix fun send(message: Any) {
        send(message, system.currentActor())
    }

    fun send(message: Any, sender: ActorReference) {
        actor.send(message, sender)
    }

    infix fun watch(actorToWatchAt: ActorReference) {
        actorToWatchAt.send(Watch)
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
    private lateinit var _system: ActorSystem
    private lateinit var _sender: ActorReference
    private lateinit var _thread: Thread
    private val _watchers = mutableSetOf<ActorReference>()

    override fun <T : Actor> actor(name: String, actor: T): ActorReference {
        val actorRef = system().actor(name, actor)
        self() watch actorRef
        return actorRef
    }

    @Synchronized
    internal fun start(name: String, system: ActorSystem): ActorReference {
        if(_running) { throw IllegalStateException("actor already started!") }
        _running = true
        _self = ActorReference(system, this, name)
        _system = system
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
        return _self
    }

    internal fun waitForShutdown() { _thread.join() }

    internal fun send(message: Any, sender: ActorReference) {
        if(_running) {
            _inbox.offer(ActorMessageWrapper(message, sender))
        } else {
            system().deadLetter(message)
        }
    }

    private fun mainLoop() {
        while (_running) {
            val (message, sender) = _inbox.take()
            _sender = sender
            when (message) {
                is Forward -> _receive(message.message)
                PoisonPill -> stop()
                Watch -> _watchers += sender()
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

    protected fun system() = _system

    protected fun sender() = _sender

    protected fun self() =  _self

    protected abstract fun receive(message: Any)

    open protected fun after() {
    }

    open protected fun before() {
    }

    open protected fun onException(e: Exception) {
        throw e
    }
}

