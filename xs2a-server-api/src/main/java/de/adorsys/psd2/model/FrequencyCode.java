package de.adorsys.psd2.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The following codes from the \"EventFrequency7Code\" of ISO 20022 are supported: - \"Daily\" - \"Weekly\" - \"EveryTwoWeeks\" - \"Monthly\" - \"EveryTwoMonths\" - \"Quarterly\" - \"SemiAnnual\" - \"Annual\" - \"MonthlyVariable\"
 */
public enum FrequencyCode {

  DAILY("Daily"),

  WEEKLY("Weekly"),

  EVERYTWOWEEKS("EveryTwoWeeks"),

  MONTHLY("Monthly"),

  EVERYTWOMONTHS("EveryTwoMonths"),

  QUARTERLY("Quarterly"),

  SEMIANNUAL("SemiAnnual"),

  ANNUAL("Annual"),

  MONTHLYVARIABLE("MonthlyVariable");

  private String value;

  FrequencyCode(String value) {
    this.value = value;
  }

  @Override
  @JsonValue
  public String toString() {
    return String.valueOf(value);
  }

  @JsonCreator
  public static FrequencyCode fromValue(String text) {
    for (FrequencyCode b : FrequencyCode.values()) {
      if (String.valueOf(b.value).equals(text)) {
        return b;
      }
    }
    return null;
  }
}

