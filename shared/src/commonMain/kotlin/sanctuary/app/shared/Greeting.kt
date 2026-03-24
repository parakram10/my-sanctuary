package sanctuary.app.shared

class Greeting {
    private val platform: Platform = getPlatform()

    fun greet(): String = "Sanctuary — running on ${platform.name}"
}
