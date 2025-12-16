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

import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.ImmutableObjectSubject.assertAboutImmutableObjects;
import static google.registry.persistence.transaction.TransactionManagerFactory.tm;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.collect.ImmutableList;
import google.registry.model.EntityTestCase;
import java.util.Optional;
import org.joda.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link MosApiThreatMatch}. */
public class MosApiThreatMatchTest extends EntityTestCase {

  private MosApiThreatMatch match;

  MosApiThreatMatchTest() {
    super(JpaEntityCoverageCheck.ENABLED);
  }

  @BeforeEach
  void setUp() {
    match =
        new MosApiThreatMatch.Builder()
            .setTld("tld")
            .setDomainName("example.tld")
            .setThreatType("malware")
            .setCheckDate(LocalDate.parse("2025-01-01"))
            .build();
  }

  @Test
  void testPersistence() {
    tm().transact(() -> tm().put(match));
    assertAboutImmutableObjects().that(tm().transact(() -> tm().loadByEntity(match))).isEqualExceptFields(match, "id");
  }

  @Test
  void testValidation_nullFields() {
    assertThrows(IllegalArgumentException.class, () -> match.asBuilder().setTld(null).build());
    assertThrows(IllegalArgumentException.class, () -> match.asBuilder().setDomainName(null).build());
    assertThrows(IllegalArgumentException.class, () -> match.asBuilder().setThreatType(null).build());
    assertThrows(IllegalArgumentException.class, () -> match.asBuilder().setCheckDate(null).build());
  }
  
  @Test
  void testDao_saveAndLoad() {
    MosApiThreatMatchDao.save(match);
    ImmutableList<MosApiThreatMatch> results = MosApiThreatMatchDao.loadEntriesByDate(LocalDate.parse("2025-01-01"));
    assertThat(results).hasSize(1);
    assertThat(results.get(0).getDomainName()).isEqualTo("example.tld");
  }
  
  @Test
  void testDao_getLatestCheckDate() {
    // Empty DB
    assertThat(MosApiThreatMatchDao.getLatestCheckDate("tld")).isEmpty();
    
    // Save match
    MosApiThreatMatchDao.save(match);
    assertThat(MosApiThreatMatchDao.getLatestCheckDate("tld")).hasValue(LocalDate.parse("2025-01-01"));
    
    // Save newer match
    MosApiThreatMatch newer = match.asBuilder().setCheckDate(LocalDate.parse("2025-01-02")).build();
    MosApiThreatMatchDao.save(newer);
    assertThat(MosApiThreatMatchDao.getLatestCheckDate("tld")).hasValue(LocalDate.parse("2025-01-02"));
    
    // TLD isolation
    assertThat(MosApiThreatMatchDao.getLatestCheckDate("other")).isEmpty();
  }
  
  @Test
  void testDao_deleteEntries() {
     MosApiThreatMatchDao.save(match);
     assertThat(MosApiThreatMatchDao.loadEntriesByDate(LocalDate.parse("2025-01-01"))).hasSize(1);
     
     MosApiThreatMatchDao.deleteEntriesByDateAndTld(LocalDate.parse("2025-01-01"), "tld");
     assertThat(MosApiThreatMatchDao.loadEntriesByDate(LocalDate.parse("2025-01-01"))).isEmpty();
  }
}
