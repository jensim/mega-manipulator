package com.github.jensim.megamanipulator

import com.intellij.AbstractBundle
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

@NonNls
private const val BUNDLE = "messages.MyBundle"

object MyBundle : AbstractBundle(BUNDLE) {

    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String) = getMessage(key)
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, param: Any) = getMessage(key, param)

    @Suppress("SpreadOperator")
    fun messagePointer(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any) =
        getLazyMessage(key, *params)
}
