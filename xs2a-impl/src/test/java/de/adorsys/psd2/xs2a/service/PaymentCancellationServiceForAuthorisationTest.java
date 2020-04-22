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

package de.adorsys.psd2.xs2a.service;

import de.adorsys.psd2.consent.api.pis.proto.PisCommonPaymentResponse;
import de.adorsys.psd2.xs2a.core.domain.ErrorHolder;
import de.adorsys.psd2.xs2a.core.error.ErrorType;
import de.adorsys.psd2.xs2a.core.error.MessageError;
import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.core.error.TppMessage;
import de.adorsys.psd2.xs2a.core.mapper.ServiceType;
import de.adorsys.psd2.xs2a.core.profile.PaymentType;
import de.adorsys.psd2.xs2a.core.psu.PsuIdData;
import de.adorsys.psd2.xs2a.core.sca.ScaStatus;
import de.adorsys.psd2.xs2a.domain.ResponseObject;
import de.adorsys.psd2.xs2a.domain.consent.GetPaymentScaStatusRequest;
import de.adorsys.psd2.xs2a.domain.consent.Xs2aScaStatusResponse;
import de.adorsys.psd2.xs2a.service.context.SpiContextDataProvider;
import de.adorsys.psd2.xs2a.service.mapper.payment.SpiPaymentFactory;
import de.adorsys.psd2.xs2a.service.mapper.spi_xs2a_mappers.SpiErrorMapper;
import de.adorsys.psd2.xs2a.service.spi.SpiAspspConsentDataProviderFactory;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.service.PaymentCancellationSpi;
import de.adorsys.xs2a.reader.JsonReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentCancellationServiceForAuthorisationTest {
    private static final String AUTHORISATION_ID = "3278921mxl-n2131-13nw";
    private static final String PAYMENT_ID = "3278921mxl-n2131-13nw";
    private static final String CORRECT_PSU_ID = "marion.mueller";
    private static final String PAYMENT_PRODUCT = "sepa-credit-transfers";
    private static final PsuIdData PSU_ID_DATA = new PsuIdData(CORRECT_PSU_ID, null, null, null, null);

    @InjectMocks
    private PaymentCancellationServiceForAuthorisation paymentCancellationServiceForAuthorisation;

    @Mock
    private PaymentCancellationSpi paymentCancellationSpi;
    @Mock
    private PaymentCancellationAuthorisationService paymentCancellationAuthorisationService;
    @Mock
    private SpiContextDataProvider spiContextDataProvider;
    @Mock
    private SpiAspspConsentDataProviderFactory aspspConsentDataProviderFactory;
    @Mock
    private SpiErrorMapper spiErrorMapper;
    @Mock
    private SpiPaymentFactory spiPaymentFactory;

    private JsonReader jsonReader = new JsonReader();
    private PisCommonPaymentResponse pisCommonPaymentResponse;

    @BeforeEach
    void setUp() {
        pisCommonPaymentResponse = jsonReader.getObjectFromFile("json/service/payment/pis-common-payment-response.json", PisCommonPaymentResponse.class);
    }

    @Test
    void getPaymentAuthorisationScaStatus_errorFromAuthorisation() {
        // Given
        ResponseObject<GetPaymentScaStatusRequest> getScaStatusRequestResponse = ResponseObject.<GetPaymentScaStatusRequest>builder()
                                                                                     .fail(new MessageError())
                                                                                     .build();

        when(paymentCancellationAuthorisationService.getPaymentCancellationAuthorisationScaStatus(PAYMENT_ID, AUTHORISATION_ID, PaymentType.SINGLE, PAYMENT_PRODUCT))
            .thenReturn(getScaStatusRequestResponse);

        // When
        ResponseObject<Xs2aScaStatusResponse> actual = paymentCancellationServiceForAuthorisation.getPaymentCancellationAuthorisationScaStatus(PAYMENT_ID, AUTHORISATION_ID, PaymentType.SINGLE, PAYMENT_PRODUCT);

        // Then
        assertThat(actual).isNotNull();
        assertThat(actual.hasError()).isTrue();
        verify(spiPaymentFactory, never()).createSpiPaymentByPaymentType(any());
    }

    @Test
    void getPaymentAuthorisationScaStatus_successNotFinalised() {
        // Given
        Xs2aScaStatusResponse expected = new Xs2aScaStatusResponse(ScaStatus.RECEIVED, null);

        GetPaymentScaStatusRequest getPaymentScaStatusRequest = new GetPaymentScaStatusRequest(PSU_ID_DATA, pisCommonPaymentResponse, ScaStatus.RECEIVED);
        ResponseObject<GetPaymentScaStatusRequest> getScaStatusRequestResponse = ResponseObject.<GetPaymentScaStatusRequest>builder()
                                                                                     .body(getPaymentScaStatusRequest)
                                                                                     .build();

        when(paymentCancellationAuthorisationService.getPaymentCancellationAuthorisationScaStatus(PAYMENT_ID, AUTHORISATION_ID, PaymentType.SINGLE, PAYMENT_PRODUCT))
            .thenReturn(getScaStatusRequestResponse);

        // When
        ResponseObject<Xs2aScaStatusResponse> actual = paymentCancellationServiceForAuthorisation.getPaymentCancellationAuthorisationScaStatus(PAYMENT_ID, AUTHORISATION_ID, PaymentType.SINGLE, PAYMENT_PRODUCT);

        // Then
        assertThat(actual).isNotNull();
        assertThat(actual.hasError()).isFalse();
        assertThat(actual.getBody()).isEqualTo(expected);
    }

    @Test
    void getPaymentAuthorisationScaStatus_spiError() {
        // Given
        GetPaymentScaStatusRequest getPaymentScaStatusRequest = new GetPaymentScaStatusRequest(PSU_ID_DATA, pisCommonPaymentResponse, ScaStatus.FINALISED);
        ResponseObject<GetPaymentScaStatusRequest> getScaStatusRequestResponse = ResponseObject.<GetPaymentScaStatusRequest>builder()
                                                                                     .body(getPaymentScaStatusRequest)
                                                                                     .build();

        when(paymentCancellationAuthorisationService.getPaymentCancellationAuthorisationScaStatus(PAYMENT_ID, AUTHORISATION_ID, PaymentType.SINGLE, PAYMENT_PRODUCT))
            .thenReturn(getScaStatusRequestResponse);
        when(spiPaymentFactory.createSpiPaymentByPaymentType(pisCommonPaymentResponse)).thenReturn(Optional.empty());

        SpiResponse<Boolean> spiResponse = SpiResponse.<Boolean>builder()
                                               .error(new TppMessage(MessageErrorCode.FORMAT_ERROR))
                                               .build();
        when(paymentCancellationSpi.requestTrustedBeneficiaryFlag(any(), any(), any(), any())).thenReturn(spiResponse);
        when(spiErrorMapper.mapToErrorHolder(spiResponse, ServiceType.PIS)).thenReturn(ErrorHolder.builder(ErrorType.PIS_400).build());

        // When
        ResponseObject<Xs2aScaStatusResponse> actual = paymentCancellationServiceForAuthorisation.getPaymentCancellationAuthorisationScaStatus(PAYMENT_ID, AUTHORISATION_ID, PaymentType.SINGLE, PAYMENT_PRODUCT);

        // Then
        assertThat(actual).isNotNull();
        assertThat(actual.hasError()).isTrue();
        verify(spiPaymentFactory, times(1)).createSpiPaymentByPaymentType(any());
        verify(paymentCancellationSpi, times(1)).requestTrustedBeneficiaryFlag(any(), any(), any(), any());
        verify(spiErrorMapper, times(1)).mapToErrorHolder(spiResponse, ServiceType.PIS);
    }

    @Test
    void getPaymentAuthorisationScaStatus_success() {
        // Given
        Xs2aScaStatusResponse expected = new Xs2aScaStatusResponse(ScaStatus.FINALISED, true);

        GetPaymentScaStatusRequest getPaymentScaStatusRequest = new GetPaymentScaStatusRequest(PSU_ID_DATA, pisCommonPaymentResponse, ScaStatus.FINALISED);
        ResponseObject<GetPaymentScaStatusRequest> getScaStatusRequestResponse = ResponseObject.<GetPaymentScaStatusRequest>builder()
                                                                                     .body(getPaymentScaStatusRequest)
                                                                                     .build();

        when(paymentCancellationAuthorisationService.getPaymentCancellationAuthorisationScaStatus(PAYMENT_ID, AUTHORISATION_ID, PaymentType.SINGLE, PAYMENT_PRODUCT))
            .thenReturn(getScaStatusRequestResponse);
        when(spiPaymentFactory.createSpiPaymentByPaymentType(pisCommonPaymentResponse)).thenReturn(Optional.empty());

        SpiResponse<Boolean> spiResponse = SpiResponse.<Boolean>builder()
                                               .payload(true)
                                               .build();
        when(paymentCancellationSpi.requestTrustedBeneficiaryFlag(any(), any(), any(), any())).thenReturn(spiResponse);

        // When
        ResponseObject<Xs2aScaStatusResponse> actual = paymentCancellationServiceForAuthorisation.getPaymentCancellationAuthorisationScaStatus(PAYMENT_ID, AUTHORISATION_ID, PaymentType.SINGLE, PAYMENT_PRODUCT);

        // Then
        assertThat(actual).isNotNull();
        assertThat(actual.hasError()).isFalse();
        assertThat(actual.getBody()).isEqualTo(expected);

        verify(spiPaymentFactory, times(1)).createSpiPaymentByPaymentType(any());
        verify(paymentCancellationSpi, times(1)).requestTrustedBeneficiaryFlag(any(), any(), any(), any());
        verify(spiErrorMapper, never()).mapToErrorHolder( any(), any());
    }
}
