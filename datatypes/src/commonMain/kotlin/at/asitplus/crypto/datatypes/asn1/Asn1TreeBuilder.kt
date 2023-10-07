package at.asitplus.crypto.datatypes.asn1

import io.matthewnelson.encoding.base16.Base16
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString

class Asn1TreeBuilder(input: ByteArray) {

    private var rest = input

    @Throws(IllegalArgumentException::class)
    fun readAll(): List<ExtendedTlv> {
        val result = mutableListOf<ExtendedTlv>()
        while (rest.isNotEmpty()) {
            val tlv = read()
            if (tlv.isContainer() && tlv.content.isNotEmpty()) {
                result.add(ExtendedTlv(tlv, Asn1TreeBuilder(tlv.content).readAll()))
            } else {
                result.add(ExtendedTlv(tlv, listOf()))
            }
        }
        return result.toList()
    }

    private fun TLV.isContainer() = tag == 0x30.toByte() || tag == 0x31.toByte() || tag == 0xA0.toByte()

    @Throws(IllegalArgumentException::class)
    private fun read(): TLV {
        val tlv = rest.readTlv()
        if (tlv.overallLength > rest.size)
            throw IllegalArgumentException("Out of bytes")
        rest = rest.drop(tlv.overallLength).toByteArray()
        return tlv
    }
}

data class ExtendedTlv(val tlv: TLV, val children: List<ExtendedTlv>) {

    val encodedLength by lazy { length.encodeLength() }
    val length: Int by lazy {
        if (children.isEmpty()) tlv.length
        else children.fold(0) { acc, extendedTlv -> acc + extendedTlv.overallLength }
    }

    val overallLength by lazy { length + 1 + encodedLength.size }

    val derEncoded: ByteArray by lazy {
        if (children.isEmpty()) byteArrayOf(tlv.tag, *encodedLength, *tlv.content)
        else {
            children.fold(byteArrayOf()) { acc, extendedTlv -> acc + extendedTlv.derEncoded }
                .let { byteArrayOf(tlv.tag, *it.size.encodeLength(), *it) }
        }
    }

    override fun toString(): String {
        return "ETLV(tag=0x${byteArrayOf(tlv.tag).encodeToString(Base16)}" +
                ", length=${tlv.length}" +
                ", overallLength=${tlv.overallLength}" +
                if (children.isNotEmpty()) ", children=${children}" else ", content=${tlv.content.encodeToString(Base16)}" +
                        ")"
    }
}