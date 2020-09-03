package com.computablefacts.morta;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.Sets;

import nl.jqno.equalsverifier.EqualsVerifier;

public class DictionaryTest {

  @Test
  public void testEqualsAndHashcode() {
    EqualsVerifier.forClass(Dictionary.class).verify();
  }

  @Test(expected = IllegalStateException.class)
  public void testForbiddenDuplicateLabels() {
    Dictionary dictionary = new Dictionary();
    dictionary.put("un", 1);
    dictionary.put("un", 2);
  }

  @Test(expected = IllegalStateException.class)
  public void testForbiddenDuplicateIds() {
    Dictionary dictionary = new Dictionary();
    dictionary.put("un", 1);
    dictionary.put("deux", 1);
  }

  @Test
  public void testEntrySet() {

    Set<Map.Entry<String, Integer>> map = Sets.newHashSet(new AbstractMap.SimpleEntry<>("un", 1),
        new AbstractMap.SimpleEntry<>("deux", 2), new AbstractMap.SimpleEntry<>("trois", 3),
        new AbstractMap.SimpleEntry<>("quatre", 4));

    Dictionary dictionary = dictionary();

    Assert.assertEquals(map, dictionary.entrySet());
  }

  @Test
  public void testPutAll() {

    Map<String, Integer> map = new HashMap<>();
    map.put("cinq", 5);
    map.put("six", 6);
    map.put("sept", 7);

    Dictionary dictionary = dictionary();

    Assert.assertFalse(dictionary.containsKey("cinq"));
    Assert.assertFalse(dictionary.containsKey("six"));
    Assert.assertFalse(dictionary.containsKey("sept"));

    dictionary.putAll(map);

    Assert.assertTrue(dictionary.containsKey("cinq"));
    Assert.assertTrue(dictionary.containsKey("six"));
    Assert.assertTrue(dictionary.containsKey("sept"));
  }

  @Test
  public void testKeys() {

    Dictionary dictionary = dictionary();

    Assert.assertEquals(Sets.newHashSet("un", "deux", "trois", "quatre"), dictionary.keySet());
  }

  @Test
  public void testValue() {

    Dictionary dictionary = dictionary();

    Assert.assertEquals(Sets.newHashSet(1, 2, 3, 4), dictionary.values());
  }

  @Test
  public void testSize() {

    Dictionary dictionary = dictionary();

    Assert.assertEquals(4, dictionary.size());
  }

  @Test
  public void testClear() {

    Dictionary dictionary = dictionary();

    Assert.assertFalse(dictionary.isEmpty());

    dictionary.clear();

    Assert.assertTrue(dictionary.isEmpty());
  }

  @Test
  public void testContainsKey() {

    Dictionary dictionary = dictionary();

    Assert.assertTrue(dictionary.containsKey("trois"));
  }

  @Test
  public void testContainsValue() {

    Dictionary dictionary = dictionary();

    Assert.assertTrue(dictionary.containsValue(3));
  }

  @Test
  public void testRemove() {

    Dictionary dictionary = dictionary();

    Assert.assertEquals((Object) 3, dictionary.get("trois"));
    Assert.assertEquals((Object) 3, dictionary.remove("trois"));
    Assert.assertFalse(dictionary.containsKey("trois"));
  }

  @Test
  public void testGetId() {

    Dictionary dictionary = dictionary();

    Assert.assertEquals(3, dictionary.id("trois"));
  }

  @Test
  public void testGetLabel() {

    Dictionary dictionary = dictionary();

    Assert.assertEquals("trois", dictionary.label(3));
  }

  private Dictionary dictionary() {

    Dictionary dictionary = new Dictionary();
    dictionary.put("un", 1);
    dictionary.put("deux", 2);
    dictionary.put("trois", 3);
    dictionary.put("quatre", 4);

    return dictionary;
  }
}
