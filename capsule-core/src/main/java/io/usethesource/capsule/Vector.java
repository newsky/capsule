/**
 * Copyright (c) Michael Steindorfer <Centrum Wiskunde & Informatica> and Contributors.
 * All rights reserved.
 *
 * This file is licensed under the BSD 2-Clause License, which accompanies this project
 * and is available under https://opensource.org/licenses/BSD-2-Clause.
 */
package io.usethesource.capsule;

public interface Vector<K> {

  interface Immutable<K> extends Vector<K> {

  }

  interface Transient<K> extends Vector<K> {

  }

}
