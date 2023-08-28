package scraper;

import legend.game.scripting.ScriptParam;

public class ScriptFunction {
  public final String name;
  public final String description;
  public final ScriptParam[] params;

  public ScriptFunction(final String name, final String description, final ScriptParam[] params) {
    this.name = name;
    this.description = description;
    this.params = params;
  }
}
