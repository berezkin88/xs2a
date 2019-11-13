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

package de.adorsys.psd2.consent.service;

import de.adorsys.psd2.aspsp.profile.service.AspspProfileService;
import de.adorsys.psd2.consent.api.CmsScaMethod;
import de.adorsys.psd2.consent.api.pis.authorisation.*;
import de.adorsys.psd2.consent.api.service.PisAuthorisationService;
import de.adorsys.psd2.consent.domain.AuthorisationTemplateEntity;
import de.adorsys.psd2.consent.domain.PsuData;
import de.adorsys.psd2.consent.domain.ScaMethod;
import de.adorsys.psd2.consent.domain.payment.PisAuthorization;
import de.adorsys.psd2.consent.domain.payment.PisCommonPaymentData;
import de.adorsys.psd2.consent.repository.PisAuthorisationRepository;
import de.adorsys.psd2.consent.repository.PisCommonPaymentDataRepository;
import de.adorsys.psd2.consent.repository.PisPaymentDataRepository;
import de.adorsys.psd2.consent.service.mapper.PisCommonPaymentMapper;
import de.adorsys.psd2.consent.service.mapper.PsuDataMapper;
import de.adorsys.psd2.consent.service.mapper.ScaMethodMapper;
import de.adorsys.psd2.consent.service.psu.CmsPsuService;
import de.adorsys.psd2.xs2a.core.pis.PaymentAuthorisationType;
import de.adorsys.psd2.xs2a.core.profile.ScaApproach;
import de.adorsys.psd2.xs2a.core.psu.PsuIdData;
import de.adorsys.psd2.xs2a.core.sca.AuthorisationScaApproachResponse;
import de.adorsys.psd2.xs2a.core.sca.ScaStatus;
import de.adorsys.psd2.xs2a.core.tpp.TppRedirectUri;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static de.adorsys.psd2.xs2a.core.pis.TransactionStatus.PATC;
import static de.adorsys.psd2.xs2a.core.pis.TransactionStatus.RCVD;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PisAuthorisationServiceInternal implements PisAuthorisationService {
    private final PisCommonPaymentMapper pisCommonPaymentMapper;
    private final PsuDataMapper psuDataMapper;
    private final PisAuthorisationRepository pisAuthorisationRepository;
    private final PisPaymentDataRepository pisPaymentDataRepository;
    private final PisCommonPaymentDataRepository pisCommonPaymentDataRepository;
    private final AspspProfileService aspspProfileService;
    private final PisCommonPaymentConfirmationExpirationService pisCommonPaymentConfirmationExpirationService;
    private final ScaMethodMapper scaMethodMapper;
    private final CmsPsuService cmsPsuService;

    /**
     * Create common payment authorization
     *
     * @param paymentId id of the payment
     * @param request   needed parameters for creating PIS authorisation
     * @return response contains authorisation id
     */
    @Override
    @Transactional
    public Optional<CreatePisAuthorisationResponse> createAuthorization(String paymentId, CreatePisAuthorisationRequest request) {
        return readReceivedCommonPaymentDataByPaymentId(paymentId)
                   .map(pmt -> {
                       closePreviousAuthorisationsByPsu(pmt.getAuthorizations(), request.getAuthorizationType(), request.getPsuData());
                       return saveNewAuthorisation(pmt, request);
                   })
                   .map(c -> new CreatePisAuthorisationResponse(c.getExternalId(), c.getScaStatus(), c.getPaymentData().getInternalRequestId(), c.getPaymentData().getCancellationInternalRequestId(), request.getPsuData()));
    }

    @Override
    @Transactional
    public Optional<CreatePisAuthorisationResponse> createAuthorizationCancellation(String paymentId, CreatePisAuthorisationRequest request) {
        return readPisCommonPaymentDataByPaymentId(paymentId)
                   .filter(p -> p.getTransactionStatus().isNotFinalisedStatus())
                   .map(pmt -> {
                       closePreviousAuthorisationsByPsu(pmt.getAuthorizations(), request.getAuthorizationType(), request.getPsuData());
                       return saveNewAuthorisation(pmt, request);
                   })
                   .map(c -> new CreatePisAuthorisationResponse(c.getExternalId(), c.getScaStatus(), c.getPaymentData().getInternalRequestId(), c.getPaymentData().getCancellationInternalRequestId(), request.getPsuData()));
    }

    /**
     * Update common payment authorisation
     *
     * @param authorisationId id of the authorisation to be updated
     * @param request         contains data for updating authorisation
     * @return response contains updated data
     */
    @Override
    @Transactional
    public Optional<UpdatePisCommonPaymentPsuDataResponse> updatePisAuthorisation(String authorisationId, UpdatePisCommonPaymentPsuDataRequest request) {
        Optional<PisAuthorization> pisAuthorisationOptional = pisAuthorisationRepository.findByExternalIdAndAuthorizationType(
            authorisationId, PaymentAuthorisationType.CREATED);

        if (!pisAuthorisationOptional.isPresent()) {
            log.info("Authorisation ID: [{}]. Update pis authorisation failed, because pis authorisation with PaymentAuthorisationType.CREATED is not found by id",
                     authorisationId);
            return Optional.empty();
        }

        PisAuthorization authorisation = pisAuthorisationOptional.get();
        closePreviousAuthorisationsByPsu(authorisation, request.getPsuData());

        ScaStatus scaStatus = doUpdatePaymentAuthorisation(request, authorisation);
        return Optional.of(new UpdatePisCommonPaymentPsuDataResponse(scaStatus));
    }

    @Override
    @Transactional
    public boolean updatePisAuthorisationStatus(String authorisationId, ScaStatus scaStatus) {
        Optional<PisAuthorization> pisAuthorisationOptional = pisAuthorisationRepository.findByExternalId(authorisationId);

        if (!pisAuthorisationOptional.isPresent()) {
            log.info("Authorisation ID: [{}]. Update pis authorisation failed, because pis authorisation with PaymentAuthorisationType.CREATED is not found by id",
                     authorisationId);
            return false;
        }

        PisAuthorization authorisation = pisAuthorisationOptional.get();
        authorisation.setScaStatus(scaStatus);
        pisAuthorisationRepository.save(authorisation);
        return true;
    }

    /**
     * Update common payment cancellation authorisation
     *
     * @param cancellationId id of the authorisation to be updated
     * @param request        contains data for updating authorisation
     * @return response contains updated data
     */
    @Override
    @Transactional
    public Optional<UpdatePisCommonPaymentPsuDataResponse> updatePisCancellationAuthorisation(String cancellationId, UpdatePisCommonPaymentPsuDataRequest request) {
        Optional<PisAuthorization> pisAuthorisationOptional = pisAuthorisationRepository.findByExternalIdAndAuthorizationType(
            cancellationId, PaymentAuthorisationType.CANCELLED);

        if (!pisAuthorisationOptional.isPresent()) {
            log.info("Cancellation ID: [{}]. Update pis cancellation authorisation failed, because pis authorisation with PaymentAuthorisationType.CANCELLED is not found by id",
                     cancellationId);
            return Optional.empty();
        }

        PisAuthorization authorisation = pisAuthorisationOptional.get();
        closePreviousAuthorisationsByPsu(authorisation, request.getPsuData());

        ScaStatus scaStatus = doUpdatePaymentAuthorisation(request, authorisation);
        return Optional.of(new UpdatePisCommonPaymentPsuDataResponse(scaStatus));
    }
    /**
     * Reads authorisation data by authorisation Id
     *
     * @param authorisationId id of the authorisation
     * @return response contains authorisation data
     */
    @Override
    public Optional<GetPisAuthorisationResponse> getPisAuthorisationById(String authorisationId) {
        return pisAuthorisationRepository.findByExternalIdAndAuthorizationType(authorisationId, PaymentAuthorisationType.CREATED)
                   .map(pisCommonPaymentMapper::mapToGetPisAuthorizationResponse);
    }

    /**
     * Reads cancellation authorisation data by cancellation Id
     *
     * @param cancellationId id of the authorisation
     * @return response contains authorisation data
     */
    @Override
    public Optional<GetPisAuthorisationResponse> getPisCancellationAuthorisationById(String cancellationId) {
        return pisAuthorisationRepository.findByExternalIdAndAuthorizationType(cancellationId, PaymentAuthorisationType.CANCELLED)
                   .map(pisCommonPaymentMapper::mapToGetPisAuthorizationResponse);
    }

    /**
     * Reads authorisation IDs data by payment Id and type of authorization
     *
     * @param paymentId         id of the payment
     * @param authorisationType type of authorization required to create. Can be  CREATED or CANCELLED
     * @return response contains authorisation IDs
     */
    @Override
    public Optional<List<String>> getAuthorisationsByPaymentId(String paymentId, PaymentAuthorisationType authorisationType) {
        return readPisCommonPaymentDataByPaymentId(paymentId)
                   .map(pisCommonPaymentConfirmationExpirationService::checkAndUpdatePaymentDataOnConfirmationExpiration)
                   .map(pmt -> readAuthorisationsFromPaymentCommonData(pmt, authorisationType));
    }

    @Override
    @Transactional
    public Optional<ScaStatus> getAuthorisationScaStatus(@NotNull String paymentId, @NotNull String authorisationId, PaymentAuthorisationType authorisationType) {
        Optional<PisAuthorization> authorisationOptional = pisAuthorisationRepository.findByExternalIdAndAuthorizationType(authorisationId, authorisationType);

        if (!authorisationOptional.isPresent()) {
            log.info("Authorisation ID: [{}], Authorisation Type: [{}]. Get authorisation SCA status failed, because authorisation is not found",
                     authorisationId, authorisationType);
            return Optional.empty();
        }

        PisCommonPaymentData paymentData = authorisationOptional.get().getPaymentData();
        if (pisCommonPaymentConfirmationExpirationService.isPaymentDataOnConfirmationExpired(paymentData)) {
            pisCommonPaymentConfirmationExpirationService.updatePaymentDataOnConfirmationExpiration(paymentData);
            log.info("Payment ID: [{}]. Get authorisation SCA status failed, because Payment is expired",
                     paymentId);
            return Optional.of(ScaStatus.FAILED);
        }

        return authorisationOptional
                   .filter(auth -> paymentId.equals(auth.getPaymentData().getPaymentId()))
                   .map(PisAuthorization::getScaStatus);
    }
    @Override
    public boolean isAuthenticationMethodDecoupled(String authorisationId, String authenticationMethodId) {
        Optional<PisAuthorization> authorisationOptional = pisAuthorisationRepository.findByExternalId(authorisationId);

        return authorisationOptional.map(a -> a.getAvailableScaMethods()
                                                  .stream()
                                                  .filter(m -> Objects.equals(m.getAuthenticationMethodId(), authenticationMethodId))
                                                  .anyMatch(ScaMethod::isDecoupled))
                   .orElseGet(() -> {
                       log.info("Authorisation ID: [{}]. Get authorisation method decoupled status failed, because pis authorisation is not found",
                                authorisationId);
                       return false;
                   });
    }

    @Override
    @Transactional
    public boolean saveAuthenticationMethods(String authorisationId, List<CmsScaMethod> methods) {
        Optional<PisAuthorization> authorisationOptional = pisAuthorisationRepository.findByExternalId(authorisationId);

        if (!authorisationOptional.isPresent()) {
            log.info(" Authorisation ID: [{}]. Save authentication methods failed, because authorisation is not found", authorisationId);
            return false;
        }

        PisAuthorization authorisation = authorisationOptional.get();

        authorisation.setAvailableScaMethods(scaMethodMapper.mapToScaMethods(methods));
        pisAuthorisationRepository.save(authorisation);
        return true;
    }

    @Override
    @Transactional
    public boolean updateScaApproach(String authorisationId, ScaApproach scaApproach) {
        Optional<PisAuthorization> authorisationOptional = pisAuthorisationRepository.findByExternalId(authorisationId);

        if (!authorisationOptional.isPresent()) {
            log.info("Authorisation ID: [{}]. Update SCA approach failed, because pis authorisation is not found",
                     authorisationId);
            return false;
        }

        PisAuthorization authorisation = authorisationOptional.get();

        authorisation.setScaApproach(scaApproach);
        pisAuthorisationRepository.save(authorisation);
        return true;
    }

    @Override
    public Optional<AuthorisationScaApproachResponse> getAuthorisationScaApproach(String authorisationId, PaymentAuthorisationType authorisationType) {
        return pisAuthorisationRepository.findByExternalIdAndAuthorizationType(authorisationId, authorisationType)
                   .map(a -> new AuthorisationScaApproachResponse(a.getScaApproach()));
    }

    private Optional<PisCommonPaymentData> readReceivedCommonPaymentDataByPaymentId(String paymentId) {
        // todo implementation should be changed https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/issues/534
        Optional<PisCommonPaymentData> commonPaymentData = pisPaymentDataRepository.findByPaymentIdAndPaymentDataTransactionStatusIn(paymentId, Arrays.asList(RCVD, PATC))
                                                               .filter(CollectionUtils::isNotEmpty)
                                                               .map(list -> list.get(0).getPaymentData())
                                                               .map(pisCommonPaymentConfirmationExpirationService::checkAndUpdatePaymentDataOnConfirmationExpiration)
                                                               .filter(p -> EnumSet.of(RCVD, PATC).contains(p.getTransactionStatus()));

        if (!commonPaymentData.isPresent()) {
            commonPaymentData = pisCommonPaymentDataRepository.findByPaymentIdAndTransactionStatusIn(paymentId, Arrays.asList(RCVD, PATC))
                                    .map(pisCommonPaymentConfirmationExpirationService::checkAndUpdatePaymentDataOnConfirmationExpiration)
                                    .filter(p -> EnumSet.of(RCVD, PATC).contains(p.getTransactionStatus()));
        }

        return commonPaymentData;
    }

    /**
     * Creates {@link PisAuthorization} entity and stores it into database
     *
     * @param paymentData PIS payment data, for which authorisation is performed
     * @param request     needed parameters for creating PIS authorisation
     * @return PisAuthorization
     */
    private PisAuthorization saveNewAuthorisation(PisCommonPaymentData paymentData, CreatePisAuthorisationRequest request) {
        PisAuthorization pisAuthorisation = new PisAuthorization();
        Optional<PsuData> psuDataOptional = cmsPsuService.definePsuDataForAuthorisation(psuDataMapper.mapToPsuData(request.getPsuData()), paymentData.getPsuDataList());

        ScaStatus scaStatus = ScaStatus.RECEIVED;

        if (psuDataOptional.isPresent()) {
            PsuData psuData = psuDataOptional.get();
            if (PaymentAuthorisationType.CANCELLED != request.getAuthorizationType()) {
                paymentData.setPsuDataList(cmsPsuService.enrichPsuData(psuData, paymentData.getPsuDataList()));
            }
            pisAuthorisation.setPsuData(psuData);
            scaStatus = ScaStatus.PSUIDENTIFIED;
        }

        pisAuthorisation.setExternalId(UUID.randomUUID().toString());
        pisAuthorisation.setScaStatus(scaStatus);
        pisAuthorisation.setAuthorizationType(request.getAuthorizationType());
        pisAuthorisation.setRedirectUrlExpirationTimestamp(countRedirectUrlExpirationTimestampForAuthorisationType(request.getAuthorizationType()));
        pisAuthorisation.setAuthorisationExpirationTimestamp(countAuthorisationExpirationTimestamp());
        pisAuthorisation.setScaApproach(request.getScaApproach());
        pisAuthorisation.setPaymentData(paymentData);
        TppRedirectUri redirectURIs = request.getTppRedirectURIs();
        AuthorisationTemplateEntity authorisationTemplate = paymentData.getAuthorisationTemplate();

        boolean isCreatedType = PaymentAuthorisationType.CREATED == request.getAuthorizationType();

        String uri = StringUtils.defaultIfBlank(redirectURIs.getUri(), isCreatedType
                                                                           ? authorisationTemplate.getRedirectUri()
                                                                           : authorisationTemplate.getCancelRedirectUri());

        String nokUri = StringUtils.defaultIfBlank(redirectURIs.getNokUri(), isCreatedType
                                                                                 ? authorisationTemplate.getNokRedirectUri()
                                                                                 : authorisationTemplate.getCancelNokRedirectUri());

        pisAuthorisation.setTppOkRedirectUri(uri);
        pisAuthorisation.setTppNokRedirectUri(nokUri);
        return pisAuthorisationRepository.save(pisAuthorisation);
    }

    private void closePreviousAuthorisationsByPsu(PisAuthorization authorisation, PsuIdData psuIdData) {
        PisCommonPaymentData paymentData = authorisation.getPaymentData();
        PaymentAuthorisationType authorizationType = authorisation.getAuthorizationType();

        List<PisAuthorization> previousAuthorisations = paymentData.getAuthorizations().stream()
                                                            .filter(a -> !a.getExternalId().equals(authorisation.getExternalId()))
                                                            .collect(Collectors.toList());

        closePreviousAuthorisationsByPsu(previousAuthorisations, authorizationType, psuIdData);
    }

    private List<String> readAuthorisationsFromPaymentCommonData(PisCommonPaymentData paymentData, PaymentAuthorisationType authorisationType) {
        return paymentData.getAuthorizations()
                   .stream()
                   .filter(auth -> auth.getAuthorizationType() == authorisationType)
                   .map(PisAuthorization::getExternalId)
                   .collect(Collectors.toList());
    }

    private ScaStatus doUpdatePaymentAuthorisation(UpdatePisCommonPaymentPsuDataRequest request, PisAuthorization pisAuthorisation) {
        if (pisAuthorisation.getScaStatus().isFinalisedStatus()) {
            return pisAuthorisation.getScaStatus();
        }

        PsuData psuDataInAuthorisation = pisAuthorisation.getPsuData();
        PsuData psuDataInRequest = psuDataMapper.mapToPsuData(request.getPsuData());

        if (ScaStatus.RECEIVED == pisAuthorisation.getScaStatus()) {

            if (!cmsPsuService.isPsuDataRequestCorrect(psuDataInRequest, psuDataInAuthorisation)) {
                log.info("Authorisation ID: [{}], SCA status: [{}]. Update payment authorisation failed, because PSU data request does not match stored PSU data",
                         pisAuthorisation.getExternalId(), pisAuthorisation.getScaStatus().getValue());
                return pisAuthorisation.getScaStatus();
            }

            PisCommonPaymentData paymentData = pisAuthorisation.getPaymentData();
            List<PsuData> psuListInPayment = paymentData.getPsuDataList();
            Optional<PsuData> psuDataOptional = cmsPsuService.definePsuDataForAuthorisation(psuDataInRequest, psuListInPayment);

            if (psuDataOptional.isPresent()) {
                PsuData psuData = psuDataOptional.get();
                paymentData.setPsuDataList(cmsPsuService.enrichPsuData(psuData, psuListInPayment));
                pisAuthorisation.setPsuData(psuData);
            }

        } else {
            boolean isPsuCorrect = Objects.nonNull(psuDataInAuthorisation)
                                       && Objects.nonNull(psuDataInRequest)
                                       && psuDataInAuthorisation.contentEquals(psuDataInRequest);
            if (!isPsuCorrect) {
                log.info("Authorisation ID: [{}], SCA status: [{}]. Update payment authorisation failed, because PSU data request does not match stored PSU data",
                         pisAuthorisation.getExternalId(), pisAuthorisation.getScaStatus().getValue());
                return pisAuthorisation.getScaStatus();
            }
        }

        if (ScaStatus.SCAMETHODSELECTED == request.getScaStatus()) {
            String chosenMethod = request.getAuthenticationMethodId();
            if (StringUtils.isNotBlank(chosenMethod)) {
                pisAuthorisation.setChosenScaMethod(chosenMethod);
            }
        }
        pisAuthorisation.setScaStatus(request.getScaStatus());
        PisAuthorization saved = pisAuthorisationRepository.save(pisAuthorisation);
        return saved.getScaStatus();
    }

    private void closePreviousAuthorisationsByPsu(List<PisAuthorization> authorisations, PaymentAuthorisationType authorisationType, PsuIdData psuIdData) {
        PsuData psuData = psuDataMapper.mapToPsuData(psuIdData);

        if (psuData == null || psuData.isEmpty()) {
            log.info("Close previous authorisations by PSU failed because PSU data is not allowed");
            return;
        }

        List<PisAuthorization> pisAuthorisationList = authorisations
                                                          .stream()
                                                          .filter(auth -> auth.getAuthorizationType() == authorisationType)
                                                          .filter(auth -> Objects.nonNull(auth.getPsuData()) && auth.getPsuData().contentEquals(psuData))
                                                          .map(this::makeAuthorisationFailedAndExpired)
                                                          .collect(Collectors.toList());

        pisAuthorisationRepository.saveAll(pisAuthorisationList);
    }

    private OffsetDateTime countRedirectUrlExpirationTimestampForAuthorisationType(PaymentAuthorisationType authorisationType) {
        long redirectUrlExpirationTimeMs;

        if (authorisationType == PaymentAuthorisationType.CANCELLED) {
            redirectUrlExpirationTimeMs = aspspProfileService.getAspspSettings().getPaymentCancellationRedirectUrlExpirationTimeMs();
        } else {
            redirectUrlExpirationTimeMs = aspspProfileService.getAspspSettings().getRedirectUrlExpirationTimeMs();
        }

        return OffsetDateTime.now().plus(redirectUrlExpirationTimeMs, ChronoUnit.MILLIS);
    }

    private OffsetDateTime countAuthorisationExpirationTimestamp() {
        long authorisationExpirationTimeMs = aspspProfileService.getAspspSettings().getAuthorisationExpirationTimeMs();

        return OffsetDateTime.now().plus(authorisationExpirationTimeMs, ChronoUnit.MILLIS);
    }

    private PisAuthorization makeAuthorisationFailedAndExpired(PisAuthorization auth) {
        auth.setScaStatus(ScaStatus.FAILED);
        auth.setRedirectUrlExpirationTimestamp(OffsetDateTime.now());
        return auth;
    }

    private Optional<PisCommonPaymentData> readPisCommonPaymentDataByPaymentId(String paymentId) {
        // todo implementation should be changed https://git.adorsys.de/adorsys/xs2a/aspsp-xs2a/issues/534
        Optional<PisCommonPaymentData> commonPaymentData = pisPaymentDataRepository.findByPaymentId(paymentId)
                                                               .filter(CollectionUtils::isNotEmpty)
                                                               .map(list -> list.get(0).getPaymentData());
        if (!commonPaymentData.isPresent()) {
            commonPaymentData = pisCommonPaymentDataRepository.findByPaymentId(paymentId);
        }

        return commonPaymentData;
    }
}