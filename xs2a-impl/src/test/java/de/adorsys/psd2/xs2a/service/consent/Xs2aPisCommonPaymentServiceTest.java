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

package de.adorsys.psd2.xs2a.service.consent;

import de.adorsys.psd2.consent.api.CmsError;
import de.adorsys.psd2.consent.api.CmsResponse;
import de.adorsys.psd2.consent.api.CmsScaMethod;
import de.adorsys.psd2.consent.api.pis.CreatePisCommonPaymentResponse;
import de.adorsys.psd2.consent.api.pis.authorisation.UpdatePisCommonPaymentPsuDataRequest;
import de.adorsys.psd2.consent.api.pis.proto.PisCommonPaymentResponse;
import de.adorsys.psd2.consent.api.pis.proto.PisPaymentInfo;
import de.adorsys.psd2.consent.api.service.PisAuthorisationServiceEncrypted;
import de.adorsys.psd2.consent.api.service.PisCommonPaymentServiceEncrypted;
import de.adorsys.psd2.xs2a.core.authorisation.AuthenticationObject;
import de.adorsys.psd2.xs2a.core.pis.TransactionStatus;
import de.adorsys.psd2.xs2a.core.profile.PaymentType;
import de.adorsys.psd2.xs2a.core.psu.PsuIdData;
import de.adorsys.psd2.xs2a.core.sca.ScaStatus;
import de.adorsys.psd2.xs2a.core.tpp.TppInfo;
import de.adorsys.psd2.xs2a.core.tpp.TppRole;
import de.adorsys.psd2.xs2a.domain.pis.PaymentInitiationParameters;
import de.adorsys.psd2.xs2a.service.mapper.consent.Xs2aAuthenticationObjectToCmsScaMethodMapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class Xs2aPisCommonPaymentServiceTest {
    private static final String PRODUCT = "sepa-credit-transfers";
    private static final String PAYMENT_ID = "d6cb50e5-bb88-4bbf-a5c1-42ee1ed1df2c";
    private static final String AUTHORISATION_ID = "a01562ea-19ff-4b5a-8188-c45d85bfa20a";
    private static final String AUTHENTICATION_METHOD_ID = "19ff-4b5a-8188";
    private static final byte[] PAYMENT_DATA = new byte[16];
    private static final TppInfo TPP_INFO = buildTppInfo();
    private static final PaymentInitiationParameters PAYMENT_INITIATION_PARAMETERS = buildPaymentInitiationParameters();
    private static final PisPaymentInfo PIS_PAYMENT_INFO = buildPisPaymentInfo(PAYMENT_DATA);
    private static final PsuIdData PSU_DATA = new PsuIdData("psuId", "psuIdType", "psuCorporateId", "psuCorporateIdType", "psuIpAddress");
    private static final CreatePisCommonPaymentResponse CREATE_PIS_COMMON_PAYMENT_RESPONSE = new CreatePisCommonPaymentResponse(PAYMENT_ID, null);
    private static final PisCommonPaymentResponse PIS_COMMON_PAYMENT_RESPONSE = new PisCommonPaymentResponse();
    private static final List<AuthenticationObject> AUTHENTICATION_OBJECT_LIST = Collections.singletonList(new AuthenticationObject());
    private static final List<CmsScaMethod> CMS_SCA_METHOD_LIST = Collections.singletonList(new CmsScaMethod(AUTHORISATION_ID, true));

    @InjectMocks
    private Xs2aPisCommonPaymentService xs2aPisCommonPaymentService;

    @Mock
    private PisCommonPaymentServiceEncrypted pisCommonPaymentServiceEncrypted;
    @Mock
    private PisAuthorisationServiceEncrypted pisAuthorisationServiceEncrypted;
    @Mock
    private Xs2aAuthenticationObjectToCmsScaMethodMapper xs2AAuthenticationObjectToCmsScaMethodMapper;

    @Test
    public void createCommonPayment_by_request_success() {
        //Given
        when(pisCommonPaymentServiceEncrypted.createCommonPayment(PIS_PAYMENT_INFO))
            .thenReturn(CmsResponse.<CreatePisCommonPaymentResponse>builder().payload(CREATE_PIS_COMMON_PAYMENT_RESPONSE).build());

        //When
        CreatePisCommonPaymentResponse actualResponse = xs2aPisCommonPaymentService.createCommonPayment(PIS_PAYMENT_INFO);

        //Then
        assertThat(actualResponse).isEqualTo(CREATE_PIS_COMMON_PAYMENT_RESPONSE);
    }

    @Test
    public void createCommonPayment_by_request_failed() {
        //Given
        when(pisCommonPaymentServiceEncrypted.createCommonPayment(PIS_PAYMENT_INFO))
            .thenReturn(CmsResponse.<CreatePisCommonPaymentResponse>builder().error(CmsError.TECHNICAL_ERROR).build());

        //When
        CreatePisCommonPaymentResponse actualResponse = xs2aPisCommonPaymentService.createCommonPayment(PIS_PAYMENT_INFO);

        //Then
        assertThat(actualResponse).isNull();
    }

    @Test
    public void getPisCommonPaymentById_success() {
        //Given
        when(pisCommonPaymentServiceEncrypted.getCommonPaymentById(PAYMENT_ID))
            .thenReturn(CmsResponse.<PisCommonPaymentResponse>builder().payload(PIS_COMMON_PAYMENT_RESPONSE).build());

        //When
        Optional<PisCommonPaymentResponse> actualResponse = xs2aPisCommonPaymentService.getPisCommonPaymentById(PAYMENT_ID);

        //Then
        assertThat(actualResponse.isPresent()).isTrue();
        assertThat(actualResponse.get()).isEqualTo(PIS_COMMON_PAYMENT_RESPONSE);
    }

    @Test
    public void getPisCommonPaymentById_failed() {
        //Given
        when(pisCommonPaymentServiceEncrypted.getCommonPaymentById(PAYMENT_ID))
            .thenReturn(CmsResponse.<PisCommonPaymentResponse>builder().error(CmsError.TECHNICAL_ERROR).build());

        //When
        Optional<PisCommonPaymentResponse> actualResponse = xs2aPisCommonPaymentService.getPisCommonPaymentById(PAYMENT_ID);

        //Then
        assertThat(actualResponse.isPresent()).isFalse();
    }

    @Test
    public void isAuthenticationMethodDecoupled_success() {
        //Given
        when(pisAuthorisationServiceEncrypted.isAuthenticationMethodDecoupled(AUTHORISATION_ID, AUTHENTICATION_METHOD_ID))
            .thenReturn(CmsResponse.<Boolean>builder().payload(true).build());

        //When
        boolean actualResponse = xs2aPisCommonPaymentService.isAuthenticationMethodDecoupled(AUTHORISATION_ID, AUTHENTICATION_METHOD_ID);

        //Then
        assertThat(actualResponse).isTrue();
    }

    @Test
    public void isAuthenticationMethodDecoupled_failed() {
        //Given
        when(pisAuthorisationServiceEncrypted.isAuthenticationMethodDecoupled(AUTHORISATION_ID, AUTHENTICATION_METHOD_ID))
            .thenReturn(CmsResponse.<Boolean>builder().payload(false).build());

        //When
        boolean actualResponse = xs2aPisCommonPaymentService.isAuthenticationMethodDecoupled(AUTHORISATION_ID, AUTHENTICATION_METHOD_ID);

        //Then
        assertThat(actualResponse).isFalse();
    }

    @Test
    public void saveAuthenticationMethods_success() {
        //Given
        when(xs2AAuthenticationObjectToCmsScaMethodMapper.mapToCmsScaMethods(AUTHENTICATION_OBJECT_LIST))
            .thenReturn(CMS_SCA_METHOD_LIST);
        when(pisAuthorisationServiceEncrypted.saveAuthenticationMethods(AUTHORISATION_ID, CMS_SCA_METHOD_LIST))
            .thenReturn(CmsResponse.<Boolean>builder().payload(true).build());

        //When
        boolean actualResponse = xs2aPisCommonPaymentService.saveAuthenticationMethods(AUTHORISATION_ID, AUTHENTICATION_OBJECT_LIST);

        //Then
        assertThat(actualResponse).isTrue();
    }

    @Test
    public void saveAuthenticationMethods_failed() {
        //Given
        when(xs2AAuthenticationObjectToCmsScaMethodMapper.mapToCmsScaMethods(AUTHENTICATION_OBJECT_LIST))
            .thenReturn(CMS_SCA_METHOD_LIST);
        when(pisAuthorisationServiceEncrypted.saveAuthenticationMethods(AUTHORISATION_ID, CMS_SCA_METHOD_LIST))
            .thenReturn(CmsResponse.<Boolean>builder().payload(false).build());

        //When
        boolean actualResponse = xs2aPisCommonPaymentService.saveAuthenticationMethods(AUTHORISATION_ID, AUTHENTICATION_OBJECT_LIST);

        //Then
        assertThat(actualResponse).isFalse();
    }

    @Test
    public void updateMultilevelSca() {
        // Given
        when(pisCommonPaymentServiceEncrypted.updateMultilevelSca(PAYMENT_ID, true))
            .thenReturn(CmsResponse.<Boolean>builder().payload(true).build());

        // When
        boolean actualResponse = xs2aPisCommonPaymentService.updateMultilevelSca(PAYMENT_ID, true);

        // Then
        assertThat(actualResponse).isTrue();
    }

    @Test
    public void updatePisAuthorisationStatus_success() {
        // Given
        when(pisAuthorisationServiceEncrypted.updatePisAuthorisationStatus(AUTHORISATION_ID, ScaStatus.FAILED))
            .thenReturn(CmsResponse.<Boolean>builder().payload(true).build());

        // When
        boolean result = xs2aPisCommonPaymentService.updatePisAuthorisationStatus(AUTHORISATION_ID, ScaStatus.FAILED);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    public void updatePisAuthorisationStatus_failure() {
        // Given
        when(pisAuthorisationServiceEncrypted.updatePisAuthorisationStatus(AUTHORISATION_ID, ScaStatus.FAILED))
            .thenReturn(CmsResponse.<Boolean>builder().payload(false).build());

        // When
        boolean result = xs2aPisCommonPaymentService.updatePisAuthorisationStatus(AUTHORISATION_ID, ScaStatus.FAILED);

        // Then
        assertThat(result).isFalse();
    }

    private static UpdatePisCommonPaymentPsuDataRequest buildUpdatePisCommonPaymentPsuDataRequest() {
        UpdatePisCommonPaymentPsuDataRequest request = new UpdatePisCommonPaymentPsuDataRequest();
        request.setAuthorizationId(AUTHORISATION_ID);
        return request;
    }


    private static TppInfo buildTppInfo() {
        TppInfo tppInfo = new TppInfo();
        tppInfo.setAuthorisationNumber("registrationNumber");
        tppInfo.setTppName("tppName");
        tppInfo.setTppRoles(Collections.singletonList(TppRole.PISP));
        tppInfo.setAuthorityId("authorityId");
        return tppInfo;
    }

    private static PisPaymentInfo buildPisPaymentInfo(byte[] paymentData) {
        PisPaymentInfo request = new PisPaymentInfo();
        request.setPaymentProduct(PAYMENT_INITIATION_PARAMETERS.getPaymentProduct());
        request.setPaymentType(PAYMENT_INITIATION_PARAMETERS.getPaymentType());
        request.setTransactionStatus(TransactionStatus.RCVD);
        request.setPaymentData(paymentData);
        request.setTppInfo(TPP_INFO);
        request.setPsuDataList(Collections.singletonList(PAYMENT_INITIATION_PARAMETERS.getPsuData()));
        return request;
    }

    private static PaymentInitiationParameters buildPaymentInitiationParameters() {
        PaymentInitiationParameters parameters = new PaymentInitiationParameters();
        parameters.setPaymentProduct(PRODUCT);
        parameters.setPaymentType(PaymentType.SINGLE);
        parameters.setPsuData(PSU_DATA);
        return parameters;
    }
}
