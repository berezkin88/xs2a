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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

import static de.adorsys.psd2.xs2a.core.consent.ConsentStatus.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class NonRecurringConsentExpirationScheduleTask {
    private final ConsentJpaRepository consentJpaRepository;

    @Scheduled(cron = "${xs2a.cms.used-non-recurring-consent-expiration.cron.expression}")
    @Transactional
    public void expireUsedNonRecurringConsent() {
        log.info("Non-recurring consent expiration task has started!");
        List<ConsentEntity> consents = consentJpaRepository.findUsedNonRecurringConsents(EnumSet.of(RECEIVED, VALID),
                                                                                      LocalDate.now())
                                        .stream()
                                        .distinct()
                                        .map(this::expireConsent)
                                        .collect(Collectors.toList());

        consentJpaRepository.saveAll(consents);
    }

    private ConsentEntity expireConsent(ConsentEntity consent) {
        consent.setConsentStatus(EXPIRED);
        return consent;
    }
}
