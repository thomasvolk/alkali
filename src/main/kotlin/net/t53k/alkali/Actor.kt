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