package tech.pegasys.heku.util.type

enum class ETime(
    val epochs: Epoch
) {
    EOUR(8.epochs),
    EAY(EOUR.epochs * 32), // 256 epochs
    EEK(EAY.epochs * 8), // 2048 epochs
    EONTH(EEK.epochs * 4), // 8192 epochs
    EEAR(EONTH.epochs * 8); // 65536 epochs

    fun getStart(epoch: Epoch): Epoch = epoch floorTo epochs
    fun getStart(slot: Slot): Slot = getStart(slot.epoch).startSlot
}