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

package de.adorsys.psd2.xs2a.service.validator.pis.authorisation.initiation;

import de.adorsys.psd2.xs2a.core.error.ErrorType;
import de.adorsys.psd2.xs2a.core.pis.PaymentAuthorisationType;
import de.adorsys.psd2.xs2a.core.pis.TransactionStatus;
import de.adorsys.psd2.xs2a.domain.authorisation.AuthorisationServiceType;
import de.adorsys.psd2.xs2a.service.validator.PisEndpointAccessCheckerService;
import de.adorsys.psd2.xs2a.service.validator.PisPsuDataUpdateAuthorisationCheckerValidator;
import de.adorsys.psd2.xs2a.service.validator.ValidationResult;
import de.adorsys.psd2.xs2a.service.validator.authorisation.AuthorisationStageCheckValidator;
import de.adorsys.psd2.xs2a.service.validator.pis.authorisation.AbstractUpdatePisPsuDataValidator;
import de.adorsys.psd2.xs2a.service.validator.pis.authorisation.PisAuthorisationStatusValidator;
import de.adorsys.psd2.xs2a.service.validator.pis.authorisation.PisAuthorisationValidator;
import de.adorsys.psd2.xs2a.service.validator.pis.authorisation.UpdatePisPsuDataPO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static de.adorsys.psd2.xs2a.core.error.MessageErrorCode.RESOURCE_EXPIRED_403;
import static de.adorsys.psd2.xs2a.domain.authorisation.AuthorisationServiceType.PIS;

/**
 * Validator to be used for validating update PSU Data in payment authorisation request according to some business rules
 */
@Slf4j
@Component
public class UpdatePisCommonPaymentPsuDataValidator extends AbstractUpdatePisPsuDataValidator<UpdatePisCommonPaymentPsuDataPO> {

    public UpdatePisCommonPaymentPsuDataValidator(PisEndpointAccessCheckerService pisEndpointAccessCheckerService,
                                                  PisAuthorisationValidator pisAuthorisationValidator,
                                                  PisAuthorisationStatusValidator pisAuthorisationStatusValidator,
                                                  PisPsuDataUpdateAuthorisationCheckerValidator pisPsuDataUpdateAuthorisationCheckerValidator,
                                                  AuthorisationStageCheckValidator authorisationStageCheckValidator) {
        super(pisEndpointAccessCheckerService, pisAuthorisationValidator,
              pisAuthorisationStatusValidator, pisPsuDataUpdateAuthorisationCheckerValidator,
              authorisationStageCheckValidator);
    }

    @Override
    protected AuthorisationServiceType getAuthorisationServiceType() {
        return PIS;
    }

    @Override
    protected PaymentAuthorisationType getPaymentAuthorisationType() {
        return PaymentAuthorisationType.CREATED;
    }

    @Override
    protected ValidationResult validateTransactionStatus(UpdatePisPsuDataPO paymentObject) {
        if (paymentObject.getPisCommonPaymentResponse().getTransactionStatus() == TransactionStatus.RJCT) {
            log.info("Authorisation ID: [{}]. Updating PIS initiation authorisation PSU Data has failed: payment has been rejected",
                     paymentObject.getUpdateRequest().getAuthorisationId());

            return ValidationResult.invalid(ErrorType.PIS_403, RESOURCE_EXPIRED_403);
        }
        return ValidationResult.valid();
    }
}
