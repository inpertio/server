package org.inpertio.server.util

class ProcessingResult<SUCCESS, FAILURE> private constructor(
    private val _successValue: SUCCESS?,
    private val _failureValue: FAILURE?
) {

    val success: Boolean
        get() = _failureValue == null

    @Suppress("UNCHECKED_CAST")
    val successValue: SUCCESS
        get() = if (success) {
            _successValue as SUCCESS
        } else {
            throw IllegalStateException("Can't get a success value from a failed result")
        }

    @Suppress("UNCHECKED_CAST")
    val failureValue: FAILURE
        get() = if (success) {
            throw IllegalStateException("Can't get a failure value from a successful result")
        } else {
            _failureValue as FAILURE
        }

    override fun toString(): String {
        return if (success) {
            "success: $_successValue"
        } else {
            "failure: $_failureValue"
        }
    }

    companion object {

        fun <S : Any, F> success(value: S): ProcessingResult<S, F> {
            return ProcessingResult(value, null)
        }

        fun <S, F : Any> failure(value: F): ProcessingResult<S, F> {
            return ProcessingResult(null, value)
        }
    }
}