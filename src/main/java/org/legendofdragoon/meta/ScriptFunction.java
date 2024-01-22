package org.legendofdragoon.meta;

import legend.game.scripting.ScriptEnum;
import legend.game.scripting.ScriptParam;

public class ScriptFunction {
  public final String name;
  public final String description;
  public final ScriptParam[] params;
  public final ScriptEnum[] enums;

  public ScriptFunction(final String name, final String description, final ScriptParam[] params, final ScriptEnum[] enums) {
    this.name = name;
    this.description = description;
    this.params = params;
    this.enums = enums;
  }
}
