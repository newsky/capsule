/**
 * Copyright (c) Michael Steindorfer <Centrum Wiskunde & Informatica> and Contributors.
 * All rights reserved.
 *
 * This file is licensed under the BSD 2-Clause License, which accompanies this project
 * and is available under https://opensource.org/licenses/BSD-2-Clause.
 */
package io.usethesource.capsule.core;

import io.usethesource.capsule.api.Set;
import io.usethesource.capsule.util.ArrayUtils;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("rawtypes")
public class TrieSet<K> implements Set.Immutable<K> {

  @SuppressWarnings("unchecked")
  private static final TrieSet EMPTY_SET = new TrieSet(CompactSetNode.EMPTY_NODE, 0, 0);

  private static final boolean DEBUG = false;

  private final AbstractSetNode<K> rootNode;
  private final int cachedHashCode;
  private final int cachedSize;

  TrieSet(AbstractSetNode<K> rootNode, int hashCode, int cachedSize) {
    this.rootNode = rootNode;
    this.cachedHashCode = hashCode;
    this.cachedSize = cachedSize;
    if (DEBUG) {
      assert checkHashCodeAndSize(hashCode, cachedSize);
    }
  }

  @SuppressWarnings("unchecked")
  public static final <K> Set.Immutable<K> of() {
    return TrieSet.EMPTY_SET;
  }

  public static final <K> Set.Immutable<K> of(K key0) {
    final int keyHash0 = key0.hashCode();

    final int nodeMap = 0;
    final int dataMap = CompactSetNode.bitpos(CompactSetNode.mask(keyHash0, 0));

    CompactSetNode<K> newRootNode = CompactSetNode.nodeOf(null, nodeMap, dataMap, key0);

    return new TrieSet<K>(newRootNode, keyHash0, 1);
  }

  public static final <K> Set.Immutable<K> of(K key0, K key1) {
    assert !Objects.equals(key0, key1);

    final int keyHash0 = key0.hashCode();
    final int keyHash1 = key1.hashCode();

    CompactSetNode<K> newRootNode =
        CompactSetNode.mergeTwoKeyValPairs(key0, keyHash0, key1, keyHash1, 0);

    return new TrieSet<K>(newRootNode, keyHash0 + keyHash1, 2);
  }

  @SuppressWarnings("unchecked")
  public static final <K> Set.Immutable<K> of(K... keys) {
    Set.Immutable<K> result = TrieSet.EMPTY_SET;

    for (final K key : keys) {
      result = result.insert(key);
    }

    return result;
  }

  @SuppressWarnings("unchecked")
  public static final <K> Set.Transient<K> transientOf() {
    return TrieSet.EMPTY_SET.asTransient();
  }

  @SuppressWarnings("unchecked")
  public static final <K> Set.Transient<K> transientOf(K... keys) {
    final Set.Transient<K> result = TrieSet.EMPTY_SET.asTransient();

    for (final K key : keys) {
      result.insert(key);
    }

    return result;
  }

  private boolean checkHashCodeAndSize(final int targetHash, final int targetSize) {
    int hash = 0;
    int size = 0;

    for (Iterator<K> it = keyIterator(); it.hasNext();) {
      final K key = it.next();

      hash += key.hashCode();
      size += 1;
    }

    return hash == targetHash && size == targetSize;
  }

  private static final int transformHashCode(final int hash) {
    return hash;
  }

  @Override
  public boolean contains(final Object o) {
    try {
      @SuppressWarnings("unchecked")
      final K key = (K) o;
      return rootNode.contains(key, transformHashCode(key.hashCode()), 0);
    } catch (ClassCastException unused) {
      return false;
    }
  }

  @Override
  public Optional<K> apply(K key) {
    return rootNode.findByKey(key, transformHashCode(key.hashCode()), 0);
  }

  public K get(final Object o) {
    try {
      @SuppressWarnings("unchecked")
      final K key = (K) o;
      return apply(key).orElse(null);
    } catch (ClassCastException unused) {
      return null;
    }
  }

  @Override
  public Set.Immutable<K> insert(final K key) {
    final int keyHash = key.hashCode();
    final SetResult<K> details = SetResult.unchanged();

    final CompactSetNode<K> newRootNode =
        rootNode.updated(null, key, transformHashCode(keyHash), 0, details);

    if (details.isModified()) {
      return new TrieSet<K>(newRootNode, cachedHashCode ^ keyHash, cachedSize + 1);
    }

    return this;
  }

  @Override
  public Set.Immutable<K> remove(final K key) {
    final int keyHash = key.hashCode();
    final SetResult<K> details = SetResult.unchanged();

    final CompactSetNode<K> newRootNode =
        rootNode.removed(null, key, transformHashCode(keyHash), 0, details);

    if (details.isModified()) {
      return new TrieSet<K>(newRootNode, cachedHashCode ^ keyHash, cachedSize - 1);
    }

    return this;
  }

  @Override
  public Set.Immutable<K> insertAll(final Set<? extends K> set) {
    final Set.Transient<K> tmpTransient = this.asTransient();
    tmpTransient.insertAll(set);
    return tmpTransient.asImmutable();
  }

  @Override
  public Set.Immutable<K> removeAll(final Set<? extends K> set) {
    final Set.Transient<K> tmpTransient = this.asTransient();
    tmpTransient.removeAll(set);
    return tmpTransient.asImmutable();
  }

  @Override
  public Set.Immutable<K> retainAll(final Set<? extends K> set) {
    final Set.Transient<K> tmpTransient = this.asTransient();
    tmpTransient.retainAll(set);
    return tmpTransient.asImmutable();
  }

  @Override
  public long size() {
    return cachedSize;
  }

  @Override
  public boolean isEmpty() {
    return cachedSize == 0;
  }

  @Override
  public Iterator<K> iterator() {
    return keyIterator();
  }

  private Iterator<K> keyIterator() {
    return new SetKeyIterator<>(rootNode);
  }

  @Override
  public boolean equals(final Object other) {
    if (other == this) {
      return true;
    }
    if (other == null) {
      return false;
    }

    if (other instanceof TrieSet) {
      TrieSet<?> that = (TrieSet<?>) other;

      if (this.cachedSize != that.cachedSize) {
        return false;
      }

      if (this.cachedHashCode != that.cachedHashCode) {
        return false;
      }

      return rootNode.equals(that.rootNode);
    } else if (other instanceof io.usethesource.capsule.api.Set) {
      io.usethesource.capsule.api.Set that = (io.usethesource.capsule.api.Set) other;

      if (this.size() != that.size())
        return false;

      return containsAll(that);
    }

    return false;
  }

  @Override
  public int hashCode() {
    return cachedHashCode;
  }

  @Override
  public java.util.Set<K> asJdkCollection() {
    return new TrieSetAsImmutableJdkCollection<>(this);
  }

  @Override
  public boolean isTransientSupported() {
    return true;
  }

  @Override
  public Set.Transient<K> asTransient() {
    return new TransientTrieSet<K>(this);
  }

  @Override
  public Set.Immutable<K> asImmutable() {
    return this;
  }

