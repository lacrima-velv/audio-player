package com.lacrima.audioplayer.generalutils

open class Event<out T>(private val data: T) {

    var hasBeenHandled = false
        private set

    fun getContentIfNotHandled(): T? {
        return if (hasBeenHandled) {
            null
        } else {
            hasBeenHandled = true
            data
        }
    }
    // Could be used to get the data from an event
    fun peekContent() = data
}