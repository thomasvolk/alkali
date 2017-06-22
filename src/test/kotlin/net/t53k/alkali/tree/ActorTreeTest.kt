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
package net.t53k.alkali.tree

import net.t53k.alkali.Actor
import net.t53k.alkali.ActorReference
import net.t53k.alkali.test.actorTest
import org.junit.Assert
import org.junit.Test

data class Init(val maxLevel: Long, val leafes: Int) {
    fun totalCount() = (0..maxLevel).map { Math.pow(leafes.toDouble(), it.toDouble()) }.sum().toInt()
}
data class Start(val path: String, val level: Long, val maxLevel: Long, val leafes: Int) {
    fun maxLevelReached() = level >= maxLevel
    fun leaf(leafNumber: Int) = Start("$path-$leafNumber", level + 1, maxLevel, leafes)
}
data class Created(val name: String)
data class TreeCreated(val leafeCount: Long)
data class Question(val message: String)
data class Answer(val question: Question, val message: String)

class Tree : Actor() {
    private lateinit var root: ActorReference
    private var count: Long = 0
    private lateinit var start: Init
    private lateinit var starter: ActorReference

    override fun receive(message: Any) {
        when(message) {
            is Init -> {
                root = actor("Test-R", Node::class)
                root send Start("R", 0, message.maxLevel, message.leafes)
                start = message
                starter = sender()
            }
            is Created -> {
                count++
                if(count >= start.totalCount()) {
                    starter send TreeCreated(count)
                }
            }
            is Question -> root send message
            is Answer -> starter send message
        }
    }
}

class Node : Actor() {
    private lateinit var parent: ActorReference
    private val children = mutableListOf<ActorReference>()
    override fun receive(message: Any) {
        when(message) {
            is Start -> {
                parent = sender()
                if(!message.maxLevelReached()) {
                    for(i in 0..(message.leafes - 1)) {
                        val leafMsg = message.leaf(i)
                        val n = actor("Test-${leafMsg.path}", Node::class)
                        n send leafMsg
                        children += n
                    }
                }
                sender() send Created(self().name)
            }
            is Created -> parent send message
            is Question -> {
                children.forEach { it send message }
                self() send Answer(message, "Answer to: ${message.message}")
            }
            is Answer -> parent send message
        }
    }
}

class ActorTreeTest {
    @Test
    fun tree() {
        actorTest {
            val tree = testSystem().actor("tree", Tree::class)
            tree send Init(3, 2)
            onMessage { msg ->
                when(msg) {
                    is TreeCreated -> Assert.assertEquals(15,  msg.leafeCount)
                    else -> Assert.fail("wrong message: $msg")
                }
            }
        }
    }
}
