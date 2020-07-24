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

package de.adorsys.psd2.consent.service.psu;

import de.adorsys.psd2.consent.api.piis.v2.CmsConfirmationOfFundsResponse;
import de.adorsys.psd2.consent.domain.AuthorisationEntity;
import de.adorsys.psd2.consent.domain.consent.ConsentEntity;
import de.adorsys.psd2.consent.repository.ConsentJpaRepository;
import de.adorsys.psd2.consent.repository.specification.ConfirmationOfFundsConsentSpecification;
import de.adorsys.psd2.consent.service.AisConsentUsageService;
import de.adorsys.psd2.consent.service.authorisation.CmsConsentAuthorisationServiceInternal;
import de.adorsys.psd2.consent.service.mapper.AuthorisationTemplateMapperImpl;
import de.adorsys.psd2.consent.service.mapper.ConfirmationOfFundsMapper;
import de.adorsys.psd2.consent.service.mapper.PsuDataMapper;
import de.adorsys.psd2.consent.service.mapper.TppInfoMapperImpl;
import de.adorsys.psd2.xs2a.core.exception.AuthorisationIsExpiredException;
import de.adorsys.psd2.xs2a.core.exception.RedirectUrlIsExpiredException;
import de.adorsys.psd2.xs2a.core.psu.PsuIdData;
import de.adorsys.psd2.xs2a.core.sca.AuthenticationDataHolder;
import de.adorsys.psd2.xs2a.core.sca.ScaStatus;
import de.adorsys.xs2a.reader.JsonReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CmsPsuConfirmationOfFundsServiceInternalTest {
    private static final String CONSENT_ID = "4b112130-6a96-4941-a220-2da8a4af2c65";
    private static final String AUTHORISATION_ID = "9304a6a0-8f02-4b79-aeab-00aa7e03a06d";
    private static final String INSTANCE_ID = "UNDEFINED";
    private static final String METHOD_ID = "SMS";
    private static final String AUTHENTICATION_DATA = "123456";
    private static final String TPP_NOK_REDIRECT_URI = "Mock tppNokRedirectUri";

    private AuthenticationDataHolder authenticationDataHolder;

    @InjectMocks
    private CmsPsuConfirmationOfFundsServiceInternal cmsPsuConfirmationOfFundsServiceInternal;

    @Mock
    private CmsConsentAuthorisationServiceInternal consentAuthorisationService;
    @Mock
    private ConfirmationOfFundsConsentSpecification confirmationOfFundsConsentSpecification;
    @Mock
    private ConsentJpaRepository consentJpaRepository;
    @Mock
    private AisConsentUsageService consentUsageService;

    private ConfirmationOfFundsMapper confirmationOfFundsMapper;


    private JsonReader jsonReader = new JsonReader();
    private PsuIdData psuIdData;
    private ConsentEntity consentEntity;
    private AuthorisationEntity authorisationEntity;

    @BeforeEach
    void setUp() {
        psuIdData = jsonReader.getObjectFromFile("json/psu-id-data.json", PsuIdData.class);
        authenticationDataHolder = new AuthenticationDataHolder(METHOD_ID, AUTHENTICATION_DATA);
        consentEntity = jsonReader.getObjectFromFile("json/consent-entity.json", ConsentEntity.class);
        authorisationEntity = jsonReader.getObjectFromFile("json/authorisation-entity.json", AuthorisationEntity.class);

        confirmationOfFundsMapper = new ConfirmationOfFundsMapper(new PsuDataMapper(), new TppInfoMapperImpl(), new AuthorisationTemplateMapperImpl(), consentUsageService);
        cmsPsuConfirmationOfFundsServiceInternal = new CmsPsuConfirmationOfFundsServiceInternal(consentJpaRepository, consentAuthorisationService,
                                                                                                confirmationOfFundsConsentSpecification, confirmationOfFundsMapper);
    }

    @Test
    void updateAuthorisationStatus() throws AuthorisationIsExpiredException {
        when(confirmationOfFundsConsentSpecification.byConsentIdAndInstanceId(CONSENT_ID, INSTANCE_ID))
            .thenReturn((root, criteriaQuery, criteriaBuilder) -> null);
        when(consentJpaRepository.findOne(any(Specification.class)))
            .thenReturn(Optional.of(consentEntity));
        when(consentAuthorisationService.getAuthorisationByAuthorisationId(AUTHORISATION_ID, INSTANCE_ID))
            .thenReturn(Optional.of(authorisationEntity));
        when(consentAuthorisationService.updateScaStatusAndAuthenticationData(ScaStatus.RECEIVED, authorisationEntity, authenticationDataHolder))
            .thenReturn(true);

        boolean result = cmsPsuConfirmationOfFundsServiceInternal.updateAuthorisationStatus(psuIdData, CONSENT_ID,
                                                                                            AUTHORISATION_ID, ScaStatus.RECEIVED,
                                                                                            INSTANCE_ID, authenticationDataHolder);
        assertTrue(result);
    }

    @Test
    void updateAuthorisationStatus_getAuthorisationByExternalId_empty() throws AuthorisationIsExpiredException {
        // Given
        when(confirmationOfFundsConsentSpecification.byConsentIdAndInstanceId(CONSENT_ID, INSTANCE_ID))
            .thenReturn((root, criteriaQuery, criteriaBuilder) -> null);
        when(consentJpaRepository.findOne(any(Specification.class)))
            .thenReturn(Optional.of(consentEntity));
        when(consentAuthorisationService.getAuthorisationByAuthorisationId(AUTHORISATION_ID, INSTANCE_ID))
            .thenReturn(Optional.empty());

        // When
        boolean result = cmsPsuConfirmationOfFundsServiceInternal.updateAuthorisationStatus(psuIdData, CONSENT_ID, AUTHORISATION_ID, ScaStatus.RECEIVED,
                                                                                            INSTANCE_ID, authenticationDataHolder);

        assertFalse(result);
        verify(consentAuthorisationService, never()).updateScaStatusAndAuthenticationData(any(), any(), any());
    }

    @Test
    void updateAuthorisationStatus_getActualConsent_empty() throws AuthorisationIsExpiredException {
        // Given
        when(confirmationOfFundsConsentSpecification.byConsentIdAndInstanceId(CONSENT_ID, INSTANCE_ID))
            .thenReturn((root, criteriaQuery, criteriaBuilder) -> null);
        when(consentJpaRepository.findOne(any(Specification.class)))
            .thenReturn(Optional.empty());

        // When
        boolean result = cmsPsuConfirmationOfFundsServiceInternal.updateAuthorisationStatus(psuIdData, CONSENT_ID, AUTHORISATION_ID, ScaStatus.RECEIVED,
                                                                                            INSTANCE_ID, authenticationDataHolder);

        assertFalse(result);
        verify(consentAuthorisationService, never()).getAuthorisationByAuthorisationId(any(), any());
        verify(consentAuthorisationService, never()).updateScaStatusAndAuthenticationData(any(), any(), any());
    }

    @Test
    void checkRedirectAndGetConsent_success() throws RedirectUrlIsExpiredException {
        // Given
        when(consentAuthorisationService.getAuthorisationByRedirectId(AUTHORISATION_ID, INSTANCE_ID)).thenReturn(Optional.of(authorisationEntity));
        when(consentJpaRepository.findByExternalId(CONSENT_ID)).thenReturn(Optional.of(consentEntity));

        List<AuthorisationEntity> authorisations = Collections.singletonList(authorisationEntity);
        when(consentAuthorisationService.getAuthorisationsByParentExternalId(CONSENT_ID))
            .thenReturn(authorisations);

        // When
        Optional<CmsConfirmationOfFundsResponse> consentResponseOptional = cmsPsuConfirmationOfFundsServiceInternal.checkRedirectAndGetConsent(AUTHORISATION_ID, INSTANCE_ID);

        // Then
        assertTrue(consentResponseOptional.isPresent());

        CmsConfirmationOfFundsResponse expected = jsonReader.getObjectFromFile("json/service/psu/piis/confirmation-of-funds-response.json", CmsConfirmationOfFundsResponse.class);
        expected.getConsent().setCreationTimestamp(consentResponseOptional.get().getConsent().getCreationTimestamp());
        assertEquals(expected, consentResponseOptional.get());
    }

    @Test
    void checkRedirectAndGetConsent_authorisationNotFound() throws RedirectUrlIsExpiredException {
        // Given
        when(consentAuthorisationService.getAuthorisationByRedirectId(AUTHORISATION_ID, INSTANCE_ID)).thenReturn(Optional.empty());

        // When
        Optional<CmsConfirmationOfFundsResponse> consentResponseOptional = cmsPsuConfirmationOfFundsServiceInternal.checkRedirectAndGetConsent(AUTHORISATION_ID, INSTANCE_ID);

        // Then
        assertTrue(consentResponseOptional.isEmpty());

        verify(consentJpaRepository, never()).findByExternalId(anyString());
        verify(consentAuthorisationService, never()).getAuthorisationsByParentExternalId(anyString());
    }

    @Test
    void checkRedirectAndGetConsent_redirectUrlIsExpired() throws RedirectUrlIsExpiredException {
        // Given
        when(consentAuthorisationService.getAuthorisationByRedirectId(AUTHORISATION_ID, INSTANCE_ID))
            .thenThrow(new RedirectUrlIsExpiredException(TPP_NOK_REDIRECT_URI));

        // When
        assertThrows(RedirectUrlIsExpiredException.class,
                     () -> cmsPsuConfirmationOfFundsServiceInternal.checkRedirectAndGetConsent(AUTHORISATION_ID, INSTANCE_ID));

        verify(consentJpaRepository, never()).findByExternalId(anyString());
        verify(consentAuthorisationService, never()).getAuthorisationsByParentExternalId(anyString());
    }

}
