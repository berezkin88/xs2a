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
import de.adorsys.psd2.xs2a.core.mapper.ServiceType;
import de.adorsys.psd2.xs2a.core.profile.PaymentType;
import de.adorsys.psd2.xs2a.core.psu.PsuIdData;
import de.adorsys.psd2.xs2a.core.sca.ScaStatus;
import de.adorsys.psd2.xs2a.domain.ResponseObject;
import de.adorsys.psd2.xs2a.domain.consent.GetPaymentScaStatusRequest;
import de.adorsys.psd2.xs2a.domain.consent.Xs2aScaStatusResponse;
import de.adorsys.psd2.xs2a.service.context.SpiContextDataProvider;
import de.adorsys.psd2.xs2a.service.mapper.spi_xs2a_mappers.SpiErrorMapper;
import de.adorsys.psd2.xs2a.service.payment.support.SpiPaymentFactory;
import de.adorsys.psd2.xs2a.service.spi.SpiAspspConsentDataProviderFactory;
import de.adorsys.psd2.xs2a.spi.domain.response.SpiResponse;
import de.adorsys.psd2.xs2a.spi.service.PaymentCancellationSpi;
import de.adorsys.psd2.xs2a.spi.service.SpiPayment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCancellationServiceForAuthorisation {
    private final PaymentCancellationSpi paymentCancellationSpi;
    private final PaymentCancellationAuthorisationService paymentCancellationAuthorisationService;
    private final SpiContextDataProvider spiContextDataProvider;
    private final SpiAspspConsentDataProviderFactory aspspConsentDataProviderFactory;
    private final SpiErrorMapper spiErrorMapper;
    private final SpiPaymentFactory spiPaymentFactory;

    /**
     * Gets SCA status response of payment authorisation
     *
     * @param paymentId       String representation of consent identifier
     * @param authorisationId String representation of authorisation identifier
     * @return Response containing SCA status of the authorisation and optionally trusted beneficiaries flag or corresponding error
     */
    public ResponseObject<Xs2aScaStatusResponse> getPaymentCancellationAuthorisationScaStatus(String paymentId, String authorisationId, PaymentType paymentType, String paymentProduct) {
        ResponseObject<GetPaymentScaStatusRequest> getScaStatusRequestResponse = paymentCancellationAuthorisationService.getPaymentCancellationAuthorisationScaStatus(paymentId, authorisationId, paymentType, paymentProduct);
        if (getScaStatusRequestResponse.hasError()) {
            return ResponseObject.<Xs2aScaStatusResponse>builder()
                       .fail(getScaStatusRequestResponse.getError())
                       .build();
        }

        GetPaymentScaStatusRequest getPaymentScaStatusRequest = getScaStatusRequestResponse.getBody();
        ScaStatus scaStatus = getPaymentScaStatusRequest.getScaStatus();

        if (scaStatus.isNotFinalisedStatus()) {
            Xs2aScaStatusResponse response = new Xs2aScaStatusResponse(scaStatus, null);
            return ResponseObject.<Xs2aScaStatusResponse>builder()
                       .body(response)
                       .build();
        }

        ResponseObject<Boolean> beneficiaryFlagResponse = getTrustedBeneficiaryFlag(getPaymentScaStatusRequest.getPsuIdData(),
                                                                                    paymentId, authorisationId,
                                                                                    getPaymentScaStatusRequest.getPisCommonPaymentResponse());
        if (beneficiaryFlagResponse.hasError()) {
            return ResponseObject.<Xs2aScaStatusResponse>builder()
                       .fail(beneficiaryFlagResponse.getError())
                       .build();
        }

        Boolean beneficiaryFlag = beneficiaryFlagResponse.getBody();
        Xs2aScaStatusResponse response = new Xs2aScaStatusResponse(scaStatus, beneficiaryFlag);

        return ResponseObject.<Xs2aScaStatusResponse>builder()
                   .body(response)
                   .build();
    }

    private ResponseObject<Boolean> getTrustedBeneficiaryFlag(PsuIdData psuIdData, String paymentId, String authorisationId, PisCommonPaymentResponse pisCommonPaymentResponse) {
        SpiPayment spiPayment = spiPaymentFactory.createSpiPaymentByPaymentType(pisCommonPaymentResponse).orElse(null);

        SpiResponse<Boolean> spiResponse =
            paymentCancellationSpi.requestTrustedBeneficiaryFlag(spiContextDataProvider.provideWithPsuIdData(psuIdData),
                                                                 spiPayment,
                                                                 authorisationId,
                                                                 aspspConsentDataProviderFactory.getSpiAspspDataProviderFor(paymentId));

        if (spiResponse.hasError()) {
            ErrorHolder errorHolder = spiErrorMapper.mapToErrorHolder(spiResponse, ServiceType.PIS);
            log.info("Authorisation-ID [{}], Payment-ID [{}]. Get trusted beneficiaries flag for cancellation failed.",
                     authorisationId, paymentId);
            return ResponseObject.<Boolean>builder()
                       .fail(errorHolder)
                       .build();
        }

        return ResponseObject.<Boolean>builder()
                   .body(spiResponse.getPayload())
                   .build();
    }
}
