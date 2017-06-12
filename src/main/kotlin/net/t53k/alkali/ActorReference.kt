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