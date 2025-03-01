/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.node.app.spi;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.TransactionBody;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/**
 * Utility class for working with the HAPI. We might move this to the HAPI project.
 */
public class HapiUtils {
    private HapiUtils() {}

    public static Timestamp asTimestamp(final Instant instant) {
        return Timestamp.newBuilder()
                .seconds(instant.getEpochSecond())
                .nanos(instant.getNano())
                .build();
    }

    public static final Set<HederaFunctionality> QUERY_FUNCTIONS = EnumSet.of(
            HederaFunctionality.CONSENSUS_GET_TOPIC_INFO,
            HederaFunctionality.GET_BY_SOLIDITY_ID,
            HederaFunctionality.CONTRACT_CALL_LOCAL,
            HederaFunctionality.CONTRACT_GET_INFO,
            HederaFunctionality.CONTRACT_GET_BYTECODE,
            HederaFunctionality.CONTRACT_GET_RECORDS,
            HederaFunctionality.CRYPTO_GET_ACCOUNT_BALANCE,
            HederaFunctionality.CRYPTO_GET_ACCOUNT_RECORDS,
            HederaFunctionality.CRYPTO_GET_INFO,
            HederaFunctionality.CRYPTO_GET_LIVE_HASH,
            HederaFunctionality.FILE_GET_CONTENTS,
            HederaFunctionality.FILE_GET_INFO,
            HederaFunctionality.TRANSACTION_GET_RECEIPT,
            HederaFunctionality.TRANSACTION_GET_RECORD,
            HederaFunctionality.GET_VERSION_INFO,
            HederaFunctionality.TOKEN_GET_INFO,
            HederaFunctionality.SCHEDULE_GET_INFO,
            HederaFunctionality.TOKEN_GET_NFT_INFO,
            HederaFunctionality.TOKEN_GET_NFT_INFOS,
            HederaFunctionality.TOKEN_GET_ACCOUNT_NFT_INFOS,
            HederaFunctionality.NETWORK_GET_EXECUTION_TIME,
            HederaFunctionality.GET_ACCOUNT_DETAILS);

    public static HederaFunctionality functionOf(final TransactionBody txn) throws UnknownHederaFunctionality {
        return switch (txn.data().kind()) {
            case CONSENSUS_CREATE_TOPIC -> HederaFunctionality.CONSENSUS_CREATE_TOPIC;
            case CONSENSUS_UPDATE_TOPIC -> HederaFunctionality.CONSENSUS_UPDATE_TOPIC;
            case CONSENSUS_DELETE_TOPIC -> HederaFunctionality.CONSENSUS_DELETE_TOPIC;
            case CONSENSUS_SUBMIT_MESSAGE -> HederaFunctionality.CONSENSUS_SUBMIT_MESSAGE;
            case CONTRACT_CALL -> HederaFunctionality.CONTRACT_CALL;
            case CONTRACT_CREATE_INSTANCE -> HederaFunctionality.CONTRACT_CREATE;
            case CONTRACT_UPDATE_INSTANCE -> HederaFunctionality.CONTRACT_UPDATE;
            case CONTRACT_DELETE_INSTANCE -> HederaFunctionality.CONTRACT_DELETE;
            case CRYPTO_ADD_LIVE_HASH -> HederaFunctionality.CRYPTO_ADD_LIVE_HASH;
            case CRYPTO_APPROVE_ALLOWANCE -> HederaFunctionality.CRYPTO_APPROVE_ALLOWANCE;
            case CRYPTO_CREATE_ACCOUNT -> HederaFunctionality.CRYPTO_CREATE;
            case CRYPTO_UPDATE_ACCOUNT -> HederaFunctionality.CRYPTO_UPDATE;
            case CRYPTO_DELETE -> HederaFunctionality.CRYPTO_DELETE;
            case CRYPTO_DELETE_ALLOWANCE -> HederaFunctionality.CRYPTO_DELETE_ALLOWANCE;
            case CRYPTO_DELETE_LIVE_HASH -> HederaFunctionality.CRYPTO_DELETE_LIVE_HASH;
            case CRYPTO_TRANSFER -> HederaFunctionality.CRYPTO_TRANSFER;
            case ETHEREUM_TRANSACTION -> HederaFunctionality.ETHEREUM_TRANSACTION;
            case FILE_APPEND -> HederaFunctionality.FILE_APPEND;
            case FILE_CREATE -> HederaFunctionality.FILE_CREATE;
            case FILE_UPDATE -> HederaFunctionality.FILE_UPDATE;
            case FILE_DELETE -> HederaFunctionality.FILE_DELETE;
            case FREEZE -> HederaFunctionality.FREEZE;
            case NODE_STAKE_UPDATE -> HederaFunctionality.NODE_STAKE_UPDATE;
            case SCHEDULE_CREATE -> HederaFunctionality.SCHEDULE_CREATE;
            case SCHEDULE_SIGN -> HederaFunctionality.SCHEDULE_SIGN;
            case SCHEDULE_DELETE -> HederaFunctionality.SCHEDULE_DELETE;
            case SYSTEM_DELETE -> HederaFunctionality.SYSTEM_DELETE;
            case SYSTEM_UNDELETE -> HederaFunctionality.SYSTEM_UNDELETE;
            case TOKEN_ASSOCIATE -> HederaFunctionality.TOKEN_ASSOCIATE_TO_ACCOUNT;
            case TOKEN_BURN -> HederaFunctionality.TOKEN_BURN;
            case TOKEN_CREATION -> HederaFunctionality.TOKEN_CREATE;
            case TOKEN_DELETION -> HederaFunctionality.TOKEN_DELETE;
            case TOKEN_DISSOCIATE -> HederaFunctionality.TOKEN_DISSOCIATE_FROM_ACCOUNT;
            case TOKEN_FEE_SCHEDULE_UPDATE -> HederaFunctionality.TOKEN_FEE_SCHEDULE_UPDATE;
            case TOKEN_FREEZE -> HederaFunctionality.TOKEN_FREEZE_ACCOUNT;
            case TOKEN_GRANT_KYC -> HederaFunctionality.TOKEN_GRANT_KYC_TO_ACCOUNT;
            case TOKEN_MINT -> HederaFunctionality.TOKEN_MINT;
            case TOKEN_PAUSE -> HederaFunctionality.TOKEN_PAUSE;
            case TOKEN_REVOKE_KYC -> HederaFunctionality.TOKEN_REVOKE_KYC_FROM_ACCOUNT;
            case TOKEN_UNFREEZE -> HederaFunctionality.TOKEN_UNFREEZE_ACCOUNT;
            case TOKEN_UNPAUSE -> HederaFunctionality.TOKEN_UNPAUSE;
            case TOKEN_UPDATE -> HederaFunctionality.TOKEN_UPDATE;
            case TOKEN_WIPE -> HederaFunctionality.TOKEN_ACCOUNT_WIPE;
            case UTIL_PRNG -> HederaFunctionality.UTIL_PRNG;
            case UNCHECKED_SUBMIT -> HederaFunctionality.UNCHECKED_SUBMIT;
            case UNSET -> throw new UnknownHederaFunctionality();
        };
    }

