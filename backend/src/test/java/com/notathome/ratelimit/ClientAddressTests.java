package com.notathome.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClientAddressTests {

  @Test
  void ipv4AddressesAreUsedAsTheyAre() {
    assertThat(ClientAddress.key("203.0.113.7")).isEqualTo("203.0.113.7");
  }

  @Test
  void anIpv4AddressWrittenInIpv6FormIsTheSameClient() {
    assertThat(ClientAddress.key("::ffff:203.0.113.7")).isEqualTo("203.0.113.7");
    assertThat(ClientAddress.key("0:0:0:0:0:ffff:cb00:7107")).isEqualTo("203.0.113.7");
  }

  @Test
  void addressesInTheSameIpv6Slash64ShareOneKey() {
    String key = ClientAddress.key("2001:db8:1:2::1");

    assertThat(ClientAddress.key("2001:db8:1:2:aaaa:bbbb:cccc:dddd")).isEqualTo(key);
    assertThat(ClientAddress.key("2001:0DB8:0001:0002:0:0:0:ffff")).isEqualTo(key);
    assertThat(key).isEqualTo("2001:0db8:0001:0002/64");
  }

  @Test
  void differentIpv6Slash64sAreDifferentClients() {
    assertThat(ClientAddress.key("2001:db8:1:2::1"))
        .isNotEqualTo(ClientAddress.key("2001:db8:1:3::1"));
  }

  @Test
  void aZoneIdIsIgnored() {
    assertThat(ClientAddress.key("fe80::1%eth0")).isEqualTo(ClientAddress.key("fe80::1"));
  }

  @Test
  void missingOrUnparseableAddressesStillGetAKey() {
    assertThat(ClientAddress.key(null)).isEqualTo("unknown");
    assertThat(ClientAddress.key("  ")).isEqualTo("unknown");
    assertThat(ClientAddress.key("not:an:address")).isEqualTo("not:an:address");
  }
}
