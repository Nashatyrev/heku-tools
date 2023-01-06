package tech.pegasys.heku.util.gradle

fun main(args: Array<String>) {
    System.err.println("This is the default gradle main class executing with args: ${args.contentToString()}")
    System.err.println("Run gradle -PmainClass=<your.main.Class> [--args=\"you args\"]")
    System.exit(-1)
}
