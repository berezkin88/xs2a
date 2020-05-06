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

package de.adorsys.psd2.consent.web.psu.config;

import de.adorsys.psd2.consent.psu.api.config.CmsPsuApiTagName;
import springfox.documentation.service.Tag;

public class CmsPsuApiTagHolder {
    public static final Tag PSU_PIIS_CONSENTS = new Tag(CmsPsuApiTagName.PSU_PIIS_CONSENTS, "Provides access to consent management system for PSU PIIS");

    private CmsPsuApiTagHolder() {
    }
}
