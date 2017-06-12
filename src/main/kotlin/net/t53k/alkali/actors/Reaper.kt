package net.t53k.alkali.actors

import net.t53k.alkali.Actor
import net.t53k.alkali.ActorReference
import net.t53k.alkali.Terminated

class Reaper(val starter: Reaper.() -> Unit): Actor() {
    private val actors = mutableListOf<ActorReference>()

    override fun <T : Actor> actor(name: String, actor: T): ActorReference {
        val actorRef = super.actor(name, actor)
        actors += actorRef
        return actorRef
    }

    override fun before() {
        starter()
    }
    override fun receive(message: Any) {
        when(message) {
            Terminated -> {
                actors -= sender()
                if(actors.size == 0) {
                    system().shutdown()
                }
            }
        }
    }
}