package sanctuary.app.shared.domain.usecase

sealed class UsecaseResult<out S, out F> {
    data class Success<S>(val data: S) : UsecaseResult<S, Nothing>()
    data class Failure<F>(val error: F) : UsecaseResult<Nothing, F>()
}

inline fun <S, F, R> UsecaseResult<S, F>.fold(
    onSuccess: (S) -> R,
    onFailure: (F) -> R,
): R = when (this) {
    is UsecaseResult.Success -> onSuccess(data)
    is UsecaseResult.Failure -> onFailure(error)
}

inline fun <S, F> UsecaseResult<S, F>.onSuccess(block: (S) -> Unit): UsecaseResult<S, F> {
    if (this is UsecaseResult.Success) block(data)
    return this
}

inline fun <S, F> UsecaseResult<S, F>.onFailure(block: (F) -> Unit): UsecaseResult<S, F> {
    if (this is UsecaseResult.Failure) block(error)
    return this
}
