package com.computablefacts.morta;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class SummaryTest {

  @Test
  public void testEqualsAndHashcode() {
    EqualsVerifier.forClass(Summary.class).verify();
  }
}
