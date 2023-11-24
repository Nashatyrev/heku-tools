package tech.consensys.linea

import org.apache.logging.log4j.Level
import tech.pegasys.heku.node.HekuNodeBuilder
import tech.pegasys.heku.util.config.logging.RawFilter
import tech.pegasys.heku.util.config.startLogging
import tech.pegasys.heku.util.ext.toBytes
import tech.pegasys.heku.util.ext.toUInt64
import tech.pegasys.heku.util.generatePrivateKeyFromSeed
import tech.pegasys.heku.util.setDefaultExceptionHandler
import tech.pegasys.teku.BeaconNodeFacade
import tech.pegasys.teku.TekuFacade
import tech.pegasys.teku.bls.BLSKeyPair
import tech.pegasys.teku.cli.subcommand.internal.validator.tools.EncryptedKeystoreWriter
import tech.pegasys.teku.config.TekuConfiguration
import tech.pegasys.teku.ethereum.executionlayer.BuilderCircuitBreaker
import tech.pegasys.teku.ethereum.executionlayer.ExecutionLayerManagerStub
import tech.pegasys.teku.infrastructure.unsigned.UInt64
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

class RunBootNode {
    companion object {

        @JvmStatic
        fun main(vararg args: String) {
            val lineaTeku = LineaTeku()
//            lineaTeku.createGenesisIfRequired()
            lineaTeku.resetWithNewGenesis()
            lineaTeku.runBootNode(0, 0 until 32)
        }
    }
}

class RunNode {
    companion object {

        @JvmStatic
        fun main(vararg args: String) {
            LineaTeku().runNode(1, 32 until 48)
        }
    }
}

class RunClientNode {
    companion object {

        @JvmStatic
        fun main(vararg args: String) {
            LineaTeku().runNode(2, 48 until 64)
        }
    }
}

class LineaTeku(
    val validatorsCount: Int = 64,
    val genesisTime: Long = System.currentTimeMillis() / 1000,
    val spec: Spec = TestSpecFactory.createMinimalBellatrix {
        it
            .secondsPerSlot(1)
            .slotsPerEpoch(1)
            .eth1FollowDistance(1.toUInt64())
            .altairBuilder {
                it
                    // TODO: can't change NetworkConstants.SYNC_COMMITTEE_SUBNET_COUNT
                    .syncCommitteeSize(4)
            }
    },
    val validatorDepositAmount: UInt64 = spec.genesisSpecConfig.maxEffectiveBalance * 100,
    val stateStorageMode: StateStorageMode = StateStorageMode.PRUNE,

    val workDir: String = "./work.dir/linea",
    val advertisedIp: String = "10.150.1.122"
) {

    val genesisFile = "$workDir/genesis.ssz"
    val validatorKeyPass = "1234"
    val bootnodeEnrFile = "$workDir/bootnode-enr.txt"

    val random = SecureRandom(byteArrayOf())
    val validatorKeys: List<BLSKeyPair> = List(validatorsCount) { BLSKeyPair.random(random) }

    fun createGenesisIfRequired() {
        if (!File(genesisFile).exists()) {
            resetWithNewGenesis()
        }
    }

    fun resetWithNewGenesis() {
        File(workDir).also {
            it.deleteRecursively()
            it.mkdirs()
        }
        writeGenesis()
    }

    fun runBootNode(
        number: Int,
        validators: IntRange
    ): BeaconNodeFacade {
        val bootNode = runNode(number, validatorKeys.slice(validators), null)
        File(bootnodeEnrFile).writeText(
            bootNode.beaconChainService.orElseThrow().beaconChainController.p2pNetwork.enr.orElseThrow()
        )
        return bootNode
    }

    fun runNode(
        number: Int,
        validators: IntRange
    ): BeaconNodeFacade {
        val bootnodeEnr = File(bootnodeEnrFile).readText()
        return runNode(number, validatorKeys.slice(validators), bootnodeEnr)
    }

    private fun runNode(
        number: Int,
        validators: List<BLSKeyPair>,
        bootnodeEnr: String?
    ): BeaconNodeFacade {
        val port = 9004 + number
        val dataPath = "$workDir/node-$number"
        val validatorKeysPath = "$dataPath/keys"
        val nodePrivKey = generatePrivateKeyFromSeed(port.toLong())

        writeValidatorKeys(validators, validatorKeysPath)

        return HekuNodeBuilder().apply {
            tekuConfigBuilder
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
                .executionLayer {
                    it
                        .stubExecutionLayerManagerConstructor { serviceConfig, _ ->
                            createStubExecutionManager(serviceConfig)
//                            .withSkippingSlots { it % 5 == 0L }
//                            .withLogging()
                        }
                }
            if (validators.isNotEmpty()) {
                tekuConfigBuilder
                    .validator {
                        it
                            .validatorKeys(listOf("$validatorKeysPath;$validatorKeysPath"))
                            .validatorKeystoreLockingEnabled(false)
                            .proposerDefaultFeeRecipient("0x7777777777777777777777777777777777777777")
                    }
            }
            if (bootnodeEnr != null) {
                tekuConfigBuilder
                    .eth2NetworkConfig {
                        it
                            .discoveryBootnodes(bootnodeEnr)
                    }
            }

            with(loggingConfig) {
                logConfigBuilder.logLevel(Level.DEBUG)
                consoleStatusLevel = Level.INFO
                addFilter(
                    // Filter entries: WARN  - Failed to publish sync committee message(s) for slot 3 because no peers were available on the required gossip topic
                    // Filter entries: WARN  - Failed to publish attestation(s) for slot 3 because no peers were available on the required gossip topic
                    GossipFailureLogger::class.qualifiedName!!,
                    RawFilter.excludeByMessage("because no peers were available on the required gossip topic")
                )
            }
        }.buildAndStart().beaconNode
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

    fun writeValidatorKeys(validators: List<BLSKeyPair>, validatorKeysPath: String) {
        File(validatorKeysPath).also {
            it.deleteRecursively()
            it.mkdirs()
        }

        val keystoreWriter =
            EncryptedKeystoreWriter(random, validatorKeyPass, "qqq", Path.of(validatorKeysPath)) {
                println("[EncryptedKeystoreWriter] $it")
            }
        validators.forEach { keyPair ->
            keystoreWriter.writeValidatorKey(keyPair)
            val validatorPasswordFileName: String = keyPair.getPublicKey().toAbbreviatedString() + "_validator.txt"
            val validatorPasswordFile = Files.createFile(Path.of(validatorKeysPath).resolve(validatorPasswordFileName))
            Files.write(validatorPasswordFile, validatorKeyPass.toByteArray(Charset.defaultCharset()))
        }

    }
}