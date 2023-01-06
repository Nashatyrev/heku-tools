package tech.pegasys.heku.util.beacon

import tech.pegasys.teku.service.serviceutils.ServiceConfig
import tech.pegasys.teku.services.beaconchain.BeaconChainConfiguration
import tech.pegasys.teku.services.beaconchain.BeaconChainController

open class ServiceAwareBeaconChainController(
    val serviceConfig: ServiceConfig,
    beaconConfig: BeaconChainConfiguration
) : BeaconChainController(serviceConfig, beaconConfig)