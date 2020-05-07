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

package de.adorsys.psd2.consent.web.psu.config;

import com.google.common.base.Predicates;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.Contact;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

@EnableSwagger2
@RequiredArgsConstructor
public class PsuApiSwaggerConfig {
    @Value("${xs2a.license.url}")
    private String licenseUrl;
    private final BuildProperties buildProperties;

    @SuppressWarnings("Guava")  // Intellij IDEA claims that Guava predicates could be replaced with Java API,
    // but actually it is not possible
    @Bean(name = "psu-api")
    public Docket apiDocklet() {
        return new Docket(DocumentationType.SWAGGER_2)
                   .groupName("CMS-PSU-API")
                   .apiInfo(getApiInfo())
                   .tags(CmsPsuApiTagHolder.PSU_PIIS_CONSENTS)
                   .select()
                   .apis(RequestHandlerSelectors.basePackage("de.adorsys.psd2.consent.web.psu"))
                   .paths(Predicates.not(PathSelectors.regex("/error.*?")))
                   .paths(Predicates.not(PathSelectors.regex("/connect.*")))
                   .paths(Predicates.not(PathSelectors.regex("/management.*")))
                   .build();
    }

    private ApiInfo getApiInfo() {
        return new ApiInfoBuilder()
                   .title("XS2A CMS PSU REST API")
                   .contact(new Contact("adorsys GmbH & Co. KG", "https://adorsys-platform.de/solutions/", "psd2@adorsys.de"))
                   .version(buildProperties.getVersion() + " " + buildProperties.get("build.number"))
                   .license("Apache License 2.0")
                   .licenseUrl(licenseUrl)
                   .build();
    }
}