  /*
   * For analysis purposes only.
   */
  protected AbstractSetNode<K> getRootNode() {
    return rootNode;
  }

  /*
   * For analysis purposes only.
   */
  protected Iterator<AbstractSetNode<K>> nodeIterator() {
    return new SetNodeIterator<>(rootNode);
  }

  /*
   * For analysis purposes only.
   */
  protected int getNodeCount() {
    final Iterator<AbstractSetNode<K>> it = nodeIterator();
    int sumNodes = 0;

    for (; it.hasNext(); it.next()) {
      sumNodes += 1;
    }

    return sumNodes;
  }

  /*
   * For analysis purposes only. Payload X Node
   */
  protected int[][] arityCombinationsHistogram() {
    final Iterator<AbstractSetNode<K>> it = nodeIterator();
    final int[][] sumArityCombinations = new int[33][33];

    while (it.hasNext()) {
      final AbstractSetNode<K> node = it.next();
      sumArityCombinations[node.payloadArity()][node.nodeArity()] += 1;
    }

    return sumArityCombinations;
  }

  /*
   * For analysis purposes only.
   */
  protected int[] arityHistogram() {
    final int[][] sumArityCombinations = arityCombinationsHistogram();
    final int[] sumArity = new int[33];

    final int maxArity = 32; // TODO: factor out constant

    for (int j = 0; j <= maxArity; j++) {
      for (int maxRestArity = maxArity - j, k = 0; k <= maxRestArity - j; k++) {
        sumArity[j + k] += sumArityCombinations[j][k];
      }
    }

    return sumArity;
  }

  /*
   * For analysis purposes only.
   */
  public void printStatistics() {
    final int[][] sumArityCombinations = arityCombinationsHistogram();
    final int[] sumArity = arityHistogram();
    final int sumNodes = getNodeCount();

    final int[] cumsumArity = new int[33];
    for (int cumsum = 0, i = 0; i < 33; i++) {
      cumsum += sumArity[i];
      cumsumArity[i] = cumsum;
    }

    final float threshhold = 0.01f; // for printing results
    for (int i = 0; i < 33; i++) {
      float arityPercentage = (float) (sumArity[i]) / sumNodes;
      float cumsumArityPercentage = (float) (cumsumArity[i]) / sumNodes;

      if (arityPercentage != 0 && arityPercentage >= threshhold) {
        // details per level
        StringBuilder bldr = new StringBuilder();
        int max = i;
        for (int j = 0; j <= max; j++) {
          for (int k = max - j; k <= max - j; k++) {
            float arityCombinationsPercentage = (float) (sumArityCombinations[j][k]) / sumNodes;

            if (arityCombinationsPercentage != 0 && arityCombinationsPercentage >= threshhold) {
              bldr.append(String.format("%d/%d: %s, ", j, k,
                  new DecimalFormat("0.00%").format(arityCombinationsPercentage)));
            }
          }
        }
        final String detailPercentages = bldr.toString();

        // overview
        System.out.println(String.format("%2d: %s\t[cumsum = %s]\t%s", i,
            new DecimalFormat("0.00%").format(arityPercentage),
            new DecimalFormat("0.00%").format(cumsumArityPercentage), detailPercentages));
      }
    }
  }

  static final class SetResult<K> {
    private K replacedValue;
    private boolean isModified;
    private boolean isReplaced;

    // update: inserted/removed single element, element count changed
    public void modified() {
      this.isModified = true;
    }

    public void updated(K replacedValue) {
      this.replacedValue = replacedValue;
      this.isModified = true;
      this.isReplaced = true;
    }

    // update: neither element, nor element count changed
    public static <K> SetResult<K> unchanged() {
      return new SetResult<>();
    }

    private SetResult() {}

    public boolean isModified() {
      return isModified;
    }

    public boolean hasReplacedValue() {
      return isReplaced;
    }

    public K getReplacedValue() {
      return replacedValue;
    }
  }

  protected static interface INode<K, V> {
  }

  protected static abstract class AbstractSetNode<K> implements INode<K, java.lang.Void> {

    static final int TUPLE_LENGTH = 1;

    abstract boolean contains(final K key, final int keyHash, final int shift);

    abstract boolean contains(final K key, final int keyHash, final int shift,
        final Comparator<Object> cmp);

    abstract Optional<K> findByKey(final K key, final int keyHash, final int shift);

    abstract Optional<K> findByKey(final K key, final int keyHash, final int shift,
        final Comparator<Object> cmp);

    abstract CompactSetNode<K> updated(final AtomicReference<Thread> mutator, final K key,
        final int keyHash, final int shift, final SetResult<K> details);

    abstract CompactSetNode<K> updated(final AtomicReference<Thread> mutator, final K key,
        final int keyHash, final int shift, final SetResult<K> details,
        final Comparator<Object> cmp);

    abstract CompactSetNode<K> removed(final AtomicReference<Thread> mutator, final K key,
        final int keyHash, final int shift, final SetResult<K> details);

    abstract CompactSetNode<K> removed(final AtomicReference<Thread> mutator, final K key,
        final int keyHash, final int shift, final SetResult<K> details,
        final Comparator<Object> cmp);

    static final boolean isAllowedToEdit(AtomicReference<Thread> x, AtomicReference<Thread> y) {
      return x != null && y != null && (x == y || x.get() == y.get());
    }

    abstract boolean hasNodes();

    abstract int nodeArity();

    abstract AbstractSetNode<K> getNode(final int index);

    @Deprecated
    Iterator<? extends AbstractSetNode<K>> nodeIterator() {
      return new Iterator<AbstractSetNode<K>>() {

        int nextIndex = 0;
        final int nodeArity = AbstractSetNode.this.nodeArity();

        @Override
        public void remove() {
          throw new UnsupportedOperationException();
        }

        @Override
        public AbstractSetNode<K> next() {
          if (!hasNext())
            throw new NoSuchElementException();
          return AbstractSetNode.this.getNode(nextIndex++);
        }

        @Override
        public boolean hasNext() {
          return nextIndex < nodeArity;
        }
      };
    }

    abstract boolean hasPayload();

    abstract int payloadArity();

    abstract K getKey(final int index);

    @Deprecated
    abstract boolean hasSlots();

    abstract int slotArity();

    abstract Object getSlot(final int index);

    /**
     * The arity of this trie node (i.e. number of values and nodes stored on this level).
     * 
     * @return sum of nodes and values stored within
     */

    int arity() {
      return payloadArity() + nodeArity();
    }

    int size() {
      final Iterator<K> it = new SetKeyIterator<>(this);

      int size = 0;
      while (it.hasNext()) {
        size += 1;
        it.next();
      }

      return size;
    }
  }

  protected static abstract class CompactSetNode<K> extends AbstractSetNode<K> {

    static final int HASH_CODE_LENGTH = 32;

    static final int BIT_PARTITION_SIZE = 5;
    static final int BIT_PARTITION_MASK = 0b11111;

    static final int mask(final int keyHash, final int shift) {
      return (keyHash >>> shift) & BIT_PARTITION_MASK;
    }

    static final int bitpos(final int mask) {
      return 1 << mask;
    }

    abstract int nodeMap();

    abstract int dataMap();

