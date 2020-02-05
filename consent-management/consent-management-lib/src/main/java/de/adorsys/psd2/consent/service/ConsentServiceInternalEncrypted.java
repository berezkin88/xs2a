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

package de.adorsys.psd2.consent.service;

import de.adorsys.psd2.consent.api.CmsResponse;
import de.adorsys.psd2.consent.api.WrongChecksumException;
import de.adorsys.psd2.consent.api.ais.*;
import de.adorsys.psd2.consent.api.service.ConsentService;
import de.adorsys.psd2.consent.api.service.ConsentServiceEncrypted;
import de.adorsys.psd2.consent.service.security.SecurityDataService;
import de.adorsys.psd2.xs2a.core.consent.ConsentStatus;
import de.adorsys.psd2.xs2a.core.psu.PsuIdData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static de.adorsys.psd2.consent.api.CmsError.TECHNICAL_ERROR;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsentServiceInternalEncrypted implements ConsentServiceEncrypted {
    private final SecurityDataService securityDataService;
    private final ConsentService aisConsentService;

    @Override
    @Transactional(rollbackFor = WrongChecksumException.class)
    public CmsResponse<CreateConsentResponse> createConsent(CreateConsentRequest request) throws WrongChecksumException {
        CmsResponse<CreateConsentResponse> serviceResponse = aisConsentService.createConsent(request);

        if (serviceResponse.hasError()) {
            return serviceResponse;
        }

        CreateConsentResponse createAisConsentResponse = serviceResponse.getPayload();
        Optional<String> encryptIdOptional = securityDataService.encryptId(createAisConsentResponse.getConsentId());

        if (!encryptIdOptional.isPresent()) {
            log.info("Consent ID: [{}]. Create consent failed, couldn't encrypt consent id", createAisConsentResponse.getConsentId());
            return CmsResponse.<CreateConsentResponse>builder()
                       .error(TECHNICAL_ERROR)
                       .build();
        }

        return CmsResponse.<CreateConsentResponse>builder()
                   .payload(new CreateConsentResponse(encryptIdOptional.get(), createAisConsentResponse.getAisAccountConsent(), createAisConsentResponse.getTppNotificationContentPreferred()))
                   .build();
    }

    @Override
    @Transactional
    public CmsResponse<ConsentStatus> getConsentStatusById(String encryptedConsentId) {
        Optional<String> decryptIdOptional = securityDataService.decryptId(encryptedConsentId);

        if (!decryptIdOptional.isPresent()) {
            log.info("Encrypted Consent ID: [{}]. Get consent status by id failed, couldn't decrypt consent id", encryptedConsentId);
            return CmsResponse.<ConsentStatus>builder()
                       .error(TECHNICAL_ERROR)
                       .build();
        }

        return aisConsentService.getConsentStatusById(decryptIdOptional.get());
    }

    @Override
    @Transactional(rollbackFor = WrongChecksumException.class)
    public CmsResponse<Boolean> updateConsentStatusById(String encryptedConsentId, ConsentStatus status) throws WrongChecksumException {
        Optional<String> decryptIdOptional = securityDataService.decryptId(encryptedConsentId);

        if (!decryptIdOptional.isPresent()) {
            log.info("Encrypted Consent ID: [{}]. Update consent by id failed, couldn't decrypt consent id", encryptedConsentId);
            return CmsResponse.<Boolean>builder()
                       .error(TECHNICAL_ERROR)
                       .build();
        }

        return aisConsentService.updateConsentStatusById(decryptIdOptional.get(), status);
    }

    @Override
    @Transactional
    public CmsResponse<CmsAccountConsent> getAccountConsentById(String encryptedConsentId) {
        Optional<String> decryptIdOptional = securityDataService.decryptId(encryptedConsentId);

        if (!decryptIdOptional.isPresent()) {
            log.info("Encrypted Consent ID: [{}]. Get consent by id failed, couldn't decrypt consent id", encryptedConsentId);
            return CmsResponse.<CmsAccountConsent>builder()
                       .error(TECHNICAL_ERROR)
                       .build();
        }

        return aisConsentService.getAccountConsentById(decryptIdOptional.get());
    }

    @Override
    @Transactional
    public CmsResponse<Boolean> findAndTerminateOldConsentsByNewConsentId(String encryptedNewConsentId) {
        Optional<String> decryptIdOptional = securityDataService.decryptId(encryptedNewConsentId);

        if (!decryptIdOptional.isPresent()) {
            log.info("Encrypted Consent ID: [{}]. Terminate consent by id failed, couldn't decrypt consent id", encryptedNewConsentId);
            return CmsResponse.<Boolean>builder()
                       .error(TECHNICAL_ERROR)
                       .build();
        }

        return aisConsentService.findAndTerminateOldConsentsByNewConsentId(decryptIdOptional.get());
    }

    @Override
    @Transactional(rollbackFor = WrongChecksumException.class)
    public CmsResponse<String> updateAspspAccountAccess(String encryptedConsentId, AisAccountAccessInfo request) throws WrongChecksumException {
        Optional<String> decryptIdOptional = securityDataService.decryptId(encryptedConsentId);

        if (!decryptIdOptional.isPresent()) {
            log.info("Encrypted Consent ID: [{}]. Update aspsp account access failed, couldn't decrypt consent id", encryptedConsentId);
            return CmsResponse.<String>builder()
                       .error(TECHNICAL_ERROR)
                       .build();
        }

        CmsResponse<String> cmsResponse = aisConsentService.updateAspspAccountAccess(decryptIdOptional.get(), request);

        if (cmsResponse.hasError()) {
            return cmsResponse;
        }

        Optional<String> encryptIdOptional = securityDataService.encryptId(cmsResponse.getPayload());

        if (!encryptIdOptional.isPresent()) {
            log.info("Encrypted Consent ID: [{}]. Update aspsp account access failed, couldn't decrypt consent id", encryptedConsentId);
            return CmsResponse.<String>builder()
                       .error(TECHNICAL_ERROR)
                       .build();
        }

        return CmsResponse.<String>builder()
                   .payload(encryptIdOptional.get())
                   .build();
    }

    @Override
    @Transactional(rollbackFor = WrongChecksumException.class)
    public CmsResponse<CmsAccountConsent> updateAspspAccountAccessWithResponse(String
                                                                                   encryptedConsentId, AisAccountAccessInfo request) throws WrongChecksumException {
        Optional<String> decryptIdOptional = securityDataService.decryptId(encryptedConsentId);

        if (!decryptIdOptional.isPresent()) {
            log.info("Encrypted Consent ID: [{}]. Update aspsp account access with response failed, couldn't decrypt consent id",
                     encryptedConsentId);
            return CmsResponse.<CmsAccountConsent>builder()
                       .error(TECHNICAL_ERROR)
                       .build();
        }

        return aisConsentService.updateAspspAccountAccessWithResponse(decryptIdOptional.get(), request);
    }

    @Override
    public CmsResponse<List<PsuIdData>> getPsuDataByConsentId(String encryptedConsentId) {
        Optional<String> decryptIdOptional = securityDataService.decryptId(encryptedConsentId);

        if (!decryptIdOptional.isPresent()) {
            log.info("Encrypted Consent ID: [{}]. Get psu data by consent id failed, couldn't decrypt consent id",
                     encryptedConsentId);
            return CmsResponse.<List<PsuIdData>>builder()
                       .error(TECHNICAL_ERROR)
                       .build();
        }

        return aisConsentService.getPsuDataByConsentId(decryptIdOptional.get());
    }

    @Override
    @Transactional(rollbackFor = WrongChecksumException.class)
    public CmsResponse<Boolean> updateMultilevelScaRequired(String encryptedConsentId, boolean multilevelScaRequired) throws WrongChecksumException {
        Optional<String> decryptIdOptional = securityDataService.decryptId(encryptedConsentId);

        if (!decryptIdOptional.isPresent()) {
            log.info("Encrypted Consent ID: [{}]. Update MultilevelScaRequired failed, couldn't decrypt consent id",
                     encryptedConsentId);
            return CmsResponse.<Boolean>builder()
                       .error(TECHNICAL_ERROR)
                       .build();
        }

        return aisConsentService.updateMultilevelScaRequired(decryptIdOptional.get(), multilevelScaRequired);
    }
}
