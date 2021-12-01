package com.lacrima.audioplayer.generalutils

data class Resource<out T>(val status: Status, val data: T?, val message: String?) {

    companion object {

        fun <T> notStarted(message: String, data: T?) = Resource(Status.NOT_STARTED, data, message)

        fun <T> success(data: T?) = Resource(Status.SUCCESS, data, null)

        fun <T> error(message: String, data: T?) = Resource(Status.ERROR, data, message)

        fun <T> loading(data: T?) = Resource(Status.LOADING, data, null)
    }
}

enum class Status {
    NOT_STARTED,
    SUCCESS,
    ERROR,
    LOADING
}