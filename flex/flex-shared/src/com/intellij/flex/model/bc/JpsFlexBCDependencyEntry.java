package com.intellij.flex.model.bc;

import org.jetbrains.annotations.Nullable;

public interface JpsFlexBCDependencyEntry extends JpsFlexDependencyEntry {

  @Nullable
  JpsFlexBuildConfiguration getBC();
}
