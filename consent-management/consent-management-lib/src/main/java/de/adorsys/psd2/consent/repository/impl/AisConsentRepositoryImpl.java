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

package de.adorsys.psd2.consent.repository.impl;

import de.adorsys.psd2.consent.api.WrongChecksumException;
import de.adorsys.psd2.consent.domain.account.Consent;
import de.adorsys.psd2.consent.repository.ConsentJpaRepository;
import de.adorsys.psd2.consent.repository.ConsentVerifyingRepository;
import de.adorsys.psd2.consent.service.sha.ChecksumCalculatingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static de.adorsys.psd2.xs2a.core.consent.ConsentStatus.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class AisConsentRepositoryImpl implements ConsentVerifyingRepository {
    private final ConsentJpaRepository aisConsentRepository;
    private final ChecksumCalculatingFactory calculatingFactory;

    @Override
    @Transactional(rollbackFor = WrongChecksumException.class)
    public Consent verifyAndSave(Consent entity) throws WrongChecksumException {
        return verifyAndSaveInternal(entity);
    }

    @Override
    @Transactional(rollbackFor = WrongChecksumException.class)
    public Consent verifyAndUpdate(Consent entity) throws WrongChecksumException {
        return verifyAndUpdateInternal(entity);
    }

    @Override
    @Transactional(rollbackFor = WrongChecksumException.class)
    public List<Consent> verifyAndSaveAll(List<Consent> consents) throws WrongChecksumException {
        List<Consent> consentList = new ArrayList<>();

        for (Consent aisConsent : consents) {
            Consent consent = verifyAndSaveInternal(aisConsent);
            consentList.add(consent);
        }
        return consentList;
    }

    @Override
    @Transactional
    public Optional<Consent> getActualAisConsent(String consentId) {
        return aisConsentRepository.findByExternalId(consentId)
                   .filter(c -> !c.getConsentStatus().isFinalisedStatus());
    }

    private Consent verifyAndSaveInternal(Consent entity) throws WrongChecksumException {
        Optional<ChecksumCalculatingService> calculatingServiceOptional = calculatingFactory.getServiceByChecksum(entity.getChecksum());

        if (calculatingServiceOptional.isPresent()) {
            ChecksumCalculatingService calculatingService = calculatingServiceOptional.get();

            if (!isAisConsentChecksumCorrect(entity, calculatingService)) {
                throw new WrongChecksumException();
            }

            if (wasStatusSwitchedToValid(entity)) {
                byte[] newChecksum = calculatingService.calculateChecksumForConsent(entity);
                entity.setChecksum(newChecksum);
            }
        }

        return aisConsentRepository.save(entity);
    }

    private Consent verifyAndUpdateInternal(Consent entity) throws WrongChecksumException {
        Optional<ChecksumCalculatingService> calculatingServiceOptional = calculatingFactory.getServiceByChecksum(entity.getChecksum());

        if (calculatingServiceOptional.isPresent()) {
            ChecksumCalculatingService calculatingService = calculatingServiceOptional.get();

            if (!isAisConsentChecksumCorrect(entity, calculatingService)) {
                throw new WrongChecksumException();
            }

            if (entity.getConsentStatus() == VALID) {
                byte[] newChecksum = calculatingService.calculateChecksumForConsent(entity);
                entity.setChecksum(newChecksum);
            }
        }

        return aisConsentRepository.save(entity);
    }

    private boolean isAisConsentChecksumCorrect(Consent entity, ChecksumCalculatingService calculatingService) {
        byte[] checksumFromDb = entity.getChecksum();

        if (checksumFromDb != null
                && wasStatusHoldBefore(entity)
                && !calculatingService.verifyConsentWithChecksum(entity, checksumFromDb)) {
            log.warn("AIS consent checksum verification failed! AIS consent ID: [{}]. Contact ASPSP for details.", entity.getExternalId());
            return false;
        }
        return true;
    }

    private boolean wasStatusSwitchedToValid(Consent entity) {
        return entity.getConsentStatus() == VALID
                   && EnumSet.of(RECEIVED, PARTIALLY_AUTHORISED).contains(entity.getPreviousConsentStatus());
    }

    private boolean wasStatusHoldBefore(Consent entity) {
        return entity.getPreviousConsentStatus() == VALID
                   || entity.getPreviousConsentStatus().isFinalisedStatus();
    }
}
