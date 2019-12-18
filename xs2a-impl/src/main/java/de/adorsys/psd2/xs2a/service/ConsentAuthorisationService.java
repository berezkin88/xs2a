/*
 * Copyright 2018-2019 adorsys GmbH & Co KG
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

import de.adorsys.psd2.event.core.model.EventType;
import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.core.profile.ScaApproach;
import de.adorsys.psd2.xs2a.core.psu.PsuIdData;
import de.adorsys.psd2.xs2a.core.sca.ScaStatus;
import de.adorsys.psd2.xs2a.domain.ResponseObject;
import de.adorsys.psd2.xs2a.domain.authorisation.AuthorisationResponse;
import de.adorsys.psd2.xs2a.domain.consent.*;
import de.adorsys.psd2.xs2a.service.authorization.AuthorisationChainResponsibilityService;
import de.adorsys.psd2.xs2a.service.authorization.ais.AisAuthorisationConfirmationService;
import de.adorsys.psd2.xs2a.service.authorization.ais.AisAuthorizationService;
import de.adorsys.psd2.xs2a.service.authorization.ais.AisScaAuthorisationServiceResolver;
import de.adorsys.psd2.xs2a.service.authorization.processor.model.AisAuthorisationProcessorRequest;
import de.adorsys.psd2.xs2a.service.consent.Xs2aAisConsentService;
import de.adorsys.psd2.xs2a.service.context.LoggingContextService;
import de.adorsys.psd2.xs2a.service.event.Xs2aEventService;
import de.adorsys.psd2.xs2a.service.validator.AisEndpointAccessCheckerService;
import de.adorsys.psd2.xs2a.service.validator.ValidationResult;
import de.adorsys.psd2.xs2a.service.validator.ais.consent.dto.CreateConsentAuthorisationObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Optional;

import static de.adorsys.psd2.xs2a.core.domain.TppMessageInformation.of;
import static de.adorsys.psd2.xs2a.core.error.ErrorType.*;
import static de.adorsys.psd2.xs2a.core.error.MessageErrorCode.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsentAuthorisationService {

    private final Xs2aAisConsentService aisConsentService;
    private final AisScaAuthorisationServiceResolver aisScaAuthorisationServiceResolver;
    private final AisEndpointAccessCheckerService endpointAccessCheckerService;
    private final Xs2aEventService xs2aEventService;
    private final ConsentValidationService consentValidationService;
    private final RequestProviderService requestProviderService;
    private final AuthorisationChainResponsibilityService authorisationChainResponsibilityService;
    private final LoggingContextService loggingContextService;
    private final AisAuthorisationConfirmationService aisAuthorisationConfirmationService;

    public ResponseObject<AuthorisationResponse> createAisAuthorisation(PsuIdData psuData, String consentId, String password) {
        ResponseObject<CreateConsentAuthorizationResponse> createAisAuthorizationResponse = createConsentAuthorizationWithResponse(psuData, consentId);

        if (createAisAuthorizationResponse.hasError()) {
            return ResponseObject.<AuthorisationResponse>builder()
                       .fail(createAisAuthorizationResponse.getError())
                       .build();
        }

        PsuIdData psuIdDataFromResponse = createAisAuthorizationResponse.getBody().getPsuIdData();
        if (psuIdDataFromResponse == null || psuIdDataFromResponse.isEmpty() || StringUtils.isBlank(password)) {
            loggingContextService.storeScaStatus(createAisAuthorizationResponse.getBody().getScaStatus());
            return ResponseObject.<AuthorisationResponse>builder()
                       .body(createAisAuthorizationResponse.getBody())
                       .build();
        }

        String authorisationId = createAisAuthorizationResponse.getBody().getAuthorisationId();

        UpdateConsentPsuDataReq updatePsuData = new UpdateConsentPsuDataReq();
        updatePsuData.setPsuData(psuData);
        updatePsuData.setConsentId(consentId);
        updatePsuData.setAuthorizationId(authorisationId);
        updatePsuData.setPassword(password);

        ResponseObject<UpdateConsentPsuDataResponse> updatePsuDataResponse = updateConsentPsuData(updatePsuData);
        if (updatePsuDataResponse.hasError()) {
            return ResponseObject.<AuthorisationResponse>builder()
                       .fail(updatePsuDataResponse.getError())
                       .build();
        }

        return ResponseObject.<AuthorisationResponse>builder()
                   .body(updatePsuDataResponse.getBody())
                   .build();
    }

    public ResponseObject<Xs2aAuthorisationSubResources> getConsentInitiationAuthorisations(String consentId) {
        xs2aEventService.recordAisTppRequest(consentId, EventType.GET_CONSENT_AUTHORISATION_REQUEST_RECEIVED);

        Optional<AccountConsent> accountConsentOptional = aisConsentService.getAccountConsentById(consentId);
        if (!accountConsentOptional.isPresent()) {
            log.info("InR-ID: [{}], X-Request-ID: [{}], Consent-ID: [{}]. Get consent initiation authorisations failed: consent not found by id",
                     requestProviderService.getInternalRequestId(), requestProviderService.getRequestId(), consentId);
            return ResponseObject.<Xs2aAuthorisationSubResources>builder()
                       .fail(AIS_403, of(CONSENT_UNKNOWN_403)).build();
        }
        AccountConsent accountConsent = accountConsentOptional.get();

        ValidationResult validationResult = consentValidationService.validateConsentAuthorisationOnGettingById(accountConsent);
        if (validationResult.isNotValid()) {
            log.info("InR-ID: [{}], X-Request-ID: [{}], Consent-ID: [{}]. Get consent authorisations - validation failed: {}",
                     requestProviderService.getInternalRequestId(), requestProviderService.getRequestId(), consentId, validationResult.getMessageError());
            return ResponseObject.<Xs2aAuthorisationSubResources>builder()
                       .fail(validationResult.getMessageError())
                       .build();
        }

        loggingContextService.storeConsentStatus(accountConsent.getConsentStatus());

        return getAuthorisationSubResources(consentId)
                   .map(resp -> ResponseObject.<Xs2aAuthorisationSubResources>builder().body(resp).build())
                   .orElseGet(() -> {
                       log.info("InR-ID: [{}], X-Request-ID: [{}], Consent-ID: [{}]. Get consent initiation authorisations failed: authorisation not found at CMS by consent id",
                                requestProviderService.getInternalRequestId(), requestProviderService.getRequestId(), consentId);
                       return ResponseObject.<Xs2aAuthorisationSubResources>builder()
                                  .fail(AIS_404, of(RESOURCE_UNKNOWN_404))
                                  .build();
                   });
    }

    public ResponseObject<ScaStatus> getConsentAuthorisationScaStatus(String consentId, String authorisationId) {
        xs2aEventService.recordAisTppRequest(consentId, EventType.GET_CONSENT_SCA_STATUS_REQUEST_RECEIVED);

        Optional<AccountConsent> accountConsentOptional = aisConsentService.getAccountConsentById(consentId);
        if (!accountConsentOptional.isPresent()) {
            log.info("InR-ID: [{}], X-Request-ID: [{}], Consent-ID: [{}]. Get consent authorisation SCA status failed: consent not found by id",
                     requestProviderService.getInternalRequestId(), requestProviderService.getRequestId(), consentId);
            return ResponseObject.<ScaStatus>builder()
                       .fail(AIS_403, of(CONSENT_UNKNOWN_403)).build();
        }
        AccountConsent accountConsent = accountConsentOptional.get();

        ValidationResult validationResult = consentValidationService.validateConsentAuthorisationScaStatus(accountConsent, authorisationId);
        if (validationResult.isNotValid()) {
            log.info("InR-ID: [{}], X-Request-ID: [{}], Consent-ID: [{}], Authorisation-ID [{}]. Get consent authorisation SCA status - validation failed: {}",
                     requestProviderService.getInternalRequestId(), requestProviderService.getRequestId(), consentId, authorisationId, validationResult.getMessageError());
            return ResponseObject.<ScaStatus>builder()
                       .fail(validationResult.getMessageError())
                       .build();
        }

        Optional<ScaStatus> scaStatusOptional = aisScaAuthorisationServiceResolver.getServiceInitiation(authorisationId)
                                                    .getAuthorisationScaStatus(consentId, authorisationId);

        if (!scaStatusOptional.isPresent()) {
            log.info("InR-ID: [{}], X-Request-ID: [{}], Consent-ID: [{}]. Get consent authorisation SCA status failed: consent not found at CMS by id",
                     requestProviderService.getInternalRequestId(), requestProviderService.getRequestId(), consentId);
            return ResponseObject.<ScaStatus>builder()
                       .fail(AIS_403, of(RESOURCE_UNKNOWN_403))
                       .build();
        }

        ScaStatus scaStatus = scaStatusOptional.get();

        loggingContextService.storeConsentStatus(accountConsent.getConsentStatus());
        loggingContextService.storeScaStatus(scaStatus);

        return ResponseObject.<ScaStatus>builder()
                   .body(scaStatus)
                   .build();
    }

    public ResponseObject<UpdateConsentPsuDataResponse> updateConsentPsuData(UpdateConsentPsuDataReq updatePsuData) {
        xs2aEventService.recordAisTppRequest(updatePsuData.getConsentId(), EventType.UPDATE_AIS_CONSENT_PSU_DATA_REQUEST_RECEIVED, updatePsuData);

        String consentId = updatePsuData.getConsentId();
        String authorisationId = updatePsuData.getAuthorizationId();
        boolean confirmationCodeReceived = StringUtils.isNotBlank(updatePsuData.getConfirmationCode());

        if (!endpointAccessCheckerService.isEndpointAccessible(authorisationId, consentId, confirmationCodeReceived)) {
            log.info("InR-ID: [{}], X-Request-ID: [{}], Consent-ID: [{}], Authorisation-ID [{}]. Update consent PSU data failed: update endpoint is blocked for current authorisation",
                     requestProviderService.getInternalRequestId(), requestProviderService.getRequestId(), consentId, authorisationId);
            return ResponseObject.<UpdateConsentPsuDataResponse>builder()
                       .fail(AIS_403, of(SERVICE_BLOCKED))
                       .build();
        }

        Optional<AccountConsent> accountConsentOptional = aisConsentService.getAccountConsentById(consentId);

        if (!accountConsentOptional.isPresent()) {
            log.info("InR-ID: [{}], X-Request-ID: [{}], Consent-ID: [{}]. Update consent PSU data failed: consent not found by id",
                     requestProviderService.getInternalRequestId(), requestProviderService.getRequestId(), consentId);
            return ResponseObject.<UpdateConsentPsuDataResponse>builder()
                       .fail(AIS_403, of(CONSENT_UNKNOWN_403)).build();
        }
        AccountConsent accountConsent = accountConsentOptional.get();

        loggingContextService.storeConsentStatus(accountConsent.getConsentStatus());
        ValidationResult validationResult = consentValidationService.validateConsentPsuDataOnUpdate(accountConsent, updatePsuData);

        if (validationResult.isNotValid()) {
            MessageErrorCode messageErrorCode = validationResult.getMessageError().getTppMessage().getMessageErrorCode();

            if (EnumSet.of(PSU_CREDENTIALS_INVALID, FORMAT_ERROR_NO_PSU).contains(messageErrorCode)) {
                aisConsentService.updateConsentAuthorisationStatus(authorisationId, ScaStatus.FAILED);
            }

            log.info("InR-ID: [{}], X-Request-ID: [{}], Consent-ID: [{}], Authorisation-ID [{}]. Update consent PSU data - validation failed: {}",
                     requestProviderService.getInternalRequestId(), requestProviderService.getRequestId(), consentId, authorisationId, validationResult.getMessageError());
            return ResponseObject.<UpdateConsentPsuDataResponse>builder()
                       .fail(validationResult.getMessageError())
                       .build();
        }

        if (accountConsent.isExpired()) {
            log.info("InR-ID: [{}], X-Request-ID: [{}], Consent-ID: [{}]. Update consent PSU data failed: consent expired",
                     requestProviderService.getInternalRequestId(), requestProviderService.getRequestId(), consentId);
            return ResponseObject.<UpdateConsentPsuDataResponse>builder()
                       .fail(AIS_401, of(CONSENT_EXPIRED))
                       .build();
        }

        return getUpdateConsentPsuDataResponse(updatePsuData);
    }

    private ResponseObject<UpdateConsentPsuDataResponse> getUpdateConsentPsuDataResponse(UpdateConsentPsuDataReq updatePsuData) {
        AisAuthorizationService service = aisScaAuthorisationServiceResolver.getServiceInitiation(updatePsuData.getAuthorizationId());

        Optional<AccountConsentAuthorization> authorization = service.getAccountConsentAuthorizationById(updatePsuData.getAuthorizationId(), updatePsuData.getConsentId());

        if (!authorization.isPresent()) {
            log.info("InR-ID: [{}], X-Request-ID: [{}], Authorisation-ID: [{}]. Update consent PSU data failed: authorisation not found by id",
                     requestProviderService.getInternalRequestId(), requestProviderService.getRequestId(), updatePsuData.getAuthorizationId());
            return ResponseObject.<UpdateConsentPsuDataResponse>builder()
                       .fail(AIS_403, of(CONSENT_UNKNOWN_403)).build();
        }

        AccountConsentAuthorization consentAuthorization = authorization.get();

        if (consentAuthorization.getChosenScaApproach() == ScaApproach.REDIRECT) {
            return aisAuthorisationConfirmationService.processAuthorisationConfirmation(updatePsuData);
        }

        UpdateConsentPsuDataResponse response = (UpdateConsentPsuDataResponse) authorisationChainResponsibilityService.apply(
            new AisAuthorisationProcessorRequest(consentAuthorization.getChosenScaApproach(),
                                                 consentAuthorization.getScaStatus(),
                                                 updatePsuData,
                                                 consentAuthorization));
        loggingContextService.storeScaStatus(response.getScaStatus());

        return Optional.ofNullable(response)
                   .map(s -> Optional.ofNullable(s.getErrorHolder())
                                 .map(e -> ResponseObject.<UpdateConsentPsuDataResponse>builder()
                                               .fail(e)
                                               .build())
                                 .orElseGet(ResponseObject.<UpdateConsentPsuDataResponse>builder().body(response)::build))
                   .orElseGet(ResponseObject.<UpdateConsentPsuDataResponse>builder()
                                  .fail(AIS_400, of(FORMAT_ERROR))
                                  ::build);
    }

    private ResponseObject<CreateConsentAuthorizationResponse> createConsentAuthorizationWithResponse(PsuIdData psuDataFromRequest, String consentId) {
        xs2aEventService.recordAisTppRequest(consentId, EventType.START_AIS_CONSENT_AUTHORISATION_REQUEST_RECEIVED);

        Optional<AccountConsent> accountConsentOptional = aisConsentService.getAccountConsentById(consentId);

        if (!accountConsentOptional.isPresent()) {
            log.info("InR-ID: [{}], X-Request-ID: [{}], Consent-ID: [{}]. Create consent authorisation with response failed: consent not found by id",
                     requestProviderService.getInternalRequestId(), requestProviderService.getRequestId(), consentId);
            return ResponseObject.<CreateConsentAuthorizationResponse>builder()
                       .fail(AIS_403, of(CONSENT_UNKNOWN_403)).build();
        }
        AccountConsent accountConsent = accountConsentOptional.get();

        ValidationResult validationResult = consentValidationService.validateConsentAuthorisationOnCreate(new CreateConsentAuthorisationObject(accountConsent, psuDataFromRequest));

        if (validationResult.isNotValid()) {
            log.info("InR-ID: [{}], X-Request-ID: [{}], Consent-ID: [{}]. Create consent authorisation with response - validation failed: {}",
                     requestProviderService.getInternalRequestId(), requestProviderService.getRequestId(), consentId, validationResult.getMessageError());
            return ResponseObject.<CreateConsentAuthorizationResponse>builder()
                       .fail(validationResult.getMessageError())
                       .build();
        }

        if (accountConsent.isExpired()) {
            log.info("InR-ID: [{}], X-Request-ID: [{}], Consent-ID: [{}]. Create consent authorisation with response failed: consent expired",
                     requestProviderService.getInternalRequestId(), requestProviderService.getRequestId(), consentId);
            return ResponseObject.<CreateConsentAuthorizationResponse>builder()
                       .fail(AIS_401, of(CONSENT_EXPIRED))
                       .build();
        }

        loggingContextService.storeConsentStatus(accountConsent.getConsentStatus());

        AisAuthorizationService service = aisScaAuthorisationServiceResolver.getService();
        PsuIdData psuIdData = getActualPsuData(psuDataFromRequest, accountConsent);

        return service.createConsentAuthorization(psuIdData, consentId)
                   .map(resp -> ResponseObject.<CreateConsentAuthorizationResponse>builder().body(resp).build())
                   .orElseGet(ResponseObject.<CreateConsentAuthorizationResponse>builder()
                                  .fail(AIS_403, of(CONSENT_UNKNOWN_403))
                                  ::build);
    }

    private PsuIdData getActualPsuData(PsuIdData psuDataFromRequest, AccountConsent accountConsent) {
        boolean isMultilevel = accountConsent.isMultilevelScaRequired();

        if (psuDataFromRequest.isNotEmpty() || isMultilevel) {
            return psuDataFromRequest;
        }

        return accountConsent.getPsuIdDataList().stream()
                   .findFirst()
                   .orElse(psuDataFromRequest);
    }

    private Optional<Xs2aAuthorisationSubResources> getAuthorisationSubResources(String consentId) {
        return aisConsentService.getAuthorisationSubResources(consentId)
                   .map(Xs2aAuthorisationSubResources::new);
    }
}
