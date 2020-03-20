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

package de.adorsys.psd2.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Optional if supported by API provider.  Is asking for additional information as added within this structured object. The usage of this data element requires at least one of the entries \&quot;accounts\&quot;,  \&quot;transactions\&quot; or \&quot;balances\&quot; also to be contained in the object.  If detailed accounts are referenced, it is required in addition that any account addressed within  the additionalInformation attribute is also addressed by at least one of the attributes \&quot;accounts\&quot;,  \&quot;transactions\&quot; or \&quot;balances\&quot;.
 */
@ApiModel(description = "Optional if supported by API provider.  Is asking for additional information as added within this structured object. The usage of this data element requires at least one of the entries \"accounts\",  \"transactions\" or \"balances\" also to be contained in the object.  If detailed accounts are referenced, it is required in addition that any account addressed within  the additionalInformation attribute is also addressed by at least one of the attributes \"accounts\",  \"transactions\" or \"balances\". ")
@Validated
@javax.annotation.Generated(value = "io.swagger.codegen.v3.generators.java.SpringCodegen", date = "2020-03-16T13:49:16.891743+02:00[Europe/Kiev]")

public class AdditionalInformationAccess {
    @JsonProperty("ownerName")
    @Valid
    private List<AccountReference> ownerName = null;

    public AdditionalInformationAccess ownerName(List<AccountReference> ownerName) {
        this.ownerName = ownerName;
        return this;
    }

    public AdditionalInformationAccess addOwnerNameItem(AccountReference ownerNameItem) {
        if (this.ownerName == null) {
            this.ownerName = new ArrayList<>();
        }
        this.ownerName.add(ownerNameItem);
        return this;
    }

    /**
     * Is asking for account owner name of the accounts referenced within.  If the array is empty in the request, the TPP is asking for the account  owner name of all accessible accounts.  This may be restricted in a PSU/ASPSP authorization dialogue.  If the array is empty, also the arrays for accounts, balances or transactions shall be empty, if used. The ASPSP will indicate in the consent resource after a successful authorisation,  whether the ownerName consent can be accepted by providing the accounts on which the ownerName will  be delivered. This array can be empty.
     *
     * @return ownerName
     **/
    @ApiModelProperty(value = "Is asking for account owner name of the accounts referenced within.  If the array is empty in the request, the TPP is asking for the account  owner name of all accessible accounts.  This may be restricted in a PSU/ASPSP authorization dialogue.  If the array is empty, also the arrays for accounts, balances or transactions shall be empty, if used. The ASPSP will indicate in the consent resource after a successful authorisation,  whether the ownerName consent can be accepted by providing the accounts on which the ownerName will  be delivered. This array can be empty. ")

    @Valid


    @JsonProperty("ownerName")
    public List<AccountReference> getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(List<AccountReference> ownerName) {
        this.ownerName = ownerName;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AdditionalInformationAccess additionalInformationAccess = (AdditionalInformationAccess) o;
        return Objects.equals(this.ownerName, additionalInformationAccess.ownerName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ownerName);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class AdditionalInformationAccess {\n");

        sb.append("    ownerName: ").append(toIndentedString(ownerName)).append("\n");
        sb.append("}");
        return sb.toString();
    }

    /**
     * Convert the given object to string with each line indented by 4 spaces
     * (except the first line).
     */
    private String toIndentedString(Object o) {
        if (o == null) {
            return "null";
        }
        return o.toString().replace("\n", "\n    ");
    }
}