    static final byte SIZE_EMPTY = 0b00;
    static final byte SIZE_ONE = 0b01;
    static final byte SIZE_MORE_THAN_ONE = 0b10;

    /**
     * Abstract predicate over a node's size. Value can be either {@value #SIZE_EMPTY},
     * {@value #SIZE_ONE}, or {@value #SIZE_MORE_THAN_ONE}.
     * 
     * @return size predicate
     */
    abstract byte sizePredicate();

    @Override
    abstract CompactSetNode<K> getNode(final int index);

    boolean nodeInvariant() {
      boolean inv1 = (size() - payloadArity() >= 2 * (arity() - payloadArity()));
      boolean inv2 = (this.arity() == 0) ? sizePredicate() == SIZE_EMPTY : true;
      boolean inv3 =
          (this.arity() == 1 && payloadArity() == 1) ? sizePredicate() == SIZE_ONE : true;
      boolean inv4 = (this.arity() >= 2) ? sizePredicate() == SIZE_MORE_THAN_ONE : true;

      boolean inv5 = (this.nodeArity() >= 0) && (this.payloadArity() >= 0)
          && ((this.payloadArity() + this.nodeArity()) == this.arity());

      return inv1 && inv2 && inv3 && inv4 && inv5;
    }

    abstract CompactSetNode<K> copyAndInsertValue(final AtomicReference<Thread> mutator,
        final int bitpos, final K key);

    abstract CompactSetNode<K> copyAndRemoveValue(final AtomicReference<Thread> mutator,
        final int bitpos);

    abstract CompactSetNode<K> copyAndSetNode(final AtomicReference<Thread> mutator,
        final int bitpos, final CompactSetNode<K> node);

    abstract CompactSetNode<K> copyAndMigrateFromInlineToNode(final AtomicReference<Thread> mutator,
        final int bitpos, final CompactSetNode<K> node);

    abstract CompactSetNode<K> copyAndMigrateFromNodeToInline(final AtomicReference<Thread> mutator,
        final int bitpos, final CompactSetNode<K> node);

    static final <K> CompactSetNode<K> mergeTwoKeyValPairs(final K key0, final int keyHash0,
        final K key1, final int keyHash1, final int shift) {
      assert !(key0.equals(key1));

      if (shift >= HASH_CODE_LENGTH) {
        return new HashCollisionSetNode<>(keyHash0, (K[]) new Object[] {key0, key1});
      }

      final int mask0 = mask(keyHash0, shift);
      final int mask1 = mask(keyHash1, shift);

      if (mask0 != mask1) {
        // both nodes fit on same level
        final int dataMap = bitpos(mask0) | bitpos(mask1);

        if (mask0 < mask1) {
          return nodeOf(null, (0), dataMap, new Object[] {key0, key1});
        } else {
          return nodeOf(null, (0), dataMap, new Object[] {key1, key0});
        }
      } else {
        final CompactSetNode<K> node =
            mergeTwoKeyValPairs(key0, keyHash0, key1, keyHash1, shift + BIT_PARTITION_SIZE);
        // values fit on next level

        final int nodeMap = bitpos(mask0);
        return nodeOf(null, nodeMap, (0), new Object[] {node});
      }
    }

    static final CompactSetNode EMPTY_NODE;

    static {

      EMPTY_NODE = new BitmapIndexedSetNode<>(null, (0), (0), new Object[] {});

    };

    static final <K> CompactSetNode<K> nodeOf(final AtomicReference<Thread> mutator,
        final int nodeMap, final int dataMap, final Object[] nodes) {
      return new BitmapIndexedSetNode<>(mutator, nodeMap, dataMap, nodes);
    }

    @SuppressWarnings("unchecked")
    static final <K> CompactSetNode<K> nodeOf(AtomicReference<Thread> mutator) {
      return EMPTY_NODE;
    }

    static final <K> CompactSetNode<K> nodeOf(AtomicReference<Thread> mutator, final int nodeMap,
        final int dataMap, final K key) {
      assert nodeMap == 0;
      return nodeOf(mutator, (0), dataMap, new Object[] {key});
    }

    static final int index(final int bitmap, final int bitpos) {
      return java.lang.Integer.bitCount(bitmap & (bitpos - 1));
    }

    static final int index(final int bitmap, final int mask, final int bitpos) {
      return (bitmap == -1) ? mask : index(bitmap, bitpos);
    }

    int dataIndex(final int bitpos) {
      return java.lang.Integer.bitCount(dataMap() & (bitpos - 1));
    }

    int nodeIndex(final int bitpos) {
      return java.lang.Integer.bitCount(nodeMap() & (bitpos - 1));
    }

    CompactSetNode<K> nodeAt(final int bitpos) {
      return getNode(nodeIndex(bitpos));
    }

    @Override
    boolean contains(final K key, final int keyHash, final int shift) {
      final int mask = mask(keyHash, shift);
      final int bitpos = bitpos(mask);

      final int dataMap = dataMap();
      if ((dataMap & bitpos) != 0) {
        final int index = index(dataMap, mask, bitpos);
        return getKey(index).equals(key);
      }

      final int nodeMap = nodeMap();
      if ((nodeMap & bitpos) != 0) {
        final int index = index(nodeMap, mask, bitpos);
        return getNode(index).contains(key, keyHash, shift + BIT_PARTITION_SIZE);
      }

      return false;
    }

    @Override
    boolean contains(final K key, final int keyHash, final int shift,
        final Comparator<Object> cmp) {
      final int mask = mask(keyHash, shift);
      final int bitpos = bitpos(mask);

      final int dataMap = dataMap();
      if ((dataMap & bitpos) != 0) {
        final int index = index(dataMap, mask, bitpos);
        return cmp.compare(getKey(index), key) == 0;
      }

      final int nodeMap = nodeMap();
      if ((nodeMap & bitpos) != 0) {
        final int index = index(nodeMap, mask, bitpos);
        return getNode(index).contains(key, keyHash, shift + BIT_PARTITION_SIZE, cmp);
      }

      return false;
    }

    @Override
    Optional<K> findByKey(final K key, final int keyHash, final int shift) {
      final int mask = mask(keyHash, shift);
      final int bitpos = bitpos(mask);

      if ((dataMap() & bitpos) != 0) { // inplace value
        final int index = dataIndex(bitpos);
        if (getKey(index).equals(key)) {
          return Optional.of(getKey(index));
        }

        return Optional.empty();
      }

      if ((nodeMap() & bitpos) != 0) { // node (not value)
        final AbstractSetNode<K> subNode = nodeAt(bitpos);

        return subNode.findByKey(key, keyHash, shift + BIT_PARTITION_SIZE);
      }

      return Optional.empty();
    }

    @Override
    Optional<K> findByKey(final K key, final int keyHash, final int shift,
        final Comparator<Object> cmp) {
      final int mask = mask(keyHash, shift);
      final int bitpos = bitpos(mask);

      if ((dataMap() & bitpos) != 0) { // inplace value
        final int index = dataIndex(bitpos);
        if (cmp.compare(getKey(index), key) == 0) {
          return Optional.of(getKey(index));
        }

        return Optional.empty();
      }

      if ((nodeMap() & bitpos) != 0) { // node (not value)
        final AbstractSetNode<K> subNode = nodeAt(bitpos);

        return subNode.findByKey(key, keyHash, shift + BIT_PARTITION_SIZE, cmp);
      }

      return Optional.empty();
    }

