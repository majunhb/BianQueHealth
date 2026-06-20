package com.bianque.health.base.common

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val code: Int = -1, val message: String) : Result<Nothing>()
    data object Loading : Result<Nothing>()
}