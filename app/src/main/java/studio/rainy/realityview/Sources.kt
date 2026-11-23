package studio.rainy.realityview

import java.net.URI

fun validRtspAddress(raw: String): Boolean {
    val address = runCatching { URI(raw.trim()) }.getOrNull() ?: return false
    return address.scheme.equals("rtsp", ignoreCase = true) &&
        address.host?.isNotBlank() == true && address.rawUserInfo == null &&
        (address.port == -1 || address.port in 1..65535) && address.rawFragment == null
}
