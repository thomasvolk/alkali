Alkali
======

Alkali is a small and simple actor library written in Kotlin.

Install
-------


```
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    compile 'com.github.thomasvolk:alkali:RELEASE_1_0_1-SNAPSHOT'
}
```


API
---

```kotlin
class HelloWorld : Actor() {
    override fun receive(message: Any) {
        println("Hello $message")
    }
}

val system = ActorSystemBuilder().build()
val actor = system.actor("helloWorld", HelloWorld::class)
actor send "World"
```