    @Override
    CompactSetNode<K> updated(final AtomicReference<Thread> mutator, final K key, final int keyHash,
        final int shift, final SetResult<K> details) {
      final int mask = mask(keyHash, shift);
      final int bitpos = bitpos(mask);

      if ((dataMap() & bitpos) != 0) { // inplace value
        final int dataIndex = dataIndex(bitpos);
        final K currentKey = getKey(dataIndex);

        if (currentKey.equals(key)) {
          return this;
        } else {
          final CompactSetNode<K> subNodeNew = mergeTwoKeyValPairs(currentKey,
              transformHashCode(currentKey.hashCode()), key, keyHash, shift + BIT_PARTITION_SIZE);

          details.modified();
          return copyAndMigrateFromInlineToNode(mutator, bitpos, subNodeNew);
        }
      } else if ((nodeMap() & bitpos) != 0) { // node (not value)
        final CompactSetNode<K> subNode = nodeAt(bitpos);
        final CompactSetNode<K> subNodeNew =
            subNode.updated(mutator, key, keyHash, shift + BIT_PARTITION_SIZE, details);

        if (details.isModified()) {
          return copyAndSetNode(mutator, bitpos, subNodeNew);
        } else {
          return this;
        }
      } else {
        // no value
        details.modified();
        return copyAndInsertValue(mutator, bitpos, key);
      }
    }

    @Override
    CompactSetNode<K> updated(final AtomicReference<Thread> mutator, final K key, final int keyHash,
        final int shift, final SetResult<K> details, final Comparator<Object> cmp) {
      final int mask = mask(keyHash, shift);
      final int bitpos = bitpos(mask);

      if ((dataMap() & bitpos) != 0) { // inplace value
        final int dataIndex = dataIndex(bitpos);
        final K currentKey = getKey(dataIndex);

        if (cmp.compare(currentKey, key) == 0) {
          return this;
        } else {
          final CompactSetNode<K> subNodeNew = mergeTwoKeyValPairs(currentKey,
              transformHashCode(currentKey.hashCode()), key, keyHash, shift + BIT_PARTITION_SIZE);

          details.modified();
          return copyAndMigrateFromInlineToNode(mutator, bitpos, subNodeNew);
        }
      } else if ((nodeMap() & bitpos) != 0) { // node (not value)
        final CompactSetNode<K> subNode = nodeAt(bitpos);
        final CompactSetNode<K> subNodeNew =
            subNode.updated(mutator, key, keyHash, shift + BIT_PARTITION_SIZE, details, cmp);

        if (details.isModified()) {
          return copyAndSetNode(mutator, bitpos, subNodeNew);
        } else {
          return this;
        }
      } else {
        // no value
        details.modified();
        return copyAndInsertValue(mutator, bitpos, key);
      }
    }

    @Override
    CompactSetNode<K> removed(final AtomicReference<Thread> mutator, final K key, final int keyHash,
        final int shift, final SetResult<K> details) {
      final int mask = mask(keyHash, shift);
      final int bitpos = bitpos(mask);

      if ((dataMap() & bitpos) != 0) { // inplace value
        final int dataIndex = dataIndex(bitpos);

        if (getKey(dataIndex).equals(key)) {
          details.modified();

          if (this.payloadArity() == 2 && this.nodeArity() == 0) {
            /*
             * Create new node with remaining pair. The new node will a) either become the new root
             * returned, or b) unwrapped and inlined during returning.
             */
            final int newDataMap =
                (shift == 0) ? (int) (dataMap() ^ bitpos) : bitpos(mask(keyHash, 0));

            if (dataIndex == 0) {
              return CompactSetNode.<K>nodeOf(mutator, 0, newDataMap, getKey(1));
            } else {
              return CompactSetNode.<K>nodeOf(mutator, 0, newDataMap, getKey(0));
            }
          } else {
            return copyAndRemoveValue(mutator, bitpos);
          }
        } else {
          return this;
        }
      } else if ((nodeMap() & bitpos) != 0) { // node (not value)
        final CompactSetNode<K> subNode = nodeAt(bitpos);
        final CompactSetNode<K> subNodeNew =
            subNode.removed(mutator, key, keyHash, shift + BIT_PARTITION_SIZE, details);

        if (!details.isModified()) {
          return this;
        }

        switch (subNodeNew.sizePredicate()) {
          case 0: {
            throw new IllegalStateException("Sub-node must have at least one element.");
          }
          case 1: {
            if (this.payloadArity() == 0 && this.nodeArity() == 1) {
              // escalate (singleton or empty) result
              return subNodeNew;
            } else {
              // inline value (move to front)
              return copyAndMigrateFromNodeToInline(mutator, bitpos, subNodeNew);
            }
          }
          default: {
            // modify current node (set replacement node)
            return copyAndSetNode(mutator, bitpos, subNodeNew);
          }
        }
      }

      return this;
    }

    @Override
    CompactSetNode<K> removed(final AtomicReference<Thread> mutator, final K key, final int keyHash,
        final int shift, final SetResult<K> details, final Comparator<Object> cmp) {
      final int mask = mask(keyHash, shift);
      final int bitpos = bitpos(mask);

      if ((dataMap() & bitpos) != 0) { // inplace value
        final int dataIndex = dataIndex(bitpos);

        if (cmp.compare(getKey(dataIndex), key) == 0) {
          details.modified();

          if (this.payloadArity() == 2 && this.nodeArity() == 0) {
            /*
             * Create new node with remaining pair. The new node will a) either become the new root
             * returned, or b) unwrapped and inlined during returning.
             */
            final int newDataMap =
                (shift == 0) ? (int) (dataMap() ^ bitpos) : bitpos(mask(keyHash, 0));

            if (dataIndex == 0) {
              return CompactSetNode.<K>nodeOf(mutator, 0, newDataMap, getKey(1));
            } else {
              return CompactSetNode.<K>nodeOf(mutator, 0, newDataMap, getKey(0));
            }
          } else {
            return copyAndRemoveValue(mutator, bitpos);
          }
        } else {
          return this;
        }
      } else if ((nodeMap() & bitpos) != 0) { // node (not value)
        final CompactSetNode<K> subNode = nodeAt(bitpos);
        final CompactSetNode<K> subNodeNew =
            subNode.removed(mutator, key, keyHash, shift + BIT_PARTITION_SIZE, details, cmp);

        if (!details.isModified()) {
          return this;
        }

        switch (subNodeNew.sizePredicate()) {
          case 0: {
            throw new IllegalStateException("Sub-node must have at least one element.");
          }
          case 1: {
            if (this.payloadArity() == 0 && this.nodeArity() == 1) {
              // escalate (singleton or empty) result
              return subNodeNew;
            } else {
              // inline value (move to front)
              return copyAndMigrateFromNodeToInline(mutator, bitpos, subNodeNew);
            }
          }
          default: {
            // modify current node (set replacement node)
            return copyAndSetNode(mutator, bitpos, subNodeNew);
          }
        }
      }

      return this;
    }

