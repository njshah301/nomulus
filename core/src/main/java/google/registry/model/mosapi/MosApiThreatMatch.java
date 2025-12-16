// Copyright 2025 The Nomulus Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package google.registry.model.mosapi;

import static com.google.common.base.Preconditions.checkArgument;
import static google.registry.util.PreconditionsUtils.checkArgumentNotNull;

import google.registry.model.Buildable;
import google.registry.model.ImmutableObject;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.io.Serializable;
import org.joda.time.LocalDate;

/**
 * Entity representing a domain abuse threat detected by MoSAPI.
 *
 * <p>Each row represents a single domain name flagged with a specific threat type on a specific date.
 */
@Entity
@Table(
    indexes = {
      @Index(name = "mosapithreatmatch_check_date_idx", columnList = "checkDate"),
      @Index(name = "mosapithreatmatch_tld_idx", columnList = "tld")
    })
public class MosApiThreatMatch extends ImmutableObject implements Buildable, Serializable {

  /** An auto-generated identifier and unique primary key for this entity. */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  /** The name of the offending domain */
  @Column(nullable = false)
  String domainName;

  /** The TLD of the domain. */
  @Column(nullable = false)
  String tld;

  /** The type of threat detected (e.g. "phishing", "malware", "botnetCc", "spam"). */
  @Column(nullable = false)
  String threatType;

  /** Date on which the check was run. */
  @Column(nullable = false, columnDefinition = "date")
  LocalDate checkDate;

  public Long getId() {
    return id;
  }

  public String getDomainName() {
    return domainName;
  }

  public String getTld() {
    return tld;
  }

  public String getThreatType() {
    return threatType;
  }

  public LocalDate getCheckDate() {
    return checkDate;
  }

  @Override
  public Builder asBuilder() {
    return new Builder(clone(this));
  }

  /** A builder for constructing {@link MosApiThreatMatch}, since it is immutable. */
  public static class Builder extends Buildable.Builder<MosApiThreatMatch> {
    public Builder() {}

    private Builder(MosApiThreatMatch instance) {
      super(instance);
    }

    @Override
    public MosApiThreatMatch build() {
      checkArgumentNotNull(getInstance().domainName, "Domain name cannot be null");
      checkArgumentNotNull(getInstance().tld, "TLD cannot be null");
      checkArgumentNotNull(getInstance().threatType, "Threat type cannot be null");
      checkArgumentNotNull(getInstance().checkDate, "Check date cannot be null");
      return super.build();
    }

    public Builder setDomainName(String domainName) {
      getInstance().domainName = domainName;
      return this;
    }

    public Builder setTld(String tld) {
      getInstance().tld = tld;
      return this;
    }

    public Builder setThreatType(String threatType) {
      getInstance().threatType = threatType;
      return this;
    }

    public Builder setCheckDate(LocalDate checkDate) {
      getInstance().checkDate = checkDate;
      return this;
    }
  }
}
