package sanctuary.app.shared.platform

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
