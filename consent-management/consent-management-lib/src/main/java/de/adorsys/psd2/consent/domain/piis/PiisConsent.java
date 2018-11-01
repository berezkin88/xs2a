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

package de.adorsys.psd2.consent.domain.piis;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.adorsys.psd2.consent.api.piis.PiisConsentTppAccessType;
import de.adorsys.psd2.consent.domain.ConsentType;
import de.adorsys.psd2.consent.domain.TppInfo;
import de.adorsys.psd2.xs2a.core.consent.ConsentStatus;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity(name = "piis_consent")
@ApiModel(description = "Piis consent entity", value = "PiisConsent")
public class PiisConsent {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "piis_consent_generator")
    @SequenceGenerator(name = "piis_consent_generator", sequenceName = "piis_consent_id_seq")
    private Long id;

    @Column(name = "external_id", nullable = false)
    @ApiModelProperty(value = "An external exposed identification of the created PIIS consent", required = true, example = "bf489af6-a2cb-4b75-b71d-d66d58b934d7")
    private String externalId;

    @Column(name = "request_date_time", nullable = false)
    @ApiModelProperty(value = "Date of the last request for this consent. The content is the local ASPSP date in ISODate Format", required = true, example = "2018-10-25T15:30:35.035Z")
    private LocalDateTime requestDateTime;

    @Column(name = "last_action_date")
    @ApiModelProperty(value = "Date of the last action for this consent. The content is the local ASPSP date in ISODate Format", example = "2018-05-04")
    private LocalDate lastActionDate;

    @Column(name = "expire_date")
    @ApiModelProperty(value = "Expiration date for the requested consent. The content is the local ASPSP date in ISODate Format", example = "2018-05-04")
    private LocalDate expireDate;

    @Column(name = "psu_id")
    @ApiModelProperty(value = "Psu id", example = "PSU_001")
    private String psuId;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "tpp_info_id")
    @ApiModelProperty(value = "Information about TPP")
    private TppInfo tppInfo;

    @Column(name = "consent_status", nullable = false)
    @Enumerated(value = EnumType.STRING)
    @ApiModelProperty(value = "The following code values are permitted 'received', 'valid', 'rejected', 'expired', 'revoked by psu', 'terminated by tpp'. These values might be extended by ASPSP by more values.", required = true, example = "VALID")
    private ConsentStatus consentStatus;

    @Column(name = "consent_type", nullable = false)
    @Enumerated(value = EnumType.STRING)
    @ApiModelProperty(value = "Type of the consent: AIS, PIS or PIIS.", required = true, example = "PIIS")
    private ConsentType consentType = ConsentType.PIIS;

    @ElementCollection
    @CollectionTable(name = "piis_account_reference", joinColumns = @JoinColumn(name = "consent_id"))
    @ApiModelProperty(value = "List of accounts", required = true)
    private List<PiisAccountReference> accounts = new ArrayList<>();

    @Column(name = "tpp_access_type", nullable = false)
    @Enumerated(value = EnumType.STRING)
    @ApiModelProperty(value = "Type of the tpp access: SINGLE_TPP or ALL_TPP.", required = true, example = "ALL_TPP")
    private PiisConsentTppAccessType tppAccessType;

    @Lob
    @JsonIgnore
    @Column(name = "aspsp_consent_data")
    private byte[] aspspConsentData;
}