    public static HederaFunctionality functionOf(final Query txn) throws UnknownHederaFunctionality {
        return switch (txn.query().kind()) {
            case TOKEN_GET_ACCOUNT_NFT_INFOS -> HederaFunctionality.TOKEN_GET_ACCOUNT_NFT_INFOS;
            case TOKEN_GET_NFT_INFOS -> HederaFunctionality.TOKEN_GET_NFT_INFOS;
            case ACCOUNT_DETAILS -> HederaFunctionality.GET_ACCOUNT_DETAILS;
            case CONSENSUS_GET_TOPIC_INFO -> HederaFunctionality.CONSENSUS_GET_TOPIC_INFO;
            case CONTRACT_CALL_LOCAL -> HederaFunctionality.CONTRACT_CALL_LOCAL;
            case CONTRACT_GET_BYTECODE -> HederaFunctionality.CONTRACT_GET_BYTECODE;
            case CONTRACT_GET_INFO -> HederaFunctionality.CONTRACT_GET_INFO;
            case CONTRACT_GET_RECORDS -> HederaFunctionality.CONTRACT_GET_RECORDS;
            case CRYPTO_GET_ACCOUNT_RECORDS -> HederaFunctionality.CRYPTO_GET_ACCOUNT_RECORDS;
            case CRYPTO_GET_INFO -> HederaFunctionality.CRYPTO_GET_INFO;
            case CRYPTO_GET_LIVE_HASH -> HederaFunctionality.CRYPTO_GET_LIVE_HASH;
            case FILE_GET_CONTENTS -> HederaFunctionality.FILE_GET_CONTENTS;
            case FILE_GET_INFO -> HederaFunctionality.FILE_GET_INFO;
            case CRYPTO_GET_PROXY_STAKERS -> HederaFunctionality.CRYPTO_GET_STAKERS;
            case GET_BY_SOLIDITY_ID -> HederaFunctionality.GET_BY_SOLIDITY_ID;
            case CRYPTOGET_ACCOUNT_BALANCE -> HederaFunctionality.CRYPTO_GET_ACCOUNT_BALANCE;
            case GET_BY_KEY -> HederaFunctionality.GET_BY_KEY;
            case NETWORK_GET_EXECUTION_TIME -> HederaFunctionality.NETWORK_GET_EXECUTION_TIME;
            case SCHEDULE_GET_INFO -> HederaFunctionality.SCHEDULE_GET_INFO;
            case TOKEN_GET_INFO -> HederaFunctionality.TOKEN_GET_INFO;
            case TOKEN_GET_NFT_INFO -> HederaFunctionality.TOKEN_GET_NFT_INFO;
            case NETWORK_GET_VERSION_INFO -> HederaFunctionality.GET_VERSION_INFO;
            case TRANSACTION_GET_RECEIPT -> HederaFunctionality.TRANSACTION_GET_RECEIPT;
            case TRANSACTION_GET_RECORD -> HederaFunctionality.TRANSACTION_GET_RECORD;
            case TRANSACTION_GET_FAST_RECORD -> throw new UnknownHederaFunctionality();
            case UNSET -> throw new UnknownHederaFunctionality();
        };
    }
}
