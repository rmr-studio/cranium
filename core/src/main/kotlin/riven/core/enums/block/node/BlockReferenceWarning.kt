package cranium.core.enums.block.node

enum class BlockReferenceWarning {
    MISSING,
    REQUIRES_LOADING,
    UNSUPPORTED,
    CIRCULAR,
    DEPTH_EXCEEDED,
}