package sample

private val prodConfig = ServerConfig(
    "https://api.feeba.io", "ru",
    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE3NjE4MDE5OTYsInBheWxvYWQiOnsidXNlcklkIjoiNjNkYzliMDFjN2IxNjYyZWE3MmQ3OTllIiwicHJvamVjdE5hbWUiOiJmZWViYSJ9LCJpYXQiOjE2NzU0MDE5OTZ9.VuIAQ90oQar5958nZHNKc6nOa8m3abzoFvGnk0cbpnY"
)
private val devConfig = ServerConfig(
    "https://def-api.feeba.io", "ru",
    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE3NjIxMTQ4NDQsInBheWxvYWQiOnsidXNlcklkIjoiNjNlMGIzNDQ1MzdkNzg2YWY0Yzc4OGMyIiwicHJvamVjdE5hbWUiOiJmZWViYSJ9LCJpYXQiOjE2NzU3MTQ4NDR9.crhC6URHc0NH5D3x-7GKgHLV8CNFeP4wpnzjbiiAgzI"
)

object ConfigHolder {
    private var currentConfig = prodConfig

    val hostUrl: String get() = currentConfig.hostUrl
    val langCode: String get() = currentConfig.langCode
    val jwtToken: String get() = currentConfig.jwtToken

    fun setEnv(isProd: Boolean) {
        currentConfig = if (isProd) {
            prodConfig
        } else {
            devConfig
        }
    }
}

private data class ServerConfig(
    val hostUrl: String,
    val langCode: String,
    val jwtToken: String,
)