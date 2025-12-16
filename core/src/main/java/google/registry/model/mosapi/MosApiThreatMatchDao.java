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

import static google.registry.persistence.transaction.TransactionManagerFactory.tm;

import com.google.common.collect.ImmutableList;
import google.registry.persistence.transaction.JpaTransactionManager;
import java.util.Optional;
import org.joda.time.LocalDate;

/** Data access object for {@link MosApiThreatMatch}. */
public class MosApiThreatMatchDao {

  /** Delete all entries with the specified date from the database. */
  public static void deleteEntriesByDate(LocalDate date) {
    tm().transact(() ->
        tm().query("DELETE FROM MosApiThreatMatch WHERE checkDate = :date")
            .setParameter("date", date)
            .executeUpdate());
  }

    /** Delete all entries with the specified date and TLD from the database. */
  public static void deleteEntriesByDateAndTld(LocalDate date, String tld) {
    tm().transact(() ->
        tm().query("DELETE FROM MosApiThreatMatch WHERE checkDate = :date AND tld = :tld")
            .setParameter("date", date)
            .setParameter("tld", tld)
            .executeUpdate());
  }

  /** Query the database and return a list of matches with the specified date and TLD. */
  public static ImmutableList<MosApiThreatMatch> loadEntriesByDateAndTld(LocalDate date, String tld) {
    return tm().transact(() -> ImmutableList.copyOf(
        tm().query("SELECT match FROM MosApiThreatMatch match WHERE match.checkDate = :date AND match.tld = :tld",
            MosApiThreatMatch.class)
            .setParameter("date", date)
            .setParameter("tld", tld)
            .getResultList()));
  }

  /** Query the database and return a list of matches with the specified date. */
  public static ImmutableList<MosApiThreatMatch> loadEntriesByDate(LocalDate date) {
    return tm().transact(() -> ImmutableList.copyOf(
        tm().query("SELECT match FROM MosApiThreatMatch match WHERE match.checkDate = :date",
            MosApiThreatMatch.class)
            .setParameter("date", date)
            .getResultList()));
  }

  /** Finds the latest date for which we have entries in the database. */
  public static Optional<LocalDate> getLatestCheckDate(String tld) {
    return tm().transact(() -> {
      return tm().query("SELECT MAX(checkDate) FROM MosApiThreatMatch WHERE tld = :tld", LocalDate.class)
          .setParameter("tld", tld)
          .getResultStream()
          .findFirst();
    });
  }
  
  /** Saves a single entry. */
  public static void save(MosApiThreatMatch match) {
      tm().transact(() -> tm().put(match));
  }
}
