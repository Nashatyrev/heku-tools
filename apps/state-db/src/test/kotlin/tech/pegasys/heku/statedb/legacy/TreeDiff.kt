package tech.pegasys.heku.statedb.legacy

import tech.pegasys.teku.infrastructure.ssz.tree.BranchNode
import tech.pegasys.teku.infrastructure.ssz.tree.LeafNode
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode

typealias TreeDiffVisitor = (node1: TreeNode, node2: TreeNode) -> Unit

class TreeDiff {

    fun compare(node1: TreeNode, node2: TreeNode, visitor: TreeDiffVisitor) {
        when {
            node1 == node2 -> {}
            node1 is BranchNode && node2 is BranchNode -> {
                compare(node1.left(), node2.left(), visitor)
                compare(node1.right(), node2.right(), visitor)
            }

            node1 is LeafNode && node2 is LeafNode -> visitor(node1, node2)

            else -> visitor(node1, node2)
        }
    }

    data class Diff(
        val removed: List<LeafNode>,
        val added: List<LeafNode>
    )

    fun calcLeafDiff(tree1: TreeNode, tree2: TreeNode): Diff {
        val removed = mutableListOf<LeafNode>()
        val added = mutableListOf<LeafNode>()

        compare(tree1, tree2) { node1, node2 ->
            fun gatherLeafs(node: TreeNode): List<LeafNode> {
                val ret = mutableListOf<LeafNode>()
                node.iterateAll { n: TreeNode ->
                    if (n is LeafNode) ret += n
                }
                return ret;
            }
            removed += gatherLeafs(node1)
            added += gatherLeafs(node2)
        }
        return Diff(removed, added)
    }
}