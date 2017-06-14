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

class ActorReference(private val system: ActorSystem, private val actor: Actor, val name: String) {

    fun ask(message: Any, timeout: Long): Any {
        return system.ask(this, message, timeout)
    }

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