    /**
     * @return 0 <= mask <= 2^BIT_PARTITION_SIZE - 1
     */
    static byte recoverMask(int map, byte i_th) {
      assert 1 <= i_th && i_th <= 32;

      byte cnt1 = 0;
      byte mask = 0;

      while (mask < 32) {
        if ((map & 0x01) == 0x01) {
          cnt1 += 1;

          if (cnt1 == i_th) {
            return mask;
          }
        }

        map = map >> 1;
        mask += 1;
      }

      assert cnt1 != i_th;
      throw new RuntimeException("Called with invalid arguments.");
    }

    @Override
    public String toString() {
      final StringBuilder bldr = new StringBuilder();
      bldr.append('[');

      for (byte i = 0; i < payloadArity(); i++) {
        final byte pos = recoverMask(dataMap(), (byte) (i + 1));
        bldr.append(String.format("@%d<#%d>", pos, Objects.hashCode(getKey(i))));

        if (!((i + 1) == payloadArity())) {
          bldr.append(", ");
        }
      }

      if (payloadArity() > 0 && nodeArity() > 0) {
        bldr.append(", ");
      }

      for (byte i = 0; i < nodeArity(); i++) {
        final byte pos = recoverMask(nodeMap(), (byte) (i + 1));
        bldr.append(String.format("@%d: %s", pos, getNode(i)));

        if (!((i + 1) == nodeArity())) {
          bldr.append(", ");
        }
      }

      bldr.append(']');
      return bldr.toString();
    }

  }

  protected static abstract class CompactMixedSetNode<K> extends CompactSetNode<K> {

    private final int nodeMap;
    private final int dataMap;

    CompactMixedSetNode(final AtomicReference<Thread> mutator, final int nodeMap,
        final int dataMap) {
      this.nodeMap = nodeMap;
      this.dataMap = dataMap;
    }

    @Override
    public int nodeMap() {
      return nodeMap;
    }

    @Override
    public int dataMap() {
      return dataMap;
    }

  }

  private static final class BitmapIndexedSetNode<K> extends CompactMixedSetNode<K> {

    final AtomicReference<Thread> mutator;
    final Object[] nodes;

    private BitmapIndexedSetNode(final AtomicReference<Thread> mutator, final int nodeMap,
        final int dataMap, final Object[] nodes) {
      super(mutator, nodeMap, dataMap);

      this.mutator = mutator;
      this.nodes = nodes;

      if (DEBUG) {

        assert (TUPLE_LENGTH * java.lang.Integer.bitCount(dataMap)
            + java.lang.Integer.bitCount(nodeMap) == nodes.length);

        for (int i = 0; i < TUPLE_LENGTH * payloadArity(); i++) {
          assert ((nodes[i] instanceof CompactSetNode) == false);
        }
        for (int i = TUPLE_LENGTH * payloadArity(); i < nodes.length; i++) {
          assert ((nodes[i] instanceof CompactSetNode) == true);
        }
      }

      assert nodeInvariant();
    }

    @SuppressWarnings("unchecked")
    @Override
    K getKey(final int index) {
      return (K) nodes[TUPLE_LENGTH * index];
    }

    @SuppressWarnings("unchecked")
    @Override
    CompactSetNode<K> getNode(final int index) {
      return (CompactSetNode<K>) nodes[nodes.length - 1 - index];
    }

    @Override
    boolean hasPayload() {
      return dataMap() != 0;
    }

    @Override
    int payloadArity() {
      return java.lang.Integer.bitCount(dataMap());
    }

    @Override
    boolean hasNodes() {
      return nodeMap() != 0;
    }

    @Override
    int nodeArity() {
      return java.lang.Integer.bitCount(nodeMap());
    }

    @Override
    Object getSlot(final int index) {
      return nodes[index];
    }

    @Override
    boolean hasSlots() {
      return nodes.length != 0;
    }

    @Override
    int slotArity() {
      return nodes.length;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 0;
      result = prime * result + (dataMap());
      result = prime * result + (dataMap());
      result = prime * result + Arrays.hashCode(nodes);
      return result;
    }

    @Override
    public boolean equals(final Object other) {
      if (null == other) {
        return false;
      }
      if (this == other) {
        return true;
      }
      if (getClass() != other.getClass()) {
        return false;
      }
      BitmapIndexedSetNode<?> that = (BitmapIndexedSetNode<?>) other;
      if (nodeMap() != that.nodeMap()) {
        return false;
      }
      if (dataMap() != that.dataMap()) {
        return false;
      }
      if (!ArrayUtils.equals(nodes, that.nodes)) {
        return false;
      }
      return true;
    }

    @Override
    byte sizePredicate() {
      if (this.nodeArity() == 0) {
        switch (this.payloadArity()) {
          case 0:
            return SIZE_EMPTY;
          case 1:
            return SIZE_ONE;
          default:
            return SIZE_MORE_THAN_ONE;
        }
      } else {
        return SIZE_MORE_THAN_ONE;
      }
    }

    @Override
    CompactSetNode<K> copyAndSetNode(final AtomicReference<Thread> mutator, final int bitpos,
        final CompactSetNode<K> node) {

      final int idx = this.nodes.length - 1 - nodeIndex(bitpos);

      if (isAllowedToEdit(this.mutator, mutator)) {
        // no copying if already editable
        this.nodes[idx] = node;
        return this;
      } else {
        final Object[] src = this.nodes;
        final Object[] dst = new Object[src.length];

        // copy 'src' and set 1 element(s) at position 'idx'
        System.arraycopy(src, 0, dst, 0, src.length);
        dst[idx + 0] = node;

        return nodeOf(mutator, nodeMap(), dataMap(), dst);
      }
    }

    @Override
    CompactSetNode<K> copyAndInsertValue(final AtomicReference<Thread> mutator, final int bitpos,
        final K key) {
      final int idx = TUPLE_LENGTH * dataIndex(bitpos);

      final Object[] src = this.nodes;
      final Object[] dst = new Object[src.length + 1];

      // copy 'src' and insert 1 element(s) at position 'idx'
      System.arraycopy(src, 0, dst, 0, idx);
      dst[idx + 0] = key;
      System.arraycopy(src, idx, dst, idx + 1, src.length - idx);

      return nodeOf(mutator, nodeMap(), dataMap() | bitpos, dst);
    }

    @Override
    CompactSetNode<K> copyAndRemoveValue(final AtomicReference<Thread> mutator, final int bitpos) {
      final int idx = TUPLE_LENGTH * dataIndex(bitpos);

      final Object[] src = this.nodes;
      final Object[] dst = new Object[src.length - 1];

      // copy 'src' and remove 1 element(s) at position 'idx'
      System.arraycopy(src, 0, dst, 0, idx);
      System.arraycopy(src, idx + 1, dst, idx, src.length - idx - 1);

      return nodeOf(mutator, nodeMap(), dataMap() ^ bitpos, dst);
    }

