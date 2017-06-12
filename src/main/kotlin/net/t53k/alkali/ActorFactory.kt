package net.t53k.alkali

import kotlin.reflect.KClass

interface ActorFactory {
    fun <T> actor(name: String, actorClass: KClass<T>): ActorReference where T : Actor = actor(name, actorClass.java)

    fun <T> actor(name: String, actorClass: Class<T>): ActorReference where T : Actor = actor(name, actorClass.newInstance())

    fun <T> actor(name: String, actor: T): ActorReference where T : Actor
}