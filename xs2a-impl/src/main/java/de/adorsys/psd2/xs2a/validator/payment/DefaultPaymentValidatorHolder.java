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

package de.adorsys.psd2.xs2a.validator.payment;

import de.adorsys.psd2.validator.payment.CountryValidatorHolder;
import de.adorsys.psd2.validator.payment.PaymentBodyFieldsValidator;
import de.adorsys.psd2.validator.payment.PaymentBusinessValidator;
import de.adorsys.psd2.xs2a.service.validator.pis.payment.raw.DefaultPaymentBusinessValidatorImpl;
import de.adorsys.psd2.xs2a.web.validator.body.payment.handler.DefaultPaymentBodyFieldsValidatorImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DefaultPaymentValidatorHolder implements CountryValidatorHolder {

    private final DefaultPaymentBodyFieldsValidatorImpl defaultPaymentBodyFieldsValidator;
    private final DefaultPaymentBusinessValidatorImpl defaultPaymentBusinessValidator;

    @Override
    public String getCountryIdentifier() {
        return "DE";
    }

    @Override
    public PaymentBodyFieldsValidator getPaymentBodyFieldsValidator() {
        return defaultPaymentBodyFieldsValidator;
    }

    @Override
    public PaymentBusinessValidator getPaymentBusinessValidator() {
        return defaultPaymentBusinessValidator;
    }

    @Override
    public boolean isCustom() {
        return false;
    }
}