    @Override
    CompactSetNode<K> copyAndMigrateFromInlineToNode(final AtomicReference<Thread> mutator,
        final int bitpos, final CompactSetNode<K> node) {

      final int idxOld = TUPLE_LENGTH * dataIndex(bitpos);
      final int idxNew = this.nodes.length - TUPLE_LENGTH - nodeIndex(bitpos);

      final Object[] src = this.nodes;
      final Object[] dst = new Object[src.length - 1 + 1];

      // copy 'src' and remove 1 element(s) at position 'idxOld' and
      // insert 1 element(s) at position 'idxNew' (TODO: carefully test)
      assert idxOld <= idxNew;
      System.arraycopy(src, 0, dst, 0, idxOld);
      System.arraycopy(src, idxOld + 1, dst, idxOld, idxNew - idxOld);
      dst[idxNew + 0] = node;
      System.arraycopy(src, idxNew + 1, dst, idxNew + 1, src.length - idxNew - 1);

      return nodeOf(mutator, nodeMap() | bitpos, dataMap() ^ bitpos, dst);
    }

    @Override
    CompactSetNode<K> copyAndMigrateFromNodeToInline(final AtomicReference<Thread> mutator,
        final int bitpos, final CompactSetNode<K> node) {

      final int idxOld = this.nodes.length - 1 - nodeIndex(bitpos);
      final int idxNew = TUPLE_LENGTH * dataIndex(bitpos);

      final Object[] src = this.nodes;
      final Object[] dst = new Object[src.length - 1 + 1];

      // copy 'src' and remove 1 element(s) at position 'idxOld' and
      // insert 1 element(s) at position 'idxNew' (TODO: carefully test)
      assert idxOld >= idxNew;
      System.arraycopy(src, 0, dst, 0, idxNew);
      dst[idxNew + 0] = node.getKey(0);
      System.arraycopy(src, idxNew, dst, idxNew + 1, idxOld - idxNew);
      System.arraycopy(src, idxOld + 1, dst, idxOld + 1, src.length - idxOld - 1);

      return nodeOf(mutator, nodeMap() ^ bitpos, dataMap() | bitpos, dst);
    }

  }

  private static final class HashCollisionSetNode<K> extends CompactSetNode<K> {
    private final K[] keys;

    private final int hash;

    HashCollisionSetNode(final int hash, final K[] keys) {
      this.keys = keys;

      this.hash = hash;

      assert payloadArity() >= 2;
    }

    @Override
    boolean contains(final K key, final int keyHash, final int shift) {
      if (this.hash == keyHash) {
        for (K k : keys) {
          if (k.equals(key)) {
            return true;
          }
        }
      }
      return false;
    }

    @Override
    boolean contains(final K key, final int keyHash, final int shift,
        final Comparator<Object> cmp) {
      if (this.hash == keyHash) {
        for (K k : keys) {
          if (cmp.compare(k, key) == 0) {
            return true;
          }
        }
      }
      return false;
    }

    @Override
    Optional<K> findByKey(final K key, final int keyHash, final int shift) {
      for (int i = 0; i < keys.length; i++) {
        final K _key = keys[i];
        if (key.equals(_key)) {
          return Optional.of(_key);
        }
      }
      return Optional.empty();
    }

    @Override
    Optional<K> findByKey(final K key, final int keyHash, final int shift,
        final Comparator<Object> cmp) {
      for (int i = 0; i < keys.length; i++) {
        final K _key = keys[i];
        if (cmp.compare(key, _key) == 0) {
          return Optional.of(_key);
        }
      }
      return Optional.empty();
    }

    @Override
    CompactSetNode<K> updated(final AtomicReference<Thread> mutator, final K key, final int keyHash,
        final int shift, final SetResult<K> details) {
      assert this.hash == keyHash;

      for (int idx = 0; idx < keys.length; idx++) {
        if (keys[idx].equals(key)) {
          return this;
        }
      }

      @SuppressWarnings("unchecked")
      final K[] keysNew = (K[]) new Object[this.keys.length + 1];

      // copy 'this.keys' and insert 1 element(s) at position
      // 'keys.length'
      System.arraycopy(this.keys, 0, keysNew, 0, keys.length);
      keysNew[keys.length + 0] = key;
      System.arraycopy(this.keys, keys.length, keysNew, keys.length + 1,
          this.keys.length - keys.length);

      details.modified();
      return new HashCollisionSetNode<>(keyHash, keysNew);
    }

    @Override
    CompactSetNode<K> updated(final AtomicReference<Thread> mutator, final K key, final int keyHash,
        final int shift, final SetResult<K> details, final Comparator<Object> cmp) {
      assert this.hash == keyHash;

      for (int idx = 0; idx < keys.length; idx++) {
        if (cmp.compare(keys[idx], key) == 0) {
          return this;
        }
      }

      @SuppressWarnings("unchecked")
      final K[] keysNew = (K[]) new Object[this.keys.length + 1];

      // copy 'this.keys' and insert 1 element(s) at position
      // 'keys.length'
      System.arraycopy(this.keys, 0, keysNew, 0, keys.length);
      keysNew[keys.length + 0] = key;
      System.arraycopy(this.keys, keys.length, keysNew, keys.length + 1,
          this.keys.length - keys.length);

      details.modified();
      return new HashCollisionSetNode<>(keyHash, keysNew);
    }

    @Override
    CompactSetNode<K> removed(final AtomicReference<Thread> mutator, final K key, final int keyHash,
        final int shift, final SetResult<K> details) {
      for (int idx = 0; idx < keys.length; idx++) {
        if (keys[idx].equals(key)) {
          details.modified();

          if (this.arity() == 1) {
            return nodeOf(mutator);
          } else if (this.arity() == 2) {
            /*
             * Create root node with singleton element. This node will be a) either be the new root
             * returned, or b) unwrapped and inlined.
             */
            final K theOtherKey = (idx == 0) ? keys[1] : keys[0];

            return CompactSetNode.<K>nodeOf(mutator).updated(mutator, theOtherKey, keyHash, 0,
                details);
          } else {
            @SuppressWarnings("unchecked")
            final K[] keysNew = (K[]) new Object[this.keys.length - 1];

            // copy 'this.keys' and remove 1 element(s) at position
            // 'idx'
            System.arraycopy(this.keys, 0, keysNew, 0, idx);
            System.arraycopy(this.keys, idx + 1, keysNew, idx, this.keys.length - idx - 1);

            return new HashCollisionSetNode<>(keyHash, keysNew);
          }
        }
      }
      return this;
    }

    @Override
    CompactSetNode<K> removed(final AtomicReference<Thread> mutator, final K key, final int keyHash,
        final int shift, final SetResult<K> details, final Comparator<Object> cmp) {
      for (int idx = 0; idx < keys.length; idx++) {
        if (cmp.compare(keys[idx], key) == 0) {
          details.modified();

          if (this.arity() == 1) {
            return nodeOf(mutator);
          } else if (this.arity() == 2) {
            /*
             * Create root node with singleton element. This node will be a) either be the new root
             * returned, or b) unwrapped and inlined.
             */
            final K theOtherKey = (idx == 0) ? keys[1] : keys[0];

            return CompactSetNode.<K>nodeOf(mutator).updated(mutator, theOtherKey, keyHash, 0,
                details, cmp);
          } else {
            @SuppressWarnings("unchecked")
            final K[] keysNew = (K[]) new Object[this.keys.length - 1];

            // copy 'this.keys' and remove 1 element(s) at position
            // 'idx'
            System.arraycopy(this.keys, 0, keysNew, 0, idx);
            System.arraycopy(this.keys, idx + 1, keysNew, idx, this.keys.length - idx - 1);

            return new HashCollisionSetNode<>(keyHash, keysNew);
          }
        }
      }
      return this;
    }

