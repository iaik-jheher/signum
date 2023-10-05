package at.asitplus.crypto.datatypes

import at.asitplus.crypto.datatypes.asn1.encodeToAsn1
import at.asitplus.crypto.datatypes.asn1.ensureSize
import at.asitplus.crypto.datatypes.asn1.sequence
import at.asitplus.crypto.datatypes.io.ByteArrayBase64Serializer
import at.asitplus.crypto.datatypes.io.MultibaseHelper
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
sealed class CryptoPublicKey {

    //must be serializable, therefore <String,String>
    val additionalProperties = mutableMapOf<String,String>()

    @Transient
    abstract val keyId: String

    @Transient
    abstract val encoded: ByteArray

    @Transient
    val pkcs8Encoded by lazy {  encodeToAsn1()}

    companion object {

        fun fromKeyId(it: String): CryptoPublicKey? {
            val (xCoordinate, yCoordinate) = MultibaseHelper.calcEcPublicKeyCoords(it)
                ?: return null
            val curve = EcCurve.entries.find { it.coordinateLengthBytes.toInt() == xCoordinate.size } ?: return null
            return Ec(curve = curve, x = xCoordinate, y = yCoordinate)
        }
    }

    @Serializable
    data class Rsa(
        val bits: Size,
        @Serializable(with = ByteArrayBase64Serializer::class) val n: ByteArray,
        val e: UInt,
    ) : CryptoPublicKey() {

        enum class Size(val number: UInt) {
            RSA_512(512u),
            RSA_1024(1024u),
            RSA_2048(2048u),
            RSA_3027(3072u),
            RSA_4096(4096u);


            companion object {
                fun of(numBits: UInt) = entries.find { it.number == numBits }
            }
        }

        @Transient
        override val keyId by lazy { MultibaseHelper.calcKid(this) }

        /**
         * PKCS#1 encoded RSA Public Key
         */
        @Transient
        override val encoded = sequence {
            tagged(0x02) {
                n.ensureSize(bits.number / 8u).let { if (it.first() == 0x00.toByte()) it else byteArrayOf(0x00, *it) }
            }
            int { e.toInt() }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Rsa

            return encoded.contentEquals(other.encoded)
        }

        override fun hashCode(): Int {
            var result = bits.hashCode()
            result = 31 * result + n.contentHashCode()
            result = 31 * result + e.hashCode()
            return result
        }

    }

    @Serializable
    @SerialName("EC")
    data class Ec(
        val curve: EcCurve,
        @Serializable(with = ByteArrayBase64Serializer::class) val x: ByteArray,
        @Serializable(with = ByteArrayBase64Serializer::class) val y: ByteArray,
    ) : CryptoPublicKey() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as Ec

            if (curve != other.curve) return false
            if (!encoded.contentEquals(other.encoded)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = curve.hashCode()
            result = 31 * result + x.contentHashCode()
            result = 31 * result + y.contentHashCode()
            return result
        }

        companion object {
            fun fromCoordinates(curve: EcCurve, x: ByteArray, y: ByteArray): Ec =
                Ec(curve = curve, x = x, y = y)

            fun fromAnsiX963Bytes(src: ByteArray): CryptoPublicKey? {
                val curve =
                    EcCurve.entries.find { 2 * it.coordinateLengthBytes.toInt() == src.size - 1 } ?: return null
                if (src[0] != 0x04.toByte()) return null

                val numBytes = curve.coordinateLengthBytes.toInt()

                val xCoordinate = src.sliceArray(1..<numBytes)
                val yCoordinate =
                    src.sliceArray((numBytes + 1)..<(numBytes * 2 + 1))
                return Ec(curve = curve, x = xCoordinate, y = yCoordinate)
            }

        }

        /**
         * ANSI X9.63 Encoding as used by iOS
         */
        @Transient
        override val encoded =
            curve.coordinateLengthBytes.let { byteArrayOf(0x04.toByte()) + x.ensureSize(it) + y.ensureSize(it) }

        @Transient
        override val keyId = MultibaseHelper.calcKeyId(curve, x, y)


    }
}