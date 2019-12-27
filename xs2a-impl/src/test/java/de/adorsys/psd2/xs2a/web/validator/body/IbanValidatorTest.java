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

package de.adorsys.psd2.xs2a.web.validator.body;

import de.adorsys.psd2.xs2a.core.error.ErrorType;
import de.adorsys.psd2.xs2a.core.error.MessageError;
import de.adorsys.psd2.xs2a.core.error.MessageErrorCode;
import de.adorsys.psd2.xs2a.web.validator.ErrorBuildingService;
import de.adorsys.psd2.xs2a.web.validator.header.ErrorBuildingServiceMock;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.*;

public class IbanValidatorTest {

    private IbanValidator validator;
    private MessageError messageError;

    @Before
    public void setUp() {
        messageError = new MessageError();
        ErrorBuildingService errorService = new ErrorBuildingServiceMock(ErrorType.PIS_400);
        validator = new IbanValidator(errorService);
    }

    @Test
    public void validate_success() {
        validator.validate("DE15500105172295759744", messageError);
        assertTrue(messageError.getTppMessages().isEmpty());
    }

    @Test
    public void validate_invalidIban() {
        validator.validate("123", messageError);

        assertFalse(messageError.getTppMessages().isEmpty());
        assertEquals(MessageErrorCode.FORMAT_ERROR_INVALID_FIELD, messageError.getTppMessage().getMessageErrorCode());
    }

    @Test
    public void validate_validationDisabled() {
        ReflectionTestUtils.setField(validator, "ibanValidationEnabled", false);

        validator.validate("123", messageError);

        assertTrue(messageError.getTppMessages().isEmpty());
    }
}