    @Override
    boolean hasPayload() {
      return true;
    }

    @Override
    int payloadArity() {
      return keys.length;
    }

    @Override
    boolean hasNodes() {
      return false;
    }

    @Override
    int nodeArity() {
      return 0;
    }

    @Override
    int arity() {
      return payloadArity();
    }

    @Override
    byte sizePredicate() {
      return SIZE_MORE_THAN_ONE;
    }

    @Override
    K getKey(final int index) {
      return keys[index];
    }

    @Override
    public CompactSetNode<K> getNode(int index) {
      throw new IllegalStateException("Is leaf node.");
    }

    @Override
    Object getSlot(final int index) {
      throw new UnsupportedOperationException();
    }

    @Override
    boolean hasSlots() {
      throw new UnsupportedOperationException();
    }

    @Override
    int slotArity() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 0;
      result = prime * result + hash;
      result = prime * result + Arrays.hashCode(keys);
      return result;
    }

    @Override
    public boolean equals(Object other) {
      if (null == other) {
        return false;
      }
      if (this == other) {
        return true;
      }
      if (getClass() != other.getClass()) {
        return false;
      }

      HashCollisionSetNode<?> that = (HashCollisionSetNode<?>) other;

      if (hash != that.hash) {
        return false;
      }

      if (arity() != that.arity()) {
        return false;
      }

      /*
       * Linear scan for each key, because of arbitrary element order.
       */
      outerLoop: for (int i = 0; i < that.payloadArity(); i++) {
        final Object otherKey = that.getKey(i);

        for (int j = 0; j < keys.length; j++) {
          final K key = keys[j];

          if (key.equals(otherKey)) {
            continue outerLoop;
          }
        }
        return false;

      }

      return true;
    }

    @Override
    CompactSetNode<K> copyAndInsertValue(final AtomicReference<Thread> mutator, final int bitpos,
        final K key) {
      throw new UnsupportedOperationException();
    }

    @Override
    CompactSetNode<K> copyAndRemoveValue(final AtomicReference<Thread> mutator, final int bitpos) {
      throw new UnsupportedOperationException();
    }

    @Override
    CompactSetNode<K> copyAndSetNode(final AtomicReference<Thread> mutator, final int bitpos,
        final CompactSetNode<K> node) {
      throw new UnsupportedOperationException();
    }

    @Override
    CompactSetNode<K> copyAndMigrateFromInlineToNode(final AtomicReference<Thread> mutator,
        final int bitpos, final CompactSetNode<K> node) {
      throw new UnsupportedOperationException();
    }

    @Override
    CompactSetNode<K> copyAndMigrateFromNodeToInline(final AtomicReference<Thread> mutator,
        final int bitpos, final CompactSetNode<K> node) {
      throw new UnsupportedOperationException();
    }

    @Override
    int nodeMap() {
      throw new UnsupportedOperationException();
    }

