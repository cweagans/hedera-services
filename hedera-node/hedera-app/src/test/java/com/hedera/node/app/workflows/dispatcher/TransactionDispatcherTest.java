/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.dispatcher;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.TopicID;
import com.hedera.hapi.node.consensus.ConsensusCreateTopicTransactionBody;
import com.hedera.hapi.node.consensus.ConsensusDeleteTopicTransactionBody;
import com.hedera.hapi.node.consensus.ConsensusSubmitMessageTransactionBody;
import com.hedera.hapi.node.consensus.ConsensusUpdateTopicTransactionBody;
import com.hedera.hapi.node.contract.ContractCallTransactionBody;
import com.hedera.hapi.node.contract.ContractCreateTransactionBody;
import com.hedera.hapi.node.contract.ContractDeleteTransactionBody;
import com.hedera.hapi.node.contract.ContractUpdateTransactionBody;
import com.hedera.hapi.node.contract.EthereumTransactionBody;
import com.hedera.hapi.node.file.FileAppendTransactionBody;
import com.hedera.hapi.node.file.FileCreateTransactionBody;
import com.hedera.hapi.node.file.FileDeleteTransactionBody;
import com.hedera.hapi.node.file.FileUpdateTransactionBody;
import com.hedera.hapi.node.file.SystemDeleteTransactionBody;
import com.hedera.hapi.node.file.SystemUndeleteTransactionBody;
import com.hedera.hapi.node.freeze.FreezeTransactionBody;
import com.hedera.hapi.node.scheduled.ScheduleCreateTransactionBody;
import com.hedera.hapi.node.scheduled.ScheduleSignTransactionBody;
import com.hedera.hapi.node.token.CryptoAddLiveHashTransactionBody;
import com.hedera.hapi.node.token.CryptoApproveAllowanceTransactionBody;
import com.hedera.hapi.node.token.CryptoCreateTransactionBody;
import com.hedera.hapi.node.token.CryptoDeleteAllowanceTransactionBody;
import com.hedera.hapi.node.token.CryptoDeleteLiveHashTransactionBody;
import com.hedera.hapi.node.token.CryptoDeleteTransactionBody;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.token.CryptoUpdateTransactionBody;
import com.hedera.hapi.node.token.TokenAssociateTransactionBody;
import com.hedera.hapi.node.token.TokenBurnTransactionBody;
import com.hedera.hapi.node.token.TokenCreateTransactionBody;
import com.hedera.hapi.node.token.TokenDeleteTransactionBody;
import com.hedera.hapi.node.token.TokenDissociateTransactionBody;
import com.hedera.hapi.node.token.TokenFeeScheduleUpdateTransactionBody;
import com.hedera.hapi.node.token.TokenFreezeAccountTransactionBody;
import com.hedera.hapi.node.token.TokenGrantKycTransactionBody;
import com.hedera.hapi.node.token.TokenMintTransactionBody;
import com.hedera.hapi.node.token.TokenPauseTransactionBody;
import com.hedera.hapi.node.token.TokenRevokeKycTransactionBody;
import com.hedera.hapi.node.token.TokenUnfreezeAccountTransactionBody;
import com.hedera.hapi.node.token.TokenUnpauseTransactionBody;
import com.hedera.hapi.node.token.TokenUpdateTransactionBody;
import com.hedera.hapi.node.token.TokenWipeAccountTransactionBody;
import com.hedera.hapi.node.transaction.NodeStakeUpdateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.node.transaction.UncheckedSubmitBody;
import com.hedera.hapi.node.util.UtilPrngTransactionBody;
import com.hedera.node.app.service.admin.impl.handlers.FreezeHandler;
import com.hedera.node.app.service.consensus.impl.WritableTopicStore;
import com.hedera.node.app.service.consensus.impl.config.ConsensusServiceConfig;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusCreateTopicHandler;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusDeleteTopicHandler;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusSubmitMessageHandler;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusUpdateTopicHandler;
import com.hedera.node.app.service.consensus.impl.records.ConsensusCreateTopicRecordBuilder;
import com.hedera.node.app.service.consensus.impl.records.SubmitMessageRecordBuilder;
import com.hedera.node.app.service.contract.impl.handlers.ContractCallHandler;
import com.hedera.node.app.service.contract.impl.handlers.ContractCreateHandler;
import com.hedera.node.app.service.contract.impl.handlers.ContractDeleteHandler;
import com.hedera.node.app.service.contract.impl.handlers.ContractSystemDeleteHandler;
import com.hedera.node.app.service.contract.impl.handlers.ContractSystemUndeleteHandler;
import com.hedera.node.app.service.contract.impl.handlers.ContractUpdateHandler;
import com.hedera.node.app.service.contract.impl.handlers.EtherumTransactionHandler;
import com.hedera.node.app.service.file.impl.handlers.FileAppendHandler;
import com.hedera.node.app.service.file.impl.handlers.FileCreateHandler;
import com.hedera.node.app.service.file.impl.handlers.FileDeleteHandler;
import com.hedera.node.app.service.file.impl.handlers.FileSystemDeleteHandler;
import com.hedera.node.app.service.file.impl.handlers.FileSystemUndeleteHandler;
import com.hedera.node.app.service.file.impl.handlers.FileUpdateHandler;
import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.state.validation.UsageLimits;
import com.hedera.node.app.service.network.impl.handlers.NetworkUncheckedSubmitHandler;
import com.hedera.node.app.service.schedule.impl.handlers.ScheduleCreateHandler;
import com.hedera.node.app.service.schedule.impl.handlers.ScheduleDeleteHandler;
import com.hedera.node.app.service.schedule.impl.handlers.ScheduleSignHandler;
import com.hedera.node.app.service.token.impl.ReadableAccountStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.service.token.impl.handlers.CryptoAddLiveHashHandler;
import com.hedera.node.app.service.token.impl.handlers.CryptoApproveAllowanceHandler;
import com.hedera.node.app.service.token.impl.handlers.CryptoCreateHandler;
import com.hedera.node.app.service.token.impl.handlers.CryptoDeleteAllowanceHandler;
import com.hedera.node.app.service.token.impl.handlers.CryptoDeleteHandler;
import com.hedera.node.app.service.token.impl.handlers.CryptoDeleteLiveHashHandler;
import com.hedera.node.app.service.token.impl.handlers.CryptoTransferHandler;
import com.hedera.node.app.service.token.impl.handlers.CryptoUpdateHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenAccountWipeHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenAssociateToAccountHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenBurnHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenCreateHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenDeleteHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenDissociateFromAccountHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenFeeScheduleUpdateHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenFreezeAccountHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenGrantKycToAccountHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenMintHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenPauseHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenRevokeKycFromAccountHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenUnfreezeAccountHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenUnpauseHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenUpdateHandler;
import com.hedera.node.app.service.util.impl.handlers.UtilPrngHandler;
import com.hedera.node.app.spi.KeyOrLookupFailureReason;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.HandleContext;
import com.hedera.node.app.spi.numbers.HederaAccountNumbers;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.state.HederaState;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionDispatcherTest {

    @Mock(strictness = LENIENT)
    private HederaState state;

    @Mock(strictness = LENIENT)
    private ReadableAccountStore accountStore;

    @Mock
    private ConsensusCreateTopicHandler consensusCreateTopicHandler;

    @Mock
    private ConsensusUpdateTopicHandler consensusUpdateTopicHandler;

    @Mock
    private ConsensusDeleteTopicHandler consensusDeleteTopicHandler;

    @Mock
    private ConsensusSubmitMessageHandler consensusSubmitMessageHandler;

    @Mock
    private ContractCreateHandler contractCreateHandler;

    @Mock
    private ContractUpdateHandler contractUpdateHandler;

    @Mock
    private ContractCallHandler contractCallHandler;

    @Mock
    private ContractDeleteHandler contractDeleteHandler;

    @Mock
    private ContractSystemDeleteHandler contractSystemDeleteHandler;

    @Mock
    private ContractSystemUndeleteHandler contractSystemUndeleteHandler;

    @Mock
    private EtherumTransactionHandler etherumTransactionHandler;

    @Mock
    private CryptoCreateHandler cryptoCreateHandler;

    @Mock
    private CryptoUpdateHandler cryptoUpdateHandler;

    @Mock
    private CryptoTransferHandler cryptoTransferHandler;

    @Mock
    private CryptoDeleteHandler cryptoDeleteHandler;

    @Mock
    private CryptoApproveAllowanceHandler cryptoApproveAllowanceHandler;

    @Mock
    private CryptoDeleteAllowanceHandler cryptoDeleteAllowanceHandler;

    @Mock
    private CryptoAddLiveHashHandler cryptoAddLiveHashHandler;

    @Mock
    private CryptoDeleteLiveHashHandler cryptoDeleteLiveHashHandler;

    @Mock
    private FileCreateHandler fileCreateHandler;

    @Mock
    private FileUpdateHandler fileUpdateHandler;

    @Mock
    private FileDeleteHandler fileDeleteHandler;

    @Mock
    private FileAppendHandler fileAppendHandler;

    @Mock
    private FileSystemDeleteHandler fileSystemDeleteHandler;

    @Mock
    private FileSystemUndeleteHandler fileSystemUndeleteHandler;

    @Mock
    private FreezeHandler freezeHandler;

    @Mock
    private NetworkUncheckedSubmitHandler networkUncheckedSubmitHandler;

    @Mock
    private ScheduleCreateHandler scheduleCreateHandler;

    @Mock
    private ScheduleSignHandler scheduleSignHandler;

    @Mock
    private ScheduleDeleteHandler scheduleDeleteHandler;

    @Mock
    private TokenCreateHandler tokenCreateHandler;

    @Mock
    private TokenUpdateHandler tokenUpdateHandler;

    @Mock
    private TokenMintHandler tokenMintHandler;

    @Mock
    private TokenBurnHandler tokenBurnHandler;

    @Mock
    private TokenDeleteHandler tokenDeleteHandler;

    @Mock
    private TokenAccountWipeHandler tokenAccountWipeHandler;

    @Mock
    private TokenFreezeAccountHandler tokenFreezeAccountHandler;

    @Mock
    private TokenUnfreezeAccountHandler tokenUnfreezeAccountHandler;

    @Mock
    private TokenGrantKycToAccountHandler tokenGrantKycToAccountHandler;

    @Mock
    private TokenRevokeKycFromAccountHandler tokenRevokeKycFromAccountHandler;

    @Mock
    private TokenAssociateToAccountHandler tokenAssociateToAccountHandler;

    @Mock
    private TokenDissociateFromAccountHandler tokenDissociateFromAccountHandler;

    @Mock
    private TokenFeeScheduleUpdateHandler tokenFeeScheduleUpdateHandler;

    @Mock
    private TokenPauseHandler tokenPauseHandler;

    @Mock
    private TokenUnpauseHandler tokenUnpauseHandler;

    @Mock
    private UtilPrngHandler utilPrngHandler;

    @Mock
    private HederaAccountNumbers accountNumbers;

    @Mock
    private GlobalDynamicProperties dynamicProperties;

    @Mock
    private WritableStoreFactory writableStoreFactory;

    @Mock
    private WritableTopicStore writableTopicStore;

    @Mock
    private WritableTokenStore writableTokenStore;

    @Mock
    private UsageLimits usageLimits;

    @Mock
    private HandleContext handleContext;

    @Mock
    private TransactionContext txnCtx;

    @Mock
    private TransactionBody transactionBody;

    private TransactionHandlers handlers;
    private TransactionDispatcher dispatcher;

    @BeforeEach
    void setup(@Mock final ReadableStates readableStates, @Mock HederaKey payerKey) {
        when(state.createReadableStates(any())).thenReturn(readableStates);
        when(accountStore.getKey(any(AccountID.class))).thenReturn(KeyOrLookupFailureReason.withKey(payerKey));

        handlers = new TransactionHandlers(
                consensusCreateTopicHandler,
                consensusUpdateTopicHandler,
                consensusDeleteTopicHandler,
                consensusSubmitMessageHandler,
                contractCreateHandler,
                contractUpdateHandler,
                contractCallHandler,
                contractDeleteHandler,
                contractSystemDeleteHandler,
                contractSystemUndeleteHandler,
                etherumTransactionHandler,
                cryptoCreateHandler,
                cryptoUpdateHandler,
                cryptoTransferHandler,
                cryptoDeleteHandler,
                cryptoApproveAllowanceHandler,
                cryptoDeleteAllowanceHandler,
                cryptoAddLiveHashHandler,
                cryptoDeleteLiveHashHandler,
                fileCreateHandler,
                fileUpdateHandler,
                fileDeleteHandler,
                fileAppendHandler,
                fileSystemDeleteHandler,
                fileSystemUndeleteHandler,
                freezeHandler,
                networkUncheckedSubmitHandler,
                scheduleCreateHandler,
                scheduleSignHandler,
                scheduleDeleteHandler,
                tokenCreateHandler,
                tokenUpdateHandler,
                tokenMintHandler,
                tokenBurnHandler,
                tokenDeleteHandler,
                tokenAccountWipeHandler,
                tokenFreezeAccountHandler,
                tokenUnfreezeAccountHandler,
                tokenGrantKycToAccountHandler,
                tokenRevokeKycFromAccountHandler,
                tokenAssociateToAccountHandler,
                tokenDissociateFromAccountHandler,
                tokenFeeScheduleUpdateHandler,
                tokenPauseHandler,
                tokenUnpauseHandler,
                utilPrngHandler);

        dispatcher = new TransactionDispatcher(
                handleContext, txnCtx, handlers, accountNumbers, dynamicProperties, usageLimits);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testConstructorWithIllegalParameters() {
        assertThatThrownBy(() -> new TransactionDispatcher(
                        null, txnCtx, handlers, accountNumbers, dynamicProperties, usageLimits))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TransactionDispatcher(
                        handleContext, null, handlers, accountNumbers, dynamicProperties, usageLimits))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TransactionDispatcher(
                        handleContext, txnCtx, null, accountNumbers, dynamicProperties, usageLimits))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TransactionDispatcher(
                        handleContext, txnCtx, handlers, null, dynamicProperties, usageLimits))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() ->
                        new TransactionDispatcher(handleContext, txnCtx, handlers, accountNumbers, null, usageLimits))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TransactionDispatcher(
                        handleContext, txnCtx, handlers, accountNumbers, dynamicProperties, null))
                .isInstanceOf(NullPointerException.class);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testDispatchWithIllegalParameters() {
        // given
        final var payer = AccountID.newBuilder().build();
        final var tracker = new ReadableStoreFactory(state);
        final var validContext = new PreHandleContext(
                accountStore,
                TransactionBody.newBuilder()
                        .fileCreate(FileCreateTransactionBody.newBuilder().build())
                        .build(),
                payer);
        final var invalidSystemDelete = new PreHandleContext(
                accountStore,
                TransactionBody.newBuilder()
                        .systemDelete(SystemDeleteTransactionBody.newBuilder().build())
                        .build(),
                payer);
        final var invalidSystemUndelete = new PreHandleContext(
                accountStore,
                TransactionBody.newBuilder()
                        .systemUndelete(
                                SystemUndeleteTransactionBody.newBuilder().build())
                        .build(),
                payer);

        // then
        assertThatThrownBy(() -> dispatcher.dispatchPreHandle(null, validContext))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> dispatcher.dispatchPreHandle(tracker, null)).isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> dispatcher.dispatchPreHandle(tracker, invalidSystemDelete))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> dispatcher.dispatchPreHandle(tracker, invalidSystemUndelete))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testDataNotSetFails() {
        // given
        final var txBody = TransactionBody.newBuilder().build();
        final var payer = AccountID.newBuilder().build();
        final var tracker = new ReadableStoreFactory(state);
        final var context = new PreHandleContext(accountStore, txBody, payer);

        // then
        assertThatThrownBy(() -> dispatcher.dispatchPreHandle(tracker, context))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void testNodeStakeUpdateFails() {
        // given
        final var txBody = TransactionBody.newBuilder()
                .nodeStakeUpdate(NodeStakeUpdateTransactionBody.newBuilder().build())
                .build();
        final var payer = AccountID.newBuilder().build();
        final var tracker = new ReadableStoreFactory(state);
        final var context = new PreHandleContext(accountStore, txBody, payer);

        // then
        assertThatThrownBy(() -> dispatcher.dispatchPreHandle(tracker, context))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void dispatchesCreateTopicAsExpected() {
        final var createBuilder = mock(ConsensusCreateTopicRecordBuilder.class);

        given(consensusCreateTopicHandler.newRecordBuilder()).willReturn(createBuilder);
        given(dynamicProperties.maxNumTopics()).willReturn(123L);
        given(dynamicProperties.messageMaxBytesAllowed()).willReturn(456);
        given(createBuilder.getCreatedTopic()).willReturn(666L);
        given(writableStoreFactory.createTopicStore()).willReturn(writableTopicStore);

        dispatcher.dispatchHandle(HederaFunctionality.CONSENSUS_CREATE_TOPIC, transactionBody, writableStoreFactory);

        verify(txnCtx)
                .setCreated(
                        PbjConverter.fromPbj(TopicID.newBuilder().topicNum(666L).build()));
        verify(writableTopicStore).commit();
    }

    @Test
    void dispatchesUpdateTopicAsExpected() {
        given(writableStoreFactory.createTopicStore()).willReturn(writableTopicStore);

        dispatcher.dispatchHandle(HederaFunctionality.CONSENSUS_UPDATE_TOPIC, transactionBody, writableStoreFactory);

        verifyNoInteractions(txnCtx);
    }

    @Test
    void dispatchesDeleteTopicAsExpected() {
        given(writableStoreFactory.createTopicStore()).willReturn(writableTopicStore);

        dispatcher.dispatchHandle(HederaFunctionality.CONSENSUS_DELETE_TOPIC, transactionBody, writableStoreFactory);

        verifyNoInteractions(txnCtx);
    }

    @Test
    void dispatchesSubmitMessageAsExpected() {
        final var newRunningHash = new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        final var submitBuilder = mock(SubmitMessageRecordBuilder.class);

        given(consensusSubmitMessageHandler.newRecordBuilder()).willReturn(submitBuilder);
        given(dynamicProperties.maxNumTopics()).willReturn(123L);
        given(dynamicProperties.messageMaxBytesAllowed()).willReturn(456);
        given(submitBuilder.getNewTopicRunningHash()).willReturn(newRunningHash);
        given(submitBuilder.getNewTopicSequenceNumber()).willReturn(2L);
        final var expectedConfig = new ConsensusServiceConfig(123L, 456);
        given(writableStoreFactory.createTopicStore()).willReturn(writableTopicStore);

        doAnswer(invocation -> {
                    final var builder = (SubmitMessageRecordBuilder) invocation.getArguments()[3];
                    builder.setNewTopicMetadata(newRunningHash, 2, 3L);
                    return null;
                })
                .when(consensusSubmitMessageHandler)
                .handle(eq(handleContext), eq(transactionBody), eq(expectedConfig), any(), any());

        dispatcher.dispatchHandle(HederaFunctionality.CONSENSUS_SUBMIT_MESSAGE, transactionBody, writableStoreFactory);

        verify(txnCtx).setTopicRunningHash(newRunningHash, 2);
    }

    @Test
    void dispatchesTokenPauseAsExpected() {
        given(writableStoreFactory.createTokenStore()).willReturn(writableTokenStore);

        dispatcher.dispatchHandle(HederaFunctionality.TOKEN_PAUSE, transactionBody, writableStoreFactory);

        verify(writableTokenStore).commit();
    }

    @Test
    void dispatchesTokenUnpauseAsExpected() {
        given(writableStoreFactory.createTokenStore()).willReturn(writableTokenStore);

        dispatcher.dispatchHandle(HederaFunctionality.TOKEN_UNPAUSE, transactionBody, writableStoreFactory);

        verify(writableTokenStore).commit();
    }

    @Test
    void cannotDispatchUnsupportedOperations() {
        assertThatThrownBy(() -> dispatcher.dispatchHandle(
                        HederaFunctionality.CRYPTO_TRANSFER, transactionBody, writableStoreFactory))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @MethodSource("getDispatchParameters")
    void testPreHandleWithPayer(
            final TransactionBody txBody, final BiConsumer<TransactionHandlers, PreHandleContext> verification) {
        // given
        final var payer = AccountID.newBuilder().build();
        final var tracker = new ReadableStoreFactory(state);
        final var context = new PreHandleContext(accountStore, txBody, payer);

        // when
        dispatcher.dispatchPreHandle(tracker, context);

        // then
        verification.accept(this.handlers, context);
    }

    private static Stream<Arguments> getDispatchParameters() {
        return Stream.of(
                // consensus
                Arguments.of(
                        TransactionBody.newBuilder()
                                .consensusCreateTopic(ConsensusCreateTopicTransactionBody.DEFAULT)
                                .build(),
                        (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                verify(handlers.consensusCreateTopicHandler()).preHandle(meta)),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .consensusUpdateTopic(ConsensusUpdateTopicTransactionBody.DEFAULT)
                                .build(),
                        (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                verify(handlers.consensusUpdateTopicHandler()).preHandle(eq(meta), any()),
                        Arguments.of(
                                TransactionBody.newBuilder()
                                        .consensusDeleteTopic(ConsensusDeleteTopicTransactionBody.DEFAULT)
                                        .build(),
                                (BiConsumer<TransactionHandlers, PreHandleContext>)
                                        (handlers, meta) -> verify(handlers.consensusDeleteTopicHandler())
                                                .preHandle(eq(meta), any())),
                        Arguments.of(
                                TransactionBody.newBuilder()
                                        .consensusSubmitMessage(ConsensusSubmitMessageTransactionBody.DEFAULT)
                                        .build(),
                                (BiConsumer<TransactionHandlers, PreHandleContext>)
                                        (handlers, meta) -> verify(handlers.consensusSubmitMessageHandler())
                                                .preHandle(eq(meta), any())),

                        // contract
                        Arguments.of(
                                TransactionBody.newBuilder()
                                        .contractCreateInstance(ContractCreateTransactionBody.DEFAULT)
                                        .build(),
                                (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                        verify(handlers.contractCreateHandler()).preHandle(meta)),
                        Arguments.of(
                                TransactionBody.newBuilder()
                                        .contractUpdateInstance(ContractUpdateTransactionBody.DEFAULT)
                                        .build(),
                                (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                        verify(handlers.contractUpdateHandler()).preHandle(meta)),
                        Arguments.of(
                                TransactionBody.newBuilder()
                                        .contractCall(ContractCallTransactionBody.DEFAULT)
                                        .build(),
                                (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                        verify(handlers.contractCallHandler()).preHandle(meta)),
                        Arguments.of(
                                TransactionBody.newBuilder()
                                        .contractDeleteInstance(ContractDeleteTransactionBody.DEFAULT)
                                        .build(),
                                (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                        verify(handlers.contractDeleteHandler()).preHandle(meta)),
                        Arguments.of(
                                TransactionBody.newBuilder()
                                        .ethereumTransaction(EthereumTransactionBody.DEFAULT)
                                        .build(),
                                (BiConsumer<TransactionHandlers, PreHandleContext>)
                                        (handlers, meta) -> verify(handlers.etherumTransactionHandler())
                                                .preHandle(meta)),

                        // crypto
                        Arguments.of(
                                TransactionBody.newBuilder()
                                        .cryptoCreateAccount(CryptoCreateTransactionBody.DEFAULT)
                                        .build(),
                                (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                        verify(handlers.cryptoCreateHandler()).preHandle(meta)),
                        Arguments.of(
                                TransactionBody.newBuilder()
                                        .cryptoUpdateAccount(CryptoUpdateTransactionBody.DEFAULT)
                                        .build(),
                                (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                        verify(handlers.cryptoUpdateHandler()).preHandle(eq(meta), any())),
                        Arguments.of(
                                TransactionBody.newBuilder()
                                        .cryptoTransfer(CryptoTransferTransactionBody.DEFAULT)
                                        .build(),
                                (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                        verify(handlers.cryptoTransferHandler()).preHandle(eq(meta), any(), any())),
                        Arguments.of(
                                TransactionBody.newBuilder()
                                        .cryptoDelete(CryptoDeleteTransactionBody.DEFAULT)
                                        .build(),
                                (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                        verify(handlers.cryptoDeleteHandler()).preHandle(meta)),
                        Arguments.of(
                                TransactionBody.newBuilder()
                                        .cryptoApproveAllowance(CryptoApproveAllowanceTransactionBody.DEFAULT)
                                        .build(),
                                (BiConsumer<TransactionHandlers, PreHandleContext>)
                                        (handlers, meta) -> verify(handlers.cryptoApproveAllowanceHandler())
                                                .preHandle(meta)),
                        Arguments.of(
                                TransactionBody.newBuilder()
                                        .cryptoDeleteAllowance(CryptoDeleteAllowanceTransactionBody.DEFAULT)
                                        .build(),
                                (BiConsumer<TransactionHandlers, PreHandleContext>)
                                        (handlers, meta) -> verify(handlers.cryptoDeleteAllowanceHandler())
                                                .preHandle(meta)),
                        Arguments.of(
                                TransactionBody.newBuilder()
                                        .cryptoAddLiveHash(CryptoAddLiveHashTransactionBody.DEFAULT)
                                        .build(),
                                (BiConsumer<TransactionHandlers, PreHandleContext>)
                                        (handlers, meta) -> verify(handlers.cryptoAddLiveHashHandler())
                                                .preHandle(meta)),
                        Arguments.of(
                                TransactionBody.newBuilder()
                                        .cryptoDeleteLiveHash(CryptoDeleteLiveHashTransactionBody.DEFAULT)
                                        .build(),
                                (BiConsumer<TransactionHandlers, PreHandleContext>)
                                        (handlers, meta) -> verify(handlers.cryptoDeleteLiveHashHandler())
                                                .preHandle(meta)),

                        // file
                        Arguments.of(
                                TransactionBody.newBuilder()
                                        .fileCreate(FileCreateTransactionBody.DEFAULT)
                                        .build(),
                                (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                        verify(handlers.fileCreateHandler()).preHandle(meta)),
                        Arguments.of(
                                TransactionBody.newBuilder()
                                        .fileUpdate(FileUpdateTransactionBody.DEFAULT)
                                        .build(),
                                (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                        verify(handlers.fileUpdateHandler()).preHandle(meta)),
                        Arguments.of(
                                TransactionBody.newBuilder()
                                        .fileDelete(FileDeleteTransactionBody.DEFAULT)
                                        .build(),
                                (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                        verify(handlers.fileDeleteHandler()).preHandle(meta)),
                        Arguments.of(
                                TransactionBody.newBuilder()
                                        .fileAppend(FileAppendTransactionBody.DEFAULT)
                                        .build(),
                                (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                        verify(handlers.fileAppendHandler()).preHandle(meta)),

                        // freeze
                        Arguments.of(
                                TransactionBody.newBuilder()
                                        .freeze(FreezeTransactionBody.DEFAULT)
                                        .build(),
                                (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                        verify(handlers.freezeHandler()).preHandle(meta, any())),

                        // network
                        Arguments.of(
                                TransactionBody.newBuilder()
                                        .uncheckedSubmit(UncheckedSubmitBody.DEFAULT)
                                        .build(),
                                (BiConsumer<TransactionHandlers, PreHandleContext>)
                                        (handlers, meta) -> verify(handlers.networkUncheckedSubmitHandler())
                                                .preHandle(meta)),

                        // schedule
                        Arguments.of(
                                TransactionBody.newBuilder()
                                        .scheduleCreate(ScheduleCreateTransactionBody.DEFAULT)
                                        .build(),
                                (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                        verify(handlers.scheduleCreateHandler()).preHandle(eq(meta), any())),
                        Arguments.of(
                                TransactionBody.newBuilder()
                                        .scheduleSign(ScheduleSignTransactionBody.DEFAULT)
                                        .build(),
                                (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                        verify(handlers.scheduleSignHandler()).preHandle(eq(meta), any(), any())),
                        Arguments.of(
                                TransactionBody.newBuilder()
                                        .scheduleCreate(ScheduleCreateTransactionBody.DEFAULT)
                                        .build(),
                                (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                        verify(handlers.scheduleDeleteHandler()).preHandle(eq(meta), any())),

                        // token
                        Arguments.of(
                                TransactionBody.newBuilder()
                                        .tokenCreation(TokenCreateTransactionBody.DEFAULT)
                                        .build(),
                                (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                        verify(handlers.tokenCreateHandler()).preHandle(meta)),
                        Arguments.of(
                                TransactionBody.newBuilder()
                                        .tokenUpdate(TokenUpdateTransactionBody.DEFAULT)
                                        .build(),
                                (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                        verify(handlers.tokenUpdateHandler()).preHandle(eq(meta), any())),
                        Arguments.of(
                                TransactionBody.newBuilder()
                                        .tokenMint(TokenMintTransactionBody.DEFAULT)
                                        .build(),
                                (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                        verify(handlers.tokenMintHandler()).preHandle(eq(meta), any())),
                        Arguments.of(
                                TransactionBody.newBuilder()
                                        .tokenBurn(TokenBurnTransactionBody.DEFAULT)
                                        .build(),
                                (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                        verify(handlers.tokenBurnHandler()).preHandle(eq(meta), any())),
                        Arguments.of(
                                TransactionBody.newBuilder()
                                        .tokenDeletion(TokenDeleteTransactionBody.DEFAULT)
                                        .build(),
                                (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                        verify(handlers.tokenDeleteHandler()).preHandle(eq(meta), any())),
                        Arguments.of(
                                TransactionBody.newBuilder()
                                        .tokenWipe(TokenWipeAccountTransactionBody.DEFAULT)
                                        .build(),
                                (BiConsumer<TransactionHandlers, PreHandleContext>)
                                        (handlers, meta) -> verify(handlers.tokenAccountWipeHandler())
                                                .preHandle(eq(meta), any())),
                        Arguments.of(
                                TransactionBody.newBuilder()
                                        .tokenFreeze(TokenFreezeAccountTransactionBody.DEFAULT)
                                        .build(),
                                (BiConsumer<TransactionHandlers, PreHandleContext>)
                                        (handlers, meta) -> verify(handlers.tokenFreezeAccountHandler())
                                                .preHandle(eq(meta), any())),
                        Arguments.of(
                                TransactionBody.newBuilder()
                                        .tokenUnfreeze(TokenUnfreezeAccountTransactionBody.DEFAULT)
                                        .build(),
                                (BiConsumer<TransactionHandlers, PreHandleContext>)
                                        (handlers, meta) -> verify(handlers.tokenUnfreezeAccountHandler())
                                                .preHandle(eq(meta), any())),
                        Arguments.of(
                                TransactionBody.newBuilder()
                                        .tokenGrantKyc(TokenGrantKycTransactionBody.DEFAULT)
                                        .build(),
                                (BiConsumer<TransactionHandlers, PreHandleContext>)
                                        (handlers, meta) -> verify(handlers.tokenGrantKycToAccountHandler())
                                                .preHandle(eq(meta), any())),
                        Arguments.of(
                                TransactionBody.newBuilder()
                                        .tokenRevokeKyc(TokenRevokeKycTransactionBody.DEFAULT)
                                        .build(),
                                (BiConsumer<TransactionHandlers, PreHandleContext>)
                                        (handlers, meta) -> verify(handlers.tokenRevokeKycFromAccountHandler())
                                                .preHandle(eq(meta), any())),
                        Arguments.of(
                                TransactionBody.newBuilder()
                                        .tokenAssociate(TokenAssociateTransactionBody.DEFAULT)
                                        .build(),
                                (BiConsumer<TransactionHandlers, PreHandleContext>)
                                        (handlers, meta) -> verify(handlers.tokenAssociateToAccountHandler())
                                                .preHandle(meta)),
                        Arguments.of(
                                TransactionBody.newBuilder()
                                        .tokenDissociate(TokenDissociateTransactionBody.DEFAULT)
                                        .build(),
                                (BiConsumer<TransactionHandlers, PreHandleContext>)
                                        (handlers, meta) -> verify(handlers.tokenDissociateFromAccountHandler())
                                                .preHandle(meta)),
                        Arguments.of(
                                TransactionBody.newBuilder()
                                        .tokenFeeScheduleUpdate(TokenFeeScheduleUpdateTransactionBody.DEFAULT)
                                        .build(),
                                (BiConsumer<TransactionHandlers, PreHandleContext>)
                                        (handlers, meta) -> verify(handlers.tokenFeeScheduleUpdateHandler())
                                                .preHandle(eq(meta), any())),
                        Arguments.of(
                                TransactionBody.newBuilder()
                                        .tokenPause(TokenPauseTransactionBody.DEFAULT)
                                        .build(),
                                (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                        verify(handlers.tokenPauseHandler()).preHandle(eq(meta), any())),
                        Arguments.of(
                                TransactionBody.newBuilder()
                                        .tokenUnpause(TokenUnpauseTransactionBody.DEFAULT)
                                        .build(),
                                (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                        verify(handlers.tokenUnpauseHandler()).preHandle(eq(meta), any())),

                        // util
                        Arguments.of(
                                TransactionBody.newBuilder()
                                        .utilPrng(UtilPrngTransactionBody.DEFAULT)
                                        .build(),
                                (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                        verify(handlers.utilPrngHandler()).preHandle(meta)),

                        // mixed
                        Arguments.of(
                                TransactionBody.newBuilder()
                                        .systemDelete(SystemDeleteTransactionBody.newBuilder()
                                                .contractID(ContractID.DEFAULT)
                                                .build())
                                        .build(),
                                (BiConsumer<TransactionHandlers, PreHandleContext>)
                                        (handlers, meta) -> verify(handlers.contractSystemDeleteHandler())
                                                .preHandle(meta)),
                        Arguments.of(
                                TransactionBody.newBuilder()
                                        .systemDelete(SystemDeleteTransactionBody.newBuilder()
                                                .fileID(FileID.DEFAULT)
                                                .build())
                                        .build(),
                                (BiConsumer<TransactionHandlers, PreHandleContext>)
                                        (handlers, meta) -> verify(handlers.fileSystemDeleteHandler())
                                                .preHandle(meta)),
                        Arguments.of(
                                TransactionBody.newBuilder()
                                        .systemUndelete(SystemUndeleteTransactionBody.newBuilder()
                                                .contractID(ContractID.DEFAULT)
                                                .build())
                                        .build(),
                                (BiConsumer<TransactionHandlers, PreHandleContext>)
                                        (handlers, meta) -> verify(handlers.contractSystemUndeleteHandler())
                                                .preHandle(meta)),
                        Arguments.of(
                                TransactionBody.newBuilder()
                                        .systemUndelete(SystemUndeleteTransactionBody.newBuilder()
                                                .fileID(FileID.DEFAULT)
                                                .build())
                                        .build(),
                                (BiConsumer<TransactionHandlers, PreHandleContext>)
                                        (handlers, meta) -> verify(handlers.fileSystemUndeleteHandler())
                                                .preHandle(meta))));
    }
}
