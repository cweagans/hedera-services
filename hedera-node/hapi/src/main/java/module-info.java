module com.hedera.node.hapi {
    requires com.hedera.pbj.runtime;

    exports com.hedera.hapi.node.base;
    exports com.hedera.hapi.node.base.codec;
    exports com.hedera.hapi.node.base.schema;
    exports com.hedera.hapi.node.consensus;
    exports com.hedera.hapi.node.consensus.codec;
    exports com.hedera.hapi.node.consensus.schema;
    exports com.hedera.hapi.node.contract;
    exports com.hedera.hapi.node.contract.codec;
    exports com.hedera.hapi.node.contract.schema;
    exports com.hedera.hapi.node.file;
    exports com.hedera.hapi.node.file.codec;
    exports com.hedera.hapi.node.file.schema;
    exports com.hedera.hapi.node.freeze;
    exports com.hedera.hapi.node.freeze.codec;
    exports com.hedera.hapi.node.freeze.schema;
    exports com.hedera.hapi.node.network;
    exports com.hedera.hapi.node.network.codec;
    exports com.hedera.hapi.node.network.schema;
    exports com.hedera.hapi.node.scheduled;
    exports com.hedera.hapi.node.scheduled.codec;
    exports com.hedera.hapi.node.scheduled.schema;
    exports com.hedera.hapi.node.token;
    exports com.hedera.hapi.node.token.codec;
    exports com.hedera.hapi.node.token.schema;
    exports com.hedera.hapi.node.transaction;
    exports com.hedera.hapi.node.transaction.codec;
    exports com.hedera.hapi.node.transaction.schema;
    exports com.hedera.hapi.node.util;
    exports com.hedera.hapi.node.util.codec;
    exports com.hedera.hapi.node.util.schema;
    exports com.hedera.hapi.streams;
    exports com.hedera.hapi.streams.codec;
    exports com.hedera.hapi.streams.schema;

    requires com.github.spotbugs.annotations;

    exports com.hedera.hapi.node.state.consensus.codec;
    exports com.hedera.hapi.node.state.consensus;
    exports com.hedera.hapi.node.state.token;
}