    @Override
    int dataMap() {
      throw new UnsupportedOperationException();
    }

  }

  /**
   * Iterator skeleton that uses a fixed stack in depth.
   */
  private static abstract class AbstractSetIterator<K> {

    private static final int MAX_DEPTH = 7;

    protected int currentValueCursor;
    protected int currentValueLength;
    protected AbstractSetNode<K> currentValueNode;

    private int currentStackLevel = -1;
    private final int[] nodeCursorsAndLengths = new int[MAX_DEPTH * 2];

    @SuppressWarnings("unchecked")
    AbstractSetNode<K>[] nodes = new AbstractSetNode[MAX_DEPTH];

    AbstractSetIterator(AbstractSetNode<K> rootNode) {
      if (rootNode.hasNodes()) {
        currentStackLevel = 0;

        nodes[0] = rootNode;
        nodeCursorsAndLengths[0] = 0;
        nodeCursorsAndLengths[1] = rootNode.nodeArity();
      }

      if (rootNode.hasPayload()) {
        currentValueNode = rootNode;
        currentValueCursor = 0;
        currentValueLength = rootNode.payloadArity();
      }
    }

    /*
     * search for next node that contains values
     */
    private boolean searchNextValueNode() {
      while (currentStackLevel >= 0) {
        final int currentCursorIndex = currentStackLevel * 2;
        final int currentLengthIndex = currentCursorIndex + 1;

        final int nodeCursor = nodeCursorsAndLengths[currentCursorIndex];
        final int nodeLength = nodeCursorsAndLengths[currentLengthIndex];

        if (nodeCursor < nodeLength) {
          final AbstractSetNode<K> nextNode = nodes[currentStackLevel].getNode(nodeCursor);
          nodeCursorsAndLengths[currentCursorIndex]++;

          if (nextNode.hasNodes()) {
            /*
             * put node on next stack level for depth-first traversal
             */
            final int nextStackLevel = ++currentStackLevel;
            final int nextCursorIndex = nextStackLevel * 2;
            final int nextLengthIndex = nextCursorIndex + 1;

            nodes[nextStackLevel] = nextNode;
            nodeCursorsAndLengths[nextCursorIndex] = 0;
            nodeCursorsAndLengths[nextLengthIndex] = nextNode.nodeArity();
          }

          if (nextNode.hasPayload()) {
            /*
             * found next node that contains values
             */
            currentValueNode = nextNode;
            currentValueCursor = 0;
            currentValueLength = nextNode.payloadArity();
            return true;
          }
        } else {
          currentStackLevel--;
        }
      }

      return false;
    }

    public boolean hasNext() {
      if (currentValueCursor < currentValueLength) {
        return true;
      } else {
        return searchNextValueNode();
      }
    }

    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  protected static class SetKeyIterator<K> extends AbstractSetIterator<K> implements Iterator<K> {

    SetKeyIterator(AbstractSetNode<K> rootNode) {
      super(rootNode);
    }

    @Override
    public K next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      } else {
        return currentValueNode.getKey(currentValueCursor++);
      }
    }

  }

  /**
   * Iterator that first iterates over inlined-values and then continues depth first recursively.
   */
  private static class SetNodeIterator<K> implements Iterator<AbstractSetNode<K>> {

    final Deque<Iterator<? extends AbstractSetNode<K>>> nodeIteratorStack;

    SetNodeIterator(AbstractSetNode<K> rootNode) {
      nodeIteratorStack = new ArrayDeque<>();
      nodeIteratorStack.push(Collections.singleton(rootNode).iterator());
    }

    @Override
    public boolean hasNext() {
      while (true) {
        if (nodeIteratorStack.isEmpty()) {
          return false;
        } else {
          if (nodeIteratorStack.peek().hasNext()) {
            return true;
          } else {
            nodeIteratorStack.pop();
            continue;
          }
        }
      }
    }

    @Override
    public AbstractSetNode<K> next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }

      AbstractSetNode<K> innerNode = nodeIteratorStack.peek().next();

      if (innerNode.hasNodes()) {
        nodeIteratorStack.push(innerNode.nodeIterator());
      }

      return innerNode;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  static final class TransientTrieSet<K> implements Set.Transient<K> {
    final private AtomicReference<Thread> mutator;
    private AbstractSetNode<K> rootNode;
    private int cachedHashCode;
    private int cachedSize;

    TransientTrieSet(TrieSet<K> trieSet) {
      this.mutator = new AtomicReference<Thread>(Thread.currentThread());
      this.rootNode = trieSet.rootNode;
      this.cachedHashCode = trieSet.cachedHashCode;
      this.cachedSize = trieSet.cachedSize;
      if (DEBUG) {
        assert checkHashCodeAndSize(cachedHashCode, cachedSize);
      }
    }

    private boolean checkHashCodeAndSize(final int targetHash, final int targetSize) {
      int hash = 0;
      int size = 0;

      for (Iterator<K> it = keyIterator(); it.hasNext();) {
        final K key = it.next();

        hash += key.hashCode();
        size += 1;
      }

      return hash == targetHash && size == targetSize;
    }

    @Override
    public boolean contains(final Object o) {
      try {
        @SuppressWarnings("unchecked")
        final K key = (K) o;
        return rootNode.contains(key, transformHashCode(key.hashCode()), 0);
      } catch (ClassCastException unused) {
        return false;
      }
    }

    @Override
    public Optional<K> apply(K key) {
      return rootNode.findByKey(key, transformHashCode(key.hashCode()), 0);
    }

    public K get(final Object o) {
      try {
        @SuppressWarnings("unchecked")
        final K key = (K) o;
        return apply(key).orElse(null);
      } catch (ClassCastException unused) {
        return null;
      }
    }

    @Override
    public boolean insert(final K key) {
      if (mutator.get() == null) {
        throw new IllegalStateException("Transient already frozen.");
      }

      final int keyHash = key.hashCode();
      final SetResult<K> details = SetResult.unchanged();

      final CompactSetNode<K> newRootNode =
          rootNode.updated(mutator, key, transformHashCode(keyHash), 0, details);

      if (details.isModified()) {

        rootNode = newRootNode;
        cachedHashCode ^= keyHash;
        cachedSize += 1;

        if (DEBUG) {
          assert checkHashCodeAndSize(cachedHashCode, cachedSize);
        }
        return true;

      }

      if (DEBUG) {
        assert checkHashCodeAndSize(cachedHashCode, cachedSize);
      }
      return false;
    }

    @Override
    public boolean remove(final K key) {
      if (mutator.get() == null) {
        throw new IllegalStateException("Transient already frozen.");
      }

      final int keyHash = key.hashCode();
      final SetResult<K> details = SetResult.unchanged();

      final CompactSetNode<K> newRootNode =
          rootNode.removed(mutator, key, transformHashCode(keyHash), 0, details);

      if (details.isModified()) {
        rootNode = newRootNode;
        cachedHashCode = cachedHashCode ^ keyHash;
        cachedSize = cachedSize - 1;

        if (DEBUG) {
          assert checkHashCodeAndSize(cachedHashCode, cachedSize);
        }
        return true;
      }

      if (DEBUG) {
        assert checkHashCodeAndSize(cachedHashCode, cachedSize);
      }

      return false;
    }

    @Override
    public boolean insertAll(final Set<? extends K> set) {
      boolean modified = false;

      for (final K key : set) {
        modified |= this.insert(key);
      }

      return modified;
    }

    @Override
    public boolean removeAll(final Set<? extends K> set) {
      boolean modified = false;

      for (final K key : set) {
        modified |= this.remove(key);
      }

      return modified;
    }

    @Override
    public boolean retainAll(final Set<? extends K> set) {
      boolean modified = false;

      Iterator<K> thisIterator = iterator();
      while (thisIterator.hasNext()) {
        if (!set.contains(thisIterator.next())) {
          thisIterator.remove();
          modified = true;
        }
      }

      return modified;
    }

    @Override
    public long size() {
      return cachedSize;
    }

    @Override
    public boolean isEmpty() {
      return cachedSize == 0;
    }

    @Override
    public Iterator<K> iterator() {
      return keyIterator();
    }

    private Iterator<K> keyIterator() {
      return new TransientSetKeyIterator<>(this);
    }

    public static class TransientSetKeyIterator<K> extends SetKeyIterator<K> {
      final TransientTrieSet<K> collection;
      K lastKey;

      public TransientSetKeyIterator(final TransientTrieSet<K> collection) {
        super(collection.rootNode);
        this.collection = collection;
      }

      @Override
      public K next() {
        return lastKey = super.next();
      }

      @Override
      public void remove() {
        // TODO: test removal at iteration rigorously
        collection.remove(lastKey);
      }
    }

    @Override
    public boolean equals(final Object other) {
      if (other == this) {
        return true;
      }
      if (other == null) {
        return false;
      }

      if (other instanceof TransientTrieSet) {
        TransientTrieSet<?> that = (TransientTrieSet<?>) other;

        if (this.cachedSize != that.cachedSize) {
          return false;
        }

        if (this.cachedHashCode != that.cachedHashCode) {
          return false;
        }

        return rootNode.equals(that.rootNode);
      } else if (other instanceof Set) {
        Set that = (Set) other;

        if (this.size() != that.size())
          return false;

        return contains(that);
      }

      return false;
    }

    @Override
    public int hashCode() {
      return cachedHashCode;
    }

    // @Override
    // public java.util.Set<K> asJdkCollection() {
    // throw new UnsupportedOperationException("Not yet implemented.");
    // }

    @Override
    public Set.Immutable<K> asImmutable() {
      if (mutator.get() == null) {
        throw new IllegalStateException("Transient already frozen.");
      }

      mutator.set(null);
      return new TrieSet<K>(rootNode, cachedHashCode, cachedSize);
    }
  }

  private static class TrieSetAsImmutableJdkCollection<K> extends java.util.AbstractSet<K> {
    private final AbstractSetNode<K> rootNode;
    private final int cachedSize;

    private TrieSetAsImmutableJdkCollection(TrieSet<K> original) {
      this.rootNode = original.rootNode;
      this.cachedSize = original.cachedSize;
    }

    private TrieSetAsImmutableJdkCollection(TransientTrieSet<K> original) {
      this.rootNode = original.rootNode;
      this.cachedSize = original.cachedSize;
    }

    @Override
    public boolean contains(final Object o) {
      try {
        @SuppressWarnings("unchecked")
        final K key = (K) o;
        return rootNode.contains(key, transformHashCode(key.hashCode()), 0);
      } catch (ClassCastException unused) {
        return false;
      }
    }

    @Override
    public Iterator<K> iterator() {
      return new SetKeyIterator<>(rootNode);
    }

    @Override
    public int size() {
      return cachedSize;
    }
  }

}
