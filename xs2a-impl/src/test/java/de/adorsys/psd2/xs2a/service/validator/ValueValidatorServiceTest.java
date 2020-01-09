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

package de.adorsys.psd2.xs2a.service.validator;


import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnitRunner;

import javax.validation.Validation;
import javax.validation.ValidationException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RunWith(MockitoJUnitRunner.class)
public class ValueValidatorServiceTest {
    private static final String ACCOUNT_ID = "11111111";
    private static final String TRANSACTION_ID = "22222222";

    @InjectMocks
    private ValueValidatorService valueValidatorService;

    @Before
    public void setUp() {
        valueValidatorService = new ValueValidatorService(Validation.buildDefaultValidatorFactory().getValidator());
    }

    @Test
    public void validate_AccountAndTransaction() {
        //Given:
        ValidationGroup fields = new ValidationGroup();
        fields.setAccountId(ACCOUNT_ID);
        fields.setTransactionId(TRANSACTION_ID);

        //When Then:
        valueValidatorService.validate(fields, ValidationGroup.AccountIdGroup.class, ValidationGroup.TransactionIdGroup.class);
    }

    @Test
    public void shouldFail_validate_AccountAndEmptyTransaction() {
        //Given:
        ValidationGroup fields = new ValidationGroup();
        fields.setAccountId(ACCOUNT_ID);

        //When Then:
        assertThatThrownBy(() -> valueValidatorService.validate(fields, ValidationGroup.AccountIdGroup.class, ValidationGroup.TransactionIdGroup.class))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    public void shouldFail_validate_EmptyAccountAndTransaction() {
        //Given:
        ValidationGroup fields = new ValidationGroup();
        fields.setTransactionId(TRANSACTION_ID);

        //When Then:
        assertThatThrownBy(() -> valueValidatorService.validate(fields, ValidationGroup.AccountIdGroup.class, ValidationGroup.TransactionIdGroup.class))
            .isInstanceOf(ValidationException.class);
    }
}
