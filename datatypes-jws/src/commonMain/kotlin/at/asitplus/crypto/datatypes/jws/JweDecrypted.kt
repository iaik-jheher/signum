package at.asitplus.wallet.lib.jws

import at.asitplus.crypto.datatypes.jws.JweHeader

/**
 * Representation of a decrypted JSON Web Encryption object, i.e. header and payload.
 *
 * @see [JweEncrypted]
 */
data class JweDecrypted(
    val header: JweHeader,
    val payload: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as JweDecrypted

        if (header != other.header) return false
        if (!payload.contentEquals(other.payload)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = header.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }

}