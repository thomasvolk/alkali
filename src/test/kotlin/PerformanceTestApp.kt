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
import net.t53k.alkali.Actor
import net.t53k.alkali.ActorSystemBuilder
import net.t53k.alkali.tree.*

class TreeController(val levels: Long, val leafs: Int, val questionCount: Long): Actor() {
    private var answerReceived = 0L
    private val startTime = System.currentTimeMillis()

    override fun before() {
        val tree = system().actor("tree", Tree::class)
        tree send Init(levels, leafs)
    }

    override fun receive(message: Any) {
        when(message) {
            is TreeCreated -> {
                println("tree=$message, questions=$questionCount, expectedAnswers=${message.leafeCount * questionCount}")
                for(i in 1..questionCount) {
                    sender() send Question("question[$i]")
                }
            }
            is Answer -> {
                answerReceived++
            }
        }
    }

    override fun after() {
        val duration = System.currentTimeMillis() - startTime
        println("answers received: $answerReceived in $duration ms")
    }
}

fun main(args: Array<String>) {
    println("=== PerformanceTestApp::start ===")
    val system = ActorSystemBuilder().build()
    system.actor("treeController", TreeController(args[0].toLong(), args[1].toInt(), args[2].toLong()))
    Thread.sleep(args[3].toLong())
    system.shutdown()
    system.waitForShutdown()
    println("=== PerformanceTestApp::done ===")
}
