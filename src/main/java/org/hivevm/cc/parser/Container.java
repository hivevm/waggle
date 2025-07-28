// Copyright 2024 HiveVM.ORG. All rights reserved.
// SPDX-License-Identifier: BSD-3-Clause

package org.hivevm.cc.parser;

import org.hivevm.cc.model.Production;

/**
 * An object container. Used to pass references to objects as parameter
 */
class Container {

  private Production member;

  <P extends Production> P get() {
    return (P)this.member;
  }

  <P extends Production> P set(P member) {
    this.member = member;
    return member;
  }
}
