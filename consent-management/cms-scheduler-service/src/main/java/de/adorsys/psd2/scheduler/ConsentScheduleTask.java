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

package de.adorsys.psd2.scheduler;

import de.adorsys.psd2.consent.domain.consent.ConsentEntity;
import de.adorsys.psd2.consent.repository.ConsentJpaRepository;
import de.adorsys.psd2.xs2a.core.consent.ConsentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static de.adorsys.psd2.xs2a.core.consent.ConsentStatus.RECEIVED;
import static de.adorsys.psd2.xs2a.core.consent.ConsentStatus.VALID;


@Slf4j
@Component
@RequiredArgsConstructor
public class ConsentScheduleTask {
    private final ConsentJpaRepository consentJpaRepository;

    @Scheduled(cron = "${xs2a.cms.consent.cron.expression}")
    @Transactional
    public void checkConsentStatus() {
        log.info("Consent schedule task is run!");
        List<ConsentEntity> availableConsents = Optional.ofNullable(consentJpaRepository.findByConsentStatusIn(EnumSet.of(RECEIVED, VALID)))
                                                 .orElse(Collections.emptyList());
        consentJpaRepository.saveAll(updateConsent(availableConsents));
    }

    private List<ConsentEntity> updateConsent(List<ConsentEntity> availableConsents) {
        return availableConsents.stream()
                   .filter(ConsentEntity::isExpiredByDate)
                   .map(this::updateConsentParameters)
                   .collect(Collectors.toList());
    }

    private ConsentEntity updateConsentParameters(ConsentEntity consent) {
        consent.setConsentStatus(ConsentStatus.EXPIRED);
        consent.setExpireDate(LocalDate.now());
        return consent;
    }
}
