/*
 * Copyright 2018-2018 adorsys GmbH & Co KG
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

package de.adorsys.psd2.consent.web.aspsp.controller;

import de.adorsys.psd2.consent.aspsp.api.CmsAspspStopListApi;
import de.adorsys.psd2.consent.aspsp.api.tpp.CmsAspspTppService;
import de.adorsys.psd2.xs2a.core.tpp.TppStopListRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequiredArgsConstructor
public class CmsAspspStopListController implements CmsAspspStopListApi {
    private final CmsAspspTppService cmsAspspTppService;

    @Override
    public ResponseEntity<TppStopListRecord> getTppStopListRecord(String tppAuthorisationNumber, String instanceId) {
        return cmsAspspTppService.getTppStopListRecord(tppAuthorisationNumber, instanceId)
                   .map(record -> new ResponseEntity<>(record, HttpStatus.OK))
                   .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @Override
    public ResponseEntity<Boolean> blockTpp(String tppAuthorisationNumber, String instanceId, Long lockPeriod) {
        Duration lockPeriodDuration = lockPeriod != null ? Duration.ofMillis(lockPeriod) : null;
        boolean isBlocked = cmsAspspTppService.blockTpp(tppAuthorisationNumber, instanceId, lockPeriodDuration);
        return new ResponseEntity<>(isBlocked, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<Boolean> unblockTpp(String tppAuthorisationNumber, String instanceId) {
        boolean isUnblocked = cmsAspspTppService.unblockTpp(tppAuthorisationNumber, instanceId);
        return new ResponseEntity<>(isUnblocked, HttpStatus.OK);
    }
}
