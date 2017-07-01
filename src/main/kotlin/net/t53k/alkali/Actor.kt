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

internal data class ActorMessageWrapper(val message: Any, val sender: ActorReference)

abstract class Actor: ActorFactory {
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
        _thread = thread {
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
            _inbox.put(ActorMessageWrapper(message, sender))
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