package at.asitplus.crypto.datatypes.jws

import at.asitplus.KmmResult
import at.asitplus.crypto.datatypes.CryptoPublicKey
import at.asitplus.crypto.datatypes.EcCurve
import at.asitplus.crypto.datatypes.io.Base64Strict
import at.asitplus.crypto.datatypes.io.ByteArrayBase64UrlSerializer
import at.asitplus.crypto.datatypes.jws.io.jsonSerializer
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.toByteString

@Serializable
data class JsonWebKey(
    @SerialName("crv")
    val curve: EcCurve? = null,
    @SerialName("kty")
    val type: JwkType? = null,
    @SerialName("kid")
    val keyId: String? = null,
    @SerialName("x")
    @Serializable(with = ByteArrayBase64UrlSerializer::class)
    val x: ByteArray? = null,
    @SerialName("y")
    @Serializable(with = ByteArrayBase64UrlSerializer::class)
    val y: ByteArray? = null,
) {
    fun serialize() = jsonSerializer.encodeToString(this)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as JsonWebKey

        if (type != other.type) return false
        if (curve != other.curve) return false
        if (keyId != other.keyId) return false
        if (x != null) {
            if (other.x == null) return false
            if (!x.contentEquals(other.x)) return false
        } else if (other.x != null) return false
        if (y != null) {
            if (other.y == null) return false
            if (!y.contentEquals(other.y)) return false
        } else if (other.y != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type?.hashCode() ?: 0
        result = 31 * result + (curve?.hashCode() ?: 0)
        result = 31 * result + (keyId?.hashCode() ?: 0)
        result = 31 * result + (x?.contentHashCode() ?: 0)
        result = 31 * result + (y?.contentHashCode() ?: 0)
        return result
    }

    companion object {

        fun deserialize(it: String) = kotlin.runCatching {
            jsonSerializer.decodeFromString<JsonWebKey>(it)
        }.getOrElse {
            null
        }

        fun fromKeyId(it: String): JsonWebKey? = CryptoPublicKey.fromKeyId(it)?.toJsonWebKey()

        fun fromAnsiX963Bytes(it: ByteArray): JsonWebKey? = CryptoPublicKey.Ec.fromAnsiX963Bytes(it)?.toJsonWebKey()
    }

    fun fromCoordinates(
        curve: EcCurve,
        x: ByteArray,
        y: ByteArray
    ): JsonWebKey? = CryptoPublicKey.Ec.fromCoordinates(curve, x, y).toJsonWebKey()


    fun toAnsiX963ByteArray(): KmmResult<ByteArray> {
        if (x != null && y != null)
            return KmmResult.success(byteArrayOf(0x04.toByte()) + x + y);
        return KmmResult.failure(IllegalArgumentException())
    }

    val jwkThumbprint: String by lazy {
        Json.encodeToString(this).encodeToByteArray().toByteString().sha256().base64Url()
    }

    val identifier: String by lazy {
        keyId ?: "urn:ietf:params:oauth:jwk-thumbprint:sha256:${jwkThumbprint}"
    }

    override fun toString(): String {
        return "JsonWebKey(type=$type, curve=$curve, keyId=$keyId," +
                " x=${x?.encodeToString(Base64Strict)}," +
                " y=${y?.encodeToString(Base64Strict)})"
    }

    fun toCryptoPublicKey(): CryptoPublicKey? {
        if (this.type != JwkType.EC || this.curve == null || this.x == null || this.y == null) return null
        return CryptoPublicKey.Ec(
            curve = this.curve,
            x = x,
            y = y,
        ).apply { jwkId = identifier }
    }
}

fun CryptoPublicKey.toJsonWebKey(): JsonWebKey =
    if (this is CryptoPublicKey.Ec) {
        JsonWebKey(
            curve = curve,
            type = JwkType.EC,
            keyId = jwkId,
            x = x,
            y = y
        )
    } else TODO("RSA not yet implemented")



