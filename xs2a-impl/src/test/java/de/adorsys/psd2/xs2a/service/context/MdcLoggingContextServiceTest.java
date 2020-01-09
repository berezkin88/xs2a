/*
 * Copyright 2018-2020 adorsys GmbH & Co KG
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.adorsys.psd2.xs2a.service.context;

import de.adorsys.psd2.xs2a.core.consent.ConsentStatus;
import de.adorsys.psd2.xs2a.core.pis.TransactionStatus;
import de.adorsys.psd2.xs2a.core.sca.ScaStatus;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.MDC;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class MdcLoggingContextServiceTest {
    private static final String CONSENT_STATUS_KEY = "consentStatus";
    private static final String TRANSACTION_STATUS_KEY = "transactionStatus";
    private static final String SCA_STATUS_KEY = "scaStatus";
    private static final String INTERNAL_REQUEST_ID_KEY = "internal-request-id";
    private static final String X_REQUEST_ID_KEY = "x-request-id";


    private MdcLoggingContextService mdcLoggingContextService = new MdcLoggingContextService();

    @Before
    public void setUp() {
        MDC.clear();
    }

    @Test
    public void storeConsentStatus_shouldPutStatusIntoMdc() {
        // Given
        ConsentStatus status = ConsentStatus.RECEIVED;

        // When
        mdcLoggingContextService.storeConsentStatus(status);
        // Then
        assertEquals(status.getValue(), MDC.get(CONSENT_STATUS_KEY));
    }

    @Test
    public void getConsentStatus_shouldTakeStatusFromMdc() {
        // Given
        String expectedStatus = ConsentStatus.REJECTED.getValue();
        MDC.put(CONSENT_STATUS_KEY, expectedStatus);

        // When
        String actualStatus = mdcLoggingContextService.getConsentStatus();

        // Then
        assertEquals(expectedStatus, actualStatus);
    }

    @Test
    public void storeTransactionStatus_shouldPutStatusIntoMdc() {
        // Given
        TransactionStatus status = TransactionStatus.ACSP;

        // When
        mdcLoggingContextService.storeTransactionStatus(status);

        // Then
        assertEquals(status.getTransactionStatus(), MDC.get(TRANSACTION_STATUS_KEY));
    }

    @Test
    public void getTransactionStatus_shouldTakeStatusFromMdc() {
        // Given
        String expectedStatus = TransactionStatus.ACCC.getTransactionStatus();
        MDC.put(TRANSACTION_STATUS_KEY, expectedStatus);

        // When
        String actualStatus = mdcLoggingContextService.getTransactionStatus();

        // Then
        assertEquals(expectedStatus, actualStatus);
    }

    @Test
    public void storeTransactionAndScaStatus() {
        // Given
        TransactionStatus transactionStatus = TransactionStatus.ACSP;
        ScaStatus scaStatus = ScaStatus.PSUAUTHENTICATED;

        // When
        mdcLoggingContextService.storeTransactionAndScaStatus(transactionStatus, scaStatus);

        // Then
        assertEquals(transactionStatus.getTransactionStatus(), MDC.get(TRANSACTION_STATUS_KEY));
        assertEquals(scaStatus.getValue(), MDC.get(SCA_STATUS_KEY));
    }

    @Test
    public void storeScaStatus_shouldPutStatusIntoMdc() {
        // Given
        ScaStatus status = ScaStatus.PSUAUTHENTICATED;

        // When
        mdcLoggingContextService.storeScaStatus(status);

        // Then
        assertEquals(status.getValue(), MDC.get(SCA_STATUS_KEY));
    }

    @Test
    public void getScaStatus_shouldTakeStatusFromMdc() {
        // Given
        String expectedStatus = ScaStatus.PSUAUTHENTICATED.getValue();
        MDC.put(SCA_STATUS_KEY, expectedStatus);

        // When
        String actualStatus = mdcLoggingContextService.getScaStatus();

        // Then
        assertEquals(expectedStatus, actualStatus);
    }

    @Test
    public void storeRequestInformation_shouldPutRequestIdsIntoMdc() {
        // Given
        UUID xRequestId = UUID.fromString("0d7f200e-09b4-46f5-85bd-f4ea89fccace");
        UUID internalRequestId = UUID.fromString("9fe83704-6019-46fa-b8aa-53fb8fa667ea");

        // When
        mdcLoggingContextService.storeRequestInformation(internalRequestId, xRequestId);

        // Then
        assertEquals(internalRequestId.toString(), MDC.get(INTERNAL_REQUEST_ID_KEY));
        assertEquals(xRequestId.toString(), MDC.get(X_REQUEST_ID_KEY));
    }

    @Test
    public void clearContext_shouldClearMdc() {
        // Given
        MDC.put(CONSENT_STATUS_KEY, "some consent status");
        MDC.put(TRANSACTION_STATUS_KEY, "some transaction status");
        MDC.put(SCA_STATUS_KEY, "some sca status");

        // When
        mdcLoggingContextService.clearContext();

        // Then
        assertNull(MDC.get(CONSENT_STATUS_KEY));
        assertNull(MDC.get(TRANSACTION_STATUS_KEY));
        assertNull(MDC.get(SCA_STATUS_KEY));
    }
}
