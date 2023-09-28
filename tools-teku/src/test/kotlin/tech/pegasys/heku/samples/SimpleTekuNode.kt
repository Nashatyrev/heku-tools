package tech.pegasys.heku.samples

import org.apache.logging.log4j.Level
import org.junit.jupiter.api.Test
import tech.pegasys.heku.util.config.startLogging
import tech.pegasys.heku.util.ext.toBytes
import tech.pegasys.heku.util.generatePrivateKeyFromSeed
import tech.pegasys.heku.util.setDefaultExceptionHandler
import tech.pegasys.teku.TekuFacade
import tech.pegasys.teku.bls.BLSKeyPair
import tech.pegasys.teku.cli.subcommand.internal.validator.tools.EncryptedKeystoreWriter
import tech.pegasys.teku.config.TekuConfiguration
import tech.pegasys.teku.spec.Spec
import tech.pegasys.teku.spec.SpecFactory
import tech.pegasys.teku.spec.config.SpecConfigLoader
import tech.pegasys.teku.spec.datastructures.interop.GenesisStateBuilder
import tech.pegasys.teku.spec.networks.Eth2Network
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom

//@Disabled
class SimpleTekuNode {

    val validatorsCount = 16

    val workDir = "./work.dir"
    val genesisFile = "$workDir/genesis.ssz"
    val validatorKeysDir = "$workDir/validator-keys"
    val validatorKeyPass = "1234"

    val specConfig = SpecConfigLoader.loadConfig(Eth2Network.MINIMAL.configName())
    private val spec: Spec = SpecFactory.create(specConfig)

    val random = SecureRandom(byteArrayOf())
    val validatorKeys = List(validatorsCount) {  BLSKeyPair.random(random) }

    val eth2Network: Eth2Network = Eth2Network.MINIMAL
    val port: Int = 9004
    val dataPath: String = "./work.dir/simple.teku.$eth2Network"
    val skeySeed: Long = 21
    val privKey = generatePrivateKeyFromSeed(skeySeed)
    val logLevel = Level.DEBUG

    @Test
    fun runDefaultTekuNode() {
        setDefaultExceptionHandler()

        writeGenesis()
        writeValidatorKeys()

        val config = TekuConfiguration.builder()
            .eth2NetworkConfig {
                it
                    .applyNetworkDefaults(eth2Network)
                    .customGenesisState(genesisFile)
            }
            .network {
                it
                    .listenPort(port)
                    .setPrivateKeySource { privKey.bytes().toBytes() }
            }
            .data {
                it
                    .dataBasePath(Path.of(dataPath))
            }
            .validator {
                it.validatorKeys(listOf("$validatorKeysDir;$validatorKeysDir"))
            }
            .executionLayer { it.engineEndpoint("unsafe-test-stub") }
            .build()
            .startLogging(logLevel)

        val beaconNode = TekuFacade.startBeaconNode(config)

        Thread.sleep(100000000L)
    }

    fun writeGenesis() {
        val genesisState = GenesisStateBuilder()
            .spec(spec)
            .genesisTime(System.currentTimeMillis() / 1000)
            .addValidators(validatorKeys)
            .build()
        File(genesisFile).writeBytes(genesisState.sszSerialize().toArrayUnsafe())
    }

    fun writeValidatorKeys() {
        val keystoreWriter =
            EncryptedKeystoreWriter(random, validatorKeyPass, "qqq", Path.of(validatorKeysDir)) {
                println("[EncryptedKeystoreWriter] $it")
            }
        validatorKeys.forEach { keyPair ->
            keystoreWriter.writeValidatorKey(keyPair)
            val validatorPasswordFileName: String = keyPair.getPublicKey().toAbbreviatedString() + "_validator.txt"
            val validatorPasswordFile = Files.createFile(Path.of(validatorKeysDir).resolve(validatorPasswordFileName))
            Files.write(validatorPasswordFile, validatorKeyPass.toByteArray(Charset.defaultCharset()))
        }

    }
}