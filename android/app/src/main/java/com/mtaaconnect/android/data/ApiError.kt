package com.mtaaconnect.android.data

import com.google.gson.Gson
import retrofit2.HttpException
import java.io.IOException

class ApiFailure(
    val statusCode: Int? = null,
    override val message: String,
) : Exception(message)

fun Throwable.toUserMessage(): String = when (this) {
    is ApiFailure -> message
    is HttpException -> {
        val body = try {
            response()?.errorBody()?.string()
        } catch (_: IOException) {
            null
        }
        val backendMessage = body?.let {
            try {
                Gson().fromJson(it, ErrorResponse::class.java)?.error
            } catch (_: Exception) {
                null
            }
        }
        when (code()) {
            400 -> backendMessage ?: "Please check the information you entered."
            401 -> "Your session expired or your credentials are incorrect. Please sign in again."
            403 -> "Your account does not have permission for this action."
            404 -> "That item could not be found."
            409 -> backendMessage ?: "That action conflicts with the current booking or listing state."
            else -> backendMessage ?: "The server returned an error (${code()})."
        }
    }
    is IOException -> "Could not reach MtaaConnect. Check your connection and backend URL."
    else -> message ?: "Something went wrong. Please try again."
}
