package tech.pegasys.heku.samples

import org.apache.logging.log4j.Level
import org.junit.jupiter.api.Test
import tech.pegasys.heku.util.config.logging.RawFilter
import tech.pegasys.heku.util.config.startLogging
import tech.pegasys.heku.util.ext.toBytes
import tech.pegasys.heku.util.ext.toUInt64
import tech.pegasys.heku.util.generatePrivateKeyFromSeed
import tech.pegasys.heku.util.setDefaultExceptionHandler
import tech.pegasys.teku.TekuFacade
import tech.pegasys.teku.bls.BLSKeyPair
import tech.pegasys.teku.cli.subcommand.internal.validator.tools.EncryptedKeystoreWriter
import tech.pegasys.teku.config.TekuConfiguration
import tech.pegasys.teku.ethereum.executionlayer.BuilderCircuitBreaker
import tech.pegasys.teku.ethereum.executionlayer.ExecutionLayerManagerStub
import tech.pegasys.teku.networking.eth2.gossip.GossipFailureLogger
import tech.pegasys.teku.networking.eth2.gossip.SyncCommitteeMessageGossipManager
import tech.pegasys.teku.spec.Spec
import tech.pegasys.teku.spec.SpecFactory
import tech.pegasys.teku.spec.TestSpecFactory
import tech.pegasys.teku.spec.config.SpecConfigLoader
import tech.pegasys.teku.spec.datastructures.interop.GenesisStateBuilder
import tech.pegasys.teku.spec.networks.Eth2Network
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.Optional

//@Disabled
class SimpleTekuNode {
    val random = SecureRandom(byteArrayOf())

    val validatorsCount = 1
    val genesisTime = System.currentTimeMillis() / 1000
    val spec: Spec = TestSpecFactory.createMinimalBellatrix { it
        .secondsPerSlot(2)
        .slotsPerEpoch(1)
        .eth1FollowDistance(1.toUInt64())
        .altairBuilder {it
            // TODO: can't change NetworkConstants.SYNC_COMMITTEE_SUBNET_COUNT
            .syncCommitteeSize(4)
        }
    }
    val validatorDepositAmount = spec.genesisSpecConfig.maxEffectiveBalance * 100
    val validatorKeys = List(validatorsCount) { BLSKeyPair.random(random) }

    val workDir = "./work.dir/linea"
        .also {
            File(it).deleteRecursively()
            File(it).mkdirs()
        }
    val genesisFile = "$workDir/genesis.ssz"
    val validatorKeysDir = "$workDir/validator-keys"
    val validatorKeyPass = "1234"

    val port: Int = 9004
    val dataPath: String = "$workDir/data"
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
                    .applyNetworkDefaults(Eth2Network.MINIMAL)
                    .customGenesisState(genesisFile)
                    .spec(spec)
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
                it
                    .validatorKeys(listOf("$validatorKeysDir;$validatorKeysDir"))
                    .validatorKeystoreLockingEnabled(false)
                    .proposerDefaultFeeRecipient("0x7777777777777777777777777777777777777777")
            }
            .executionLayer {
                it
                    .engineEndpoint("unsafe-test-stub")
                    .stubExecutionLayerManagerConstructor { serviceConfig, _ ->
                        LoggingExecutionLayerManager(
                            ExecutionLayerManagerStub(
                                spec,
                                serviceConfig.timeProvider,
                                true,
                                Optional.empty(),
                                BuilderCircuitBreaker.NOOP
                            )
                        )
                    }
            }
            .build()
            .startLogging(logLevel) {
                // Filter the following WARN messages cause just a single validator node
                addFilter(
                    // Filter entries: WARN  - Failed to publish sync committee message(s) for slot 3 because no peers were available on the required gossip topic
                    // Filter entries: WARN  - Failed to publish attestation(s) for slot 3 because no peers were available on the required gossip topic
                    GossipFailureLogger::class.qualifiedName!!,
                    RawFilter.excludeByMessage("because no peers were available on the required gossip topic")
                )
            }

        val beaconNode = TekuFacade.startBeaconNode(config)

        Thread.sleep(100000000L)
    }

    fun writeGenesis() {
        val genesisStateBuilder = GenesisStateBuilder()
            .spec(spec)
            .genesisTime(genesisTime)

        validatorKeys.forEach {
            genesisStateBuilder.addValidator(it, validatorDepositAmount)
        }
        val genesisState = genesisStateBuilder.build()
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