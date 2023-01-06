package tech.pegasys.heku.util

import io.libp2p.core.crypto.KEY_TYPE
import io.libp2p.core.crypto.generateKeyPair
import io.libp2p.core.crypto.sha256
import io.libp2p.etc.types.toLongBigEndian
import org.apache.tuweni.bytes.Bytes
import java.net.InetAddress
import java.net.NetworkInterface
import java.security.SecureRandom


val MAC_SEED_BYTES = run {
    val localHost = InetAddress.getLocalHost()
    val ni = NetworkInterface.getByInetAddress(localHost)
    ni.hardwareAddress
}

val MAC_SEED_LONG = sha256(MAC_SEED_BYTES).copyOfRange(0, 8).toLongBigEndian()

fun generatePrivateKeys(cnt: Int, random: SecureRandom) =
    generateSequence { generateKeyPair(KEY_TYPE.SECP256K1, random = random) }
        .map { it.first }
        .take(cnt)
        .toList()

fun generatePrivateKeysFromSeed(cnt: Int, seed: Long) =
    generatePrivateKeys(cnt, SecureRandom(Bytes.ofUnsignedLong(seed).toArrayUnsafe()))

fun generatePrivateKeyFromSeed(seed: Long) = generatePrivateKeysFromSeed(1, seed)[0]

