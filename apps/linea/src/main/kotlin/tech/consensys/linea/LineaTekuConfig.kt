package tech.consensys.linea

import org.apache.logging.log4j.Level
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
import tech.pegasys.teku.service.serviceutils.ServiceConfig
import tech.pegasys.teku.spec.Spec
import tech.pegasys.teku.spec.TestSpecFactory
import tech.pegasys.teku.spec.datastructures.interop.GenesisStateBuilder
import tech.pegasys.teku.spec.networks.Eth2Network
import tech.pegasys.teku.storage.server.StateStorageMode
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.Optional

class RunSequencerNode {
    companion object {

        @JvmStatic
        fun main(vararg args: String) {
            LineaTekuConfig().runSequencerTekuNode(false)
        }
    }
}

class RunClientNode1 {
    companion object {

        @JvmStatic
        fun main(vararg args: String) {
            LineaTekuConfig().runClientTekuNode(1)
        }
    }
}

class RunClientNode2 {
    companion object {

        @JvmStatic
        fun main(vararg args: String) {
            LineaTekuConfig().runClientTekuNode(2)
        }
    }
}

class RunClientNode3 {
    companion object {

        @JvmStatic
        fun main(vararg args: String) {
            LineaTekuConfig().runClientTekuNode(3)
        }
    }
}


class LineaTekuConfig {
    val validatorsCount = 1
    val genesisTime = System.currentTimeMillis() / 1000
    val spec: Spec = TestSpecFactory.createMinimalBellatrix {
        it
            .secondsPerSlot(2)
            .slotsPerEpoch(1)
            .eth1FollowDistance(1.toUInt64())
            .altairBuilder {
                it
                    // TODO: can't change NetworkConstants.SYNC_COMMITTEE_SUBNET_COUNT
                    .syncCommitteeSize(4)
            }
    }
    val validatorDepositAmount = spec.genesisSpecConfig.maxEffectiveBalance * 100
    val stateStorageMode = StateStorageMode.PRUNE

    val workDir = "./work.dir/linea"
    val genesisFile = "$workDir/genesis.ssz"
    val validatorKeysDir = "$workDir/validator-keys"
    val validatorKeyPass = "1234"
    val sequencerNodeEnrFile = "$workDir/sequencer-enr.txt"

    //    val advertisedIp = "127.0.0.1"
    val advertisedIp = "10.150.1.122"

    val random = SecureRandom(byteArrayOf())
    val validatorKeys = List(validatorsCount) { BLSKeyPair.random(random) }

    fun TekuConfiguration.Builder.applyCommonOptions(
        port: Int,
        dataPath: String
    ): TekuConfiguration.Builder = apply {
        val nodePrivKey = generatePrivateKeyFromSeed(port.toLong())
        this
            .eth2NetworkConfig {
                it
                    .applyNetworkDefaults(Eth2Network.MINIMAL)
                    .customGenesisState(genesisFile)
                    .spec(spec)
            }
            .discovery {
                it
                    .isDiscoveryEnabled(true)
                    .siteLocalAddressesEnabled(true)
            }
            .network {
                it
                    .listenPort(port)
                    .advertisedIp(Optional.of(advertisedIp))
                    .setPrivateKeySource { nodePrivKey.bytes().toBytes() }
            }
            .data {
                it
                    .dataBasePath(Path.of(dataPath))
            }
            .storageConfiguration {
                it
                    .dataStorageMode(stateStorageMode)
            }
            .executionLayer {
                it
                    .engineEndpoint("unsafe-test-stub")
            }
    }

    fun runSequencerTekuNode(
        resetGenesisOnSequencerStart: Boolean
    ) {
        val port: Int = 9004
        val dataPath: String = "$workDir/data-seq"
        val logLevel = Level.DEBUG

        setDefaultExceptionHandler()

        if (resetGenesisOnSequencerStart || !File(genesisFile).exists()) {
            File(workDir).also {
                it.deleteRecursively()
                it.mkdirs()
            }

            writeGenesis()
            writeValidatorKeys()
        }

        val config = TekuConfiguration.builder()
            .applyCommonOptions(port, dataPath)
            .validator {
                it
                    .validatorKeys(listOf("$validatorKeysDir;$validatorKeysDir"))
                    .validatorKeystoreLockingEnabled(false)
                    .proposerDefaultFeeRecipient("0x7777777777777777777777777777777777777777")
            }
            .executionLayer {
                it
                    .stubExecutionLayerManagerConstructor { serviceConfig, _ ->
                        createStubExecutionManager(serviceConfig)
                            .withSkippingSlots { it % 5 == 0L }
                            .withLogging()
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

        File(sequencerNodeEnrFile).writeText(
            beaconNode.beaconChainService.orElseThrow().beaconChainController.p2pNetwork.enr.orElseThrow()
        )
    }

    fun runClientTekuNode(number: Int) {
        val port: Int = 9005 + (number - 1)
        val dataPath: String = "$workDir/data-client-$number"
        val logLevel = Level.DEBUG

        setDefaultExceptionHandler()

        val sequencerEnr = File(sequencerNodeEnrFile).readText()

        val config = TekuConfiguration.builder()
            .applyCommonOptions(port, dataPath)
            .eth2NetworkConfig {
                it
                    .discoveryBootnodes(sequencerEnr)
            }
            .executionLayer {
                it
                    .stubExecutionLayerManagerConstructor { serviceConfig, _ ->
                        createStubExecutionManager(serviceConfig)
                    }
            }
            .build()
            .startLogging(logLevel)

        TekuFacade.startBeaconNode(config)
    }

    private fun createStubExecutionManager(serviceConfig: ServiceConfig) =
        ExecutionLayerManagerStub(
            spec,
            serviceConfig.timeProvider,
            true,
            Optional.empty(),
            BuilderCircuitBreaker.NOOP
        )

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